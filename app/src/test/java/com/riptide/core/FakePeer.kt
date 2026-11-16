package com.riptide.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread
import org.json.JSONObject

/**
 * A test peer that speaks the Riptide wire protocol the way the PC
 * v1.6.0 does, over a real loopback TLS connection on port 50000.
 * One connection at a time. A reader thread owns all socket reads
 * and dispatches control messages; sends go through one send lock,
 * exactly like the PC's Session. Used by the loopback engine tests
 * (cancel in both directions, disconnect regression): the phone
 * under test connects to this peer with SessionManager.connect.
 *
 * The peer's cancel behaviour mirrors PC v1.6.0: a receiver-initiated
 * cancel is answered by padding zeros to the declared size and then
 * sending cancel_ack; a sender-initiated cancel is spotted by a
 * short peek after the payload, before the SHA check and ack.
 */
class FakePeer {
    val deviceName = "TestPC"

    /** Outcome of a file the peer sent, as observed by the peer. */
    sealed interface PeerSendOutcome {
        object Sent : PeerSendOutcome
        object Cancelled : PeerSendOutcome
        data class Failed(val reason: String) : PeerSendOutcome
    }

    /** A file the peer received: the bytes as delivered (zeros past
     * the cancel point when the sender padded), whether the SHA-256
     * matched, and whether the peer treated it as cancelled. */
    data class ReceivedFile(
        val name: String,
        val bytes: ByteArray,
        val shaOk: Boolean,
        val cancelled: Boolean
    )

    private val identity = run {
        val kpg = java.security.KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()
        val der = IdentityManager(null).buildSelfSignedCert(kp)
        val cert = java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(der.inputStream()) as java.security.cert.X509Certificate
        Triple(kp, cert, der)
    }

    private var server: SSLServerSocket? = null
    private var conn: SSLSocket? = null
    private var acceptThread: Thread? = null
    private var readerThread: Thread? = null
    private val sendLock = Any()
    @Volatile private var running = false

    /** Names the phone has sent "cancel" for while this peer was sending. */
    val cancelsReceived = ConcurrentHashMap.newKeySet<String>()
    /** Names this peer's own send was asked to cancel (test hook). */
    private val cancelSendRequested = ConcurrentHashMap.newKeySet<String>()
    /** Ask this peer to cancel its own in-progress send of [name]. */
    fun cancelSend(name: String) {
        cancelSendRequested.add(name)
    }
    /** Send a "cancel" for a file the phone is sending (peer as receiver cancels). */
    fun sendCancelToPhone(name: String) {
        val s = conn ?: return
        synchronized(sendLock) {
            try {
                writeHeader(
                    s.outputStream,
                    JSONObject().apply {
                        put("type", "cancel")
                        put("name", name)
                    }.toString().toByteArray()
                )
            } catch (_: Exception) {}
        }
    }
    /** Names this peer sent "cancel_ack" for. */
    val cancelAcksSent = ConcurrentHashMap.newKeySet<String>()
    /** Files this peer received, by name. */
    val receivedFiles = ConcurrentHashMap<String, ReceivedFile>()
    /** Acks this peer sent, name -> ok. */
    val acksSent = ConcurrentHashMap<String, Boolean>()

    private val connectedLatch = CountDownLatch(1)
    private val outcomeByName = ConcurrentHashMap<String, CountDownLatch>()
    private val outcomeResult = ConcurrentHashMap<String, PeerSendOutcome>()

    /** True once the hello exchange has completed. */
    fun awaitConnected(timeoutMs: Long = 10_000): Boolean =
        connectedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun start() {
        val ctx = createHostSslContext(identity.first.private, identity.second)
        val srv = ctx.serverSocketFactory.createServerSocket(
            SESSION_PORT, 50, InetAddress.getByName("127.0.0.1")
        ) as SSLServerSocket
        srv.soTimeout = 10_000
        server = srv
        running = true
        acceptThread = thread(name = "fake-peer-accept", isDaemon = true) {
            while (running) {
                try {
                    val accepted = srv.accept() as SSLSocket
                    accepted.soTimeout = 0
                    accepted.keepAlive = true
                    accepted.startHandshake()
                    conn = accepted
                    handleConnection(accepted)
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }
    }

    fun stop() {
        running = false
        try { conn?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        acceptThread?.join(2000)
        readerThread?.join(2000)
    }

    /**
     * Send a file to the phone, in 128KB chunks, polling for a peer
     * cancel between chunks. On a cancel from the phone: pad zeros
     * to the declared size, send cancel_ack, and return Cancelled.
     * On a local cancel request ([cancelSend]): pad zeros, send
     * "cancel", wait for the phone's answer, and return Cancelled
     * when the phone confirms (or Sent when its ack arrived first
     * and completion won). Otherwise wait for the phone's ack (or
     * cancel_ack, when the phone cancelled its own receive after
     * the payload) and return Sent when the ack says ok.
     * [chunkDelayMs] slows the send down so tests can cancel
     * mid-transfer deterministically.
     */
    fun sendFile(name: String, bytes: ByteArray, chunkDelayMs: Long = 0): PeerSendOutcome {
        val s = conn ?: return PeerSendOutcome.Failed("not connected")
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val latch = CountDownLatch(1)
        outcomeByName[name] = latch
        outcomeResult.remove(name)
        cancelsReceived.remove(name)
        cancelSendRequested.remove(name)
        var waitForAnswer = false
        try {
            synchronized(sendLock) {
                writeHeader(
                    s.outputStream,
                    JSONObject().apply {
                        put("type", "file")
                        put("name", name)
                        put("size", bytes.size)
                        put("sha256", sha)
                    }.toString().toByteArray()
                )
                var sent = 0
                val buf = ByteArray(128 * 1024)
                while (sent < bytes.size) {
                    if (cancelsReceived.contains(name)) {
                        padZeros(s, (bytes.size - sent).toLong())
                        writeHeader(
                            s.outputStream,
                            JSONObject().apply {
                                put("type", "cancel_ack")
                                put("name", name)
                            }.toString().toByteArray()
                        )
                        cancelAcksSent.add(name)
                        return PeerSendOutcome.Cancelled
                    }
                    if (cancelSendRequested.contains(name)) {
                        padZeros(s, (bytes.size - sent).toLong())
                        writeHeader(
                            s.outputStream,
                            JSONObject().apply {
                                put("type", "cancel")
                                put("name", name)
                            }.toString().toByteArray()
                        )
                        // The phone answers cancel_ack (cancel won) or
                        // ack (it already completed: completion stands).
                        // Wait outside the send lock: the reader records
                        // the answer without sending.
                        waitForAnswer = true
                        break
                    }
                    val n = minOf(buf.size, bytes.size - sent)
                    bytes.copyInto(buf, 0, sent, sent + n)
                    s.outputStream.write(buf, 0, n)
                    sent += n
                    if (chunkDelayMs > 0) Thread.sleep(chunkDelayMs)
                }
                s.outputStream.flush()
            }
        } catch (e: Exception) {
            return PeerSendOutcome.Failed(e.message ?: e.toString())
        }
        if (waitForAnswer) {
            val answered = latch.await(30, TimeUnit.SECONDS)
            if (!answered) return PeerSendOutcome.Cancelled
            return outcomeResult[name] ?: PeerSendOutcome.Cancelled
        }
        // The phone answers with ack (received) or cancel_ack (it
        // cancelled its receive after the payload). The reader
        // thread records the outcome and releases this latch.
        val answered = latch.await(30, TimeUnit.SECONDS)
        if (!answered) return PeerSendOutcome.Failed("no answer from phone")
        return outcomeResult[name] ?: PeerSendOutcome.Failed("no outcome recorded")
    }

    private fun padZeros(s: SSLSocket, remaining: Long) {
        val zeros = ByteArray(1024 * 1024)
        var left = remaining
        while (left > 0) {
            val n = minOf(zeros.size.toLong(), left).toInt()
            s.outputStream.write(zeros, 0, n)
            left -= n
        }
        s.outputStream.flush()
    }

    private fun handleConnection(s: SSLSocket) {
        val framed = FramedReader(s.inputStream, s)
        try {
            // Hello exchange: the phone sends hello, this peer
            // answers hello_ok directly (no challenge in tests).
            val hello = JSONObject(String(framed.readHeader()))
            if (hello.optString("type") != "hello") return
            synchronized(sendLock) {
                writeHeader(
                    s.outputStream,
                    JSONObject().apply {
                        put("type", "hello_ok")
                        put("device", deviceName)
                    }.toString().toByteArray()
                )
            }
            connectedLatch.countDown()
            readerThread = thread(name = "fake-peer-reader", isDaemon = true) {
                readerLoop(s, framed)
            }
            readerThread?.join()
        } catch (_: Exception) {
        } finally {
            try { s.close() } catch (_: Exception) {}
        }
    }

    private fun readerLoop(s: SSLSocket, framed: FramedReader) {
        val pending = ArrayDeque<JSONObject>()
        try {
            while (running) {
                val header = pending.removeFirstOrNull()
                    ?: JSONObject(String(framed.readHeader()))
                when (header.optString("type")) {
                    "ping" -> synchronized(sendLock) {
                        writeHeader(
                            s.outputStream,
                            JSONObject().put("type", "pong").toString().toByteArray()
                        )
                    }
                    "pong" -> Unit
                    "bye" -> return
                    "cancel" -> {
                        // The phone cancelled a transfer this peer is
                        // sending: the send loop polls this set.
                        cancelsReceived.add(header.optString("name"))
                    }
                    "cancel_ack" -> {
                        val name = header.optString("name")
                        outcomeResult[name] = PeerSendOutcome.Cancelled
                        outcomeByName[name]?.countDown()
                    }
                    "ack" -> {
                        val name = header.optString("name")
                        val ok = header.optBoolean("ok")
                        outcomeResult[name] =
                            if (ok) PeerSendOutcome.Sent
                            else PeerSendOutcome.Failed(header.optString("reason"))
                        outcomeByName[name]?.countDown()
                    }
                    "file" -> receiveFile(s, framed, pending, header)
                    "download_ask" -> synchronized(sendLock) {
                        writeHeader(
                            s.outputStream,
                            JSONObject().apply {
                                put("type", "download_denied")
                                put("name", header.optString("name"))
                                put("id", header.optString("id"))
                            }.toString().toByteArray()
                        )
                    }
                    "download_denied" -> Unit
                    "error" -> Unit
                }
            }
        } catch (_: Exception) {
        }
    }

    /** Receive one file: read exactly size bytes, then peek for a
     * sender-initiated cancel before the SHA check and ack. */
    private fun receiveFile(
        s: SSLSocket,
        framed: FramedReader,
        pending: ArrayDeque<JSONObject>,
        header: JSONObject
    ) {
        val name = header.optString("name")
        val size = header.optInt("size")
        val expected = header.optString("sha256")
        val out = ByteArrayOutputStream()
        val digest = MessageDigest.getInstance("SHA-256")
        var remaining = size
        val buf = ByteArray(128 * 1024)
        try {
            val input = s.inputStream
            while (remaining > 0) {
                val toRead = minOf(buf.size, remaining)
                var read = 0
                while (read < toRead) {
                    val r = input.read(buf, read, toRead - read)
                    if (r < 0) throw java.io.EOFException("session closed during file")
                    read += r
                }
                out.write(buf, 0, read)
                digest.update(buf, 0, read)
                remaining -= read
            }
        } catch (_: Exception) {
            receivedFiles[name] = ReceivedFile(name, out.toByteArray(), false, false)
            return
        }
        // Sender-initiated cancel: it padded zeros and sent "cancel"
        // right after the payload. Peek for it before the SHA check.
        val peeked = try {
            framed.peekHeader(200)?.let { JSONObject(String(it)) }
        } catch (_: Exception) {
            null
        }
        if (peeked != null && peeked.optString("type") == "cancel" && peeked.optString("name") == name) {
            synchronized(sendLock) {
                writeHeader(
                    s.outputStream,
                    JSONObject().apply {
                        put("type", "cancel_ack")
                        put("name", name)
                    }.toString().toByteArray()
                )
            }
            cancelAcksSent.add(name)
            receivedFiles[name] = ReceivedFile(
                name, out.toByteArray(),
                digest.digest().joinToString("") { "%02x".format(it) } == expected.lowercase(),
                true
            )
            return
        }
        if (peeked != null) pending.addLast(peeked)
        val shaOk = digest.digest().joinToString("") { "%02x".format(it) } == expected.lowercase()
        receivedFiles[name] = ReceivedFile(name, out.toByteArray(), shaOk, false)
        acksSent[name] = shaOk
        synchronized(sendLock) {
            writeHeader(
                s.outputStream,
                JSONObject().apply {
                    put("type", "ack")
                    put("name", name)
                    put("ok", shaOk)
                    put("reason", if (shaOk) "" else "SHA-256 mismatch")
                }.toString().toByteArray()
            )
        }
    }
}

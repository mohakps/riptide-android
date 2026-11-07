package com.riptide.core

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.riptide.data.PeerRepository
import com.riptide.data.ReceivedHistoryRepository
import com.riptide.data.SavedPeerRecord
import com.riptide.service.TransferService
import com.riptide.ui.model.ActiveTransfer
import com.riptide.ui.model.ApprovalPhase
import com.riptide.ui.model.ApprovalRequest
import com.riptide.ui.model.BusyPhase
import com.riptide.ui.model.DownloadState
import com.riptide.ui.model.ReceivedFileRecord
import com.riptide.ui.model.TransferDirection
import com.riptide.ui.model.TransferRecord
import com.riptide.ui.model.TransferStatus
import com.riptide.ui.model.TrustStatus
import com.riptide.util.basenameOnly
import com.riptide.util.certFingerprint
import com.riptide.util.derive6DigitCode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import android.util.Base64 as AndroidBase64

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, FAILED }

/** Bytes counted and SHA-256 of a stream, read in one pass. Pure and unit tested. */
internal fun hashAndCount(input: java.io.InputStream): Pair<Long, String> {
    val digest = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(256 * 1024)
    var counted = 0L
    while (true) {
        val r = input.read(buf)
        if (r < 0) break
        if (r > 0) {
            digest.update(buf, 0, r)
            counted += r
        }
    }
    return counted to digest.digest().joinToString("") { "%02x".format(it) }
}

class SessionManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val identity = IdentityManager(context)
    private val peers = PeerRepository(context)
    private val receivedHistoryRepo = ReceivedHistoryRepository(context.filesDir)

    val myCode: String get() = try { identity.code() } catch (_: Exception) { "------" }
    val myFingerprint: String get() = try { identity.fingerprint() } catch (_: Exception) { "" }
    val deviceName: String get() = peers.deviceName()

    private val _connection = MutableStateFlow(ConnectionState.IDLE)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()
    private val _approval = MutableStateFlow<ApprovalRequest?>(null)
    val approval: StateFlow<ApprovalRequest?> = _approval.asStateFlow()
    /** LOCAL_TRUST shows the HOST code; WAITING_HOST shows the PHONE code in
     * the same sheet after local approval, until the host acknowledges. */
    private val _approvalPhase = MutableStateFlow(ApprovalPhase.NONE)
    val approvalPhase: StateFlow<ApprovalPhase> = _approvalPhase.asStateFlow()
    private val _peerName = MutableStateFlow("")
    val peerName: StateFlow<String> = _peerName.asStateFlow()
    private val _peerHost = MutableStateFlow("")
    val peerHost: StateFlow<String> = _peerHost.asStateFlow()
    private val _pingMs = MutableStateFlow(0L)
    val pingMs: StateFlow<Long> = _pingMs.asStateFlow()
    /** Wall-clock time of the last pong received. verifyAlive watches this
     * move: a ping write that succeeds into a dead socket proves nothing,
     * the pong coming back does. */
    private val _lastPongAt = MutableStateFlow(0L)
    private val _active = MutableStateFlow<ActiveTransfer?>(null)
    val active: StateFlow<ActiveTransfer?> = _active.asStateFlow()
    /** Non-null while preparing, verifying or waiting for a send ack. Blocks a second transfer. */
    private val _busyPhase = MutableStateFlow<BusyPhase?>(null)
    val busyPhase: StateFlow<BusyPhase?> = _busyPhase.asStateFlow()
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    private val _history = MutableStateFlow<List<TransferRecord>>(emptyList())
    val history: StateFlow<List<TransferRecord>> = _history.asStateFlow()
    private val _receivedHistory = MutableStateFlow<List<ReceivedFileRecord>>(emptyList())
    val receivedHistory: StateFlow<List<ReceivedFileRecord>> = _receivedHistory.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    /** Transfer failures (send / receive / download). Kept separate from
     * [_error] on purpose: a failed transfer must never be shown on Home
     * as "Couldn't reach this PC". Home shows session errors only; the
     * Session screen shows transfer errors. */
    private val _transferError = MutableStateFlow<String?>(null)
    val transferError: StateFlow<String?> = _transferError.asStateFlow()
    private val _savedPeers = MutableStateFlow<List<SavedPeerRecord>>(emptyList())
    /** Sorted by last successful connection, most recent first. */
    val savedPeers: StateFlow<List<SavedPeerRecord>> = _savedPeers.asStateFlow()

    private var socket: SSLSocket? = null
    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var connectJob: Job? = null
    /** Incremented on every cancel; a dial that finishes after its generation
     * changed is stale and must not open a session. */
    @Volatile private var attemptGeneration = 0
    private val sendMutex = Mutex()
    private var approvalDecision: ((Boolean) -> Unit)? = null
    private var currentHost: String = ""
    /** Host of the last session that reached CONNECTED. Set only on a
     * successful connect, cleared on a deliberate disconnect or cancel,
     * so an automatic rejoin never fires after the user chose to leave
     * or after a Join that never connected. */
    private var lastConnectedHost: String? = null
    /** Completed by the reader when the PC acks the file being sent. Only one
     * transfer runs at a time, so the next ack is always for this send. */
    private data class AckResult(val name: String, val ok: Boolean, val reason: String)
    @Volatile private var pendingAck: CompletableDeferred<AckResult?>? = null

    init {
        scope.launch {
            _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection }
            _receivedHistory.value = receivedHistoryRepo.load()
        }
    }

    fun refreshPeers() {
        scope.launch { _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection } }
    }

    fun removeReceivedRecord(id: String) {
        scope.launch {
            receivedHistoryRepo.remove(id)
            _receivedHistory.value = receivedHistoryRepo.load()
        }
    }

    fun setDeviceName(name: String) {
        peers.setDeviceName(name)
    }

    fun forgetPeer(host: String) {
        scope.launch {
            peers.forgetPeer(host)
            _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection }
        }
    }

    fun clearError() { _error.value = null; _transferError.value = null }

    fun clearDownloadDenied() {
        if (_downloadState.value is DownloadState.Denied) _downloadState.value = DownloadState.Idle
    }

    fun approveConnection(allow: Boolean) {
        if (allow) {
            // The same sheet now becomes "Waiting for approval from host"
            // and shows the PHONE's code while the hello exchange runs.
            _approvalPhase.value = ApprovalPhase.WAITING_HOST
        }
        approvalDecision?.invoke(allow)
        approvalDecision = null
        if (!allow) {
            _approval.value = null
            _approvalPhase.value = ApprovalPhase.NONE
            _connection.value = ConnectionState.IDLE
        }
    }

    /** Abort a pending Join (connecting, local trust, or host waiting).
     * Closes the socket, cancels the attempt, and invalidates any late
     * success callback via the generation counter. */
    fun cancelConnect() {
        attemptGeneration++
        connectJob?.cancel()
        connectJob = null
        approvalDecision?.invoke(false)
        approvalDecision = null
        _approval.value = null
        _approvalPhase.value = ApprovalPhase.NONE
        _error.value = null
        _transferError.value = null
        lastConnectedHost = null
        closeSocket()
        _connection.value = ConnectionState.IDLE
    }

    fun connect(hostInput: String) {
        if (_connection.value == ConnectionState.CONNECTING) return
        // Set CONNECTING synchronously, before launching: two rapid
        // connects (double tap, or a resume rejoin racing a manual
        // Join) must not both dial.
        _connection.value = ConnectionState.CONNECTING
        val generation = attemptGeneration
        connectJob = scope.launch {
            _error.value = null
            _transferError.value = null
            try {
                val host = parseHostInput(hostInput)
                currentHost = host
                _peerHost.value = host
                val session = dial(host)
                // A late success after Cancel must never open a session.
                if (generation != attemptGeneration) {
                    try { session.socket.close() } catch (_: Exception) {}
                    return@launch
                }
                _peerName.value = session.peerDevice.ifBlank { host }
                _approval.value = null
                _approvalPhase.value = ApprovalPhase.NONE
                _connection.value = ConnectionState.CONNECTED
                lastConnectedHost = host
                // A new session starts with an empty transfer list: the
                // Session page shows THIS SESSION only, never the
                // previous session's transfers. The durable received
                // history (Received tab) is separate and untouched.
                _history.value = emptyList()
                peers.markConnected(host)
                _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection }
                startSessionService()
                startReader(session)
                startHeartbeat(session)
            } catch (e: Exception) {
                if (generation != attemptGeneration) return@launch
                _approval.value = null
                _approvalPhase.value = ApprovalPhase.NONE
                if ("denied the fingerprint" in (e.message ?: "")) {
                    // A deliberate Deny is not an error state.
                    _error.value = null
                    _connection.value = ConnectionState.IDLE
                } else {
                    _error.value = friendlyError(e)
                    _connection.value = ConnectionState.FAILED
                }
                closeSocket()
            }
        }
    }

    fun disconnect() {
        attemptGeneration++
        connectJob?.cancel()
        connectJob = null
        lastConnectedHost = null
        scope.launch {
            try {
                sendMutex.withLock {
                    socket?.let { s ->
                        try { writeHeader(s.outputStream, JSONObject().put("type", "bye").toString().toByteArray()) } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
            readerJob?.cancel()
            heartbeatJob?.cancel()
            closeSocket()
            stopSessionService()
            _approval.value = null
            _approvalPhase.value = ApprovalPhase.NONE
            _error.value = null
            _transferError.value = null
            _busyPhase.value = null
            _downloadState.value = DownloadState.Idle
            _connection.value = ConnectionState.IDLE
            _active.value = null
        }
    }

    /** The foreground service runs for the whole session, not just while
     * a file is moving. That is the entire fix for the background
     * disconnect: with no service running, minimising the app (or
     * opening the system file picker, which backgrounds the app the
     * same way) left an ordinary cached process holding the socket,
     * and Android reclaimed it within seconds. A running foreground
     * service is exempt from Doze network restrictions and App Standby
     * throttling while it runs, so no battery-setting change is needed.
     * The service is started only from a successful connect, which
     * always happens while the app is in the foreground, so the
     * background start restriction on Android 12+ never applies. */
    private fun startSessionService() {
        try {
            ContextCompat.startForegroundService(
                context, Intent(context, TransferService::class.java)
            )
        } catch (_: Exception) {}
    }

    private fun stopSessionService() {
        try { context.stopService(Intent(context, TransferService::class.java)) } catch (_: Exception) {}
    }

    /** One place where a session dies unexpectedly: set the error, drop
     * to FAILED, close the dead socket, and stop the service. Every
     * failure path goes through here so none of the four can be
     * forgotten again. */
    private fun failSession(error: String) {
        if (_connection.value != ConnectionState.CONNECTED) return
        _error.value = error
        _connection.value = ConnectionState.FAILED
        closeSocket()
        stopSessionService()
    }

    /** Ping the PC and wait for its pong. A ping write that succeeds
     * into a dead socket proves nothing, so the pong coming back is
     * the proof. Returns false and fails the session when the socket
     * is dead or the pong does not arrive in time. */
    private suspend fun verifyAlive(timeoutMs: Long = 3000): Boolean {
        if (_connection.value != ConnectionState.CONNECTED) return false
        val before = _lastPongAt.value
        try {
            sendMutex.withLock {
                val s = socket ?: return false
                writeHeader(s.outputStream, JSONObject().put("type", "ping").toString().toByteArray())
            }
        } catch (_: Exception) {
            failSession("Connection lost.")
            return false
        }
        val ok = withTimeoutOrNull(timeoutMs) {
            _lastPongAt.first { it > before }
            true
        } ?: false
        if (!ok) failSession("Connection lost.")
        return ok
    }

    /** Called when the app comes back to the foreground. If the session
     * died while the app was away, rejoin the last peer on its own
     * using the identity already in the Keystore. A deliberate
     * disconnect clears the last host, so this never reconnects after
     * the user chose to leave, and a Join that never connected never
     * rejoins either. One attempt per return: if it fails, the error
     * stays on Home for the user to act on. A rejoin of a Known peer
     * still waits for the PC's approval tap; the waiting sheet shows
     * as usual while it waits. */
    suspend fun onReturnedToForeground() {
        if (_connection.value == ConnectionState.CONNECTED) {
            verifyAlive()
        }
        if (_connection.value == ConnectionState.FAILED) {
            val host = lastConnectedHost ?: return
            connect(host)
        }
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private data class DialResult(val socket: SSLSocket, val peerDevice: String, val serverFingerprint: String)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun dial(host: String): DialResult {
        val capturing = object : X509TrustManager {
            var chain: Array<X509Certificate>? = null
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { this.chain = chain }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(capturing), null)
        val factory = ctx.socketFactory
        val raw = Socket()
        raw.connect(InetSocketAddress(host, SESSION_PORT), 30_000)
        raw.soTimeout = 0
        raw.keepAlive = true
        val tls = factory.createSocket(raw, host, SESSION_PORT, true) as SSLSocket
        tls.soTimeout = 0
        tls.keepAlive = true
        try {
            tls.startHandshake()
        } catch (e: Exception) {
            try { tls.close() } catch (_: Exception) {}
            throw e
        }
        val serverDer = capturing.chain?.firstOrNull()?.encoded
            ?: tls.session.peerCertificates.firstOrNull()?.encoded
            ?: throw IllegalStateException("host certificate missing")
        val serverFp = certFingerprint(serverDer)
        val saved = peers.loadPeers().firstOrNull { it.host == host }
        val status: TrustStatus
        val oldFp: String
        val oldCode: String?
        when {
            saved != null && saved.fingerprint == serverFp -> { status = TrustStatus.KNOWN; oldFp = ""; oldCode = null }
            saved != null -> { status = TrustStatus.CHANGED; oldFp = saved.fingerprint; oldCode = derive6DigitCode(saved.fingerprint) }
            else -> { status = TrustStatus.NEW; oldFp = ""; oldCode = null }
        }
        if (status != TrustStatus.KNOWN) {
            val decision = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                approvalDecision = { allow -> if (cont.isActive) cont.resume(allow) {} }
                _approvalPhase.value = ApprovalPhase.LOCAL_TRUST
                _approval.value = ApprovalRequest(
                    host = host, code = derive6DigitCode(serverFp), fullFingerprint = serverFp,
                    trustStatus = status, oldCode = oldCode, oldFingerprint = oldFp.ifBlank { null }
                )
            }
            if (!decision) {
                _approval.value = null
                _approvalPhase.value = ApprovalPhase.NONE
                try { tls.close() } catch (_: Exception) {}
                throw IllegalStateException("client denied the fingerprint")
            }
            // Local approval given: the sheet stays up in WAITING_HOST phase
            // (set by approveConnection) showing the PHONE's code until the
            // host acknowledges with hello_ok below. Do not clear it here.
            if (status == TrustStatus.NEW || status == TrustStatus.CHANGED) {
                peers.savePeer(SavedPeerRecord(name = host, host = host, fingerprint = serverFp))
                _savedPeers.value = peers.loadPeers().sortedByDescending { it.lastSuccessfulConnection }
            }
        }
        val myDer = identity.certDer()
        val hello = JSONObject().apply {
            put("type", "hello")
            put("device", deviceName)
            put("host", host)
            put("cert", AndroidBase64.encodeToString(myDer, AndroidBase64.NO_WRAP))
        }
        val out = tls.outputStream
        val input = tls.inputStream
        writeHeader(out, hello.toString().toByteArray())
        val reply = JSONObject(String(readHeaderBytes(input)))
        var peerDevice = ""
        when (reply.optString("type")) {
            "hello_ok" -> { peerDevice = reply.optString("device") }
            "error" -> throw IllegalStateException("host rejected: ${reply.optString("message")}")
            "hello_challenge" -> {
                val nonce = AndroidBase64.decode(reply.optString("nonce"), AndroidBase64.DEFAULT)
                val sig = identity.signNonce(nonce)
                val proof = JSONObject().apply {
                    put("type", "hello_proof")
                    put("signature", AndroidBase64.encodeToString(sig, AndroidBase64.NO_WRAP))
                }
                writeHeader(out, proof.toString().toByteArray())
                val ok = JSONObject(String(readHeaderBytes(input)))
                if (ok.optString("type") == "error") throw IllegalStateException("host rejected: ${ok.optString("message")}")
                if (ok.optString("type") != "hello_ok") throw IllegalStateException("host did not accept the session")
                peerDevice = ok.optString("device")
            }
            else -> throw IllegalStateException("host did not accept the session")
        }
        socket = tls
        return DialResult(tls, peerDevice, serverFp)
    }

    private fun startHeartbeat(session: DialResult) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            val start = System.currentTimeMillis()
            while (true) {
                delay(10_000)
                try {
                    sendMutex.withLock {
                        session.socket.let { s ->
                            writeHeader(s.outputStream, JSONObject().put("type", "ping").toString().toByteArray())
                        }
                    }
                    _pingMs.value = System.currentTimeMillis() - start
                } catch (e: Exception) {
                    // A cancelled heartbeat (deliberate disconnect) is not
                    // a failure. Any other write failure means the socket
                    // is dead, and this is what used to surface later as
                    // "send failed: Broken pipe (0 bytes sent)": the
                    // reader blocks forever on a silently dead socket, so
                    // nothing marked the session dead until the next send
                    // tried to write to it. Detect it here, within 10 s.
                    if (e is kotlinx.coroutines.CancellationException) break
                    failSession("Connection lost.")
                    break
                }
            }
        }
    }

    private fun startReader(session: DialResult) {
        readerJob?.cancel()
        readerJob = scope.launch {
            val input = session.socket.inputStream
            try {
                while (true) {
                    val header = JSONObject(String(readHeaderBytes(input)))
                    when (header.optString("type")) {
                        "file" -> {
                            // A file arriving answers a pending download request.
                            if (_downloadState.value is DownloadState.Waiting) _downloadState.value = DownloadState.Idle
                            receiveFile(session, header, input)
                        }
                        "download_denied" -> {
                            val name = header.optString("name")
                            _downloadState.value = DownloadState.Denied(name)
                            addHistory(name, name, false, 0, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
                        }
                        "ack" -> {
                            // Completes the send that is waiting for confirmation.
                            // On success the PC sends the actual saved name, which
                            // may differ from the sent name when it was renamed.
                            pendingAck?.complete(
                                AckResult(header.optString("name"), header.optBoolean("ok"), header.optString("reason"))
                            )
                            pendingAck = null
                        }
                        "download_ask" -> {
                            sendMutex.withLock {
                                val denied = JSONObject().apply {
                                    put("type", "download_denied")
                                    put("name", header.optString("name"))
                                    put("id", header.optString("id"))
                                }
                                writeHeader(session.socket.outputStream, denied.toString().toByteArray())
                            }
                        }
                        "ping" -> {
                            sendMutex.withLock {
                                writeHeader(session.socket.outputStream, JSONObject().put("type", "pong").toString().toByteArray())
                            }
                        }
                        "pong" -> { _lastPongAt.value = System.currentTimeMillis() }
                        "bye" -> {
                            // The PC ended the session cleanly. This is a
                            // session error, not a join failure: close the
                            // socket and drop to FAILED so the UI shows the
                            // interrupted state instead of staying on a dead
                            // Session. Never leave a dead socket behind for
                            // a later send to trip over with Broken pipe.
                            failSession("The PC ended the session.")
                            break
                        }
                        "error" -> {
                            // Usually "no such file" answering a download
                            // request: a transfer error, not a dead session.
                            // Keep reading; if the PC really closed, the
                            // next read fails and the session path below
                            // handles it. Never break the reader here: that
                            // used to strand the UI on a dead session.
                            _transferError.value = header.optString("message")
                        }
                    }
                }
            } catch (e: Exception) {
                // A deliberate cancel/disconnect is not a failure: the
                // reader is cancelled from disconnect(), and reporting
                // "session closed" for it used to leave a stale error on
                // Home after a clean disconnect.
                if (e is kotlinx.coroutines.CancellationException) return@launch
                pendingAck?.complete(null)
                pendingAck = null
                // Drop the dead socket now. A later send must see
                // "not connected" instead of writing to a closed
                // socket and reporting a misleading Broken pipe.
                failSession(friendlyError(e))
            }
        }
    }

    private suspend fun receiveFile(session: DialResult, header: JSONObject, input: java.io.InputStream) {
        val name = basenameOnly(header.optString("name"))
        val size = header.optLong("size")
        val expected = header.optString("sha256")
        _busyPhase.value = BusyPhase.TRANSFERRING
        _active.value = ActiveTransfer(filename = name, direction = TransferDirection.RECEIVE, totalBytes = size, transferredBytes = 0, speedFormatted = "", progress = 0f, etaFormatted = null)
        val part = File(context.cacheDir, "$name.part")
        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        var lastReport = 0L
        val start = System.currentTimeMillis()
        try {
            FileOutputStream(part).use { out ->
                var remaining = size
                val buf = ByteArray(128 * 1024)
                while (remaining > 0) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    var read = 0
                    while (read < toRead) {
                        val r = input.read(buf, read, toRead - read)
                        if (r < 0) throw java.io.EOFException("session closed during file")
                        read += r
                    }
                    out.write(buf, 0, read)
                    digest.update(buf, 0, read)
                    remaining -= read
                    received += read
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= 100 || remaining == 0L) {
                        lastReport = now
                        val elapsed = ((now - start) / 1000.0).coerceAtLeast(0.1)
                        val speed = received / elapsed
                        _active.value = ActiveTransfer(name, TransferDirection.RECEIVE, size, received, formatSpeed(speed), if (size > 0) received.toFloat() / size else 0f, formatEta(speed, size - received))
                    }
                }
            }
        } catch (e: Exception) {
            _active.value = null
            _busyPhase.value = null
            addHistory(name, name, false, received, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
            _transferError.value = "receive failed: ${e.message}"
            return
        }
        // 100% of bytes is not success yet: verify the hash, then save.
        _busyPhase.value = BusyPhase.VERIFYING
        val ok = digest.digest().joinToString("") { "%02x".format(it) } == expected.lowercase()
        if (!ok) {
            _active.value = null
            _busyPhase.value = null
            addHistory(name, name, false, size, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
            sendAck(session, name, false, "SHA-256 mismatch")
            return
        }
        val saved = saveToDownloads(part, name)
        if (saved == null) {
            // Verification passed but saving failed: this is not a success,
            // and it must not enter received history.
            _active.value = null
            _busyPhase.value = null
            addHistory(name, name, false, size, TransferDirection.RECEIVE, TransferStatus.FAILED, false)
            _transferError.value = "Could not save $name to Downloads/Riptide"
            sendAck(session, name, false, "save failed")
            return
        }
        val (savedName, savedUri) = saved
        _active.value = null
        _busyPhase.value = null
        addHistory(name, savedName, savedName != name, size, TransferDirection.RECEIVE, TransferStatus.SUCCESS, true)
        // Durable received history: only after verification AND saving succeed.
        val record = ReceivedFileRecord(
            id = UUID.randomUUID().toString(), uri = savedUri, originalName = name,
            savedName = savedName, sourcePeer = _peerName.value.ifBlank { _peerHost.value },
            sizeBytes = size, verified = true
        )
        receivedHistoryRepo.add(record)
        _receivedHistory.value = receivedHistoryRepo.load()
        sendAck(session, savedName, true, "")
    }

    /** Returns the actual saved name and its MediaStore content URI, or null
     * when saving failed. The URI is what Received history opens later. */
    private fun saveToDownloads(part: File, name: String): Pair<String, String>? {
        return try {
            val resolver: ContentResolver = context.contentResolver
            val existing = mutableSetOf<String>()
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads.DISPLAY_NAME), "${MediaStore.Downloads.RELATIVE_PATH} = ?", arrayOf("Download/Riptide/"), null)?.use { c ->
                while (c.moveToNext()) existing.add(c.getString(0))
            }
            val saved = uniqueFinalName(existing, name) ?: return null
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, saved)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Riptide")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(part).use { inp -> inp.copyTo(out) }
            }
            part.delete()
            saved to uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun sendAck(session: DialResult, name: String, ok: Boolean, reason: String) {
        sendMutex.withLock {
            val ack = JSONObject().apply { put("type", "ack"); put("name", name); put("ok", ok); put("reason", reason) }
            try { writeHeader(session.socket.outputStream, ack.toString().toByteArray()) } catch (_: Exception) {}
        }
    }

    fun sendFile(uri: Uri) {
        if (_active.value != null || _busyPhase.value != null) {
            _transferError.value = "A transfer is already in progress"
            return
        }
        // Never write to a socket that is not a live session. The reader
        // closes the socket when the session dies, so a stale non-null
        // socket used to produce "send failed: Broken pipe (0 bytes sent)"
        // on Home after the session was already gone.
        if (_connection.value != ConnectionState.CONNECTED || socket == null) {
            _transferError.value = "Not connected. Join the PC and try again."
            return
        }
        _transferError.value = null
        scope.launch {
            val s = socket ?: run { _transferError.value = "Not connected. Join the PC and try again."; return@launch }
            // The session can have died since the last heartbeat (at most
            // 10 s ago). Ping and wait for the pong before spooling a
            // whole file into a send that would fail at the first write.
            // verifyAlive already fails the session when it is dead.
            if (!verifyAlive(2000)) return@launch
            val resolver = context.contentResolver
            var name = "file"
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0) name = c.getString(ni) ?: name
                }
            }
            name = basenameOnly(name)
            _busyPhase.value = BusyPhase.PREPARING
            // Spool the picked file to a local temp copy first. The size
            // reported by a content provider (OpenableColumns.SIZE) is
            // not trustworthy: for cloud-backed files it can be 0 or
            // stale, while the stream delivers the real bytes. Sending
            // a header whose size does not match the bytes that follow
            // makes the PC read the leftover file bytes as a header,
            // fail with "bad header length", close the session, and the
            // phone then fails with Broken pipe. Size, hash and bytes
            // all come from the same local spool, so they cannot disagree.
            val spool = File(context.cacheDir, "send-${UUID.randomUUID()}.tmp")
            try {
                val opened = resolver.openInputStream(uri)
                    ?: run { _transferError.value = "send failed: could not open $name"; return@launch }
                opened.use { inp -> FileOutputStream(spool).use { out -> inp.copyTo(out) } }
                val (counted, sha) = FileInputStream(spool).use { hashAndCount(it) }
                val size = counted
                _busyPhase.value = BusyPhase.TRANSFERRING
                _active.value = ActiveTransfer(name, TransferDirection.SEND, size, 0, "", 0f, null)
                val header = JSONObject().apply { put("type", "file"); put("name", name); put("size", size); put("sha256", sha) }
                val start = System.currentTimeMillis()
                var sent = 0L
                var lastReport = 0L
                val ackDeferred = CompletableDeferred<AckResult?>()
                pendingAck?.complete(null)
                pendingAck = ackDeferred
                try {
                    sendMutex.withLock {
                        writeHeader(s.outputStream, header.toString().toByteArray())
                        FileInputStream(spool).use { inp ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                val r = inp.read(buf)
                                if (r < 0) break
                                if (r == 0) continue
                                s.outputStream.write(buf, 0, r)
                                sent += r
                                val now = System.currentTimeMillis()
                                if (now - lastReport >= 100 || sent >= size) {
                                    lastReport = now
                                    val elapsed = ((now - start) / 1000.0).coerceAtLeast(0.1)
                                    val speed = sent / elapsed
                                    _active.value = ActiveTransfer(name, TransferDirection.SEND, size, sent, formatSpeed(speed), if (size > 0) sent.toFloat() / size else 0f, formatEta(speed, size - sent))
                                }
                            }
                            s.outputStream.flush()
                        }
                    }
                    // Do not call it sent until the PC confirms it: the PC
                    // acks only after reading every byte, verifying the
                    // SHA-256 and renaming the .part file. On success the
                    // ack carries the actual saved name.
                    _busyPhase.value = BusyPhase.WAITING_ACK
                    val ack = withTimeout(60_000) { ackDeferred.await() }
                    _active.value = null
                    _busyPhase.value = null
                    if (ack == null) {
                        addHistory(name, name, false, size, TransferDirection.SEND, TransferStatus.FAILED, false)
                        _transferError.value = "send failed: connection lost before PC confirmed $name ($sent of $size bytes sent)"
                    } else if (!ack.ok) {
                        addHistory(name, name, false, size, TransferDirection.SEND, TransferStatus.FAILED, false)
                        _transferError.value = "send failed: PC rejected $name: ${ack.reason.ifBlank { "unknown reason" }}"
                    } else {
                        addHistory(name, ack.name, ack.name != name, size, TransferDirection.SEND, TransferStatus.SUCCESS, true)
                    }
                } catch (e: Exception) {
                    pendingAck = null
                    _active.value = null
                    _busyPhase.value = null
                    addHistory(name, name, false, size, TransferDirection.SEND, TransferStatus.FAILED, false)
                    _transferError.value = "send failed: ${e.message} ($sent of $size bytes sent)"
                }
            } catch (e: Exception) {
                _active.value = null
                _busyPhase.value = null
                _transferError.value = "send failed: ${e.message}"
            } finally {
                spool.delete()
            }
        }
    }

    fun requestDownload(name: String) {
        if (_downloadState.value is DownloadState.Waiting) return
        if (_connection.value != ConnectionState.CONNECTED || socket == null) {
            _transferError.value = "Not connected. Join the PC and try again."
            return
        }
        _transferError.value = null
        scope.launch {
            val s = socket ?: run { _transferError.value = "Not connected. Join the PC and try again."; return@launch }
            // Same pre-flight check as a send: do not ask for a file
            // over a session that has already died.
            if (!verifyAlive(2000)) return@launch
            val trimmed = name.trim()
            try {
                sendMutex.withLock {
                    val ask = JSONObject().apply { put("type", "download_ask"); put("name", trimmed); put("id", UUID.randomUUID().toString()) }
                    writeHeader(s.outputStream, ask.toString().toByteArray())
                }
                // The PC answers with a file (receive) or download_denied.
                _downloadState.value = DownloadState.Waiting(trimmed)
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Idle
                _transferError.value = "download request failed: ${e.message}"
            }
        }
    }

    private fun addHistory(filename: String, saved: String, renamed: Boolean, size: Long, dir: TransferDirection, status: TransferStatus, verified: Boolean) {
        _history.update { listOf(TransferRecord(id = UUID.randomUUID().toString(), filename = filename, savedFilename = saved, wasRenamed = renamed, sizeBytes = size, direction = dir, status = status, sha256Verified = verified)) + it }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024)
            else -> String.format(Locale.US, "%.0f B/s", bytesPerSec)
        }
    }

    private fun formatEta(speed: Double, remaining: Long): String? {
        if (speed <= 0) return null
        val s = (remaining / speed).toLong()
        return "~${s}s"
    }

    private fun friendlyError(e: Exception): String {
        val m = e.message ?: e.toString()
        return when {
            "timed out" in m.lowercase() -> "Connection timed out. Check the PC is on Create Server, port 50000 is open, and the router forwards TCP 50000 to that PC."
            "refused" in m.lowercase() -> "Connection refused. The host is not listening on port 50000."
            "fingerprint changed" in m.lowercase() -> "Fingerprint changed, session aborted."
            "denied the fingerprint" in m.lowercase() -> "You denied the fingerprint."
            else -> m
        }
    }
}

package com.riptide.core

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val MAX_HEADER_BYTES = 1024 * 1024
const val SESSION_PORT = 50000

fun writeHeader(out: OutputStream, json: ByteArray) {
    require(json.size <= MAX_HEADER_BYTES) { "header too large" }
    val len = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(json.size).array()
    synchronized(out) {
        out.write(len)
        out.write(json)
        out.flush()
    }
}

fun readHeaderBytes(input: InputStream): ByteArray {
    val lenBytes = readExact(input, 4)
    val length = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int
    require(length in 1..MAX_HEADER_BYTES) { "bad header length: $length" }
    return readExact(input, length)
}

fun readExact(input: InputStream, n: Int): ByteArray {
    val out = ByteArray(n)
    var read = 0
    while (read < n) {
        val r = input.read(out, read, n - read)
        if (r < 0) throw java.io.EOFException("session closed")
        read += r
    }
    return out
}

/**
 * A framed-message reader over one session socket. It owns a small
 * buffer of bytes already read from the stream but not yet consumed,
 * so a peek that times out mid-header never loses bytes: the next
 * read consumes the buffered bytes first.
 *
 * One reader only: the session's reader coroutine is the only code
 * that reads this socket, so no locking is needed. The peek sets a
 * short read timeout on the socket and restores it afterwards; the
 * heartbeat only writes, never reads, so it is unaffected.
 */
class FramedReader(private val input: InputStream, private val socket: java.net.Socket) {
    private var buffered = ByteArray(0)

    /** Read one full framed header (4-byte length + JSON bytes). */
    fun readHeader(): ByteArray {
        val lenBytes = readExactBuffered(4)
        val length = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).int
        require(length in 1..MAX_HEADER_BYTES) { "bad header length: $length" }
        return readExactBuffered(length)
    }

    /**
     * Try to read one framed header within [timeoutMs]. Returns the
     * header bytes, or null when nothing arrived in time. Bytes of a
     * header that arrived only partly are kept in the buffer and
     * returned by the next [readHeader] call.
     */
    fun peekHeader(timeoutMs: Int): ByteArray? {
        val oldTimeout = try { socket.soTimeout } catch (_: Exception) { 0 }
        return try {
            socket.soTimeout = timeoutMs
            readHeader()
        } catch (e: java.net.SocketTimeoutException) {
            null
        } finally {
            try { socket.soTimeout = oldTimeout } catch (_: Exception) {}
        }
    }

    /** Read exactly [n] bytes, consuming buffered bytes first. A
     * read timeout mid-header keeps the bytes already read in the
     * buffer and rethrows, so they are not lost. */
    private fun readExactBuffered(n: Int): ByteArray {
        val out = ByteArray(n)
        var read = 0
        if (buffered.isNotEmpty()) {
            val take = minOf(buffered.size, n)
            buffered.copyInto(out, 0, 0, take)
            buffered = if (take == buffered.size) ByteArray(0) else buffered.copyOfRange(take, buffered.size)
            read = take
        }
        while (read < n) {
            val r = try {
                input.read(out, read, n - read)
            } catch (e: java.net.SocketTimeoutException) {
                // Keep what this call already read: the next read
                // consumes it before touching the stream again.
                buffered = out.copyOf(read)
                throw e
            }
            if (r < 0) throw java.io.EOFException("session closed")
            read += r
        }
        return out
    }
}

fun uniqueFinalName(existing: Set<String>, name: String): String? {
    if (name.isBlank() || name == "." || name == "..") return name
    val base = name.substringAfterLast('/')
    if (base !in existing) return base
    val dot = base.lastIndexOf('.')
    val stem: String
    val suffix: String
    if (dot > 0) {
        stem = base.substring(0, dot)
        suffix = base.substring(dot)
    } else {
        stem = base
        suffix = ""
    }
    for (i in 1..1000) {
        val candidate = "$stem ($i)$suffix"
        if (candidate !in existing) return candidate
    }
    return null
}

fun parseHostInput(text: String): String {
    var t = (text).trim()
    if (t.isEmpty()) throw IllegalArgumentException("Enter a host first")
    if ("@" in t) t = t.substringAfterLast("@").trim()
    if (t.isEmpty()) throw IllegalArgumentException("Enter a host first")
    if (t.startsWith("[")) {
        val end = t.indexOf(']')
        if (end == -1) throw IllegalArgumentException("Bad IPv6 address: missing ]")
        return t.substring(1, end).trim()
    }
    if (t.count { it == ':' } > 1) return t
    if (t.count { it == ':' } == 1) {
        val host = t.substringBefore(':').trim()
        if (host.isEmpty()) throw IllegalArgumentException("Enter a host first")
        return host
    }
    return t
}

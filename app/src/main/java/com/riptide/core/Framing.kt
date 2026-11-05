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

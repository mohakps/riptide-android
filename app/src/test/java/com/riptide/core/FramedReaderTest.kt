package com.riptide.core

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FramedReader: the peek the receiver uses after a payload to spot a
 * sender-initiated "cancel" must never lose bytes. A header that
 * arrives only partly inside the peek window is kept in the buffer
 * and returned by the next read.
 *
 * Runs under Robolectric, not plain JUnit: the framing code uses
 * org.json, whose methods throw "not mocked" against the stub
 * android.jar in a plain unit test. Pinned to SDK 34, as in
 * LoopbackTestBase: without it Robolectric falls back to its
 * default SDK 21.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FramedReaderTest {

    private fun frame(json: String): ByteArray {
        val body = json.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(body.size).array())
        out.write(body)
        return out.toByteArray()
    }

    @Test
    fun testPeekKeepsPartialHeaderBytes() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        try {
            val client = Socket("127.0.0.1", server.localPort)
            client.soTimeout = 0
            val accepted = server.accept()
            try {
                val framed = FramedReader(client.inputStream, client)
                val bytes = frame(JSONObject().put("type", "cancel").put("name", "a.bin").toString())
                // Only the first two length bytes arrive inside the peek window.
                accepted.outputStream.write(bytes, 0, 2)
                accepted.outputStream.flush()
                assertNull(framed.peekHeader(200))
                // The rest arrives later: the buffered bytes plus the
                // remainder must still form the whole header.
                thread(isDaemon = true) {
                    Thread.sleep(100)
                    accepted.outputStream.write(bytes, 2, bytes.size - 2)
                    accepted.outputStream.flush()
                }
                val header = JSONObject(String(framed.readHeader()))
                assertEquals("cancel", header.optString("type"))
                assertEquals("a.bin", header.optString("name"))
            } finally {
                try { client.close() } catch (_: Exception) {}
                try { accepted.close() } catch (_: Exception) {}
            }
        } finally {
            try { server.close() } catch (_: Exception) {}
        }
    }

    @Test
    fun testPeekReturnsHeaderWhenPresent() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        try {
            val client = Socket("127.0.0.1", server.localPort)
            client.soTimeout = 0
            val accepted = server.accept()
            try {
                val framed = FramedReader(client.inputStream, client)
                accepted.outputStream.write(frame(JSONObject().put("type", "pong").toString()))
                accepted.outputStream.flush()
                val header = framed.peekHeader(1000)?.let { JSONObject(String(it)) }
                assertEquals("pong", header?.optString("type"))
            } finally {
                try { client.close() } catch (_: Exception) {}
                try { accepted.close() } catch (_: Exception) {}
            }
        } finally {
            try { server.close() } catch (_: Exception) {}
        }
    }

    @Test
    fun testReadHeaderRoundTrip() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        try {
            val client = Socket("127.0.0.1", server.localPort)
            client.soTimeout = 0
            val accepted = server.accept()
            try {
                val framed = FramedReader(client.inputStream, client)
                accepted.outputStream.write(frame(JSONObject().put("type", "ack").put("name", "x").put("ok", true).toString()))
                accepted.outputStream.flush()
                val header = JSONObject(String(framed.readHeader()))
                assertEquals("ack", header.optString("type"))
                assertEquals("x", header.optString("name"))
                assertEquals(true, header.optBoolean("ok"))
            } finally {
                try { client.close() } catch (_: Exception) {}
                try { accepted.close() } catch (_: Exception) {}
            }
        } finally {
            try { server.close() } catch (_: Exception) {}
        }
    }
}

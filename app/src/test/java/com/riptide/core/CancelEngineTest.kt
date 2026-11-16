package com.riptide.core

import com.riptide.ui.model.TransferDirection
import com.riptide.ui.model.TransferStatus
import java.io.File
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Fix 1 loopback tests: cancel a transfer and keep the session
 * connected, against a FakePeer that speaks the PC v1.6.0 shape
 * (drain to the boundary, cancel/cancel_ack, .part deleted, never
 * resumable, one terminal outcome per file).
 *
 * The phone sends through SessionManager.sendFile, which reads the
 * file through the ContentResolver: tests serve their files through
 * [TestFileProvider], which reports the real DISPLAY_NAME the way
 * the system picker does (a file:// URI has no provider, and every
 * send would be named "file").
 */
@RunWith(RobolectricTestRunner::class)
class CancelEngineTest : LoopbackTestBase() {

    private fun makeFile(name: String, sizeBytes: Int): File {
        val file = File(context.cacheDir, name)
        val bytes = ByteArray(sizeBytes) { (it % 251).toByte() }
        file.writeBytes(bytes)
        return file
    }

    private fun waitForHistory(
        name: String,
        status: TransferStatus,
        timeoutMs: Long = 15_000
    ): Boolean {
        return waitFor(timeoutMs) {
            session.history.value.any { it.filename == name && it.status == status }
        }
    }

    private fun historyCount(name: String): Int =
        session.history.value.count { it.filename == name }

    @Test
    fun testReceiverCancelKeepsSessionOpen() {
        assertTrue(connectAndApprove())
        val size = 32 * 1024 * 1024
        val bytes = ByteArray(size) { (it % 251).toByte() }
        var peerOutcome: FakePeer.PeerSendOutcome? = null
        val senderThread = thread(name = "peer-send", isDaemon = true) {
            peerOutcome = peer.sendFile("big-receive.bin", bytes, chunkDelayMs = 2)
        }
        // Wait until the phone is actually receiving, then cancel.
        assertTrue(waitFor(10_000) { (session.active.value?.transferredBytes ?: 0L) > 0 })
        assertTrue(session.cancelTransfer())
        assertTrue(waitForHistory("big-receive.bin", TransferStatus.CANCELLED))
        senderThread.join(15_000)
        // The peer padded zeros and confirmed; the phone deleted the .part.
        assertEquals(FakePeer.PeerSendOutcome.Cancelled, peerOutcome)
        assertTrue(peer.cancelAcksSent.contains("big-receive.bin"))
        assertFalse(File(context.cacheDir, "big-receive.bin.part").exists())
        assertTrue(session.receivedHistory.value.none { it.originalName == "big-receive.bin" })
        assertEquals(ConnectionState.CONNECTED, session.connection.value)
        // One terminal entry only.
        assertEquals(1, historyCount("big-receive.bin"))
        // A follow-up send on the same connection completes normally.
        val followUp = makeFile("after-cancel.txt", 1024)
        session.sendFile(TestFileProvider.uriFor(followUp))
        assertTrue(waitForHistory("after-cancel.txt", TransferStatus.SUCCESS))
        assertTrue(waitFor(10_000) { peer.receivedFiles.containsKey("after-cancel.txt") })
        assertEquals(true, peer.receivedFiles["after-cancel.txt"]?.shaOk)
    }

    @Test
    fun testSenderCancelKeepsSessionOpen() {
        assertTrue(connectAndApprove())
        val file = makeFile("big-send.bin", 32 * 1024 * 1024)
        session.sendFile(TestFileProvider.uriFor(file))
        assertTrue(waitFor(10_000) { (session.active.value?.transferredBytes ?: 0L) > 0 })
        assertTrue(session.cancelTransfer())
        assertTrue(waitForHistory("big-send.bin", TransferStatus.CANCELLED))
        assertEquals(ConnectionState.CONNECTED, session.connection.value)
        assertEquals(1, historyCount("big-send.bin"))
        // The peer saw the sender-initiated cancel and confirmed it.
        // Wait on the peer's recorded file, not on the cancel_ack set:
        // the peer adds the ack name a line before it records the
        // file, so polling the ack set and then reading the file
        // immediately can race the peer thread by a microsecond.
        assertTrue(waitFor(10_000) { peer.receivedFiles["big-send.bin"]?.cancelled == true })
        assertTrue(peer.cancelAcksSent.contains("big-send.bin"))
        assertEquals(true, peer.receivedFiles["big-send.bin"]?.cancelled)
        // A follow-up send on the same connection completes normally.
        val followUp = makeFile("after-cancel-2.txt", 2048)
        session.sendFile(TestFileProvider.uriFor(followUp))
        assertTrue(waitForHistory("after-cancel-2.txt", TransferStatus.SUCCESS))
        assertTrue(waitFor(10_000) { peer.receivedFiles.containsKey("after-cancel-2.txt") })
    }

    @Test
    fun testPeerCancelsOurSend() {
        assertTrue(connectAndApprove())
        val file = makeFile("peer-cancel-send.bin", 32 * 1024 * 1024)
        session.sendFile(TestFileProvider.uriFor(file))
        assertTrue(waitFor(10_000) { (session.active.value?.transferredBytes ?: 0L) > 0 })
        // The peer (receiving) cancels: it sends "cancel", the phone
        // pads zeros and answers cancel_ack.
        peer.sendCancelToPhone("peer-cancel-send.bin")
        assertTrue(waitForHistory("peer-cancel-send.bin", TransferStatus.CANCELLED))
        assertEquals(ConnectionState.CONNECTED, session.connection.value)
        assertEquals(1, historyCount("peer-cancel-send.bin"))
    }

    @Test
    fun testPeerCancelsItsOwnSend() {
        assertTrue(connectAndApprove())
        val size = 32 * 1024 * 1024
        val bytes = ByteArray(size) { (it % 251).toByte() }
        var peerOutcome: FakePeer.PeerSendOutcome? = null
        val senderThread = thread(name = "peer-send-2", isDaemon = true) {
            peerOutcome = peer.sendFile("peer-cancel-own.bin", bytes, chunkDelayMs = 2)
        }
        assertTrue(waitFor(10_000) { (session.active.value?.transferredBytes ?: 0L) > 0 })
        // The peer (sending) cancels its own send: it pads zeros and
        // sends "cancel"; the phone's receive peeks it before the SHA
        // check and reports Cancelled.
        peer.cancelSend("peer-cancel-own.bin")
        senderThread.join(15_000)
        assertEquals(FakePeer.PeerSendOutcome.Cancelled, peerOutcome)
        assertTrue(waitForHistory("peer-cancel-own.bin", TransferStatus.CANCELLED))
        assertEquals(ConnectionState.CONNECTED, session.connection.value)
        assertEquals(1, historyCount("peer-cancel-own.bin"))
        assertFalse(File(context.cacheDir, "peer-cancel-own.bin.part").exists())
    }

    @Test
    fun testRapidCancelIsOneEntry() {
        assertTrue(connectAndApprove())
        val size = 32 * 1024 * 1024
        val bytes = ByteArray(size) { (it % 251).toByte() }
        val senderThread = thread(name = "peer-send-3", isDaemon = true) {
            peer.sendFile("rapid-cancel.bin", bytes, chunkDelayMs = 2)
        }
        assertTrue(waitFor(10_000) { (session.active.value?.transferredBytes ?: 0L) > 0 })
        repeat(5) { session.cancelTransfer() }
        assertTrue(waitForHistory("rapid-cancel.bin", TransferStatus.CANCELLED))
        senderThread.join(15_000)
        Thread.sleep(500)
        assertEquals(1, historyCount("rapid-cancel.bin"))
        assertEquals(ConnectionState.CONNECTED, session.connection.value)
    }

    @Test
    fun testLateCancelCompletionStands() {
        assertTrue(connectAndApprove())
        val file = makeFile("tiny.txt", 1024)
        session.sendFile(TestFileProvider.uriFor(file))
        // Wait until the send has committed (all bytes written, ack
        // pending): a cancel now is too late and must return false.
        assertTrue(waitFor(10_000) { session.busyPhase.value == com.riptide.ui.model.BusyPhase.WAITING_ACK })
        assertFalse(session.cancelTransfer())
        assertTrue(waitForHistory("tiny.txt", TransferStatus.SUCCESS))
        assertEquals(1, historyCount("tiny.txt"))
        assertEquals(ConnectionState.CONNECTED, session.connection.value)
    }

    @Test
    fun testCancelWithNoTransferReturnsFalse() {
        assertTrue(connectAndApprove())
        assertFalse(session.cancelTransfer())
        assertFalse(session.cancellingTransfer.value)
    }
}

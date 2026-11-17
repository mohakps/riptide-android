package com.riptide.core

import com.riptide.ui.model.TransferStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The unified outgoing queue, against a FakePeer on loopback: one
 * model drives the Home OUTGOING QUEUE panel and the Session screen
 * queue. A single send is a queue of one; picker batches and shared
 * files append at the bottom; files staged before a connection send
 * when the session connects.
 */
@RunWith(RobolectricTestRunner::class)
class OutgoingQueueTest : LoopbackTestBase() {

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

    /** Completion order: history is newest-first, so reversing it gives send order. */
    private fun successfulSendOrder(): List<String> =
        session.history.value.filter { it.status == TransferStatus.SUCCESS }
            .map { it.filename }.reversed()

    @Test
    fun testSingleSendIsAQueueOfOne() {
        assertTrue(connectAndApprove())
        val file = makeFile("single.txt", 1024)
        session.sendFile(TestFileProvider.uriFor(file))
        assertTrue(waitForHistory("single.txt", TransferStatus.SUCCESS))
        assertTrue(waitFor(10_000) { peer.receivedFiles.containsKey("single.txt") })
        assertEquals(true, peer.receivedFiles["single.txt"]?.shaOk)
        assertTrue(waitFor(5_000) { session.batch.value == null && session.batchMessage.value == null })
        assertNull(session.batch.value)
    }

    @Test
    fun testAppendOrderNewFilesAtBottom() {
        assertTrue(connectAndApprove())
        val first = makeFile("first.txt", 1024)
        val second = makeFile("second.txt", 1024)
        val third = makeFile("third.txt", 1024)
        session.sendBatch(
            listOf(
                TestFileProvider.uriFor(first),
                TestFileProvider.uriFor(second),
                TestFileProvider.uriFor(third)
            )
        )
        assertTrue(waitForHistory("first.txt", TransferStatus.SUCCESS))
        assertTrue(waitForHistory("second.txt", TransferStatus.SUCCESS))
        assertTrue(waitForHistory("third.txt", TransferStatus.SUCCESS))
        assertEquals(listOf("first.txt", "second.txt", "third.txt"), successfulSendOrder())
        assertTrue(waitFor(5_000) { session.batch.value == null && session.batchMessage.value == null })
    }

    @Test
    fun testAppendDuringRunningQueueSendsAfter() {
        assertTrue(connectAndApprove())
        val big = makeFile("big-append.bin", 32 * 1024 * 1024)
        val small = makeFile("appended.txt", 1024)
        session.sendFile(TestFileProvider.uriFor(big))
        // Append immediately: the big file is still spooling or
        // sending, so the small file joins the bottom of its queue.
        session.sendFile(TestFileProvider.uriFor(small))
        assertTrue(waitFor(10_000) { session.batch.value?.items?.size == 2 })
        assertTrue(waitForHistory("big-append.bin", TransferStatus.SUCCESS))
        assertTrue(waitForHistory("appended.txt", TransferStatus.SUCCESS))
        assertEquals(listOf("big-append.bin", "appended.txt"), successfulSendOrder())
    }

    @Test
    fun testShareIntakeDuringSingleSendSwitchesToBatch() {
        assertTrue(connectAndApprove())
        val first = makeFile("active-before-share.bin", 32 * 1024 * 1024)
        val second = makeFile("shared-during-send.txt", 1024)
        session.sendFile(TestFileProvider.uriFor(first))
        assertTrue(waitFor(10_000) {
            session.active.value?.let { it.filename == first.name && it.transferredBytes > 0 } == true
        })
        assertEquals(false, session.batch.value?.isBatchUi)

        session.spoolSharedFiles(listOf(TestFileProvider.uriFor(second)))

        assertTrue(waitFor(10_000) { session.batch.value?.isBatchUi == true })
        assertTrue(waitForHistory(first.name, TransferStatus.SUCCESS))
        assertTrue(waitForHistory(second.name, TransferStatus.SUCCESS))
        assertTrue(waitFor(5_000) { session.batch.value == null })
    }

    @Test
    fun testStageThenConnectAutoSendsOnce() {
        val first = makeFile("staged-a.txt", 1024)
        val second = makeFile("staged-b.txt", 2048)
        session.sendBatch(listOf(TestFileProvider.uriFor(first), TestFileProvider.uriFor(second)))
        // Not connected: the files stage, nothing sends.
        assertTrue(waitFor(5_000) { session.batch.value?.includedCount == 2 })
        assertFalse(session.batch.value?.running ?: true)
        assertEquals(ConnectionState.IDLE, session.connection.value)
        assertTrue(peer.receivedFiles.isEmpty())
        // Connecting sends the staged queue, exactly once.
        assertTrue(connectAndApprove())
        assertTrue(waitForHistory("staged-a.txt", TransferStatus.SUCCESS))
        assertTrue(waitForHistory("staged-b.txt", TransferStatus.SUCCESS))
        assertEquals(listOf("staged-a.txt", "staged-b.txt"), successfulSendOrder())
        assertTrue(waitFor(5_000) { session.batch.value == null && session.batchMessage.value == null })
        assertEquals(1, session.history.value.count { it.filename == "staged-a.txt" })
        assertEquals(1, session.history.value.count { it.filename == "staged-b.txt" })
    }

    @Test
    fun testConnectFailureLeavesFilesStaged() {
        val first = makeFile("staged-fail-a.txt", 1024)
        val second = makeFile("staged-fail-b.txt", 1024)
        session.sendBatch(listOf(TestFileProvider.uriFor(first), TestFileProvider.uriFor(second)))
        assertTrue(waitFor(5_000) { session.batch.value?.includedCount == 2 })
        peer.stop()
        session.connect("127.0.0.1")
        assertTrue(waitFor(15_000) { session.connection.value == ConnectionState.FAILED })
        // The failed dial leaves the staged queue untouched.
        assertEquals(2, session.batch.value?.includedCount)
        assertFalse(session.batch.value?.running ?: true)
        // The peer comes back: the same staged files send on connect.
        peer.start()
        assertTrue(connectAndApprove())
        assertTrue(waitForHistory("staged-fail-a.txt", TransferStatus.SUCCESS))
        assertTrue(waitForHistory("staged-fail-b.txt", TransferStatus.SUCCESS))
    }

    @Test
    fun testRemoveUndoCountBehavior() {
        val first = makeFile("count-a.txt", 1024)
        val second = makeFile("count-b.txt", 1024)
        session.sendBatch(listOf(TestFileProvider.uriFor(first), TestFileProvider.uriFor(second)))
        assertTrue(waitFor(5_000) { session.batch.value?.includedCount == 2 })
        val idA = session.batch.value!!.items.first { it.name == "count-a.txt" }.id
        // Remove: the item stays in place, the count drops.
        session.setQueueItemExcluded(idA, true)
        assertEquals(2, session.batch.value?.items?.size)
        assertEquals(1, session.batch.value?.includedCount)
        assertTrue(session.batch.value!!.items.first { it.id == idA }.excluded)
        // Undo: the item returns to its original position, the count rises.
        session.setQueueItemExcluded(idA, false)
        assertEquals(2, session.batch.value?.includedCount)
        assertFalse(session.batch.value!!.items.first { it.id == idA }.excluded)
        assertEquals("count-a.txt", session.batch.value!!.items[0].name)
    }

    @Test
    fun testExcludedFileIsSkipped() {
        val first = makeFile("keep.txt", 1024)
        val second = makeFile("removed.txt", 1024)
        session.sendBatch(listOf(TestFileProvider.uriFor(first), TestFileProvider.uriFor(second)))
        assertTrue(waitFor(5_000) { session.batch.value?.includedCount == 2 })
        val idB = session.batch.value!!.items.first { it.name == "removed.txt" }.id
        session.setQueueItemExcluded(idB, true)
        assertTrue(connectAndApprove())
        assertTrue(waitForHistory("keep.txt", TransferStatus.SUCCESS))
        assertTrue(waitFor(5_000) { session.batch.value == null && session.batchMessage.value == null })
        assertFalse(peer.receivedFiles.containsKey("removed.txt"))
        assertEquals(0, session.history.value.count { it.filename == "removed.txt" })
    }

    @Test
    fun testSharedFileSendsFromSpool() {
        assertTrue(connectAndApprove())
        val file = makeFile("shared-photo.jpg", 4096)
        session.spoolSharedFiles(listOf(TestFileProvider.uriFor(file)))
        assertTrue(waitForHistory("shared-photo.jpg", TransferStatus.SUCCESS))
        assertTrue(waitFor(10_000) { peer.receivedFiles.containsKey("shared-photo.jpg") })
        assertEquals(true, peer.receivedFiles["shared-photo.jpg"]?.shaOk)
        // The spool is deleted when the send completes.
        assertTrue(waitFor(5_000) {
            context.cacheDir.listFiles { f -> f.name.startsWith("send-") && f.name.endsWith(".tmp") }
                .isNullOrEmpty()
        })
    }

    @Test
    fun testStagedQueueSurvivesDisconnect() {
        val first = makeFile("survive-a.txt", 1024)
        val second = makeFile("survive-b.txt", 1024)
        session.sendBatch(listOf(TestFileProvider.uriFor(first), TestFileProvider.uriFor(second)))
        assertTrue(waitFor(5_000) { session.batch.value?.includedCount == 2 })
        session.disconnect()
        Thread.sleep(500)
        // A queue that never started survives a disconnect and sends
        // when the user reconnects.
        assertEquals(2, session.batch.value?.includedCount)
        assertFalse(session.batch.value?.running ?: true)
        assertTrue(connectAndApprove())
        assertTrue(waitForHistory("survive-a.txt", TransferStatus.SUCCESS))
        assertTrue(waitForHistory("survive-b.txt", TransferStatus.SUCCESS))
    }

    @Test
    fun testRunningQueueDropsOnDisconnect() {
        assertTrue(connectAndApprove())
        val big = makeFile("big-drop.bin", 32 * 1024 * 1024)
        session.sendFile(TestFileProvider.uriFor(big))
        assertTrue(waitFor(10_000) { (session.active.value?.transferredBytes ?: 0L) > 0 })
        assertTrue(session.batch.value?.running == true)
        session.disconnect()
        assertTrue(waitFor(5_000) { session.batch.value == null })
        assertEquals(ConnectionState.IDLE, session.connection.value)
    }

    @Test
    fun testSingleSendIsSingleUi() {
        assertTrue(connectAndApprove())
        val big = makeFile("single-ui.bin", 32 * 1024 * 1024)
        session.sendFile(TestFileProvider.uriFor(big))
        assertTrue(waitFor(10_000) { session.batch.value?.running == true })
        assertEquals(false, session.batch.value?.becameBatch)
        assertEquals(false, session.batch.value?.isBatchUi)
        assertTrue(waitForHistory("single-ui.bin", TransferStatus.SUCCESS))
    }

    @Test
    fun testSecondFileAppendedMidSendConvertsToBatchUi() {
        assertTrue(connectAndApprove())
        val big = makeFile("convert-big.bin", 32 * 1024 * 1024)
        val small = makeFile("convert-small.txt", 1024)
        session.sendFile(TestFileProvider.uriFor(big))
        assertTrue(waitFor(10_000) { session.batch.value?.running == true })
        assertEquals(false, session.batch.value?.isBatchUi)
        session.sendFile(TestFileProvider.uriFor(small))
        assertTrue(waitFor(10_000) { session.batch.value?.items?.size == 2 })
        assertEquals(true, session.batch.value?.becameBatch)
        assertEquals(true, session.batch.value?.isBatchUi)
        assertTrue(waitForHistory("convert-big.bin", TransferStatus.SUCCESS))
        assertTrue(waitForHistory("convert-small.txt", TransferStatus.SUCCESS))
    }

    @Test
    fun testMultiPickOfTwoIsBatchFromStart() {
        val first = makeFile("multipick-a.txt", 1024)
        val second = makeFile("multipick-b.txt", 1024)
        session.sendBatch(listOf(TestFileProvider.uriFor(first), TestFileProvider.uriFor(second)))
        assertTrue(waitFor(5_000) { session.batch.value?.includedCount == 2 })
        assertEquals(true, session.batch.value?.becameBatch)
        assertEquals(true, session.batch.value?.isBatchUi)
    }

    @Test
    fun testSecondFileStagedMakesBatch() {
        val first = makeFile("staged-one.txt", 1024)
        session.sendFile(TestFileProvider.uriFor(first))
        assertTrue(waitFor(5_000) { session.batch.value?.includedCount == 1 })
        assertEquals(false, session.batch.value?.isBatchUi)
        val second = makeFile("staged-two.txt", 1024)
        session.sendFile(TestFileProvider.uriFor(second))
        assertTrue(waitFor(5_000) { session.batch.value?.includedCount == 2 })
        assertEquals(true, session.batch.value?.becameBatch)
        assertEquals(true, session.batch.value?.isBatchUi)
    }

    @Test
    fun testBatchDrainsThenNextSingleSendIsSingleUi() {
        assertTrue(connectAndApprove())
        val first = makeFile("drain-a.txt", 1024)
        val second = makeFile("drain-b.txt", 1024)
        session.sendBatch(listOf(TestFileProvider.uriFor(first), TestFileProvider.uriFor(second)))
        assertTrue(waitForHistory("drain-a.txt", TransferStatus.SUCCESS))
        assertTrue(waitForHistory("drain-b.txt", TransferStatus.SUCCESS))
        assertTrue(waitFor(5_000) { session.batch.value == null && session.batchMessage.value == null })
        assertNull(session.batch.value)
        // A fresh single send after the batch ends is single UI again.
        val big = makeFile("drain-single.bin", 32 * 1024 * 1024)
        session.sendFile(TestFileProvider.uriFor(big))
        assertTrue(waitFor(10_000) { session.batch.value?.running == true })
        assertEquals(false, session.batch.value?.becameBatch)
        assertEquals(false, session.batch.value?.isBatchUi)
        assertTrue(waitForHistory("drain-single.bin", TransferStatus.SUCCESS))
    }

    @Test
    fun testExcludeOneOfTwoStagedFilesReturnsToSingleUi() {
        val first = makeFile("exclude-a.txt", 1024)
        val second = makeFile("exclude-b.txt", 1024)
        session.sendBatch(listOf(TestFileProvider.uriFor(first), TestFileProvider.uriFor(second)))
        assertTrue(waitFor(5_000) { session.batch.value?.includedCount == 2 })
        assertEquals(true, session.batch.value?.isBatchUi)
        val idA = session.batch.value!!.items.first { it.name == "exclude-a.txt" }.id
        session.setQueueItemExcluded(idA, true)
        assertEquals(1, session.batch.value?.includedCount)
        assertEquals(false, session.batch.value?.becameBatch)
        assertEquals(false, session.batch.value?.isBatchUi)
        // Un-excluding sets the latch again.
        session.setQueueItemExcluded(idA, false)
        assertEquals(2, session.batch.value?.includedCount)
        assertEquals(true, session.batch.value?.isBatchUi)
        // Excluding again and closing the sheet drops the file and
        // leaves a single-file queue.
        session.setQueueItemExcluded(idA, true)
        session.purgeExcludedItems()
        assertEquals(1, session.batch.value?.items?.size)
        assertEquals(false, session.batch.value?.isBatchUi)
    }
}

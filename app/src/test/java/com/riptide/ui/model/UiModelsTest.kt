package com.riptide.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiModelsTest {
    @Test
    fun testDefaultHomeUiState() {
        val state = HomeUiState()
        assertEquals("------", state.myCode)
        assertEquals(false, state.isConnecting)
        assertNull(state.approvalRequest)
    }

    @Test
    fun testActiveTransferProgressCalculation() {
        val transfer = ActiveTransfer(filename = "sample.iso", direction = TransferDirection.RECEIVE, totalBytes = 1000L, transferredBytes = 500L, speedFormatted = "10.0 MB/s", progress = 0.5f, etaFormatted = "~2s")
        assertEquals(0.5f, transfer.progress, 0.001f)
        assertEquals(TransferDirection.RECEIVE, transfer.direction)
    }

    @Test
    fun testBatchStateOverallProgress() {
        val uri = "content://com.example/1"
        // Position counts files, the bar counts bytes: a small file and
        // a large file each advance the position by one, while the bar
        // advances by bytes.
        val items = listOf(
            BatchItem(id = "1", uri = uri, name = "a.pdf", providerSize = 100L, spooledSize = 100L, phase = BatchItemPhase.SENT),
            BatchItem(id = "2", uri = uri, name = "b.mp4", providerSize = 900L, spooledSize = 900L, transferredBytes = 450L, phase = BatchItemPhase.SENDING),
            BatchItem(id = "3", uri = uri, name = "c.txt", providerSize = 1000L, phase = BatchItemPhase.QUEUED)
        )
        val batch = BatchState(items = items, activeIndex = 1, sentCount = 1)
        assertEquals(2000L, batch.totalBytes)
        assertEquals(100L, batch.confirmedBytes)
        assertEquals(550L, batch.overallTransferred)
        assertEquals(0.275f, batch.overallProgress!!, 0.001f)
        assertEquals(27, batch.overallPercent)
        assertEquals(false, batch.isVerifying)
    }

    @Test
    fun testBatchStateUnknownSizeIsIndeterminate() {
        val uri = "content://com.example/1"
        val items = listOf(
            BatchItem(id = "1", uri = uri, name = "a.pdf", providerSize = null, phase = BatchItemPhase.SENDING),
            BatchItem(id = "2", uri = uri, name = "b.mp4", providerSize = 900L, phase = BatchItemPhase.QUEUED)
        )
        val batch = BatchState(items = items, activeIndex = 0)
        assertNull(batch.totalBytes)
        assertNull(batch.overallProgress)
        assertNull(batch.overallPercent)
        assertEquals(false, batch.isVerifying)
    }

    @Test
    fun testBatchStateVerifyingReservesFullPercent() {
        val uri = "content://com.example/1"
        // All bytes written, last ack pending: the bar is full but the
        // percent stays below 100 until the ack confirms it.
        val items = listOf(
            BatchItem(id = "1", uri = uri, name = "a.pdf", providerSize = 100L, spooledSize = 100L, phase = BatchItemPhase.SENT),
            BatchItem(id = "2", uri = uri, name = "b.mp4", providerSize = 100L, spooledSize = 100L, transferredBytes = 100L, phase = BatchItemPhase.VERIFYING)
        )
        val batch = BatchState(items = items, activeIndex = 1, sentCount = 1)
        assertEquals(true, batch.isVerifying)
        assertEquals(99, batch.overallPercent)
    }
}

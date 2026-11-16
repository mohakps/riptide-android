package com.riptide.ui.session

import com.riptide.ui.model.ActiveTransfer
import com.riptide.ui.model.BusyPhase
import com.riptide.ui.model.DownloadState
import com.riptide.ui.model.TransferDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionViewModelTest {
    @Test
    fun testActiveTransferDisablesCanStartTransfer() {
        val vm = SessionViewModel()
        vm.setActiveTransfer(ActiveTransfer(filename = "video.mp4", direction = TransferDirection.SEND, totalBytes = 2000L, transferredBytes = 500L, speedFormatted = "12.5 MB/s", progress = 0.25f, etaFormatted = "~3s"))
        assertFalse(vm.uiState.value.canStartTransfer)
        assertEquals("video.mp4", vm.uiState.value.activeTransfer?.filename)
    }

    @Test
    fun testCanStartRequiresConnected() {
        val vm = SessionViewModel()
        // Default state is not connected: no transfer may start.
        assertFalse(vm.uiState.value.canStartTransfer)
        vm.setConnected(true)
        assertTrue(vm.uiState.value.canStartTransfer)
    }

    @Test
    fun testBusyPhasesBlockSecondTransfer() {
        val vm = SessionViewModel()
        vm.setConnected(true)
        assertTrue(vm.uiState.value.canStartTransfer)
        // Preparing, verifying and waiting-for-ack all block, even with no
        // active transfer object (active == null alone is not enough).
        for (phase in listOf(BusyPhase.PREPARING, BusyPhase.VERIFYING, BusyPhase.WAITING_ACK, BusyPhase.TRANSFERRING)) {
            vm.setBusyPhase(phase)
            assertFalse("busy $phase must block", vm.uiState.value.canStartTransfer)
        }
        vm.setBusyPhase(null)
        assertTrue(vm.uiState.value.canStartTransfer)
    }

    @Test
    fun testPendingDownloadBlocksSecondTransferButDenialDoesNot() {
        val vm = SessionViewModel()
        vm.setConnected(true)
        vm.setDownloadState(DownloadState.Waiting("report.pdf"))
        assertFalse(vm.uiState.value.canStartTransfer)
        vm.setDownloadState(DownloadState.Denied("report.pdf"))
        assertTrue(vm.uiState.value.canStartTransfer)
        vm.setDownloadState(DownloadState.Idle)
        assertTrue(vm.uiState.value.canStartTransfer)
    }

    @Test
    fun testBatchBlocksSecondTransfer() {
        val vm = SessionViewModel()
        vm.setConnected(true)
        assertTrue(vm.uiState.value.canStartTransfer)
        val uri = "content://com.example/1"
        vm.setBatchState(
            com.riptide.ui.model.BatchState(
                items = listOf(
                    com.riptide.ui.model.BatchItem(id = "1", uri = uri, name = "a.pdf", providerSize = 100L),
                    com.riptide.ui.model.BatchItem(id = "2", uri = uri, name = "b.mp4", providerSize = 200L)
                ),
                activeIndex = 0
            )
        )
        assertFalse(vm.uiState.value.canStartTransfer)
        vm.setBatchState(null)
        assertTrue(vm.uiState.value.canStartTransfer)
    }

    @Test
    fun testRunningQueueAllowsAppending() {
        val vm = SessionViewModel()
        vm.setConnected(true)
        assertTrue(vm.uiState.value.canStartTransfer)
        // A running queue owns the busy phase and the active transfer:
        // appending a file to the bottom of the queue is allowed, the
        // runner serializes it behind the current file.
        vm.setBatchState(
            com.riptide.ui.model.BatchState(
                items = listOf(
                    com.riptide.ui.model.BatchItem(id = "1", uri = "content://com.example/1", name = "a.pdf", providerSize = 100L)
                ),
                activeIndex = 0,
                running = true
            )
        )
        vm.setActiveTransfer(ActiveTransfer(filename = "a.pdf", direction = TransferDirection.SEND, totalBytes = 100L, transferredBytes = 50L, speedFormatted = "1 MB/s", progress = 0.5f, etaFormatted = "~1s"))
        vm.setBusyPhase(BusyPhase.TRANSFERRING)
        assertTrue(vm.uiState.value.canStartTransfer)
    }

    @Test
    fun testReceiveBlocksAppending() {
        val vm = SessionViewModel()
        vm.setConnected(true)
        assertTrue(vm.uiState.value.canStartTransfer)
        // A receive in flight blocks a send: the engine cannot send
        // while the reader is mid-payload.
        vm.setActiveTransfer(ActiveTransfer(filename = "in.mp4", direction = TransferDirection.RECEIVE, totalBytes = 2000L, transferredBytes = 500L, speedFormatted = "12.5 MB/s", progress = 0.25f, etaFormatted = "~3s"))
        assertFalse(vm.uiState.value.canStartTransfer)
    }

    @Test
    fun testCancellingTransferDefaultsToFalse() {
        val vm = SessionViewModel()
        assertFalse(vm.uiState.value.isCancellingTransfer)
    }

    @Test
    fun testCancellingTransferBlocksNewTransfers() {
        val vm = SessionViewModel()
        vm.setConnected(true)
        assertTrue(vm.uiState.value.canStartTransfer)
        vm.setCancellingTransfer(true)
        assertFalse(vm.uiState.value.canStartTransfer)
        vm.setCancellingTransfer(false)
        assertTrue(vm.uiState.value.canStartTransfer)
    }

    @Test
    fun testCancelledRecordKeepsStatusAndSizeInHistory() {
        val vm = SessionViewModel()
        vm.addTransferRecord(
            com.riptide.ui.model.TransferRecord(
                id = "1",
                filename = "report.pdf",
                savedFilename = "report.pdf",
                wasRenamed = false,
                sizeBytes = 42L * 1024 * 1024,
                direction = com.riptide.ui.model.TransferDirection.RECEIVE,
                status = com.riptide.ui.model.TransferStatus.CANCELLED,
                sha256Verified = false
            )
        )
        val records = vm.uiState.value.transferHistory
        assertEquals(1, records.size)
        assertEquals(com.riptide.ui.model.TransferStatus.CANCELLED, records[0].status)
        assertEquals(42L * 1024 * 1024, records[0].sizeBytes)
        assertEquals("report.pdf", records[0].filename)
    }
}

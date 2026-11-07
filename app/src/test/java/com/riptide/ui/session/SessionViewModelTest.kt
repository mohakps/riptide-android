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
}

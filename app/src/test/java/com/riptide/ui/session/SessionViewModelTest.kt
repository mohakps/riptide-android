package com.riptide.ui.session

import com.riptide.ui.model.ActiveTransfer
import com.riptide.ui.model.TransferDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionViewModelTest {
    @Test
    fun testActiveTransferDisablesCanStartTransfer() {
        val vm = SessionViewModel()
        vm.setActiveTransfer(ActiveTransfer(filename = "video.mp4", direction = TransferDirection.SEND, totalBytes = 2000L, transferredBytes = 500L, speedFormatted = "12.5 MB/s", progress = 0.25f, etaFormatted = "~3s"))
        assertFalse(vm.uiState.value.canStartTransfer)
        assertEquals("video.mp4", vm.uiState.value.activeTransfer?.filename)
    }
}

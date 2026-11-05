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
}

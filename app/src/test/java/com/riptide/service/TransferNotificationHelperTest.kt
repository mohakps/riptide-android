package com.riptide.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferNotificationHelperTest {
    @Test
    fun testNotificationChannelId() {
        assertEquals("riptide_transfers", TransferNotificationHelper.CHANNEL_ID)
    }
}

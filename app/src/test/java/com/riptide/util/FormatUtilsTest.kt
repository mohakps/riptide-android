package com.riptide.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {
    @Test
    fun testFormatBytes() {
        assertEquals("128 B", formatBytes(128))
        assertEquals("512 KB", formatBytes(512 * 1024))
        assertEquals("2.4 MB", formatBytes(2_516_582)) // 2.4 * 1024 * 1024, rounded
        assertEquals("256 MB", formatBytes(256L * 1024 * 1024))
        assertEquals("0 B", formatBytes(0))
    }

    @Test
    fun testDayLabel() {
        val now = 1794060000000L // 2026-11-07 ~21:00 IST
        assertEquals("TODAY", dayLabel(now, now))
        assertEquals("YESTERDAY", dayLabel(now - 86_400_000L, now))
        assertEquals("5 Nov 2026", dayLabel(now - 2 * 86_400_000L, now))
    }
}

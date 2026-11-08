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
        assertEquals("1.2 GB", formatBytes(1_331_439_862L)) // 1.24 * 1024^3, one decimal
        assertEquals("2 GB", formatBytes(2L * 1024 * 1024 * 1024))
        assertEquals("0 B", formatBytes(0))
    }

    @Test
    fun testFormatBytesPair() {
        // Shared unit shown once, no spaces around the slash, a
        // nonbreaking space before the unit.
        assertEquals("423.8/504.9\u00A0MB", formatBytesPair(444_386_509L, 529_426_022L, java.util.Locale.US))
        assertEquals("256/512\u00A0KB", formatBytesPair(256L * 1024, 512L * 1024, java.util.Locale.US))
        assertEquals("0/0\u00A0B", formatBytesPair(0, 0, java.util.Locale.US))
        // A batch total over a gigabyte shares the GB unit.
        assertEquals("1.2/2\u00A0GB", formatBytesPair(1_331_439_862L, 2L * 1024 * 1024 * 1024, java.util.Locale.US))
        // Shared unit based on the total, even when the amounts differ.
        assertEquals("0.5/2\u00A0MB", formatBytesPair(512L * 1024, 2L * 1024 * 1024, java.util.Locale.US))
        assertEquals("0.1/2\u00A0KB", formatBytesPair(128, 2L * 1024, java.util.Locale.US))
        // A positive amount that would round to zero in the shared unit
        // keeps its own unit instead.
        assertEquals("500 B/2\u00A0MB", formatBytesPair(500, 2L * 1024 * 1024, java.util.Locale.US))
        // Values at or above 1000 in the shared unit drop the decimal,
        // and totals over a gigabyte share the GB unit.
        assertEquals(
            "1.5/2\u00A0GB",
            formatBytesPair(1610612736L, 2147483648L, java.util.Locale.US)
        )
        // The decimal separator follows the locale.
        assertEquals("423,8/504,9\u00A0MB", formatBytesPair(444_386_509L, 529_426_022L, java.util.Locale.GERMANY))
    }

    @Test
    fun testFormatDuration() {
        // At most two nonzero units; zero units are omitted.
        assertEquals("2s", formatDuration(2))
        assertEquals("2m 13s", formatDuration(133))
        assertEquals("1h 41s", formatDuration(3600 + 41))
        assertEquals("1h 2m", formatDuration(3600 + 120 + 3))
        assertEquals("1h 2m", formatDuration(3600 + 120))
        assertEquals("45s", formatDuration(45))
        assertEquals("0s", formatDuration(0))
    }

    @Test
    fun testFormatRemainingTime() {
        assertEquals("2s remaining", formatRemainingTime(20_000L, 10_000.0))
        assertEquals("2m 13s remaining", formatRemainingTime(133_000L, 1_000.0))
        assertEquals("1h 41s remaining", formatRemainingTime(3_641_000L, 1_000.0))
        // A remaining amount that would round to zero seconds still
        // shows at least one second.
        assertEquals("1s remaining", formatRemainingTime(10L, 100_000.0))
        // No usable rate, or nothing left: no ETA to show.
        assertEquals(null, formatRemainingTime(1_000L, 0.0))
        assertEquals(null, formatRemainingTime(0L, 1_000.0))
        assertEquals(null, formatRemainingTime(-5L, 1_000.0))
    }

    @Test
    fun testDayLabel() {
        val now = 1794060000000L // 2026-11-07 ~21:00 IST
        assertEquals("TODAY", dayLabel(now, now))
        assertEquals("YESTERDAY", dayLabel(now - 86_400_000L, now))
        assertEquals("5 Nov 2026", dayLabel(now - 2 * 86_400_000L, now))
    }
}

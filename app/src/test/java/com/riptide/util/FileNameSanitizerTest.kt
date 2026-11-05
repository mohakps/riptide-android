package com.riptide.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {
    @Test
    fun testSanitizeRemovesControlCharacters() {
        val dangerous = "report\u0000\u001B[31m.pdf"
        assertEquals("report.pdf", sanitizeForDisplay(dangerous))
    }

    @Test
    fun testBasenameOnly() {
        assertEquals("report.pdf", basenameOnly("/sdcard/Download/report.pdf"))
        assertEquals("report.pdf", basenameOnly("..\\..\\report.pdf"))
    }
}

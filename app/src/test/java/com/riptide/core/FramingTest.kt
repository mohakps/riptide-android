package com.riptide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FramingTest {
    @Test
    fun testHeaderRoundTrip() {
        val out = ByteArrayOutputStream()
        writeHeader(out, """{"type":"ping"}""".toByteArray())
        val bytes = readHeaderBytes(ByteArrayInputStream(out.toByteArray()))
        assertEquals("""{"type":"ping"}""", String(bytes))
    }

    @Test
    fun testUniqueFinalName() {
        assertEquals("report.pdf", uniqueFinalName(emptySet(), "report.pdf"))
        assertEquals("report (1).pdf", uniqueFinalName(setOf("report.pdf"), "report.pdf"))
        assertEquals("report (2).pdf", uniqueFinalName(setOf("report.pdf", "report (1).pdf"), "report.pdf"))
        assertEquals("archive.tar (1).gz", uniqueFinalName(setOf("archive.tar.gz"), "archive.tar.gz"))
        assertEquals(".bashrc (1)", uniqueFinalName(setOf(".bashrc"), ".bashrc"))
        assertEquals("notes (1)", uniqueFinalName(setOf("notes"), "notes"))
    }

    @Test
    fun testParseHostInput() {
        assertEquals("metalvoid.ddns.net", parseHostInput("metalvoid.ddns.net"))
        assertEquals("192.168.1.10", parseHostInput("192.168.1.10:50000"))
        assertEquals("metalvoid.ddns.net", parseHostInput("user@metalvoid.ddns.net"))
        assertEquals("2001:db8::1", parseHostInput("[2001:db8::1]"))
        assertEquals("2001:db8::1", parseHostInput("2001:db8::1"))
    }
}

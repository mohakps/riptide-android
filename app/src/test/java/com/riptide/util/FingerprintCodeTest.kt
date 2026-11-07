package com.riptide.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The design fixtures: phone code 000-001 from fingerprint prefix 000001,
 * host code 193-046 from 123456, previous host 482-610 from 075d32.
 * The trust sheet (host code) and the host-waiting sheet (phone code) must
 * derive from different certificates, so their codes must differ.
 */
class FingerprintCodeTest {
    @Test
    fun testFixtureCodes() {
        assertEquals("000-001", derive6DigitCode("000001" + "00".repeat(29)))
        assertEquals("193-046", derive6DigitCode("123456" + "00".repeat(29)))
        assertEquals("482-610", derive6DigitCode("075d32" + "00".repeat(29)))
    }

    @Test
    fun testPhoneAndHostCodesDiffer() {
        val phone = derive6DigitCode("000001" + "00".repeat(29))
        val host = derive6DigitCode("123456" + "00".repeat(29))
        assertNotEquals(phone, host)
    }

    @Test
    fun testUnavailableCode() {
        assertEquals("------", derive6DigitCode(""))
        assertEquals("------", derive6DigitCode("ab"))
    }
}

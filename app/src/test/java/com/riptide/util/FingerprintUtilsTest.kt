package com.riptide.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FingerprintUtilsTest {
    @Test
    fun testPCv13TestVectors() {
        assertEquals("193-046", derive6DigitCode("123456abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234"))
        assertEquals("000-001", derive6DigitCode("000001abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234"))
        assertEquals("------", derive6DigitCode(""))
        assertEquals("------", derive6DigitCode("xyz"))
        assertEquals("193-046", derive6DigitCode("123456ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234"))
    }
}

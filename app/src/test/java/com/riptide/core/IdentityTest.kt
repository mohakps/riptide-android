package com.riptide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityTest {
    @Test
    fun testIdentityFingerprintAndCodeAndSignature() {
        val id = IdentityManager(null)
        val der = id.certDer()
        assertTrue(der.isNotEmpty())
        val fp = id.fingerprint()
        assertEquals(64, fp.length)
        assertEquals(id.code(), com.riptide.util.derive6DigitCode(fp))
        val nonce = ByteArray(32) { it.toByte() }
        val sig = id.signNonce(nonce)
        assertTrue(id.verifyNonceSignature(der, nonce, sig))
        assertTrue(!id.verifyNonceSignature(der, nonce, ByteArray(256)))
    }
}

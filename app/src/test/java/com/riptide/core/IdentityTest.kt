package com.riptide.core

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.PSSParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun testSoftwareKeyIsExtractableAndSignsPkcs1AndPss() {
        // v0.9.2: the identity key is software-generated, so its
        // encoded form is available — the property the
        // AndroidKeyStore key lacked, which made Conscrypt's native
        // TLS code fail every host handshake.
        val id = IdentityManager(null)
        id.certDer()
        val privateKey = id.privateKey()
        val encoded = privateKey.encoded
        assertTrue(encoded != null)
        // The encoded key reloads as the same key.
        val reloaded = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(encoded))
        assertEquals(privateKey, reloaded)
        val nonce = ByteArray(32) { it.toByte() }
        // PKCS1v15 (hello challenge, TLS 1.2 ECDHE signing).
        val pkcs1 = Signature.getInstance("SHA256withRSA")
        pkcs1.initSign(privateKey)
        pkcs1.update(nonce)
        val pkcs1Sig = pkcs1.sign()
        val verifyPkcs1 = Signature.getInstance("SHA256withRSA")
        verifyPkcs1.initVerify(
            java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(id.certDer().inputStream()) as java.security.cert.X509Certificate
        )
        verifyPkcs1.update(nonce)
        assertTrue(verifyPkcs1.verify(pkcs1Sig))
        // RSA-PSS (TLS 1.3 CertificateVerify).
        val pss = Signature.getInstance("RSASSA-PSS")
        pss.setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        pss.initSign(privateKey)
        pss.update(nonce)
        val pssSig = pss.sign()
        val verifyPss = Signature.getInstance("RSASSA-PSS")
        verifyPss.setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        verifyPss.initVerify(
            java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(id.certDer().inputStream()) as java.security.cert.X509Certificate
        )
        verifyPss.update(nonce)
        assertTrue(verifyPss.verify(pssSig))
    }

    @Test
    fun testMigrationDecision() {
        val id = IdentityManager(null)
        // Upgrade: certificate file exists, no identity.key file.
        assertTrue(id.needsMigration(keyFileExists = false, certFileExists = true, keystoreAliasExists = false))
        // Upgrade: only the keystore alias is left.
        assertTrue(id.needsMigration(keyFileExists = false, certFileExists = false, keystoreAliasExists = true))
        // Already migrated: the software key file exists.
        assertFalse(id.needsMigration(keyFileExists = true, certFileExists = true, keystoreAliasExists = true))
        assertFalse(id.needsMigration(keyFileExists = true, certFileExists = false, keystoreAliasExists = false))
        // Fresh install: nothing stored at all.
        assertFalse(id.needsMigration(keyFileExists = false, certFileExists = false, keystoreAliasExists = false))
        // No context (unit tests): nothing to migrate.
        assertFalse(id.needsMigration(keyFileExists = false, certFileExists = false, keystoreAliasExists = false))
    }
}

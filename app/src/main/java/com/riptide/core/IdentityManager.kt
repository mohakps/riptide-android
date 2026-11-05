package com.riptide.core

import android.content.Context
import com.riptide.util.certFingerprint
import com.riptide.util.derive6DigitCode
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.Date
import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class IdentityManager(private val context: Context? = null) {
    private var memoryKeyPair: KeyPair? = null
    private var memoryCertDer: ByteArray? = null

    fun certDer(): ByteArray {
        memoryCertDer?.let { return it }
        val file = context?.let { File(it.filesDir, "identity.crt") }
        if (file != null && file.exists()) {
            return file.readBytes().let { pemToDer(it) }
        }
        val (keyPair, der) = generate()
        if (file != null) {
            try { file.writeBytes(derToPem(der, "CERTIFICATE")) } catch (_: Exception) {}
        }
        return der
    }

    fun fingerprint(): String = certFingerprint(certDer())
    fun code(): String = derive6DigitCode(fingerprint())

    fun signNonce(nonce: ByteArray): ByteArray {
        val key = privateKey()
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(key)
        sig.update(nonce)
        return sig.sign()
    }

    fun verifyNonceSignature(certDer: ByteArray, nonce: ByteArray, signature: ByteArray): Boolean {
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(certDer.inputStream()) as X509Certificate
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(cert.publicKey)
            sig.update(nonce)
            sig.verify(signature)
        } catch (_: Exception) {
            false
        }
    }

    private fun privateKey(): PrivateKey {
        memoryKeyPair?.let { return it.private }
        val ks = keyStoreOrNull()
        if (ks != null) {
            try {
                val entry = ks.getEntry("riptide_identity", null) as? KeyStore.PrivateKeyEntry
                if (entry != null) return entry.privateKey
            } catch (_: Exception) {}
        }
        val (kp, _) = generate()
        return kp.private
    }

    private fun keyStoreOrNull(): KeyStore? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            ks
        } catch (_: Exception) {
            null
        }
    }

    private fun generate(): Pair<KeyPair, ByteArray> {
        memoryKeyPair?.let { kp ->
            memoryCertDer?.let { return kp to it }
        }
        val ks = keyStoreOrNull()
        val keyPair: KeyPair
        if (ks != null && context != null) {
            val kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                "riptide_identity",
                android.security.keystore.KeyProperties.PURPOSE_SIGN
            )
                .setKeySize(2048)
                .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()
            kpg.initialize(spec)
            keyPair = kpg.generateKeyPair()
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048, SecureRandom())
            keyPair = kpg.generateKeyPair()
            memoryKeyPair = keyPair
        }
        val der = buildSelfSignedCert(keyPair)
        memoryCertDer = der
        return keyPair to der
    }

    fun buildSelfSignedCert(keyPair: KeyPair): ByteArray {
        val now = Date()
        val cal = Calendar.getInstance()
        cal.time = now
        cal.add(Calendar.YEAR, 10)
        val notAfter = cal.time
        val subject = X500Name("CN=riptide")
        val builder = X509v3CertificateBuilder(
            subject, BigInteger(64, SecureRandom()), now, notAfter, subject,
            org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val holder = builder.build(signer)
        val cert = JcaX509CertificateConverter().getCertificate(holder)
        return cert.encoded
    }

    private fun pemToDer(pem: ByteArray): ByteArray {
        val text = String(pem)
        return if ("-----BEGIN" in text) {
            val b64 = text.lines().filter { !it.startsWith("-----") }.joinToString("")
            android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        } else {
            pem
        }
    }

    private fun derToPem(der: ByteArray, type: String): ByteArray {
        val b64 = android.util.Base64.encodeToString(der, android.util.Base64.NO_WRAP)
        val sb = StringBuilder()
        sb.append("-----BEGIN $type-----\n")
        b64.chunked(64).forEach { sb.append(it).append('\n') }
        sb.append("-----END $type-----\n")
        return sb.toString().toByteArray()
    }
}

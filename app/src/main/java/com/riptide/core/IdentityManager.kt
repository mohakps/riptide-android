package com.riptide.core

import android.content.Context
import com.riptide.util.certFingerprint
import com.riptide.util.derive6DigitCode
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Calendar
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * The phone's identity: an RSA-2048 keypair plus a BouncyCastle
 * self-signed certificate (CN=riptide, 10 years). The fingerprint is
 * the SHA-256 of the certificate DER; the 6-digit code is derived
 * from it. Both peers compare the code out of band before Allow.
 *
 * v0.9.2: the key is SOFTWARE-generated and stored as PKCS#8 PEM in
 * the app-private filesDir (identity.key), next to the certificate
 * (identity.crt). v0.9/v0.9.1 generated the key in AndroidKeyStore
 * (PURPOSE_SIGN, PKCS1-only, later PKCS1+PSS) and handed that
 * non-extractable key to Conscrypt through an in-memory KeyStore;
 * every host handshake failed with an RSA internal error, on TLS 1.3
 * and on TLS 1.2, and survived a Reset identity (the new key was
 * made the same way). A software key has no such restriction: its
 * encoded form is available, Conscrypt can use it, and TLS 1.3
 * works. The key file lives in internal storage (private to the
 * app), the same model as the PC, which stores ~/.riptide/identity.key.
 */
class IdentityManager(private val context: Context? = null) {
    private var memoryKeyPair: KeyPair? = null
    private var memoryPrivateKey: PrivateKey? = null
    private var memoryCertDer: ByteArray? = null
    private var migrationChecked = false

    private fun certFile(): File? = context?.let { File(it.filesDir, "identity.crt") }
    private fun keyFile(): File? = context?.let { File(it.filesDir, "identity.key") }

    fun certDer(): ByteArray {
        memoryCertDer?.let { return it }
        maybeMigrate()
        val file = certFile()
        if (file != null && file.exists()) {
            return file.readBytes().let { pemToDer(it) }
        }
        val (keyPair, der) = generate()
        writeIdentityFiles(keyPair, der)
        return der
    }

    fun fingerprint(): String = certFingerprint(certDer())
    fun code(): String = derive6DigitCode(fingerprint())

    /** Regenerate the identity: a fresh keypair and certificate, so the
     * phone gets a new fingerprint and previously connected devices
     * must verify it again. Both files are written via a temp file and
     * an atomic rename, so the stored identity is never half-written.
     * On failure the prior identity is restored from the snapshot and
     * false is returned; the caller keeps using the old identity. */
    fun reset(): Boolean {
        val oldDer = try { certDer() } catch (_: Exception) { null }
        val oldKey = try { keyFile()?.takeIf { it.exists() }?.readBytes() } catch (_: Exception) { null }
        memoryKeyPair = null
        memoryPrivateKey = null
        memoryCertDer = null
        return try {
            val (kp, der) = generate()
            writeIdentityFiles(kp, der)
            true
        } catch (_: Exception) {
            memoryKeyPair = null
            memoryPrivateKey = null
            memoryCertDer = null
            val cf = certFile()
            if (cf != null && oldDer != null) {
                try { writeFileAtomic(cf, derToPem(oldDer, "CERTIFICATE")) } catch (_: Exception) {}
            }
            val kf = keyFile()
            if (kf != null && oldKey != null) {
                try { writeFileAtomic(kf, oldKey) } catch (_: Exception) {}
            }
            false
        }
    }

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

    /** The identity private key (a software key; encoded is non-null).
     * Used by signNonce and by HostManager's TLS context. */
    internal fun privateKey(): PrivateKey {
        memoryPrivateKey?.let { return it }
        memoryKeyPair?.let { return it.private.also { memoryPrivateKey = it } }
        keyFile()?.let { kf ->
            if (kf.exists()) {
                try {
                    val spec = PKCS8EncodedKeySpec(pemToDer(kf.readBytes()))
                    val pk = KeyFactory.getInstance("RSA").generatePrivate(spec)
                    memoryPrivateKey = pk
                    return pk
                } catch (_: Exception) {
                    // Fall through: no usable stored key.
                }
            }
        }
        // No stored key (fresh install, or a certificate whose key is
        // gone): generate a fresh identity and persist it. A stored
        // certificate without its key is unusable, so regenerating
        // both is correct; the fingerprint changes once.
        val (kp, der) = generate()
        writeIdentityFiles(kp, der)
        return kp.private
    }

    /**
     * Pure migration decision, unit-testable: a pre-v0.9.2 identity
     * (AndroidKeyStore key, certificate file but no identity.key)
     * must be replaced by a software identity on first use.
     */
    internal fun needsMigration(keyFileExists: Boolean, certFileExists: Boolean, keystoreAliasExists: Boolean): Boolean =
        !keyFileExists && (certFileExists || keystoreAliasExists)

    /**
     * One-time migration, runs at most once per process: if the
     * stored identity predates v0.9.2, regenerate it as a software
     * identity (new fingerprint/code; the laptop sees the phone as
     * Changed once and must verify the new code out of band). The
     * AndroidKeyStore entry is left in place (harmless; deleting it
     * could break a downgrade). After migration the AndroidKeyStore
     * is never used for the identity again: the only remaining
     * reference is the read-only alias check in this function.
     */
    private fun maybeMigrate() {
        if (migrationChecked) return
        migrationChecked = true
        val kf = keyFile() ?: return
        val certExists = certFile()?.exists() == true
        val aliasExists = try {
            keyStoreOrNull()?.containsAlias("riptide_identity") == true
        } catch (_: Exception) {
            false
        }
        if (!needsMigration(kf.exists(), certExists, aliasExists)) return
        DiagLog.log("identity", "migrating AndroidKeyStore identity to software key (new fingerprint)")
        reset()
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

    /** Software keypair, 2048-bit. No AndroidKeyStore: a keystore key
     * cannot be used by Conscrypt's TLS code (v0.9/v0.9.1). */
    private fun generate(): Pair<KeyPair, ByteArray> {
        memoryKeyPair?.let { kp ->
            memoryCertDer?.let { return kp to it }
        }
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val keyPair = kpg.generateKeyPair()
        memoryKeyPair = keyPair
        memoryPrivateKey = keyPair.private
        val der = buildSelfSignedCert(keyPair)
        memoryCertDer = der
        return keyPair to der
    }

    /** Persist both identity files (temp file + atomic rename each). */
    private fun writeIdentityFiles(keyPair: KeyPair, der: ByteArray) {
        val cf = certFile() ?: return
        val kf = keyFile() ?: return
        try { writeFileAtomic(cf, derToPem(der, "CERTIFICATE")) } catch (_: Exception) {}
        val encoded = keyPair.private.encoded ?: return
        try { writeFileAtomic(kf, derToPem(encoded, "PRIVATE KEY")) } catch (_: Exception) {}
    }

    private fun writeFileAtomic(file: File, bytes: ByteArray) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) file.writeBytes(bytes)
        } catch (_: Exception) {
            file.writeBytes(bytes)
        }
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

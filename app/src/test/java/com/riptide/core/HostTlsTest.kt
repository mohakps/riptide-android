package com.riptide.core

import com.riptide.util.certFingerprint
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

/**
 * v0.9.2 regression tests for the phone-host TLS failure: a laptop
 * joining the phone's Create Server over LAN got "TLS failed" because
 * the host handed an AndroidKeyStore-backed (non-extractable) private
 * key to Conscrypt through an in-memory KeyStore, and Conscrypt's
 * native TLS code failed to use it (RSA internal error) on TLS 1.3
 * and on TLS 1.2 alike — surviving the v0.9.1 TLS 1.2 restriction
 * and a Reset identity (the new key was made the same way). The fix
 * is a software identity key: with an extractable key the same
 * construction (createHostSslContext) negotiates TLS 1.3 fine. These
 * tests run against a software identity, as the production code now
 * generates one.
 */
class HostTlsTest {

    private fun freshIdentity(): Triple<java.security.KeyPair, X509Certificate, ByteArray> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()
        val der = IdentityManager(null).buildSelfSignedCert(kp)
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(der.inputStream()) as X509Certificate
        return Triple(kp, cert, der)
    }

    private fun trustAllContext(): SSLContext {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }
    }

    @Test
    fun testHostKeyStorePresentsIdentityCert() {
        val (kp, cert, _) = freshIdentity()
        val store = buildHostKeyStore(kp.private, cert)
        val chain = store.getCertificateChain("riptide_identity")
        assertEquals(1, chain.size)
        val presented = chain[0] as X509Certificate
        assertEquals(certFingerprint(cert.encoded), certFingerprint(presented.encoded))
        // The chain cert is the identity cert itself, not a different
        // cert for the same key: same DER, same fingerprint.
        assertTrue(cert.encoded.contentEquals(presented.encoded))
        val ctx = createHostSslContext(kp.private, cert)
        assertTrue(ctx.serverSocketFactory != null)
    }

    @Test
    fun testLoopbackHandshakeNegotiatesTls13AndPresentsIdentityCert() {
        val (kp, cert, _) = freshIdentity()
        // The software key's encoded form must be available: that is
        // exactly what the AndroidKeyStore key lacked, and what made
        // Conscrypt fail to use it.
        assertTrue(kp.private.encoded != null)
        val serverCtx = createHostSslContext(kp.private, cert)
        val server = serverCtx.serverSocketFactory.createServerSocket(
            0, 1, InetAddress.getByName("127.0.0.1")
        ) as SSLServerSocket
        server.soTimeout = 10_000
        var serverProtocol = ""
        var serverError: Exception? = null
        val acceptThread = thread {
            try {
                val accepted = server.accept() as SSLSocket
                accepted.soTimeout = 10_000
                accepted.startHandshake()
                serverProtocol = accepted.session.protocol
                accepted.close()
            } catch (e: Exception) {
                serverError = e
            } finally {
                try { server.close() } catch (_: Exception) {}
            }
        }
        val clientCtx = trustAllContext()
        val client = clientCtx.socketFactory.createSocket("127.0.0.1", server.localPort) as SSLSocket
        try {
            client.soTimeout = 10_000
            client.startHandshake()
            // No protocol restriction any more: the software key
            // signs RSA-PSS, so TLS 1.3 negotiates.
            assertEquals("TLSv1.3", client.session.protocol)
            // The cert the client saw must fingerprint-match the identity.
            val peer = client.session.peerCertificates[0] as X509Certificate
            assertEquals(certFingerprint(cert.encoded), certFingerprint(peer.encoded))
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
        acceptThread.join(15_000)
        if (serverError != null) throw AssertionError("server handshake failed: $serverError")
        // Read the server-side protocol only after the accept thread
        // has finished: the client's handshake can return while the
        // server is still completing its side.
        assertEquals("TLSv1.3", serverProtocol)
    }

    @Test
    fun testExceptionChainListsCauses() {
        val root = RuntimeException("root cause")
        val middle = IllegalStateException("middle", root)
        val top = javax.net.ssl.SSLHandshakeException("top")
        top.initCause(middle)
        val chain = exceptionChain(top)
        assertTrue(chain.contains("SSLHandshakeException: top"))
        assertTrue(chain.contains("IllegalStateException: middle"))
        assertTrue(chain.contains("RuntimeException: root cause"))
    }
}

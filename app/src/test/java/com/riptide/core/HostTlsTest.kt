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
 * v0.9.1 regression tests for the phone-host TLS failure: a laptop
 * joining the phone's Create Server over LAN got "TLS failed" because
 * (a) the unrestricted server negotiated TLS 1.3, which needs an
 * RSA-PSS CertificateVerify the PKCS1-only identity key cannot sign,
 * and (b) the server presented the AndroidKeyStore auto-generated
 * cert instead of the identity.crt cert the phone's code is derived
 * from. These tests run against the same construction the host uses
 * (createHostSslContext + HOST_TLS_PROTOCOLS), with an in-memory
 * identity standing in for the AndroidKeyStore one.
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
    fun testHostTlsProtocolsAreTls12Only() {
        assertEquals(listOf("TLSv1.2"), HOST_TLS_PROTOCOLS.toList())
    }

    @Test
    fun testLoopbackHandshakeNegotiatesTls12AndPresentsIdentityCert() {
        val (kp, cert, _) = freshIdentity()
        val serverCtx = createHostSslContext(kp.private, cert)
        val server = serverCtx.serverSocketFactory.createServerSocket(
            0, 1, InetAddress.getByName("127.0.0.1")
        ) as SSLServerSocket
        server.enabledProtocols = HOST_TLS_PROTOCOLS
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
            // The client offered TLS 1.3; the host must have forced 1.2.
            assertEquals("TLSv1.2", client.session.protocol)
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
        assertEquals("TLSv1.2", serverProtocol)
    }

    @Test
    fun testUnrestrictedServerWouldNegotiateTls13() {
        // Control: the v0.9 construction (no protocol restriction).
        // On this JVM the same client negotiates TLS 1.3, which is
        // what the PKCS1-only AndroidKeyStore key could not sign.
        val (kp, cert, _) = freshIdentity()
        val serverCtx = createHostSslContext(kp.private, cert)
        val server = serverCtx.serverSocketFactory.createServerSocket(
            0, 1, InetAddress.getByName("127.0.0.1")
        ) as SSLServerSocket
        server.soTimeout = 10_000
        var serverProtocol = ""
        val acceptThread = thread {
            try {
                val accepted = server.accept() as SSLSocket
                accepted.soTimeout = 10_000
                accepted.startHandshake()
                serverProtocol = accepted.session.protocol
                accepted.close()
            } catch (_: Exception) {
            } finally {
                try { server.close() } catch (_: Exception) {}
            }
        }
        val client = trustAllContext().socketFactory
            .createSocket("127.0.0.1", server.localPort) as SSLSocket
        try {
            client.soTimeout = 10_000
            client.startHandshake()
            // The unrestricted server negotiates TLS 1.3 on this JVM.
            // (If a future JDK ever dropped 1.3 this would need a
            // revisit, but the negotiation itself is the point.)
            assertEquals("TLSv1.3", client.session.protocol)
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
        acceptThread.join(15_000)
        assertEquals("TLSv1.3", serverProtocol)
    }
}

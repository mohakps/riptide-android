package com.riptide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Joining by hostname must remember the hostname in Recent devices,
 * not the IP it resolved to. The dial still connects to the resolved
 * candidate IP; only the stored peer key changes. A literal-IP join
 * is unchanged.
 *
 * Note: the peer store persists across tests in one run (each test
 * gets a fresh FakePeer identity), so assertions are scoped to this
 * test's peer fingerprint instead of assuming an empty store.
 */
@RunWith(RobolectricTestRunner::class)
class HostnamePeerKeyTest : LoopbackTestBase() {

    /** Connect to [host], approving the NEW fingerprint when prompted. */
    private fun connectHostAndApprove(host: String, timeoutMs: Long = 15_000): Boolean {
        session.connect(host)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (session.approval.value != null) {
                session.approveConnection(true)
            }
            if (session.connection.value == ConnectionState.CONNECTED) return true
            Thread.sleep(50)
        }
        println("connectHostAndApprove($host) failed: connection=${session.connection.value} error=${session.error.value}")
        return session.connection.value == ConnectionState.CONNECTED
    }

    private fun waitForIdle(timeoutMs: Long = 5_000): Boolean =
        waitFor(timeoutMs) { session.connection.value == ConnectionState.IDLE }

    @Test
    fun hostnameJoinSavesHostnameNotResolvedIp() {
        assertTrue(connectHostAndApprove("localhost"))
        assertTrue(
            "saved peers should contain the hostname after a hostname join: ${session.savedPeers.value}",
            waitFor { session.savedPeers.value.any { it.host == "localhost" && it.fingerprint == peer.fingerprint } }
        )
        assertFalse(
            "a hostname join must not save the resolved IP for this peer: ${session.savedPeers.value}",
            session.savedPeers.value.any { it.host == "127.0.0.1" && it.fingerprint == peer.fingerprint }
        )
        assertEquals("localhost", session.peerHost.value)
    }

    @Test
    fun literalIpJoinStillSavesIp() {
        assertTrue(connectHostAndApprove("127.0.0.1"))
        assertTrue(
            "saved peers should contain the literal IP: ${session.savedPeers.value}",
            waitFor { session.savedPeers.value.any { it.host == "127.0.0.1" && it.fingerprint == peer.fingerprint } }
        )
        assertFalse(
            "a literal-IP join must not save a hostname for this peer: ${session.savedPeers.value}",
            session.savedPeers.value.any { it.host == "localhost" && it.fingerprint == peer.fingerprint }
        )
    }

    @Test
    fun secondHostnameJoinIsKnownWithoutApproval() {
        assertTrue(connectHostAndApprove("localhost"))
        assertTrue(waitFor { session.savedPeers.value.any { it.host == "localhost" && it.fingerprint == peer.fingerprint } })
        session.disconnect()
        assertTrue(waitForIdle())
        // Second join by the same hostname: the stored fingerprint
        // must match, so no approval sheet may appear.
        var sawApproval = false
        session.connect("localhost")
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (session.approval.value != null) sawApproval = true
            if (session.connection.value == ConnectionState.CONNECTED) break
            Thread.sleep(50)
        }
        assertFalse("second join by hostname must not re-prompt for approval", sawApproval)
        assertEquals(ConnectionState.CONNECTED, session.connection.value)
    }

    @Test
    fun savedHostnameRejoinResolvesAndDials() {
        assertTrue(connectHostAndApprove("localhost"))
        assertTrue(waitFor { session.savedPeers.value.any { it.host == "localhost" && it.fingerprint == peer.fingerprint } })
        session.disconnect()
        assertTrue(waitForIdle())
        // Rejoin through the saved record exactly as the Recent
        // devices row does: the stored key is the hostname, and
        // connecting with it must re-resolve and dial.
        val savedHost = session.savedPeers.value.first { it.host == "localhost" && it.fingerprint == peer.fingerprint }.host
        assertEquals("localhost", savedHost)
        assertTrue(connectHostAndApprove(savedHost))
        assertEquals("localhost", session.peerHost.value)
    }
}

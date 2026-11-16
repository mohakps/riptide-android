package com.riptide.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Shared loopback harness: a real SessionManager (Robolectric
 * Context) talking to a [FakePeer] on 127.0.0.1:50000. The shadow
 * ConnectivityManager is given an active network with a LAN IPv4,
 * so NetworkMonitor reports IPv4 capability and connect() dials
 * instead of failing offline.
 *
 * Pinned to SDK 34: without this Robolectric falls back to its
 * default SDK 21, where ConnectivityManager.getActiveNetwork()
 * (added in API 23) does not exist and the NetworkMonitor
 * constructor throws NoSuchMethodError.
 */
@Config(sdk = [34])
abstract class LoopbackTestBase {
    protected lateinit var context: Context
    protected lateinit var peer: FakePeer
    protected lateinit var session: SessionManager

    @Before
    fun setUpLoopback() {
        context = ApplicationProvider.getApplicationContext()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // An active Wi-Fi network, so NetworkMonitor reports IPv4
        // capability and connect() dials instead of failing offline.
        // No LinkProperties are set: getLinkProperties returns null
        // and the capability read falls back to the VM's own
        // interfaces, which carry a non-loopback IPv4.
        val network = ShadowNetwork.newInstance(100)
        // ShadowNetworkInfo.newInstance, not the NetworkInfo
        // constructor: the real constructor reads a static EnumMap
        // that Robolectric resets between tests, so constructing one
        // directly throws NullPointerException here.
        val info = ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI, 0, true, true
        )
        val shadowCm = shadowOf(cm)
        shadowCm.addNetwork(network, info)
        shadowCm.setActiveNetworkInfo(info)
        // Serve test files through TestFileProvider, so sends resolve
        // their real display name from the ContentResolver, as picker
        // URIs do on a real device (a file:// URI has no provider and
        // every send would be named "file").
        // registerProviderInternal only stores the provider in a map;
        // it never attaches it, so attach first with a ProviderInfo
        // carrying the authority, or the resolver rejects the provider
        // ("authority does not match the one of the contentProvider").
        TestFileProvider.filesDir = context.cacheDir
        val provider = TestFileProvider()
        provider.attachInfo(
            context,
            android.content.pm.ProviderInfo().apply { authority = TestFileProvider.AUTHORITY }
        )
        ShadowContentResolver.registerProviderInternal(
            TestFileProvider.AUTHORITY, provider
        )
        peer = FakePeer()
        peer.start()
        session = SessionManager(context)
    }

    @After
    fun tearDownLoopback() {
        // Guarded: when setUp fails partway, the lateinits may not
        // exist yet, and an exception here would mask the real failure.
        if (::session.isInitialized) {
            try { session.disconnect() } catch (_: Exception) {}
        }
        if (::peer.isInitialized) peer.stop()
    }

    /** Connect to the fake peer, approving the NEW fingerprint on the first connect. */
    protected fun connectAndApprove(timeoutMs: Long = 15_000): Boolean {
        session.connect("127.0.0.1")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (session.approval.value != null) {
                session.approveConnection(true)
            }
            if (session.connection.value == ConnectionState.CONNECTED) return true
            Thread.sleep(50)
        }
        // Diagnostic, printed on failure: the error tells whether the
        // connect failed offline, in the dial, or in the approval.
        println("connectAndApprove failed: connection=${session.connection.value} error=${session.error.value} approval=${session.approval.value}")
        return session.connection.value == ConnectionState.CONNECTED
    }

    protected fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        val ok = condition()
        if (!ok && ::session.isInitialized) {
            // A timed-out wait is a test failure: print the session
            // state and the diagnostics log so the cause (send
            // failed, verifyAlive failed, session died) is visible
            // instead of just "expected true".
            println("waitFor timed out after ${timeoutMs}ms: connection=${session.connection.value} error=${session.error.value} transferError=${session.transferError.value} busyPhase=${session.busyPhase.value} active=${session.active.value}")
            println("DiagLog:\n" + DiagLog.snapshot().takeLast(20).joinToString("\n"))
        }
        return ok
    }
}

package com.riptide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Fix 2 regression: after a deliberate disconnect, Home must not
 * show "Couldn't reach this PC / Socket closed".
 *
 * The suspected race: disconnect() cancels the reader job (which
 * does not interrupt its blocking socket read) and closes the
 * socket, so the read throws SocketException("Socket closed"). That
 * is not a CancellationException, so the reader's catch calls
 * failSession. The disconnect cleanup clears _error and then sets
 * IDLE, but failSession can land in between — after _error was
 * cleared and before IDLE is set — leaving the stale error on Home
 * with the connection IDLE.
 *
 * The test drives a real loopback session, disconnects, and asserts
 * the error stays null and the connection ends IDLE, repeated over
 * ten connect/disconnect cycles to catch the race. Run against the
 * unfixed code it should fail; with the generation guard it passes.
 */
@RunWith(RobolectricTestRunner::class)
class SessionDisconnectTest : LoopbackTestBase() {

    @Test
    fun testDeliberateDisconnectLeavesNoError() {
        assertEquals(true, connectAndApprove())
        assertEquals(ConnectionState.CONNECTED, session.connection.value)
        session.disconnect()
        assertEquals(true, waitFor(5_000) { session.connection.value == ConnectionState.IDLE })
        // Let any late reader failure land: the race puts the error
        // in after the cleanup, so a settled read is the assertion.
        Thread.sleep(500)
        assertNull("stale error after deliberate disconnect: ${session.error.value}", session.error.value)
        assertEquals(ConnectionState.IDLE, session.connection.value)
    }

    @Test
    fun testRepeatedDisconnectsNeverLeaveAnError() {
        repeat(10) { cycle ->
            assertEquals("cycle $cycle: connect failed", true, connectAndApprove())
            assertEquals(ConnectionState.CONNECTED, session.connection.value)
            session.disconnect()
            val reachedIdle = waitFor(5_000) { session.connection.value == ConnectionState.IDLE }
            if (!reachedIdle) {
                println("cycle $cycle: did not reach IDLE; connection=${session.connection.value} error=${session.error.value}")
                println("DiagLog:\n" + DiagLog.snapshot().takeLast(30).joinToString("\n"))
            }
            assertEquals(
                "cycle $cycle: did not reach IDLE",
                true,
                reachedIdle
            )
            Thread.sleep(200)
            assertNull(
                "cycle $cycle: stale error after disconnect: ${session.error.value}",
                session.error.value
            )
            assertEquals(ConnectionState.IDLE, session.connection.value)
        }
    }
}

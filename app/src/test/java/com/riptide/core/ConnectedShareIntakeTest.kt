package com.riptide.core

import android.content.Intent
import com.riptide.ui.model.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ConnectedShareIntakeTest : LoopbackTestBase() {
    private fun share(name: String): Intent {
        val file = File(context.cacheDir, name).apply { writeText("Android share: $name") }
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, TestFileProvider.uriFor(file))
        }
    }

    @Test
    fun connectedShareSendsOnceAcrossRepeatedActivityIntentReplays() {
        assertTrue(connectAndApprove())
        val intent = share("share-connected.txt")
        assertEquals(1, session.receiveShareIntent(intent)?.uris?.size)
        repeat(10) {
            assertNull(session.receiveShareIntent(Intent(intent)))
        }
        assertTrue(waitFor(15_000) {
            session.history.value.any { it.filename == "share-connected.txt" && it.status == TransferStatus.SUCCESS }
        })
        assertTrue(waitFor { session.batch.value == null })
        assertEquals(1, session.history.value.count { it.filename == "share-connected.txt" })
        assertEquals(ConnectionState.CONNECTED, session.connection.value)
        assertEquals(true, peer.receivedFiles["share-connected.txt"]?.shaOk)

        // A genuinely new share of the same URI must still be accepted.
        val nextShare = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, extractSharedFiles(intent).uris.single())
        }
        assertEquals(1, session.receiveShareIntent(nextShare)?.uris?.size)
        assertTrue(waitFor(15_000) {
            session.history.value.count { it.filename == "share-connected.txt" && it.status == TransferStatus.SUCCESS } == 2
        })
    }

    @Test
    fun disconnectedShareStagesOnceAndSendsAfterConnection() {
        val intent = share("share-staged.txt")
        assertEquals(1, session.receiveShareIntent(intent)?.uris?.size)
        repeat(10) { assertNull(session.receiveShareIntent(Intent(intent))) }
        assertTrue(waitFor { session.batch.value?.includedCount == 1 })
        assertEquals(0, session.history.value.size)
        assertTrue(connectAndApprove())
        assertTrue(waitFor(15_000) {
            session.history.value.any { it.filename == "share-staged.txt" && it.status == TransferStatus.SUCCESS }
        })
        assertEquals(1, session.history.value.count { it.filename == "share-staged.txt" })
    }
}

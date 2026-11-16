package com.riptide.core

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android Share intake parser: single SEND, SEND_MULTIPLE in order,
 * text-only, missing stream, and file:// passthrough. Pure apart from
 * reading the Intent's extras.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareIntentsTest {

    private fun uri(path: String): Uri = Uri.parse(path)

    @Test
    fun testSingleSendReturnsOneUri() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri("content://com.example.provider/photo.jpg"))
        }
        val shared = extractSharedFiles(intent)
        assertEquals(listOf(uri("content://com.example.provider/photo.jpg")), shared.uris)
        assertFalse(shared.textOnly)
    }

    @Test
    fun testSendMultipleKeepsOrder() {
        val first = uri("content://com.example.provider/a.pdf")
        val second = uri("content://com.example.provider/b.pdf")
        val third = uri("content://com.example.provider/c.pdf")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(first, second, third)
            )
        }
        val shared = extractSharedFiles(intent)
        assertEquals(listOf(first, second, third), shared.uris)
        assertFalse(shared.textOnly)
    }

    @Test
    fun testTextOnlyShareHasNoFiles() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "https://example.com/a-note")
        }
        val shared = extractSharedFiles(intent)
        assertTrue(shared.uris.isEmpty())
        assertTrue(shared.textOnly)
    }

    @Test
    fun testMissingStreamIsNotTextOnly() {
        val intent = Intent(Intent.ACTION_SEND)
        val shared = extractSharedFiles(intent)
        assertTrue(shared.uris.isEmpty())
        assertFalse(shared.textOnly)
    }

    @Test
    fun testSendMultipleWithoutStreamIsEmpty() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        val shared = extractSharedFiles(intent)
        assertTrue(shared.uris.isEmpty())
        assertFalse(shared.textOnly)
    }

    @Test
    fun testFileUriPassesThrough() {
        val fileUri = uri("file:///sdcard/Download/report.pdf")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, fileUri)
        }
        val shared = extractSharedFiles(intent)
        assertEquals(listOf(fileUri), shared.uris)
        assertFalse(shared.textOnly)
    }

    @Test
    fun testNonShareActionIsEmpty() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri("riptide://join?host=203.0.113.10")
        }
        val shared = extractSharedFiles(intent)
        assertTrue(shared.uris.isEmpty())
        assertFalse(shared.textOnly)
    }
}

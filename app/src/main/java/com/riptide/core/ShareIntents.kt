package com.riptide.core

import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * What Android Share delivered to Riptide. [uris] is the shared files
 * in the order the sending app gave them; empty when the share carried
 * no stream (plain text, a link, a note). [textOnly] is true when the
 * share carried text but no files, so the UI can explain that Riptide
 * sends files instead of failing silently.
 *
 * Pure apart from reading the Intent's extras: no context, no I/O,
 * unit tested without a device.
 */
data class SharedFiles(val uris: List<Uri>, val textOnly: Boolean)

/** One shared file, spooled to cacheDir at share time. [uri] is the
 * original share URI (kept for the queue item); [spool] is the local
 * copy that is actually sent, and [size] is its exact byte count. */
data class SharedFile(val uri: Uri, val name: String, val size: Long, val spool: File)

@Suppress("DEPRECATION")
fun extractSharedFiles(intent: Intent): SharedFiles {
    val uris: List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> {
            val single = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (single != null) listOf(single) else emptyList()
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList() ?: emptyList()
        }
        else -> emptyList()
    }
    // getParcelableExtra(String) is deprecated on API 33+ in favour of
    // the typed overload, but the typed one needs API 33 and the app's
    // min SDK is 29; the old call works on every API level.
    val textOnly = uris.isEmpty() && intent.getStringExtra(Intent.EXTRA_TEXT) != null
    return SharedFiles(uris, textOnly)
}

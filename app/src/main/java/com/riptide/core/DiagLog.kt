package com.riptide.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic log for the background-disconnect investigation (v0.3.4 test
 * build only). Every line goes to logcat and to an internal file, and is
 * mirrored into Downloads/Riptide/riptide-diagnostics.log (best effort) so
 * the user can open and share it without adb. Settings has a Share button
 * that copies the full internal file to Downloads. Every call is guarded:
 * logging can never crash the app, break a transfer, or fail a session.
 * Lines are wall-clock stamped so a fail can be placed against a minimise
 * or a reopen.
 */
object DiagLog {
    private const val TAG = "RiptideDiag"
    private const val FILE_NAME = "riptide-diagnostics.log"
    private const val RELATIVE_PATH = "Download/Riptide/"
    private var context: Context? = null
    private var downloadsUri: Uri? = null
    private val lock = Any()

    fun init(ctx: Context) {
        if (context == null) context = ctx.applicationContext
    }

    fun log(tag: String, message: String) {
        val line = "${timestamp()} [$tag] $message"
        try {
            Log.d(TAG, line)
        } catch (_: Exception) {
        }
        val ctx = context ?: return
        synchronized(lock) {
            try {
                File(ctx.filesDir, FILE_NAME).appendText(line + "\n")
            } catch (_: Exception) {
            }
            try {
                appendToDownloads(ctx, line)
            } catch (_: Exception) {
                // Mirror failed (stale URI, Downloads unavailable). The
                // internal file still has the line; the Settings export
                // copies it out. Reset so the next line re-resolves.
                downloadsUri = null
            }
        }
    }

    /** Copy the whole internal log to Downloads/Riptide, replacing any
     * older copy. Returns true when the file is there to share. */
    fun exportToDownloads(ctx: Context): Boolean = exportUri(ctx) != null

    /** Copy the whole internal log to Downloads/Riptide and return the
     * content URI of the exported file, or null when it could not be
     * written. The URI is what a share sheet sends. */
    fun exportUri(ctx: Context): Uri? {
        return try {
            val internal = File(ctx.filesDir, FILE_NAME)
            if (!internal.exists()) return null
            val bytes = internal.readBytes()
            val resolver = ctx.contentResolver
            var uri = resolveDownloadsUri(ctx)
            if (uri == null) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
                }
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                downloadsUri = uri
            }
            val target = uri ?: return null
            resolver.openOutputStream(target, "w")?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return null
            target
        } catch (_: Exception) {
            null
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun appendToDownloads(ctx: Context, line: String) {
        var uri = downloadsUri
        if (uri == null) {
            uri = resolveDownloadsUri(ctx)
            if (uri == null) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
                }
                uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }
            downloadsUri = uri
        }
        val target = uri ?: return
        ctx.contentResolver.openOutputStream(target, "wa")?.use { out ->
            out.write((line + "\n").toByteArray())
            out.flush()
        }
    }

    /** Existing Downloads/Riptide log file, if one is already there. */
    private fun resolveDownloadsUri(ctx: Context): Uri? {
        return try {
            ctx.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(FILE_NAME, RELATIVE_PATH),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        c.getLong(0).toString()
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

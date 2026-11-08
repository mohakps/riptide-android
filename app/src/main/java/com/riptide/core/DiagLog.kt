package com.riptide.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Diagnostic log. Lines go to logcat and are kept in memory only:
 * nothing is written to a file on the phone automatically. The log is
 * saved (to Downloads/Riptide, for the share sheet) only when the user
 * taps Share diagnostics log in Settings, which calls exportUri().
 * Every call is guarded: logging can never crash the app, break a
 * transfer, or fail a session. Lines are wall-clock stamped so a fail
 * can be placed against a minimise or a reopen. The in-memory buffer
 * holds the most recent lines and is lost when the process ends, so a
 * log can only be shared from a running session.
 */
object DiagLog {
    private const val TAG = "RiptideDiag"
    private const val FILE_NAME = "riptide-diagnostics.log"
    private const val RELATIVE_PATH = "Download/Riptide/"
    private const val MAX_LINES = 2000
    private val lock = Any()
    private val lines = ArrayDeque<String>()

    fun log(tag: String, message: String) {
        val line = "${timestamp()} [$tag] $message"
        try {
            Log.d(TAG, line)
        } catch (_: Exception) {
        }
        synchronized(lock) {
            if (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(line)
        }
    }

    /** Save the buffered lines to Downloads/Riptide and return the
     * content URI of the saved file, or null when there is nothing to
     * share. This is the only place a log file is written. */
    fun exportUri(ctx: Context): Uri? {
        val snapshot: List<String>
        synchronized(lock) {
            if (lines.isEmpty()) return null
            snapshot = lines.toList()
        }
        return try {
            val resolver = ctx.contentResolver
            var uri = resolveDownloadsUri(ctx)
            if (uri == null) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
                }
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }
            val target = uri ?: return null
            resolver.openOutputStream(target, "w")?.use { out ->
                for (line in snapshot) {
                    out.write((line + "\n").toByteArray())
                }
                out.flush()
            } ?: return null
            target
        } catch (_: Exception) {
            null
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

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

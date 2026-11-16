package com.riptide.core

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * A test ContentProvider that serves files from the test's cache
 * dir under content:// URIs, the way the system file picker serves
 * them to the real app: query() reports DISPLAY_NAME and SIZE, and
 * openFile() opens the file for reading.
 *
 * This exists because SessionManager resolves a send's display name
 * through the ContentResolver (OpenableColumns.DISPLAY_NAME). A
 * file:// URI has no provider behind it, so under Robolectric the
 * query returns nothing and every send is named "file": the cancel
 * engine itself worked, but the tests waited for history entries
 * named after the real file and timed out. Serving the files through
 * this provider exercises the real name path instead.
 *
 * Registered by [LoopbackTestBase] via
 * ShadowContentResolver.registerProviderInternal.
 */
class TestFileProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.riptide.testprovider"

        /** Directory the provider serves files from (the test's cache dir). */
        var filesDir: File? = null

        /** The content:// URI for a file in [filesDir]. */
        fun uriFor(file: File): Uri =
            Uri.parse("content://$AUTHORITY/${file.name}")
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val name = uri.lastPathSegment ?: return null
        val file = File(filesDir, name)
        if (!file.exists()) return null
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf(name, file.length()))
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val name = uri.lastPathSegment ?: return null
        val file = File(filesDir, name)
        if (!file.exists()) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = "application/octet-stream"

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
}

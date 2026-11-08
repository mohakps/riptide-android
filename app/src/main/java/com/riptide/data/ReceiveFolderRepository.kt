package com.riptide.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Where received files are saved. A null tree URI means the default,
 * Downloads/Riptide. A custom folder is a Storage Access Framework tree
 * URI whose access is retained across restarts. */
data class ReceiveFolder(val treeUri: String?, val displayName: String) {
    val isDefault: Boolean get() = treeUri == null
}

class ReceiveFolderRepository(private val context: Context) {
    private val uriKey = stringPreferencesKey("receive_folder_uri")
    private val nameKey = stringPreferencesKey("receive_folder_name")

    companion object {
        const val DEFAULT_DISPLAY_NAME = "Downloads/Riptide"
    }

    fun load(): ReceiveFolder {
        return runBlocking {
            val prefs = context.riptideDataStore.data.first()
            val uri = prefs[uriKey]
            val name = prefs[nameKey]
            if (uri.isNullOrBlank()) {
                ReceiveFolder(null, DEFAULT_DISPLAY_NAME)
            } else {
                ReceiveFolder(uri, name?.ifBlank { DEFAULT_DISPLAY_NAME } ?: DEFAULT_DISPLAY_NAME)
            }
        }
    }

    fun save(treeUri: String, displayName: String) {
        runBlocking {
            context.riptideDataStore.edit {
                it[uriKey] = treeUri
                it[nameKey] = displayName.ifBlank { DEFAULT_DISPLAY_NAME }
            }
        }
    }

    fun clear() {
        runBlocking {
            context.riptideDataStore.edit {
                it.remove(uriKey)
                it.remove(nameKey)
            }
        }
    }

    /** True when the app still holds persisted access to this tree URI. */
    fun hasPersistedAccess(treeUri: String): Boolean {
        return try {
            val uri = Uri.parse(treeUri)
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Take and persist read/write access to a tree URI the user picked.
     * Returns false when the permission could not be retained. */
    fun takePersistableAccess(treeUri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /** A readable name for a tree URI, for the Settings row. Falls back
     * to the last path segment. */
    fun displayNameFor(treeUri: Uri): String {
        return try {
            val fromDoc = DocumentFile.fromTreeUri(context, treeUri)?.name
                ?.trim()?.takeIf { it.isNotEmpty() }
            if (fromDoc != null) return fromDoc
            val segment = treeUri.lastPathSegment
                ?.substringAfterLast(':')?.substringAfterLast('/')
                ?.trim()?.takeIf { it.isNotEmpty() }
            segment ?: DEFAULT_DISPLAY_NAME
        } catch (_: Exception) {
            DEFAULT_DISPLAY_NAME
        }
    }

    /** True when the folder can actually be written: access is held and
     * a probe file can be created and removed. A folder that only
     * looks accessible is not enough; the save must not fail later. */
    fun isWritable(treeUri: String): Boolean {
        if (!hasPersistedAccess(treeUri)) return false
        return try {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return false
            if (!dir.exists() || !dir.canWrite()) return false
            val probe = dir.createFile("application/octet-stream", ".riptide-write-test")
                ?: return false
            probe.delete()
            true
        } catch (_: Exception) {
            false
        }
    }
}

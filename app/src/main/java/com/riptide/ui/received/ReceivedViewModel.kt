package com.riptide.ui.received

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riptide.core.SessionManager
import com.riptide.ui.model.ReceivedFileRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class OpenResult {
    object Opened : OpenResult()
    object Missing : OpenResult()
    object NoHandler : OpenResult()
}

/**
 * Received tab: durable history of successful received files, across
 * sessions and restarts. Opening uses the stored content URI with a read
 * grant; a missing file is reported and its stale record removed.
 * Removing a record never deletes the file.
 */
class ReceivedViewModel(private val session: SessionManager? = null) : ViewModel() {
    private val _records = MutableStateFlow<List<ReceivedFileRecord>>(emptyList())
    val records: StateFlow<List<ReceivedFileRecord>> = _records.asStateFlow()

    init {
        session?.let { s ->
            viewModelScope.launch {
                s.receivedHistory.collect { list -> _records.update { list } }
            }
        }
    }

    fun setRecords(records: List<ReceivedFileRecord>) {
        _records.value = records
    }

    fun removeRecord(record: ReceivedFileRecord) {
        session?.removeReceivedRecord(record.id)
        _records.update { list -> list.filter { it.id != record.id } }
    }

    fun openRecord(record: ReceivedFileRecord, context: Context, onResult: (OpenResult) -> Unit) {
        viewModelScope.launch {
            val uri = Uri.parse(record.uri)
            val exists = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.close()
                    true
                } catch (_: Exception) {
                    false
                }
            }
            if (!exists) {
                removeRecord(record)
                onResult(OpenResult.Missing)
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeFor(record.savedName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
                onResult(OpenResult.Opened)
            } catch (_: Exception) {
                onResult(OpenResult.NoHandler)
            }
        }
    }

    private fun mimeFor(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "txt", "md" -> "text/plain"
            "zip" -> "application/zip"
            else -> "*/*"
        }
    }

    class Factory(private val session: SessionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReceivedViewModel(session) as T
    }
}

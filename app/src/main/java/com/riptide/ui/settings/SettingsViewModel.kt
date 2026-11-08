package com.riptide.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riptide.core.SessionManager
import com.riptide.data.ReceiveFolder
import com.riptide.data.ReceiveFolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings. Device name and receive folder are saved to the same
 * preferences the rest of the app reads, so a rename shows on the
 * Transfer screen and to the PC, and a new folder applies to receives
 * that start after the change. Identity reset is only offered when no
 * session, handshake or transfer is active.
 */
class SettingsViewModel(private val session: SessionManager? = null) : ViewModel() {
    private val _deviceName = MutableStateFlow(session?.deviceName ?: "")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()
    private val _myCode = MutableStateFlow(session?.myCode ?: "------")
    val myCode: StateFlow<String> = _myCode.asStateFlow()
    private val _fullFingerprint = MutableStateFlow(session?.myFingerprint ?: "")
    val fullFingerprint: StateFlow<String> = _fullFingerprint.asStateFlow()
    private val _receiveFolder = MutableStateFlow(
        session?.receiveFolder?.value ?: ReceiveFolder(null, ReceiveFolderRepository.DEFAULT_DISPLAY_NAME)
    )
    val receiveFolder: StateFlow<ReceiveFolder> = _receiveFolder.asStateFlow()
    private val _canResetIdentity = MutableStateFlow(session?.canResetIdentity() ?: false)
    val canResetIdentity: StateFlow<Boolean> = _canResetIdentity.asStateFlow()
    /** Error from the last identity reset, shown once. */
    private val _resetError = MutableStateFlow<String?>(null)
    val resetError: StateFlow<String?> = _resetError.asStateFlow()

    init {
        session?.let { s ->
            viewModelScope.launch { s.receiveFolder.collect { _receiveFolder.value = it } }
            viewModelScope.launch {
                s.connection.collect {
                    _canResetIdentity.value = s.canResetIdentity()
                    refreshIdentity()
                }
            }
            viewModelScope.launch { s.active.collect { _canResetIdentity.value = s.canResetIdentity() } }
            viewModelScope.launch { s.busyPhase.collect { _canResetIdentity.value = s.canResetIdentity() } }
            viewModelScope.launch { s.approval.collect { _canResetIdentity.value = s.canResetIdentity() } }
            viewModelScope.launch { s.approvalPhase.collect { _canResetIdentity.value = s.canResetIdentity() } }
        }
    }

    /** Save a device name. Blank after trimming is rejected. The
     * fingerprint is untouched by a rename. */
    fun saveDeviceName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        _deviceName.value = trimmed
        session?.setDeviceName(trimmed)
        return true
    }

    fun setDeviceName(name: String) {
        _deviceName.value = name.trim()
        session?.setDeviceName(name.trim())
    }

    /** Save a folder the user picked. Returns true only when the
     * folder is retained and writable. */
    fun setReceiveFolder(treeUri: Uri): Boolean {
        val ok = session?.setReceiveFolder(treeUri) ?: false
        if (ok) _receiveFolder.value = session?.receiveFolder?.value
            ?: ReceiveFolder(null, ReceiveFolderRepository.DEFAULT_DISPLAY_NAME)
        return ok
    }

    fun clearReceiveFolder() {
        session?.clearReceiveFolder()
        _receiveFolder.value = ReceiveFolder(null, ReceiveFolderRepository.DEFAULT_DISPLAY_NAME)
    }

    fun setIdentity(code: String, fingerprint: String) {
        _myCode.value = code
        _fullFingerprint.value = fingerprint
    }

    /** Refresh code and fingerprint from the active identity. */
    fun refreshIdentity() {
        session?.let {
            _myCode.value = it.myCode
            _fullFingerprint.value = it.myFingerprint
        }
    }

    /** Regenerate the identity. Returns true on success. On failure
     * the prior identity is kept and resetError is set. */
    fun resetIdentity(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = session?.resetIdentity() ?: false
            if (ok) {
                refreshIdentity()
                _resetError.value = null
            } else {
                _resetError.value = "Could not reset the identity. Your fingerprint is unchanged."
            }
            onDone(ok)
        }
    }

    fun clearResetError() {
        _resetError.value = null
    }

    class Factory(private val session: SessionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(session) as T
    }
}

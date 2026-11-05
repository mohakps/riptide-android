package com.riptide.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.riptide.core.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val session: SessionManager? = null) : ViewModel() {
    private val _deviceName = MutableStateFlow(session?.deviceName ?: "")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()
    private val _myCode = MutableStateFlow(session?.myCode ?: "------")
    val myCode: StateFlow<String> = _myCode.asStateFlow()
    private val _fullFingerprint = MutableStateFlow(session?.myFingerprint ?: "")
    val fullFingerprint: StateFlow<String> = _fullFingerprint.asStateFlow()

    fun setDeviceName(name: String) {
        _deviceName.value = name.trim()
        session?.setDeviceName(name.trim())
    }

    fun setIdentity(code: String, fingerprint: String) {
        _myCode.value = code
        _fullFingerprint.value = fingerprint
    }

    class Factory(private val session: SessionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(session) as T
    }
}

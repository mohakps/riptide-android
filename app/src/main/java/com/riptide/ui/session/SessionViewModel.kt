package com.riptide.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riptide.core.ConnectionState
import com.riptide.core.SessionManager
import com.riptide.ui.model.ActiveTransfer
import com.riptide.ui.model.SessionUiState
import com.riptide.ui.model.TransferRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionViewModel(private val session: SessionManager? = null) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        session?.let { s ->
            viewModelScope.launch { s.active.collect { a -> _uiState.update { it.copy(activeTransfer = a, canStartTransfer = a == null) } } }
            viewModelScope.launch { s.history.collect { h -> _uiState.update { it.copy(transferHistory = h) } } }
            viewModelScope.launch { s.peerName.collect { n -> _uiState.update { it.copy(peerName = n) } } }
            viewModelScope.launch { s.peerHost.collect { h -> _uiState.update { it.copy(peerHost = h) } } }
            viewModelScope.launch { s.pingMs.collect { p -> _uiState.update { it.copy(pingMs = p) } } }
            viewModelScope.launch { s.connection.collect { c -> _uiState.update { it.copy(isConnected = c == ConnectionState.CONNECTED) } } }
        }
    }

    fun setPeerDetails(name: String, host: String) {
        _uiState.update { it.copy(peerName = name, peerHost = host) }
    }

    fun updatePing(ping: Long) {
        _uiState.update { it.copy(pingMs = ping) }
    }

    fun setActiveTransfer(transfer: ActiveTransfer?) {
        _uiState.update { it.copy(activeTransfer = transfer, canStartTransfer = transfer == null) }
    }

    fun addTransferRecord(record: TransferRecord) {
        _uiState.update { state -> state.copy(activeTransfer = null, canStartTransfer = true, transferHistory = listOf(record) + state.transferHistory) }
    }

    fun onCancelTransfer() {
        session?.disconnect()
    }

    fun requestDownload(name: String) {
        session?.requestDownload(name)
    }

    fun onDisconnect(onNavBack: () -> Unit) {
        session?.disconnect()
        _uiState.update { it.copy(isConnected = false) }
        onNavBack()
    }

    class Factory(private val session: SessionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(session) as T
    }
}

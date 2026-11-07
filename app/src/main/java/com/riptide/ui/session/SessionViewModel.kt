package com.riptide.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riptide.core.ConnectionState
import com.riptide.core.SessionManager
import com.riptide.ui.model.ActiveTransfer
import com.riptide.ui.model.BusyPhase
import com.riptide.ui.model.DownloadState
import com.riptide.ui.model.SessionUiState
import com.riptide.ui.model.TransferRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Session (Transfer tab while connected). A second transfer is blocked
 * while CONNECTED is false, a transfer is active, or any busy phase
 * (preparing / verifying / waiting for ack) or a pending download request
 * is in flight — active == null alone is not enough.
 */
class SessionViewModel(private val session: SessionManager? = null) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    private var wasConnected = false

    init {
        session?.let { s ->
            viewModelScope.launch { s.active.collect { a -> _uiState.update { recompute(it.copy(activeTransfer = a)) } } }
            viewModelScope.launch { s.busyPhase.collect { b -> _uiState.update { recompute(it.copy(busyPhase = b)) } } }
            viewModelScope.launch { s.downloadState.collect { d -> _uiState.update { recompute(it.copy(downloadState = d)) } } }
            viewModelScope.launch { s.history.collect { h -> _uiState.update { it.copy(transferHistory = h) } } }
            viewModelScope.launch { s.peerName.collect { n -> _uiState.update { it.copy(peerName = n) } } }
            viewModelScope.launch { s.peerHost.collect { h -> _uiState.update { it.copy(peerHost = h) } } }
            viewModelScope.launch { s.pingMs.collect { p -> _uiState.update { it.copy(pingMs = p) } } }
            viewModelScope.launch { s.error.collect { e -> _uiState.update { it.copy(sessionError = e) } } }
            viewModelScope.launch {
                s.connection.collect { c ->
                    val connected = c == ConnectionState.CONNECTED
                    val interrupted = !connected && wasConnected && c == ConnectionState.FAILED
                    if (connected) wasConnected = true
                    _uiState.update {
                        recompute(it.copy(isConnected = connected, showInterrupted = it.showInterrupted || interrupted))
                    }
                }
            }
        }
    }

    private fun recompute(state: SessionUiState): SessionUiState {
        val canStart = state.isConnected &&
            state.activeTransfer == null &&
            state.busyPhase == null &&
            state.downloadState !is DownloadState.Waiting
        return state.copy(canStartTransfer = canStart)
    }

    fun setPeerDetails(name: String, host: String) {
        _uiState.update { it.copy(peerName = name, peerHost = host) }
    }

    fun updatePing(ping: Long) {
        _uiState.update { it.copy(pingMs = ping) }
    }

    fun setActiveTransfer(transfer: ActiveTransfer?) {
        _uiState.update { recompute(it.copy(activeTransfer = transfer)) }
    }

    fun setBusyPhase(phase: BusyPhase?) {
        _uiState.update { recompute(it.copy(busyPhase = phase)) }
    }

    fun setConnected(connected: Boolean) {
        if (connected) wasConnected = true
        _uiState.update { recompute(it.copy(isConnected = connected)) }
    }

    fun setDownloadState(state: DownloadState) {
        _uiState.update { recompute(it.copy(downloadState = state)) }
    }

    fun addTransferRecord(record: TransferRecord) {
        _uiState.update { state ->
            recompute(state.copy(activeTransfer = null, busyPhase = null, transferHistory = listOf(record) + state.transferHistory))
        }
    }

    fun dismissInterrupted() {
        _uiState.update { it.copy(showInterrupted = false) }
    }

    fun clearDownloadDenied() {
        session?.clearDownloadDenied()
    }

    fun onCancelTransfer() {
        session?.disconnect()
    }

    fun requestDownload(name: String) {
        session?.requestDownload(name)
    }

    fun onDisconnect(onNavBack: () -> Unit) {
        session?.disconnect()
        wasConnected = false
        _uiState.update { it.copy(isConnected = false, showInterrupted = false) }
        onNavBack()
    }

    class Factory(private val session: SessionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(session) as T
    }
}

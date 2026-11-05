package com.riptide.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riptide.core.ConnectionState
import com.riptide.core.SessionManager
import com.riptide.ui.model.ApprovalRequest
import com.riptide.ui.model.HomeUiState
import com.riptide.ui.model.SavedPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val session: SessionManager? = null) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        session?.let { s ->
            _uiState.update { it.copy(myDeviceName = s.deviceName, myCode = s.myCode, myFullFingerprint = s.myFingerprint) }
            viewModelScope.launch {
                s.savedPeers.collect { peers ->
                    _uiState.update { st -> st.copy(savedPeers = peers.map { SavedPeer(name = it.name.ifBlank { it.host }, host = it.host) }) }
                }
            }
            viewModelScope.launch {
                s.connection.collect { c ->
                    _uiState.update { st -> st.copy(isConnecting = c == ConnectionState.CONNECTING) }
                }
            }
            viewModelScope.launch {
                s.error.collect { e -> _uiState.update { st -> st.copy(connectionError = e) } }
            }
        }
    }

    fun setDeviceIdentity(name: String, code: String, fullFingerprint: String) {
        _uiState.update { it.copy(myDeviceName = name, myCode = code, myFullFingerprint = fullFingerprint) }
    }

    fun onHostInputChanged(input: String) {
        _uiState.update { it.copy(hostInput = input.trim()) }
    }

    fun setSavedPeers(peers: List<SavedPeer>) {
        _uiState.update { it.copy(savedPeers = peers) }
    }

    fun onJoinClicked(onNavigateToSession: () -> Unit) {
        val host = _uiState.value.hostInput
        if (host.isBlank()) return
        _uiState.update { it.copy(isConnecting = true, connectionError = null) }
        session?.connect(host)
        onNavigateToSession()
    }

    fun onApprovalReceived(request: ApprovalRequest) {
        _uiState.update { it.copy(approvalRequest = request) }
    }

    fun onApprovalHandled() {
        _uiState.update { it.copy(approvalRequest = null) }
    }

    fun onForgetPeer(peer: SavedPeer) {
        session?.forgetPeer(peer.host)
        _uiState.update { state -> state.copy(savedPeers = state.savedPeers.filter { it.host != peer.host }) }
    }

    class Factory(private val session: SessionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(session) as T
    }
}

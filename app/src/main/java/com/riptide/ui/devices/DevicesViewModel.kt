package com.riptide.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riptide.core.SessionManager
import com.riptide.ui.model.SavedPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Devices tab: the full saved list, most recent successful connection first. */
class DevicesViewModel(private val session: SessionManager? = null) : ViewModel() {
    private val _savedPeers = MutableStateFlow<List<SavedPeer>>(emptyList())
    val savedPeers: StateFlow<List<SavedPeer>> = _savedPeers.asStateFlow()

    init {
        session?.let { s ->
            viewModelScope.launch {
                s.savedPeers.collect { peers ->
                    _savedPeers.update {
                        peers.map {
                            SavedPeer(
                                name = it.name.ifBlank { it.host }, host = it.host,
                                lastSuccessfulConnection = it.lastSuccessfulConnection
                            )
                        }
                    }
                }
            }
        }
    }

    fun setSavedPeers(peers: List<SavedPeer>) {
        _savedPeers.value = peers
    }

    fun onForgetPeer(peer: SavedPeer) {
        session?.forgetPeer(peer.host)
        _savedPeers.update { list -> list.filter { it.host != peer.host } }
    }

    class Factory(private val session: SessionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DevicesViewModel(session) as T
    }
}

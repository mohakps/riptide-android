package com.riptide.ui.home

import com.riptide.ui.model.SavedPeer
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun testUpdateHostInputTrimsWhitespace() {
        val vm = HomeViewModel()
        vm.onHostInputChanged("  metalvoid.ddns.net  ")
        assertEquals("metalvoid.ddns.net", vm.uiState.value.hostInput)
    }

    @Test
    fun testSavedPeersListPopulates() {
        val vm = HomeViewModel()
        val peers = listOf(SavedPeer(name = "Workstation", host = "192.168.1.50"))
        vm.setSavedPeers(peers)
        assertEquals(1, vm.uiState.value.savedPeers.size)
        assertEquals("Workstation", vm.uiState.value.savedPeers[0].name)
    }
}

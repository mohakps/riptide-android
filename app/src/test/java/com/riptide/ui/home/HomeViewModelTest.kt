package com.riptide.ui.home

import com.riptide.ui.model.SavedPeer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun testUpdateHostInputTrimsWhitespace() {
        val vm = HomeViewModel()
        vm.onHostInputChanged("  metalvoid.ddns.net  ")
        assertEquals("metalvoid.ddns.net", vm.uiState.value.hostInput)
    }

    @Test
    fun testBlankJoinShowsErrorAndTypingClearsIt() {
        val vm = HomeViewModel()
        // No session is attached in the test, so a blank Join only
        // ever shows the warning and never starts a connection.
        vm.onJoinClicked()
        assertTrue(vm.uiState.value.showBlankAddressError)
        assertFalse(vm.uiState.value.isConnecting)
        // Whitespace-only is blank too.
        vm.onHostInputChanged("   ")
        vm.onJoinClicked()
        assertTrue(vm.uiState.value.showBlankAddressError)
        // Typing a real address clears the warning.
        vm.onHostInputChanged("192.168.1.6")
        assertFalse(vm.uiState.value.showBlankAddressError)
    }

    @Test
    fun testSavedPeersListPopulates() {
        val vm = HomeViewModel()
        val peers = listOf(SavedPeer(name = "Workstation", host = "192.168.1.50"))
        vm.setSavedPeers(peers)
        assertEquals(1, vm.uiState.value.savedPeers.size)
        assertEquals("Workstation", vm.uiState.value.savedPeers[0].name)
    }

    @Test
    fun testMalformedInputDisablesJoin() {
        val vm = HomeViewModel()
        vm.onHostInputChanged("999.1.1.1")
        assertEquals("That doesn’t look like an IP address or hostname.", vm.uiState.value.validationError)
        assertFalse(vm.uiState.value.joinEnabled)
        // Tapping Join on a validation failure never starts a connection.
        vm.onJoinClicked()
        assertFalse(vm.uiState.value.isConnecting)
        // A usable address re-enables Join.
        vm.onHostInputChanged("192.168.1.6")
        assertNull(vm.uiState.value.validationError)
        assertTrue(vm.uiState.value.joinEnabled)
    }

    @Test
    fun testDeepLinkPrefillValidatesImmediately() {
        val vm = HomeViewModel()
        // J6: prefill + immediate validation, no auto-connect.
        vm.prefillFromLink("2001:db8::1")
        assertEquals("2001:db8::1", vm.uiState.value.hostInput)
        assertNull(vm.uiState.value.validationError)
        assertTrue(vm.uiState.value.joinEnabled)
        assertFalse(vm.uiState.value.isConnecting)
        vm.prefillFromLink("not an address!!")
        assertEquals("That doesn’t look like an IP address or hostname.", vm.uiState.value.validationError)
        assertFalse(vm.uiState.value.joinEnabled)
    }
}

package com.riptide.ui.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.riptide.core.AddressFamily
import com.riptide.core.HostManager
import com.riptide.core.buildJoinLink
import com.riptide.core.copyTextFor
import com.riptide.ui.model.ApprovalRequest
import com.riptide.ui.model.HostUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Create Server screen. The screen itself owns no network state: the
 * HostManager detects addresses, runs the listener, and verifies the
 * incoming client. Copy and Share bind to the current addresses
 * through the pure link helpers, so a network change can never leave
 * a stale address on the clipboard or in a link.
 */
class HostViewModel(private val hostManager: HostManager) : ViewModel() {
    val uiState: StateFlow<HostUiState> = hostManager.uiState
    val approval: StateFlow<ApprovalRequest?> = hostManager.approval

    val deviceName: String get() = hostManager.deviceName

    fun setAdvancedOpen(open: Boolean) = hostManager.setAdvancedOpen(open)

    fun backToAddresses() = hostManager.backToAddresses()

    fun stop() = hostManager.stopHosting()

    fun approveConnection(allow: Boolean) = hostManager.approveConnection(allow)

    /** Bare address for Copy (C4): brackets stripped, port omitted. */
    fun copyTextForAddress(address: String): String = copyTextFor(address)

    /** Clipboard text for the My code tile: the displayed code,
     * verbatim, hyphen included (e.g. "244-910"). Nothing is
     * stripped or reformatted. */
    fun copyTextForCode(code: String): String = copyTextForCodeValue(code)

    /** Share link for one address row. */
    fun shareLinkFor(address: String, family: AddressFamily): String =
        buildJoinLink(address, family, deviceName)

    class Factory(private val hostManager: HostManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HostViewModel(hostManager) as T
    }
}

/** Clipboard text for the My code tile: the displayed code,
 * verbatim, hyphen included (e.g. "244-910"). Pure so the
 * copy contract is unit-testable without a ViewModel. */
internal fun copyTextForCodeValue(code: String): String = code

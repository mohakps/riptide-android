package com.riptide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.riptide.core.ConnectionState
import com.riptide.core.SessionManager
import com.riptide.service.TransferService
import com.riptide.ui.approval.PeerApprovalSheet
import com.riptide.ui.components.PhoneFingerprintSheet
import com.riptide.ui.devices.DevicesScreen
import com.riptide.ui.devices.DevicesViewModel
import com.riptide.ui.home.HomeScreen
import com.riptide.ui.home.HomeViewModel
import com.riptide.ui.model.ApprovalPhase
import com.riptide.ui.navigation.RiptideTab
import com.riptide.ui.received.ReceivedScreen
import com.riptide.ui.received.ReceivedViewModel
import com.riptide.ui.session.SessionScreen
import com.riptide.ui.session.SessionViewModel
import com.riptide.ui.settings.SettingsScreen
import com.riptide.ui.settings.SettingsViewModel
import com.riptide.ui.theme.RiptideColors
import com.riptide.ui.theme.RiptideTheme
import kotlinx.coroutines.launch

/**
 * One shared three-tab scaffold; the single SessionManager is owned here
 * and above every tab, so switching tabs never cancels a transfer,
 * disconnects, or regenerates identity. Transfer shows Home while idle
 * and the live Session once the host acknowledges. Settings is secondary
 * and remembers its originating tab.
 */
class MainActivity : ComponentActivity() {
    // Single-document picker (ACTION_OPEN_DOCUMENT) with ContentResolver.
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) (application as RiptideApp).session.sendFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RiptideApp
        val session = app.session
        startService(Intent(this, TransferService::class.java))
        setContent {
            RiptideTheme {
                var currentTab by remember { mutableStateOf(RiptideTab.Transfer) }
                var settingsOpen by remember { mutableStateOf(false) }
                var fingerprintOpen by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(session))
                val sessionVm: SessionViewModel = viewModel(factory = SessionViewModel.Factory(session))
                val devicesVm: DevicesViewModel = viewModel(factory = DevicesViewModel.Factory(session))
                val receivedVm: ReceivedViewModel = viewModel(factory = ReceivedViewModel.Factory(session))
                val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(session))

                val connection by session.connection.collectAsState()
                val approval by session.approval.collectAsState()
                val approvalPhase by session.approvalPhase.collectAsState()
                // The interrupted card (state 13) lives in the Session
                // screen: keep showing it after a drop, instead of
                // flipping straight to Home and losing it.
                val sessionUiState by sessionVm.uiState.collectAsState()

                fun openSettings() {
                    settingsOpen = true
                }
                fun closeSettings() {
                    settingsOpen = false
                    homeVm.refreshIdentity()
                }

                BackHandler {
                    when {
                        fingerprintOpen -> fingerprintOpen = false
                        settingsOpen -> closeSettings()
                        currentTab != RiptideTab.Transfer -> currentTab = RiptideTab.Transfer
                        else -> finish()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    // No system insets here: each screen's own top bar
                    // consumes the status bar, and the NavigationBar
                    // consumes the gesture inset. This padding only
                    // reserves the navigation bar's height.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        NavigationBar(containerColor = RiptideColors.Surface) {
                            RiptideTab.values().forEach { tab ->
                                val selected = currentTab == tab && !settingsOpen
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        settingsOpen = false
                                        currentTab = tab
                                    },
                                    icon = {
                                        Icon(
                                            painter = painterResource(tab.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = RiptideColors.Primary,
                                        selectedTextColor = RiptideColors.OnSurface,
                                        unselectedIconColor = RiptideColors.OnSurfaceVariant,
                                        unselectedTextColor = RiptideColors.OnSurfaceVariant,
                                        indicatorColor = RiptideColors.SurfaceRaised
                                    )
                                )
                            }
                        }
                    },
                    containerColor = RiptideColors.Background
                ) { padding ->
                    // The outer Scaffold's padding reserves the bottom
                    // navigation bar; each screen's own Scaffold handles
                    // its top bar and scrolling inside this area.
                    val contentModifier = Modifier.padding(padding)
                    when {
                        settingsOpen -> SettingsScreen(
                            viewModel = settingsVm,
                            onBack = ::closeSettings,
                            onShowFingerprint = { fingerprintOpen = true },
                            modifier = contentModifier
                        )
                        currentTab == RiptideTab.Devices -> DevicesScreen(
                            viewModel = devicesVm,
                            onNavigateToSettings = ::openSettings,
                            onJoinPeer = { peer ->
                                homeVm.joinPeer(peer)
                                currentTab = RiptideTab.Transfer
                            },
                            onGoToTransfer = { currentTab = RiptideTab.Transfer },
                            modifier = contentModifier
                        )
                        currentTab == RiptideTab.Received -> ReceivedScreen(
                            viewModel = receivedVm,
                            onNavigateToSettings = ::openSettings,
                            onGoToTransfer = { currentTab = RiptideTab.Transfer },
                            modifier = contentModifier
                        )
                        connection == ConnectionState.CONNECTED ||
                            (connection == ConnectionState.FAILED && sessionUiState.showInterrupted) -> SessionScreen(
                            viewModel = sessionVm,
                            onPickFileToSend = { pickFile.launch(arrayOf("*/*")) },
                            onDisconnect = { /* already on Transfer; IDLE shows Home */ },
                            onBackToJoin = { currentTab = RiptideTab.Transfer },
                            modifier = contentModifier
                        )
                        else -> HomeScreen(
                            viewModel = homeVm,
                            onNavigateToSettings = ::openSettings,
                            onShowFingerprint = { fingerprintOpen = true },
                            onShowAllDevices = { currentTab = RiptideTab.Devices },
                            modifier = contentModifier
                        )
                    }
                }

                approval?.let { req ->
                    if (approvalPhase != ApprovalPhase.NONE) {
                        PeerApprovalSheet(
                            request = req,
                            phase = approvalPhase,
                            myCode = session.myCode,
                            myDeviceName = session.deviceName,
                            myFingerprint = session.myFingerprint,
                            onAllow = { session.approveConnection(true) },
                            onDeny = { session.approveConnection(false) },
                            onCancelWaiting = { session.cancelConnect() }
                        )
                    }
                }
                if (fingerprintOpen) {
                    PhoneFingerprintSheet(
                        deviceName = session.deviceName,
                        code = session.myCode,
                        fingerprint = session.myFingerprint,
                        onCopyFingerprint = {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Riptide Fingerprint", session.myFingerprint))
                            scope.launch { snackbarHostState.showSnackbar("Fingerprint copied") }
                        },
                        onDone = { fingerprintOpen = false }
                    )
                }
            }
        }
    }
}

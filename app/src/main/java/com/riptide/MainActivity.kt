package com.riptide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
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
import com.riptide.core.DiagLog
import com.riptide.core.HostManager
import com.riptide.core.SessionManager
import com.riptide.core.parseJoinLink
import com.riptide.ui.approval.PeerApprovalSheet
import com.riptide.ui.components.PhoneFingerprintSheet
import com.riptide.ui.devices.DevicesScreen
import com.riptide.ui.devices.DevicesViewModel
import com.riptide.ui.home.HomeScreen
import com.riptide.ui.home.HomeViewModel
import com.riptide.ui.host.HostScreen
import com.riptide.ui.host.HostViewModel
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * One shared three-tab scaffold; the single SessionManager is owned here
 * and above every tab, so switching tabs never cancels a transfer,
 * disconnects, or regenerates identity. Transfer shows Home while idle
 * and the live Session once the host acknowledges. Settings is secondary
 * and remembers its originating tab.
 */
class MainActivity : ComponentActivity() {
    // Multiple-document picker (ACTION_OPEN_DOCUMENT, multiple
    // selection) with ContentResolver. One URI is a single send; two
    // or more are one outgoing batch, in picker result order.
    // Cancelling the picker leaves the Session as it was.
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) (application as RiptideApp).session.sendBatch(uris)
    }
    // Folder picker (ACTION_OPEN_DOCUMENT_TREE) for the receive folder.
    // One launcher serves both Settings and the Session recovery dialog;
    // the caller sets the result callback before launching.
    private var receiveFolderResult: ((Uri?) -> Unit)? = null
    private val pickReceiveFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        receiveFolderResult?.invoke(uri)
        receiveFolderResult = null
    }

    private fun launchReceiveFolderPicker(onResult: (Uri?) -> Unit) {
        receiveFolderResult = onResult
        pickReceiveFolder.launch(null)
    }

    /** Export the diagnostics log and open the system share sheet with
     * it. Only the user's tap opens the sheet. */
    private fun shareDiagnostics() {
        val uri = DiagLog.exportUri(this)
        if (uri == null) {
            Toast.makeText(this, "No diagnostics log yet", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share diagnostics log"))
    }
    // Own scope for activity-lifecycle work (the onResume liveness
    // check). No new dependency: lifecycleScope would need
    // lifecycle-runtime-ktx, which is not declared.
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Create Server host engine. One instance for the activity: the
     * listener survives tab switches, and a session it accepts runs
     * in the shared SessionManager. */
    private val hostManager by lazy {
        val app = application as RiptideApp
        HostManager(
            applicationContext,
            app.session.networkMonitor,
            app.session.connection
        ) { socket, peerDevice, peerFingerprint, peerHost ->
            app.session.attachHostSession(socket, peerDevice, peerFingerprint, peerHost)
        }
    }

    /** A deep link that arrived while the activity was already running. */
    private val deepLinkFlow = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { deepLinkFlow.value = it }
    }

    override fun onResume() {
        super.onResume()
        // v0.3.4 test build: log the return so the diagnostics log shows
        // exactly when the app came back, against any session failure.
        DiagLog.log("activity", "onResume")
        // The app was in the background: the session may have died
        // while it was away. Verify it is still alive, and rejoin the
        // last peer on its own when it is not. The system file picker
        // backgrounds the app the same way, so this also covers
        // returning from picking a file.
        activityScope.launch { (application as RiptideApp).session.onReturnedToForeground() }
    }

    override fun onPause() {
        DiagLog.log("activity", "onPause")
        super.onPause()
    }

    override fun onStop() {
        DiagLog.log("activity", "onStop")
        super.onStop()
    }

    override fun onDestroy() {
        DiagLog.log("activity", "onDestroy")
        activityScope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagLog.log("activity", "onCreate")
        val app = application as RiptideApp
        val session = app.session
        setContent {
            RiptideTheme {
                var currentTab by remember { mutableStateOf(RiptideTab.Transfer) }
                var settingsOpen by remember { mutableStateOf(false) }
                var fingerprintOpen by remember { mutableStateOf(false) }
                var createServerOpen by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(session))
                val hostVm: HostViewModel = viewModel(factory = HostViewModel.Factory(hostManager))
                val sessionVm: SessionViewModel = viewModel(factory = SessionViewModel.Factory(session))
                val devicesVm: DevicesViewModel = viewModel(factory = DevicesViewModel.Factory(session))
                val receivedVm: ReceivedViewModel = viewModel(factory = ReceivedViewModel.Factory(session))
                val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(session))

                val connection by session.connection.collectAsState()
                val approval by session.approval.collectAsState()
                val approvalPhase by session.approvalPhase.collectAsState()
                val hostApproval by hostManager.approval.collectAsState()
                val isHosting by hostManager.isHosting.collectAsState()
                // The interrupted card (state 13) lives in the Session
                // screen: keep showing it after a drop, instead of
                // flipping straight to Home and losing it.
                val sessionUiState by sessionVm.uiState.collectAsState()

                fun openCreateServer() {
                    // A dial in progress is cancelled: the phone is
                    // either hosting or joining, never both.
                    if (connection == ConnectionState.CONNECTING) session.cancelConnect()
                    settingsOpen = false
                    createServerOpen = true
                    hostManager.startHosting()
                }
                fun closeCreateServer() {
                    hostManager.stopHosting()
                    createServerOpen = false
                }

                // A shared link (J6) or a deep link received while
                // running: open Join with the address prefilled and
                // validated, and wait for the user to tap Join.
                // Never auto-connects, and never carries identity.
                val deepLink by deepLinkFlow.collectAsState()
                LaunchedEffect(deepLink) {
                    deepLink?.let { raw ->
                        parseJoinLink(raw)?.let { link ->
                            closeCreateServer()
                            settingsOpen = false
                            currentTab = RiptideTab.Transfer
                            homeVm.prefillFromLink(link.host)
                        }
                        deepLinkFlow.value = null
                    }
                }
                LaunchedEffect(Unit) {
                    intent?.data?.toString()?.let { deepLinkFlow.value = it }
                }
                // A host session that ends while still hosting returns
                // to the waiting screen, not to Home.
                var prevConnection by remember { mutableStateOf(connection) }
                LaunchedEffect(connection) {
                    if (prevConnection == ConnectionState.CONNECTED &&
                        connection != ConnectionState.CONNECTED && isHosting
                    ) {
                        createServerOpen = true
                    }
                    prevConnection = connection
                }

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
                        createServerOpen -> closeCreateServer()
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
                                        // Leaving Create Server for
                                        // another tab hides the screen;
                                        // hosting keeps running, and the
                                        // Home card brings it back.
                                        createServerOpen = false
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
                            onPickReceiveFolder = {
                                launchReceiveFolderPicker { uri ->
                                    if (uri != null) settingsVm.setReceiveFolder(uri)
                                }
                            },
                            onShareDiagnostics = ::shareDiagnostics,
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
                            onPickReceiveFolder = {
                                launchReceiveFolderPicker { uri ->
                                    if (uri != null) sessionVm.resolvePendingReceiveChooseFolder(uri)
                                }
                            },
                            onDisconnect = { /* already on Transfer; IDLE shows Home */ },
                            onBackToJoin = { currentTab = RiptideTab.Transfer },
                            onNavigateToSettings = ::openSettings,
                            modifier = contentModifier
                        )
                        createServerOpen -> HostScreen(
                            viewModel = hostVm,
                            onBack = ::closeCreateServer,
                            onRoleSwap = {
                                closeCreateServer()
                                currentTab = RiptideTab.Transfer
                            },
                            modifier = contentModifier
                        )
                        else -> HomeScreen(
                            viewModel = homeVm,
                            onNavigateToSettings = ::openSettings,
                            onShowFingerprint = { fingerprintOpen = true },
                            onShowAllDevices = { currentTab = RiptideTab.Devices },
                            onOpenCreateServer = ::openCreateServer,
                            modifier = contentModifier
                        )
                    }
                }

                // C7: a client verified by the host uses the same
                // sheet as a dialled host. Dismiss/Back is Decline;
                // the host returns to waiting.
                if (hostApproval != null) {
                    PeerApprovalSheet(
                        request = hostApproval!!,
                        phase = ApprovalPhase.LOCAL_TRUST,
                        myCode = session.myCode,
                        myDeviceName = session.deviceName,
                        myFingerprint = session.myFingerprint,
                        onAllow = { hostVm.approveConnection(true) },
                        onDeny = { hostVm.approveConnection(false) },
                        onCancelWaiting = { hostVm.approveConnection(false) }
                    )
                } else approval?.let { req ->
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

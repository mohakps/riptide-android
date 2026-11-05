package com.riptide.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onNavigateToSession: () -> Unit, onNavigateToSettings: () -> Unit, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row {
                    Text(text = "RIPTIDE", fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.titleLarge, color = RiptideColors.TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "v0.1 · JOIN", fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.labelLarge, color = RiptideColors.PrimaryCoral)
                }
            }, actions = {
                IconButton(onClick = onNavigateToSettings, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Open Settings", tint = RiptideColors.TextMuted)
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = RiptideColors.Background))
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = RiptideColors.Background,
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(12.dp))
            MyDeviceCard(deviceName = uiState.myDeviceName, code = uiState.myCode, fullFingerprint = uiState.myFullFingerprint, onCopyCode = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Riptide Code", uiState.myCode))
                scope.launch { snackbarHostState.showSnackbar("Verification code copied") }
            })
            Spacer(modifier = Modifier.height(16.dp))
            QuickJoinCard(hostInput = uiState.hostInput, isConnecting = uiState.isConnecting, onHostChanged = viewModel::onHostInputChanged, onJoinClicked = { viewModel.onJoinClicked(onNavigateToSession) })
            uiState.connectionError?.let { err ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = err, style = MaterialTheme.typography.bodyMedium, color = RiptideColors.ErrorRed)
            }
            Spacer(modifier = Modifier.height(20.dp))
            SavedPeersList(peers = uiState.savedPeers, onConnect = { peer -> viewModel.onHostInputChanged(peer.host); viewModel.onJoinClicked(onNavigateToSession) }, onForget = viewModel::onForgetPeer)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

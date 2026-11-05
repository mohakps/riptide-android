package com.riptide.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

@Composable
fun SessionScreen(viewModel: SessionViewModel, onPickFileToSend: () -> Unit, onDisconnect: () -> Unit, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    var showDownloadDialog by remember { mutableStateOf(false) }
    Scaffold(containerColor = RiptideColors.Background, bottomBar = {
        Surface(color = RiptideColors.Surface, border = BorderStroke(1.dp, RiptideColors.BorderOutline)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPickFileToSend, enabled = uiState.canStartTransfer, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.PrimaryCoral, contentColor = RiptideColors.OnPrimary), modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)) { Text("Send File") }
                OutlinedButton(onClick = { showDownloadDialog = true }, enabled = uiState.canStartTransfer, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, RiptideColors.BorderOutline), modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)) { Text("Download from PC", color = RiptideColors.TextPrimary) }
            }
        }
    }, modifier = modifier) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = uiState.peerName.ifBlank { "Connected PC" }, style = MaterialTheme.typography.titleMedium, color = RiptideColors.TextPrimary)
                    Text(text = "${uiState.peerHost} · ${uiState.pingMs}ms", fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.bodySmall, color = RiptideColors.SuccessMint)
                }
                OutlinedButton(onClick = { viewModel.onDisconnect(onDisconnect) }, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, RiptideColors.ErrorRed), modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Disconnect", color = RiptideColors.ErrorRed) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            ActiveTransferHero(activeTransfer = uiState.activeTransfer, onCancel = viewModel::onCancelTransfer)
            Spacer(modifier = Modifier.height(20.dp))
            SessionHistoryFeed(records = uiState.transferHistory)
            Spacer(modifier = Modifier.height(24.dp))
        }
        if (showDownloadDialog) {
            DownloadRequestDialog(onDismiss = { showDownloadDialog = false }, onRequestDownload = { filename -> viewModel.requestDownload(filename) })
        }
    }
}

package com.riptide.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.components.RiptideBadge
import com.riptide.ui.model.DownloadState
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

/**
 * States 08 (ready), 09 (transfer), 10 (verifying), 11 (download dialog),
 * 12 (download denied), 13 (interrupted). The Transfer tab shows this
 * screen while a session is live; it never resets to Home on return.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onPickFileToSend: () -> Unit,
    onDisconnect: () -> Unit,
    onBackToJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadPrefill by remember { mutableStateOf("") }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Session",
                        color = RiptideColors.OnSurface,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showDisconnectConfirm = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.riptide_back),
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp),
                            tint = RiptideColors.OnSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { overflowOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.riptide_more),
                            contentDescription = "More options",
                            modifier = Modifier.size(24.dp),
                            tint = RiptideColors.OnSurface
                        )
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Disconnect", color = RiptideColors.OnSurface) },
                            onClick = { overflowOpen = false; showDisconnectConfirm = true }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RiptideColors.Background)
            )
        },
        bottomBar = {
            Surface(color = RiptideColors.Background) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!uiState.canStartTransfer) {
                        Text(
                            text = "One transfer in progress",
                            color = RiptideColors.OnSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                    } else {
                        Button(
                            onClick = onPickFileToSend,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                        ) { Text("Send file", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                        Button(
                            onClick = { downloadPrefill = ""; showDownloadDialog = true },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
                        ) { Text("Download", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        },
        containerColor = RiptideColors.Background,
        modifier = modifier
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val gutter = if (maxWidth < 360.dp) 16.dp else 24.dp
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = gutter)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.showInterrupted) {
                    RiptideBadge(text = "DISCONNECTED", containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.Error)
                } else {
                    RiptideBadge(text = "CONNECTED", containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.Success)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = uiState.peerName.ifBlank { uiState.peerHost.ifBlank { "Connected PC" } },
                    color = RiptideColors.OnSurface,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = uiState.peerHost,
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showDisconnectConfirm = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Disconnect", color = RiptideColors.Error, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))

                if (uiState.showInterrupted) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = RiptideColors.Surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "TRANSFER INTERRUPTED",
                                color = RiptideColors.Error,
                                fontFamily = MonospaceFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 1.3.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Connection lost",
                                color = RiptideColors.OnSurface,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "The file was not completed. Reconnect and start the transfer again.",
                                color = RiptideColors.OnSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.dismissInterrupted(); onBackToJoin() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                            ) { Text("Back to Join", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                val denied = uiState.downloadState as? DownloadState.Denied
                if (denied != null) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = RiptideColors.Surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "DOWNLOAD DECLINED",
                                color = RiptideColors.Warning,
                                fontFamily = MonospaceFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 1.3.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Not approved on PC",
                                color = RiptideColors.OnSurface,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "The PC declined \u201c${denied.filename}\u201d. Ask the owner, then request it again.",
                                color = RiptideColors.OnSurfaceVariant,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    viewModel.clearDownloadDenied()
                                    downloadPrefill = denied.filename
                                    showDownloadDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                            ) { Text("Request again", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                val waiting = uiState.downloadState as? DownloadState.Waiting
                if (waiting != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = RiptideColors.Primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Waiting for the PC to approve \u201c${waiting.filename}\u201d…",
                            color = RiptideColors.OnSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                ActiveTransferHero(
                    activeTransfer = uiState.activeTransfer,
                    busyPhase = uiState.busyPhase,
                    onCancel = { showCancelConfirm = true }
                )
                uiState.sessionError?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Text(text = err, color = RiptideColors.Error, fontSize = 14.sp, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(20.dp))
                SessionHistoryFeed(records = uiState.transferHistory)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Interrupted transfers restart from the beginning.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showDownloadDialog) {
            DownloadRequestDialog(
                initialFilename = downloadPrefill,
                onDismiss = { showDownloadDialog = false },
                onRequestDownload = { filename -> viewModel.requestDownload(filename) }
            )
        }
        if (showCancelConfirm) {
            AlertDialog(
                onDismissRequest = { showCancelConfirm = false },
                title = { Text("Cancel this transfer?", color = RiptideColors.OnSurface) },
                text = {
                    Text(
                        "This will disconnect from the PC. The file must restart.",
                        color = RiptideColors.OnSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showCancelConfirm = false; viewModel.onCancelTransfer() },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                    ) { Text("Cancel and disconnect") }
                },
                dismissButton = {
                    Button(
                        onClick = { showCancelConfirm = false },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
                    ) { Text("Keep transferring") }
                },
                containerColor = RiptideColors.Surface,
                shape = RoundedCornerShape(28.dp)
            )
        }
        if (showDisconnectConfirm) {
            val hasActive = uiState.activeTransfer != null || uiState.busyPhase != null
            AlertDialog(
                onDismissRequest = { showDisconnectConfirm = false },
                title = { Text("Disconnect from ${uiState.peerName.ifBlank { "this PC" }}?", color = RiptideColors.OnSurface) },
                text = {
                    Text(
                        if (hasActive) "A transfer is in progress. Disconnecting ends it, and the file must restart."
                        else "You can reconnect from Transfer whenever you like.",
                        color = RiptideColors.OnSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showDisconnectConfirm = false; viewModel.onDisconnect(onDisconnect) },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                    ) { Text("Disconnect") }
                },
                dismissButton = {
                    Button(
                        onClick = { showDisconnectConfirm = false },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
                    ) { Text("Cancel") }
                },
                containerColor = RiptideColors.Surface,
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

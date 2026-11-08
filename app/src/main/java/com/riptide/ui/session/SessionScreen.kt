package com.riptide.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.model.DownloadState
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

/**
 * States 08 (ready), 09 (transfer), 10 (verifying), 11 (download dialog),
 * 12 (download denied), 13 (interrupted). The Transfer tab shows this
 * screen while a session is live; it never resets to Home on return.
 */
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onPickFileToSend: () -> Unit,
    onDisconnect: () -> Unit,
    onBackToJoin: () -> Unit,
    modifier: Modifier = Modifier,
    onPickReceiveFolder: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadPrefill by remember { mutableStateOf("") }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showCancelBatchConfirm by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    // The same live state drives the app-bar status, the header and
    // the body, so the status appears exactly once.
    val connected = !uiState.showInterrupted

    Scaffold(
        topBar = {
            // A custom bar, not TopAppBar: the two-line title (Session
            // plus the status dot and label) must let the bar grow at
            // large font sizes instead of clipping in a fixed 64 dp.
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showDisconnectConfirm = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.riptide_back),
                        contentDescription = "Back",
                        modifier = Modifier.size(24.dp),
                        tint = RiptideColors.OnSurface
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Session",
                        color = RiptideColors.OnSurface,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(6.dp)
                                .background(if (connected) RiptideColors.Success else RiptideColors.Error, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (connected) "Connected" else "Disconnected",
                            color = if (connected) RiptideColors.Success else RiptideColors.Error,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                Box {
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
                }
            }
        },
        bottomBar = {
            // The action strip lives here only while connected. After a
            // loss the disconnected panel is the only state, and the
            // send/download controls and busy text go away with it.
            if (!uiState.showInterrupted) {
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
            }
        },
        containerColor = RiptideColors.Background,
        modifier = modifier
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val gutter = if (maxWidth < 360.dp) 16.dp else 24.dp
            val narrow = maxWidth < 360.dp
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = gutter)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                // Compact peer header. The Disconnect capsule is
                // centered against the combined name/IP block. The
                // status lives in the app bar now, not below the
                // header. On narrow screens or large text the whole
                // button drops below the peer details, aligned end,
                // so peer data is never overlapped or cut. The button
                // only shows while connected.
                val peerName = uiState.peerName.ifBlank { uiState.peerHost.ifBlank { "Connected PC" } }
                val fontScale = LocalDensity.current.fontScale
                val stackHeader = narrow || fontScale >= 1.3f
                if (stackHeader) {
                    PeerNameIp(peerName = peerName, peerHost = uiState.peerHost)
                    if (connected) {
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            DisconnectButton(peerName = peerName, onClick = { showDisconnectConfirm = true })
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PeerNameIp(
                            peerName = peerName,
                            peerHost = uiState.peerHost,
                            modifier = Modifier.weight(1f)
                        )
                        if (connected) {
                            Spacer(Modifier.width(16.dp))
                            DisconnectButton(peerName = peerName, onClick = { showDisconnectConfirm = true })
                        }
                    }
                }
                // The ready and lost panels sit 32 dp below the peer
                // row (16 dp of extra space); the active-transfer card
                // keeps the normal 16 dp gap.
                val topGap = when {
                    uiState.showInterrupted -> 32.dp
                    uiState.activeTransfer != null || uiState.busyPhase != null -> 16.dp
                    uiState.batchState != null -> 16.dp
                    uiState.downloadState is DownloadState.Denied -> 16.dp
                    uiState.downloadState is DownloadState.Waiting -> 16.dp
                    else -> 32.dp
                }
                Spacer(Modifier.height(topGap))

                // One session state at a time, from the same state as
                // the header. Disconnection takes precedence over any
                // cached transfer data.
                if (uiState.showInterrupted) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = RiptideColors.Surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            if (uiState.interruptedTransfer) {
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
                            }
                            Text(
                                text = "Connection lost",
                                color = RiptideColors.OnSurface,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (uiState.interruptedTransfer) {
                                    "The file was not completed. Reconnect and start the transfer again."
                                } else {
                                    "Reconnect to send or receive files."
                                },
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
                } else {
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

                    val batch = uiState.batchState
                    if (batch != null) {
                        BatchTransferPanel(
                            batch = batch,
                            onCancelBatch = { showCancelBatchConfirm = true }
                        )
                    } else {
                        ActiveTransferHero(
                            activeTransfer = uiState.activeTransfer,
                            busyPhase = uiState.busyPhase,
                            receiveFolderName = uiState.receiveFolderName,
                            onCancel = { showCancelConfirm = true }
                        )
                    }
                    uiState.sessionError?.let { err ->
                        Spacer(Modifier.height(12.dp))
                        Text(text = err, color = RiptideColors.Error, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    uiState.batchMessage?.let { msg ->
                        Spacer(Modifier.height(12.dp))
                        Text(text = msg, color = RiptideColors.OnSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                }
                // Session history (or its empty state) is the last body
                // item now; the interrupted-transfer tip is gone.
                SessionHistoryFeed(records = uiState.transferHistory, disconnected = uiState.showInterrupted)
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
        if (showCancelBatchConfirm) {
            val batch = uiState.batchState
            val remaining = batch?.let { it.items.size - it.sentCount } ?: 0
            AlertDialog(
                onDismissRequest = { showCancelBatchConfirm = false },
                title = { Text("Cancel this batch?", color = RiptideColors.OnSurface) },
                text = {
                    Text(
                        "This stops the current file and the remaining $remaining file${if (remaining == 1) "" else "s"} in the queue, and disconnects from the PC. Files already sent stay in Session history.",
                        color = RiptideColors.OnSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showCancelBatchConfirm = false; viewModel.onCancelBatch() },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                    ) { Text("Cancel batch and disconnect") }
                },
                dismissButton = {
                    Button(
                        onClick = { showCancelBatchConfirm = false },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
                    ) { Text("Keep sending") }
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
        // The receive folder lost access after a file was verified. The
        // file waits until the user picks a folder or falls back to the
        // default; the ack and history wait for that choice.
        val pending = uiState.pendingReceive
        if (pending != null) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Receive folder unavailable", color = RiptideColors.OnSurface) },
                text = {
                    Text(
                        "“${pending.name}” was received, but the folder can no longer be opened. Choose a folder or use the default, and the file will be saved.",
                        color = RiptideColors.OnSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.resolvePendingReceiveUseDefault() },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                    ) { Text("Use default folder") }
                },
                dismissButton = {
                    Button(
                        onClick = { onPickReceiveFolder() },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface)
                    ) { Text("Choose folder") }
                },
                containerColor = RiptideColors.Surface,
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

/** Peer name and host/IP for the compact Session header. The status
 * line is a separate row below the header, outside the alignment. */
@Composable
private fun PeerNameIp(peerName: String, peerHost: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = peerName,
            color = RiptideColors.OnSurface,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = peerHost,
            color = RiptideColors.OnSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}

/** Contained tonal Disconnect button for the compact Session header.
 * Coral power glyph, off-white label. */
@Composable
private fun DisconnectButton(peerName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics {
            contentDescription = "Disconnect from $peerName"
        },
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.OnSurface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.riptide_power),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = RiptideColors.Error
        )
        Spacer(Modifier.width(8.dp))
        Text("Disconnect", fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

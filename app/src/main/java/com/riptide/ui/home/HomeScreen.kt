package com.riptide.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.ui.components.PeerRow
import com.riptide.ui.components.RiptideTopBar
import com.riptide.ui.components.StackedDeviceIdentity
import com.riptide.ui.theme.ApprovedRiptideTokens
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors
import kotlinx.coroutines.launch

/**
 * State 01 (idle), 02 (first launch), 03 (connecting), 04 (connection error).
 * Exact approved hierarchy: identity widgets, 32 dp blank gap, Join PC
 * heading, address form, Join button, recent devices. No divider, no
 * enclosing identity card, no Join panel.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit,
    onShowFingerprint: () -> Unit,
    onShowAllDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The hostname keyboard goes away on Join, before the approval
    // sheet shows; the sheet hides it again as a safety net.
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = { RiptideTopBar(onOpenSettings = onNavigateToSettings) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = RiptideColors.Background,
        modifier = modifier
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val gutter = if (maxWidth < 360.dp) 16.dp else 24.dp
            val contentModifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter)
            Column(modifier = contentModifier) {
                Spacer(modifier = Modifier.height(24.dp))
                StackedDeviceIdentity(
                    deviceName = uiState.myDeviceName.ifBlank { "This Phone" },
                    code = uiState.myCode,
                    onCopyCode = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Riptide Code", uiState.myCode.filter { it.isDigit() }))
                        scope.launch { snackbarHostState.showSnackbar("Verification code copied") }
                    },
                    onShowFingerprint = onShowFingerprint
                )
                // Final blank gap: 32 dp from the widgets' bottom to the top
                // of the Join heading's 40 dp line box. Do not add 16 more.
                Spacer(modifier = Modifier.height(ApprovedRiptideTokens.IdentityToJoinGap))
                Text(
                    text = "Join PC",
                    color = RiptideColors.OnSurface,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "PC ADDRESS",
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.hostInput,
                    onValueChange = viewModel::onHostInputChanged,
                    enabled = !uiState.isConnecting,
                    placeholder = { Text("203.0.113.10", color = RiptideColors.OnSurfaceVariant) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide(); viewModel.onJoinClicked() }),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = RiptideColors.SurfaceRaised,
                        unfocusedContainerColor = RiptideColors.SurfaceRaised,
                        disabledContainerColor = RiptideColors.SurfaceRaised,
                        focusedTextColor = RiptideColors.OnSurface,
                        unfocusedTextColor = RiptideColors.OnSurface,
                        disabledTextColor = RiptideColors.OnSurfaceVariant,
                        focusedBorderColor = RiptideColors.Outline,
                        unfocusedBorderColor = RiptideColors.Outline,
                        disabledBorderColor = RiptideColors.Outline,
                        cursorColor = RiptideColors.Primary
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .semantics {
                            if (uiState.showBlankAddressError) error("Enter a PC address.")
                        }
                )
                Spacer(modifier = Modifier.height(12.dp))
                // The helper and the blank-field warning share this
                // 14/20 supporting-text slot, so the layout stays
                // stable when the warning appears. The warning is
                // announced when it first appears.
                Text(
                    text = when {
                        uiState.showBlankAddressError -> "Enter a PC address."
                        uiState.isConnecting -> "Connecting… · Up to 30 seconds"
                        else -> "Hostname, IP or user@host"
                    },
                    color = if (uiState.showBlankAddressError) RiptideColors.Error else RiptideColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.semantics {
                        if (uiState.showBlankAddressError) liveRegion = LiveRegionMode.Assertive
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (uiState.isConnecting) {
                    Button(
                        onClick = viewModel::onCancelConnection,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = RiptideColors.OnPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Cancel connection", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    // Join PC stays coral and tappable with an empty
                    // field: tapping it runs validation, which shows
                    // the blank-field warning instead of connecting.
                    Button(
                        onClick = { keyboardController?.hide(); viewModel.onJoinClicked() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                    ) {
                        Text("Join PC", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
                uiState.connectionError?.let { err ->
                    // The heading must match the failure: a session the PC
                    // ended is not a PC that could not be reached, and a
                    // transfer error never reaches this screen at all now.
                    val sessionEnded = err == "The PC ended the session."
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (sessionEnded) "Session ended" else "Couldn't reach this PC",
                        color = RiptideColors.Error,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        // The design's recovery line, unless the cause already
                        // carries its own (e.g. the timeout hint).
                        text = if (sessionEnded) "$err Reconnect to start a new session."
                        else if ("Create Server" in err) err
                        else "$err Check the address and start Create Server on the PC. Then try Join PC again.",
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT DEVICES",
                        color = RiptideColors.OnSurfaceVariant,
                        fontFamily = MonospaceFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = onShowAllDevices, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Show all devices", color = RiptideColors.OnSurface, fontSize = 14.sp)
                    }
                }
                if (uiState.savedPeers.isEmpty()) {
                    Text(
                        text = "Your PCs will appear here.",
                        color = RiptideColors.OnSurface,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Join and approve a PC to save it.",
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.savedPeers.take(2).forEach { peer ->
                            PeerRow(peer = peer, onConnect = viewModel::joinPeer, onForget = viewModel::onForgetPeer)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

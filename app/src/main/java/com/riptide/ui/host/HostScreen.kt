package com.riptide.ui.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.core.AddressFamily
import com.riptide.core.HostPhase
import com.riptide.ui.model.HostUiState
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors
import kotlinx.coroutines.launch

/**
 * Create Server (C1-C8). Exact pack layout: 24 dp gutters, the 82 dp
 * header row (signal icon + "Waiting for client" beside the ivory
 * code tile), 24 dp above the first heading, 24 dp from a card to
 * the next heading, 8 dp from a heading's line box to its card,
 * #1C1C1C address cards with icon-only Copy and Share, and the
 * full-width coral Stop above the bottom navigation. The empty
 * states (mobile IPv4-only, offline) show no waiting status, code
 * tile, or Stop. "Listening on port 50000" is deliberately omitted.
 */
@Composable
fun HostScreen(
    viewModel: HostViewModel,
    onBack: () -> Unit,
    onRoleSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun copyAddress(address: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Riptide Address", viewModel.copyTextForAddress(address)))
        scope.launch { snackbarHostState.showSnackbar("$label copied") }
    }

    fun shareAddress(address: String, family: AddressFamily) {
        val link = viewModel.shareLinkFor(address, family)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
        }
        context.startActivity(Intent.createChooser(intent, "Share join link"))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = RiptideColors.Background,
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // App bar: existing back affordance + "Create Server".
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.riptide_back),
                        contentDescription = "Back",
                        modifier = Modifier.size(24.dp),
                        tint = RiptideColors.OnSurface
                    )
                }
                Text(
                    text = "Create Server",
                    color = RiptideColors.OnSurface,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            when (uiState.phase) {
                HostPhase.OFFLINE -> EmptyHostState(
                    message = "No network. Connect and retry.",
                    onRoleSwap = null,
                    modifier = Modifier.weight(1f)
                )
                HostPhase.MOBILE_V4_ONLY -> EmptyHostState(
                    message = "No IPv6 on this network, so there is nothing to share.",
                    onRoleSwap = onRoleSwap,
                    modifier = Modifier.weight(1f)
                )
                HostPhase.WAITING -> WaitingHostContent(
                    uiState = uiState,
                    onCopy = ::copyAddress,
                    onShare = ::shareAddress,
                    onToggleAdvanced = { viewModel.setAdvancedOpen(!uiState.advancedOpen) },
                    onBackToAddresses = viewModel::backToAddresses,
                    onRoleSwap = onRoleSwap,
                    onStop = { viewModel.stop(); onBack() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** C2/C6 empty states: the message, and the role-swap action where
 * the pack gives one. No waiting status, code tile, or Stop. */
@Composable
private fun EmptyHostState(
    message: String,
    onRoleSwap: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(72.dp))
        Text(
            text = message,
            color = RiptideColors.OnSurface,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Medium
        )
        if (onRoleSwap != null) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onRoleSwap, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    text = "Ask the other phone to Create instead.",
                    color = RiptideColors.Primary,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/** C1/C3/C4/C5: header row, address cards (or Advanced), Stop. */
@Composable
private fun WaitingHostContent(
    uiState: HostUiState,
    onCopy: (String, String) -> Unit,
    onShare: (String, AddressFamily) -> Unit,
    onToggleAdvanced: () -> Unit,
    onBackToAddresses: () -> Unit,
    onRoleSwap: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            // Header row, 82 dp: signal icon above "Waiting for
            // client" on the left, ivory code tile on the right.
            Row(
                modifier = Modifier.fillMaxWidth().height(82.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Icon(
                        painter = painterResource(R.drawable.riptide_signal),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = RiptideColors.Primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Waiting for client",
                        color = RiptideColors.OnSurface,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                // My code tile: ivory, 140x82 dp, 22 dp corners.
                Box(
                    modifier = Modifier.size(width = 140.dp, height = 82.dp)
                        .background(RiptideColors.CodeContainer, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = "MY CODE",
                            color = RiptideColors.OnCodeContainer,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.myCode,
                            color = RiptideColors.OnCodeContainer,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 23.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            // C8: briefly shown under the header.
            if (uiState.showNetworkChanged) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Network changed. Share the new address.",
                    color = RiptideColors.Primary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (uiState.advancedOpen) {
                // C5: Advanced replaces the regular cards in place.
                // Public IPv4 at the top, then the requirements.
                uiState.publicIPv4?.let { address ->
                    AddressHeading(text = "PUBLIC IPv4")
                    Spacer(modifier = Modifier.height(8.dp))
                    AddressCard(
                        value = address,
                        description = "For devices outside this Wi-Fi.",
                        copyLabel = "Copy public IPv4",
                        shareLabel = "Share public IPv4",
                        onCopy = { onCopy(address, "Public IPv4") },
                        onShare = { onShare(address, AddressFamily.IPV4) }
                    )
                } ?: run {
                    AddressHeading(text = "PUBLIC IPv4")
                    Spacer(modifier = Modifier.height(8.dp))
                    AddressCard(
                        value = if (uiState.publicIPv4Loading) "Looking up…" else "Unavailable",
                        description = "For devices outside this Wi-Fi.",
                        copyLabel = "Copy public IPv4",
                        shareLabel = "Share public IPv4",
                        onCopy = {},
                        onShare = {},
                        actionsEnabled = false
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                AddressHeading(text = "CONNECTION REQUIREMENTS")
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    RequirementRow(text = "Router must forward port 50000 to this phone.")
                    RequirementRow(text = "Network without CGNAT")
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onRoleSwap, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        text = "Can’t connect? Ask the other phone to Create.",
                        color = RiptideColors.Primary,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                // C1/C3/C4: the regular address cards.
                uiState.globalIPv6?.let { address ->
                    AddressHeading(text = "GLOBAL IPv6")
                    Spacer(modifier = Modifier.height(8.dp))
                    AddressCard(
                        value = address,
                        description = "For devices on the internet with IPv6.",
                        copyLabel = "Copy global IPv6",
                        shareLabel = "Share global IPv6",
                        onCopy = { onCopy(address, "Global IPv6") },
                        onShare = { onShare(address, AddressFamily.IPV6) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                uiState.lanIPv4?.let { address ->
                    AddressHeading(text = "LAN IPv4")
                    Spacer(modifier = Modifier.height(8.dp))
                    AddressCard(
                        value = address,
                        description = "For devices on the same Wi-Fi.",
                        copyLabel = "Copy LAN IPv4",
                        shareLabel = "Share LAN IPv4",
                        onCopy = { onCopy(address, "LAN IPv4") },
                        onShare = { onShare(address, AddressFamily.IPV4) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                // Advanced starts closed on Wi-Fi.
                TextButton(onClick = onToggleAdvanced, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Advanced", color = RiptideColors.OnSurface, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.riptide_chevron_down),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = RiptideColors.OnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        // Footer, pinned above the bottom navigation and reachable
        // while the content scrolls: Back to addresses (Advanced
        // only, 12 dp above Stop), then Stop.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            if (uiState.advancedOpen) {
                Button(
                    onClick = onBackToAddresses,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RiptideColors.SurfaceRaised,
                        contentColor = RiptideColors.OnSurface
                    )
                ) {
                    Text("Back to addresses", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RiptideColors.Primary,
                    contentColor = RiptideColors.OnPrimary
                )
            ) {
                Text("Stop", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AddressHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = RiptideColors.OnSurfaceVariant,
        fontFamily = MonospaceFontFamily,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

@Composable
private fun RequirementRow(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.riptide_check),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = RiptideColors.Success
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = RiptideColors.OnSurface,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

/** One address card: #1C1C1C, 20 dp corners, 82 dp reference height,
 * 17 sp monospace value, muted 13/18 sp description, icon-only Copy
 * (neutral outline) and Share (40 dp coral circle, dark glyph),
 * both with a minimum 48 dp touch target. Long addresses wrap. */
@Composable
private fun AddressCard(
    value: String,
    description: String,
    copyLabel: String,
    shareLabel: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    actionsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        color = RiptideColors.Surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    color = RiptideColors.OnSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onCopy,
                enabled = actionsEnabled,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.riptide_copy),
                    contentDescription = copyLabel,
                    modifier = Modifier.size(24.dp),
                    tint = RiptideColors.OnSurfaceVariant
                )
            }
            IconButton(
                onClick = onShare,
                enabled = actionsEnabled,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(RiptideColors.Primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.riptide_share),
                        contentDescription = shareLabel,
                        modifier = Modifier.size(20.dp),
                        tint = RiptideColors.OnPrimary
                    )
                }
            }
        }
    }
}

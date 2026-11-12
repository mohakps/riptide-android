package com.riptide.ui.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
 * Create Server (C1-C8). Final approved layout (v0.9.4,
 * waiting.png / advanced.png): 24 dp gutters; header row with the
 * coral radio glyph above "Waiting for client" (24 sp medium) on
 * the left and the ivory code tile (156x104 dp at 412 dp, 14 sp
 * label, 28 sp code) on the right; 50 dp from the header row's
 * bottom to the first address row; address rows of three separate
 * rounded rectangles (address cell #1C1C1C with 16 dp corners,
 * 78 dp action column, Copy #1C1C1C above Share #E04449, 14 dp
 * corners, 6 dp gaps), 162 dp reference height with both rows
 * matched to the taller; full-width Stop above the bottom
 * navigation. The whole code tile copies the code; the Advanced
 * footer buttons match heights. The empty states (mobile
 * IPv4-only, offline) show no waiting status, code tile, or Stop.
 * "Listening on port 50000" is deliberately omitted.
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

    /** The whole My code tile copies the displayed code, hyphen
     * included, using the same snackbar feedback as the address
     * Copy buttons. */
    fun copyCode() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Riptide Code", viewModel.copyTextForCode(uiState.myCode)))
        scope.launch { snackbarHostState.showSnackbar("My code copied") }
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
                    onCopyCode = ::copyCode,
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
    onCopyCode: () -> Unit,
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
            // Header row, 104 dp minimum (the code tile's height):
            // the coral radio glyph above "Waiting for client" on
            // the left, ivory code tile on the right. heightIn, not
            // a fixed height, so the row grows if the label wraps
            // at enlarged text, and the 50 dp gap below is measured
            // from the row's actual bottom. Icon and label share a
            // left edge; the icon is decorative (the label conveys
            // the status). The row is top-aligned so the icon's top
            // meets the tile's top, as in the reference.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Icon(
                        painter = painterResource(R.drawable.riptide_signal),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = RiptideColors.Primary
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Waiting for client",
                        color = RiptideColors.OnSurface,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                // My code tile: ivory, 156x104 dp at 412 dp, 22 dp
                // corners, allowed to grow at enlarged text. The
                // WHOLE tile is one tap target that copies the
                // displayed code, hyphen included; the single
                // accessibility action is "Copy verification code".
                Box(
                    modifier = Modifier.width(156.dp).heightIn(min = 104.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(RiptideColors.CodeContainer, RoundedCornerShape(22.dp))
                        .clickable(onClickLabel = "Copy verification code") { onCopyCode() }
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.align(Alignment.TopStart)) {
                        Text(
                            text = "MY CODE",
                            color = RiptideColors.OnCodeContainer,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.myCode,
                            color = RiptideColors.OnCodeContainer,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.riptide_copy),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                        tint = RiptideColors.OnCodeContainer
                    )
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
            // 50 dp from the header row's bottom to the first
            // address row, in both the standard and the Advanced
            // views (the spacer sits above the branch). The C8
            // notice above it is transient; the 50 dp minimum below
            // the notice is preserved.
            Spacer(modifier = Modifier.height(50.dp))
            if (uiState.advancedOpen) {
                // C5: Advanced replaces the regular rows in place.
                // Public IPv4 at the top, then the requirements.
                // The single row has no partner to match, so it
                // takes its natural height (162 dp minimum).
                uiState.publicIPv4?.let { address ->
                    AddressRow(
                        heading = "PUBLIC IPv4",
                        value = address,
                        description = "For devices outside this Wi-Fi.",
                        copyLabel = "Copy public IPv4",
                        shareLabel = "Share public IPv4",
                        onCopy = { onCopy(address, "Public IPv4") },
                        onShare = { onShare(address, AddressFamily.IPV4) }
                    )
                } ?: run {
                    AddressRow(
                        heading = "PUBLIC IPv4",
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
                Text(
                    text = "Connection requirements",
                    color = RiptideColors.OnSurface,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )
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
                // C1/C3/C4: the regular address rows. Both rows
                // share one height: each reports its natural height
                // and both are laid out at the taller of the two,
                // never below 162 dp, so enlarged text or a long
                // wrapped IPv6 grows both together. The first pass
                // measures unconstrained (heightIn min only); the
                // matched height applies once both have reported.
                val density = LocalDensity.current
                var ipv6RowHeight by remember { mutableStateOf(0.dp) }
                var lanRowHeight by remember { mutableStateOf(0.dp) }
                fun reportIpv6Height(size: IntSize) {
                    val h = with(density) { size.height.toDp() }
                    if (h > ipv6RowHeight) ipv6RowHeight = h
                }
                fun reportLanHeight(size: IntSize) {
                    val h = with(density) { size.height.toDp() }
                    if (h > lanRowHeight) lanRowHeight = h
                }
                // Only match when both rows are present; a lone row
                // takes its natural height (162 dp minimum).
                val bothRowsPresent = uiState.globalIPv6 != null && uiState.lanIPv4 != null
                val haveBothHeights = bothRowsPresent && ipv6RowHeight > 0.dp && lanRowHeight > 0.dp
                val matchedRow = if (haveBothHeights) matchedRowHeight(ipv6RowHeight, lanRowHeight) else null
                uiState.globalIPv6?.let { address ->
                    AddressRow(
                        heading = "GLOBAL IPv6",
                        value = address,
                        description = "For devices on the internet with IPv6.",
                        copyLabel = "Copy global IPv6",
                        shareLabel = "Share global IPv6",
                        onCopy = { onCopy(address, "Global IPv6") },
                        onShare = { onShare(address, AddressFamily.IPV6) },
                        rowHeight = matchedRow,
                        onSizeChanged = ::reportIpv6Height
                    )
                }
                if (bothRowsPresent) {
                    Spacer(modifier = Modifier.height(20.dp))
                }
                uiState.lanIPv4?.let { address ->
                    AddressRow(
                        heading = "LAN IPv4",
                        value = address,
                        description = "For devices on the same Wi-Fi.",
                        copyLabel = "Copy LAN IPv4",
                        shareLabel = "Share LAN IPv4",
                        onCopy = { onCopy(address, "LAN IPv4") },
                        onShare = { onShare(address, AddressFamily.IPV4) },
                        rowHeight = matchedRow,
                        onSizeChanged = ::reportLanHeight
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
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
        // only, 12 dp above Stop), then Stop. Both buttons share
        // one height: each reports its natural height and both are
        // laid out at the taller of the two, never below 56 dp,
        // so a wrapped label at enlarged text grows both together.
        // The first pass measures unconstrained (heightIn min
        // only); the matched height applies once both have reported.
        val density = LocalDensity.current
        var backButtonHeight by remember { mutableStateOf(0.dp) }
        var stopButtonHeight by remember { mutableStateOf(0.dp) }
        fun reportBackHeight(size: IntSize) {
            val h = with(density) { size.height.toDp() }
            if (h > backButtonHeight) backButtonHeight = h
        }
        fun reportStopHeight(size: IntSize) {
            val h = with(density) { size.height.toDp() }
            if (h > stopButtonHeight) stopButtonHeight = h
        }
        val matchedHeight = matchedFooterHeight(backButtonHeight, stopButtonHeight)
        val haveBothHeights = backButtonHeight > 0.dp && stopButtonHeight > 0.dp
        val footerButtonModifier: Modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (haveBothHeights) Modifier.height(matchedHeight) else Modifier)
        val footerButtonShape = RoundedCornerShape(50)
        val footerButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            if (uiState.advancedOpen) {
                Button(
                    onClick = onBackToAddresses,
                    modifier = footerButtonModifier.onSizeChanged(::reportBackHeight),
                    shape = footerButtonShape,
                    contentPadding = footerButtonPadding,
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
                modifier = footerButtonModifier.onSizeChanged(::reportStopHeight),
                shape = footerButtonShape,
                contentPadding = footerButtonPadding,
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

/** One address row: three separate rounded rectangles. The address
 * cell fills the available width (#1C1C1C, 16 dp corners, 18 dp
 * horizontal padding): 13 sp muted monospace heading, 18 sp
 * off-white monospace value, 13 sp muted description. At the
 * reference size the heading baseline sits 29 dp below the top of
 * the cell, the value baseline 68 dp below the top, and the
 * description baseline 25 dp above the bottom; the description is
 * pinned to the bottom so a wrapped value pushes the row taller
 * instead of crowding it. The value is shown with break
 * opportunities after IPv6 colons (displayAddressWithBreaks) and
 * wraps naturally; Copy and Share always act on the full
 * underlying [value], never on the wrapped display text. The right
 * column is 78 dp wide: Copy (#1C1C1C) above Share (#E04449), 14 dp
 * corners, 6 dp gaps everywhere; each action fills its entire cell.
 * [rowHeight], when given, fixes the row to the matched height
 * shared with its sibling row; otherwise the row is 162 dp minimum
 * and grows with its content. */
@Composable
private fun AddressRow(
    heading: String,
    value: String,
    description: String,
    copyLabel: String,
    shareLabel: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    actionsEnabled: Boolean = true,
    rowHeight: Dp? = null,
    onSizeChanged: (IntSize) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .then(if (rowHeight != null) Modifier.height(rowHeight) else Modifier.heightIn(min = 162.dp))
            .onSizeChanged(onSizeChanged)
    ) {
        // Address cell.
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(RiptideColors.Surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = heading,
                    color = RiptideColors.HeadingMuted,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.paddingFromBaseline(top = 29.dp)
                )
                Text(
                    text = displayAddressWithBreaks(value),
                    color = RiptideColors.OnSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.paddingFromBaseline(top = 33.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = description,
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.paddingFromBaseline(bottom = 25.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        // Action column: Copy above Share, 6 dp between them.
        Column(modifier = Modifier.width(78.dp).fillMaxHeight()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RiptideColors.Surface, RoundedCornerShape(14.dp))
                    .clickable(enabled = actionsEnabled, onClickLabel = copyLabel) { onCopy() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.riptide_copy),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = RiptideColors.OnSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RiptideColors.ShareRed, RoundedCornerShape(14.dp))
                    .clickable(enabled = actionsEnabled, onClickLabel = shareLabel) { onShare() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.riptide_share_nodes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        }
    }
}

/** Display form of an address: a zero-width space after each ':'
 * gives a long IPv6 value a break opportunity at every colon
 * boundary, so it wraps like the reference instead of overflowing
 * or clipping. Copy and Share never see this string — they use the
 * underlying full address. Pure so the wrapping rule is
 * unit-testable. */
internal fun displayAddressWithBreaks(address: String): String =
    address.replace(":", ":\u200B")

/** The shared height for the two regular address rows: the taller
 * of the two measured heights, never below 162 dp. Pure so the
 * matching rule is unit-testable. */
internal fun matchedRowHeight(first: Dp, second: Dp): Dp =
    maxOf(first, second).coerceAtLeast(162.dp)

/**
 * One connection requirement: a 6 dp hollow circle in muted gray,
 * aligned with the first line of the text; wrapped lines indent
 * to the text start (hanging indent). The bullet is a decorative
 * marker only — TalkBack reads the requirement text directly.
 */
@Composable
private fun RequirementRow(text: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val bulletOffset = with(density) {
        requirementBulletTopOffsetPx(20.sp.toPx(), 6.dp.toPx()).toDp()
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.padding(top = bulletOffset).size(6.dp)
                .border(1.dp, RiptideColors.OnSurfaceVariant, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = RiptideColors.OnSurface,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Top offset that centers a bullet of [bulletSizePx] on the first
 * line of text of [lineHeightPx]: half the line height minus half
 * the bullet. Pure so the alignment rule is unit-testable. */
internal fun requirementBulletTopOffsetPx(lineHeightPx: Float, bulletSizePx: Float): Float =
    (lineHeightPx / 2f - bulletSizePx / 2f).coerceAtLeast(0f)

/** The shared footer height for the Advanced buttons: the taller
 * of the two measured heights, never below 56 dp. Pure so the
 * matching rule is unit-testable. */
internal fun matchedFooterHeight(first: Dp, second: Dp): Dp =
    maxOf(first, second).coerceAtLeast(56.dp)

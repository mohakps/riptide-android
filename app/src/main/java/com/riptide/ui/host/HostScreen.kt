package com.riptide.ui.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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

/** The waiting status, always on two lines with an explicit break
 * after "Waiting". TalkBack exposes it as the single phrase
 * [WAITING_STATUS_TALKBACK], never as two separate lines. */
internal const val WAITING_STATUS_TEXT = "Waiting\nfor client"
internal const val WAITING_STATUS_TALKBACK = "Waiting for client"

/** Create Server layout sizes (v0.9.5, "Create Server alignment
 * and screen fit"). */
internal val CODE_TILE_WIDTH = 148.dp
internal val CODE_TILE_MIN_HEIGHT = 76.dp
internal val ACTION_COLUMN_WIDTH = 72.dp
internal val ROW_MIN_HEIGHT = 108.dp

/** Radio broadcast icon size (v0.9.7): 36 dp, centered vertically
 * against the two-line status, 10 dp gap. */
internal val RADIO_ICON_SIZE = 36.dp

/** Verification code value size (v0.9.7): 28 sp, monospace, one
 * line. The "MY CODE" label stays 11 sp and the Copy glyph 16 dp. */
internal val CODE_VALUE_FONT_SIZE = 28.sp
internal val CODE_VALUE_LINE_HEIGHT = 32.sp

/** Monospace advance: every glyph, the hyphen included, is 0.6em
 * wide, so the 7-character code ("123-456") is 7 x 0.6 x its
 * font size wide. Pure so the tile-fit rule is unit-testable. */
internal const val MONOSPACE_ADVANCE_RATIO = 0.6f
internal const val VERIFICATION_CODE_LENGTH = 7

/** Width in dp of the verification code at the given font size
 * (in sp) and font scale. */
internal fun verificationCodeWidthDp(codeFontSizeSp: Float, fontScale: Float): Float =
    VERIFICATION_CODE_LENGTH * MONOSPACE_ADVANCE_RATIO * codeFontSizeSp * fontScale

/** True when the code fits inside the tile at the given font size
 * and font scale: its width plus the tile's horizontal padding
 * (12 dp each side) must not exceed the tile width. At default
 * text 28 sp needs 117.6 + 24 = 141.6 dp of the 148 dp tile. */
internal fun verificationCodeFitsTile(
    codeFontSizeSp: Float,
    fontScale: Float,
    tileWidthDp: Float = 148f,
    tileHorizontalPaddingDp: Float = 24f
): Boolean =
    verificationCodeWidthDp(codeFontSizeSp, fontScale) + tileHorizontalPaddingDp <= tileWidthDp

/**
 * Create Server (C1-C8). Layout ownership (v0.9.6, "Fix Create
 * Server screen fit"): ONE screen layout with an app bar, a body,
 * and a footer. The body occupies the FULL measured space between
 * the app bar and the footer. The app bar lives in the Scaffold's
 * top slot and the action stack (network notice, then Back to
 * addresses above Stop) in the bottom slot; the Scaffold measures
 * both at their content heights before laying out the body, so
 * the body knows exactly the space that is left. The bottom
 * navigation is owned by the parent (MainActivity): its space is
 * reserved through the `modifier` passed in, and this Scaffold
 * applies no window insets of its own — the top bar consumes the
 * status bar once via statusBarsPadding. There is no weighted
 * spacer outside the body and no fixed or fractional body height:
 * the body is a single scrollable column that fills the content
 * area, its content placed at the top, and any unused height
 * follows its last item WITHIN the body. The header is ONE
 * horizontal row — the waiting group (coral 36 dp radio glyph
 * left of the two-line status, 10 dp gap) vertically centered
 * beside the ivory code tile (148x76 dp at the reference width,
 * code value 28 sp monospace on one line; the tile grows past
 * 148 dp wide only if enlarged text needs it) at the right edge; 24 dp from the header to the first address
 * row; address rows of three separate rounded rectangles
 * (address cell #1C1C1C with 16 dp corners and 16 dp horizontal
 * padding, 72 dp action column, Copy #1C1C1C above Share #FF6B6B,
 * 14 dp corners, 6 dp gaps), 108 dp reference height with both
 * regular rows matched to the taller; Advanced is a 48 dp target
 * 8 dp after the last row. The whole code tile copies the code;
 * the Advanced footer buttons match heights. The empty states
 * (mobile IPv4-only, offline) show no waiting status, code tile,
 * or Stop. "Listening on port 50000" is deliberately omitted.
 *
 * Why this shape: v0.9.5 put TWO weight(1f) siblings in one
 * Column — the body (fill = false) and a flexible Spacer outside
 * it — so the remaining space after the footer was split 50/50
 * between them. The body was capped at half the available height
 * (about 238 dp at 360x720, against 386 dp of content) and
 * became a short scroll region, while the other half sat as dead
 * empty space above the footer. The body is now the only child
 * that fills the space, and the spacer is gone.
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
    // The body's single scroll state, hoisted so it can be reset:
    // start at the top on entry and when switching between
    // standard and Advanced (or when the phase changes). The body
    // keeps one scroll state across those switches, so without
    // this reset the user could land mid-content.
    val scrollState = rememberScrollState()
    LaunchedEffect(uiState.phase, uiState.advancedOpen) {
        scrollState.scrollTo(0)
    }

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

    /** Share sends only the row's bare address as plain text —
     * the same string Copy uses (brackets stripped, no port, no
     * scheme, no link, no device name, no code). The 6-digit code
     * stays out-of-band. */
    fun shareAddress(address: String, family: AddressFamily) {
        val text = viewModel.shareTextForAddress(address)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share address"))
    }

    Scaffold(
        topBar = {
            // App bar, measured at its content height: the back
            // affordance + "Create Server", with the status bar
            // consumed once here (16 dp below it, as before).
            Column(modifier = Modifier.statusBarsPadding()) {
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
            }
        },
        bottomBar = {
            // Footer, measured at its content height before the
            // body: the network notice first, then Back to
            // addresses (Advanced only) above Stop. The bottom
            // navigation is NOT here — the parent owns it and its
            // space is already reserved by `modifier`.
            if (uiState.phase == HostPhase.WAITING) {
                HostFooter(
                    uiState = uiState,
                    onBackToAddresses = viewModel::backToAddresses,
                    onStop = { viewModel.stop(); onBack() }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // No window insets of our own: the parent already
        // reserves the bottom navigation's height, and the top
        // bar consumes the status bar once. The Scaffold's
        // default insets would reserve the navigation inset a
        // second time and shrink the body.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = RiptideColors.Background,
        modifier = modifier
    ) { padding ->
        // The Scaffold's content padding is exactly the app bar
        // height above and the footer height below, so this area
        // IS the measured space between them. The body fills it.
        when (uiState.phase) {
            HostPhase.OFFLINE -> EmptyHostState(
                message = "No network. Connect and retry.",
                onRoleSwap = null,
                modifier = Modifier.padding(padding).fillMaxSize()
            )
            HostPhase.MOBILE_V4_ONLY -> EmptyHostState(
                message = "No IPv6 on this network, so there is nothing to share.",
                onRoleSwap = onRoleSwap,
                modifier = Modifier.padding(padding).fillMaxSize()
            )
            HostPhase.WAITING -> WaitingHostBody(
                uiState = uiState,
                scrollState = scrollState,
                onCopy = ::copyAddress,
                onShare = ::shareAddress,
                onCopyCode = ::copyCode,
                onToggleAdvanced = { viewModel.setAdvancedOpen(!uiState.advancedOpen) },
                onRoleSwap = onRoleSwap,
                modifier = Modifier.padding(padding)
            )
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

/** C1/C3/C4/C5: header row and address rows (or Advanced).
 *
 * The body is the ENTIRE scroll region and the only child that
 * fills the Scaffold's content area: a single scrollable Column
 * with `fillMaxSize`, its content placed at the top. There is no
 * weight on the body (the Scaffold's content area already IS the
 * measured space between the app bar and the footer) and no
 * weighted spacer anywhere — any unused height simply follows
 * the last item inside this column, so the body's measured bottom
 * edge is always the footer's top edge and the scroll range is
 * zero whenever the content fits. The gaps compact first when
 * the measured content exceeds the available height (see
 * [chooseGaps]); the body scrolls on its own only if the compact
 * layout still overflows (shorter windows, enlarged text). */
@Composable
private fun WaitingHostBody(
    uiState: HostUiState,
    scrollState: ScrollState,
    onCopy: (String, String) -> Unit,
    onShare: (String, AddressFamily) -> Unit,
    onCopyCode: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onRoleSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rowCount = if (uiState.advancedOpen) 1
            else listOfNotNull(uiState.globalIPv6, uiState.lanIPv4).size
        // maxHeight here is the body's own measured height — the
        // space between the app bar and the footer — so the gap
        // decision is made against the space the body really has.
        val gaps = chooseGaps(maxHeight, rowCount, uiState.advancedOpen)
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
        ) {
                // Header: ONE horizontal row. The waiting group
                // (36 dp coral radio glyph, 10 dp gap, two-line
                // 20/24 sp medium status) is vertically centered
                // beside the ivory code tile at the right edge.
                // The row is 76 dp minimum (the tile's height) and
                // grows if the label wraps at enlarged text; the
                // 24 dp gap below is measured from the row's bottom.
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = CODE_TILE_MIN_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.riptide_signal),
                            contentDescription = null,
                            modifier = Modifier.size(RADIO_ICON_SIZE),
                            tint = RiptideColors.Primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = WAITING_STATUS_TEXT,
                            color = RiptideColors.OnSurface,
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = WAITING_STATUS_TALKBACK
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    // My code tile: ivory, 148x76 dp at the
                    // reference width, 22 dp corners, allowed to
                    // grow at enlarged text. The width is a
                    // minimum: at default text the 28 sp code
                    // (117.6 dp) fits inside it, and only enlarged
                    // text grows it wider instead of clipping the
                    // code. The WHOLE tile is one tap target that
                    // copies the displayed code, hyphen included;
                    // the single accessibility action is
                    // "Copy verification code".
                    Box(
                        modifier = Modifier.widthIn(min = CODE_TILE_WIDTH).heightIn(min = CODE_TILE_MIN_HEIGHT)
                            .clip(RoundedCornerShape(22.dp))
                            .background(RiptideColors.CodeContainer, RoundedCornerShape(22.dp))
                            .clickable(onClickLabel = "Copy verification code") { onCopyCode() }
                            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.align(Alignment.TopStart)) {
                            Text(
                                text = "MY CODE",
                                color = RiptideColors.OnCodeContainer,
                                fontFamily = MonospaceFontFamily,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.myCode,
                                color = RiptideColors.OnCodeContainer,
                                fontFamily = FontFamily.Monospace,
                                fontSize = CODE_VALUE_FONT_SIZE,
                                lineHeight = CODE_VALUE_LINE_HEIGHT,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.riptide_copy),
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                            tint = RiptideColors.OnCodeContainer
                        )
                    }
                }
                // 24 dp from the header row's bottom to the first
                // address row, in both the standard and the
                // Advanced views (the spacer sits above the branch).
                Spacer(modifier = Modifier.height(gaps.headerToRows))
                if (uiState.advancedOpen) {
                    // C5: Advanced replaces the regular rows in
                    // place. Public IPv4 at the top, then the
                    // requirements. The single row has no partner
                    // to match, so it takes its natural height
                    // (108 dp minimum).
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
                    Spacer(modifier = Modifier.height(gaps.requirementsGap))
                    Text(
                        text = "Connection requirements",
                        color = RiptideColors.OnSurface,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RequirementRow(text = "Router must forward port 50000 to this phone.")
                        RequirementRow(text = "Network without CGNAT")
                    }
                    Spacer(modifier = Modifier.height(gaps.listToHint))
                    TextButton(
                        onClick = onRoleSwap,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Can’t connect? Ask the other phone to Create.",
                            color = RiptideColors.Primary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    // C1/C3/C4: the regular address rows. Both rows
                    // share one height: each reports its natural
                    // height and both are laid out at the taller of
                    // the two, never below 108 dp, so enlarged text
                    // or a long wrapped IPv6 grows both together.
                    // A lone row takes its natural height (108 dp
                    // minimum) with the same action-column behaviour.
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
                        Spacer(modifier = Modifier.height(gaps.betweenRows))
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
                    // Advanced: a 48 dp target directly after the
                    // last regular address row, with an 8 dp gap;
                    // its text aligns to the page gutter. An
                    // IPv4-only device has one address row and the
                    // same 8 dp gap.
                    Spacer(modifier = Modifier.height(gaps.advancedGap))
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clickable(onClickLabel = "Advanced") { onToggleAdvanced() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Advanced", color = RiptideColors.OnSurface, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.riptide_chevron_down),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = RiptideColors.OnSurfaceVariant
                        )
                    }
                }
            }
        }
}

/** Footer for the Scaffold's bottom slot: the network notice
 * first, then Back to addresses (Advanced only) above Stop. The
 * Scaffold measures this at its content height before laying out
 * the body, so the body knows the space that is left; the footer
 * itself never scrolls and stays visible. Both buttons share one
 * height: each reports its natural height and both are laid out
 * at the taller of the two, never below 56 dp, so a wrapped label
 * at enlarged text grows both together. The notice slot collapses
 * to zero height when it clears. The button gap (8 dp) and the
 * 12 dp below Stop are fixed spec values, not part of the body's
 * gap choice: the footer is outside the body's fit budget, and
 * shrinking it would not save the body a scroll. */
@Composable
private fun HostFooter(
    uiState: HostUiState,
    onBackToAddresses: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        if (uiState.showNetworkChanged) {
            Text(
                text = "Network changed. Share the new address.",
                color = RiptideColors.Primary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
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
            Spacer(modifier = Modifier.height(8.dp))
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
        // 12 dp between Stop and the bottom navigation.
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** One address row: three separate rounded rectangles. The address
 * cell fills the available width (#1C1C1C, 16 dp corners, 16 dp
 * horizontal padding), laid out from the top: 12/16 sp muted
 * monospace heading, 16/20 sp off-white monospace value, 13/18 sp
 * muted description, 6 dp between the heading and the value and
 * 8 dp between the value and the description. The value is shown
 * with break opportunities after IPv6 colons
 * (displayAddressWithBreaks) and wraps naturally; Copy and Share
 * always act on the full underlying [value], never on the wrapped
 * display text. The right column is 72 dp wide: Copy (#1C1C1C)
 * above Share (signature coral #FF6B6B), 14 dp corners, 6 dp gaps
 * everywhere; each action fills its entire cell, and the column's
 * height matches the address cell's top and bottom edges.
 *
 * Height: [rowHeight], when given, fixes the row to the matched
 * height shared with its sibling row; otherwise the row sizes to
 * its content via IntrinsicSize.Max (never below 108 dp). The
 * intrinsic height is what keeps the action column real in every
 * state: the row is laid out in a vertically scrollable Column,
 * where an unbounded height makes fillMaxHeight a no-op and
 * weighted cells measure zero — a lone row used to render an empty
 * action column for exactly that reason. With a definite intrinsic
 * height the column's fillMaxHeight binds to it and the cells get
 * their share on the first pass. */
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
            .then(
                if (rowHeight != null) Modifier.height(rowHeight)
                else Modifier.height(IntrinsicSize.Max).heightIn(min = ROW_MIN_HEIGHT)
            )
            .onSizeChanged(onSizeChanged)
    ) {
        // Address cell.
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(RiptideColors.Surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = heading,
                    color = RiptideColors.HeadingMuted,
                    fontFamily = MonospaceFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displayAddressWithBreaks(value),
                    color = RiptideColors.OnSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        // Action column: Copy above Share, 6 dp between them.
        Column(modifier = Modifier.width(ACTION_COLUMN_WIDTH).fillMaxHeight()) {
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
                    .background(RiptideColors.Primary, RoundedCornerShape(14.dp))
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
 * of the two measured heights, never below 108 dp. Pure so the
 * matching rule is unit-testable. */
internal fun matchedRowHeight(first: Dp, second: Dp): Dp =
    maxOf(first, second).coerceAtLeast(ROW_MIN_HEIGHT)

/** Vertical gaps for the Create Server body. [NORMAL_GAPS] is the
 * spec layout; [COMPACT_GAPS] is used first when the measured
 * body content exceeds the available body height, before any
 * scrolling. The footer button gap and bottom padding are fixed
 * spec values in the footer itself (8 dp and 12 dp) and are not
 * part of this choice: the footer is measured by the Scaffold
 * outside the body's fit budget, so shrinking it would not save
 * the body a scroll. */
internal data class CreateGaps(
    val headerToRows: Dp,
    val betweenRows: Dp,
    val advancedGap: Dp,
    val requirementsGap: Dp,
    val listToHint: Dp,
    val footerButtonGap: Dp,
    val footerBottom: Dp
)

internal val NORMAL_GAPS = CreateGaps(
    headerToRows = 24.dp,
    betweenRows = 14.dp,
    advancedGap = 8.dp,
    requirementsGap = 16.dp,
    listToHint = 12.dp,
    footerButtonGap = 8.dp,
    footerBottom = 12.dp
)

internal val COMPACT_GAPS = CreateGaps(
    headerToRows = 16.dp,
    betweenRows = 10.dp,
    advancedGap = 6.dp,
    requirementsGap = 12.dp,
    listToHint = 6.dp,
    footerButtonGap = 8.dp,
    footerBottom = 8.dp
)

/** Estimated natural height of the BODY ONLY at default text,
 * from the spec's fixed sizes: the 76 dp header, the 108 dp
 * address rows, the 48 dp Advanced target and role-swap hint, the
 * 20 dp requirements heading, and the two 20 dp requirement
 * lines. The footer is deliberately not part of this: it is
 * measured by the Scaffold in the bottom slot, and the body is
 * given what is left. Pure so the fit decision is
 * unit-testable. */
internal fun estimatedBodyHeightDp(rows: Int, advanced: Boolean, gaps: CreateGaps): Dp {
    val header = 76.dp
    return if (advanced) {
        header + gaps.headerToRows + 108.dp + gaps.requirementsGap +
            20.dp + 8.dp + 48.dp + gaps.listToHint + 48.dp
    } else {
        header + gaps.headerToRows + 108.dp * rows +
            gaps.betweenRows * (rows - 1).coerceAtLeast(0) +
            gaps.advancedGap + 48.dp
    }
}

/** Measured height of the footer at default text: the 56 dp
 * buttons (Back to addresses above Stop in Advanced, with an
 * 8 dp gap), the 12 dp below Stop, and the 18 dp notice line
 * plus its 8 dp gap when the network changed. Pure so the fit
 * budget is unit-testable. */
internal fun estimatedFooterHeightDp(advanced: Boolean, notice: Boolean): Dp {
    return (if (notice) 18.dp + 8.dp else 0.dp) +
        (if (advanced) 56.dp + 8.dp else 0.dp) + 56.dp + 12.dp
}

/** The body's scroll range for a given content height against
 * the body's measured height: zero whenever the content fits,
 * so the body shows everything on entry with no scroll region.
 * Pure so the zero-scroll fit is unit-testable. */
internal fun bodyScrollRangeDp(contentHeightDp: Dp, availableHeightDp: Dp): Dp =
    (contentHeightDp - availableHeightDp).coerceAtLeast(0.dp)

/** Pick the gap set for the body's measured height: the normal
 * gaps when the body content fits, the compact gaps first when
 * it does not. The body still scrolls on its own if the compact
 * layout also overflows (shorter windows, enlarged text). Pure
 * so the fit decision is unit-testable. */
internal fun chooseGaps(availableHeightDp: Dp, rows: Int, advanced: Boolean): CreateGaps =
    if (estimatedBodyHeightDp(rows, advanced, NORMAL_GAPS) <= availableHeightDp) NORMAL_GAPS
    else COMPACT_GAPS

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

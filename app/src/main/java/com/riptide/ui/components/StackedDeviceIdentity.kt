package com.riptide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.theme.ApprovedRiptideTokens as T

/**
 * Stateless approved identity composition. No socket, persistence or clipboard side effects.
 * The route supplies live data and handles callbacks. Copy supplied drawable XMLs into res/drawable.
 * There is intentionally no outer card around these three widgets.
 * [panelWidth] supplies the queue panel's stable expanded width so
 * the tiles can adapt without a subcomposed intrinsic measurement.
 */
@Composable
fun StackedDeviceIdentity(
    deviceName: String,
    code: String,
    onCopyCode: () -> Unit,
    onShowFingerprint: () -> Unit,
    modifier: Modifier = Modifier,
    panelWidth: Dp? = null,
) {
    if (panelWidth == null) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            StackedDeviceIdentity(deviceName, code, onCopyCode, onShowFingerprint, panelWidth = maxWidth)
        }
        return
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val rowWidth = panelWidth - T.IdentityGap
    fun requiredWidths(scale: Float, padding: Dp): Pair<Dp, Dp> = with(density) {
        val label = measurer.measure("MY CODE", TextStyle(
            fontFamily = FontFamily.Monospace, fontSize = (12 * scale).sp,
            letterSpacing = (1.3f * scale).sp, fontWeight = FontWeight.Medium
        ), maxLines = 1, softWrap = false).size.width.toDp()
        val digits = measurer.measure(code, TextStyle(
            fontFamily = FontFamily.Monospace, fontSize = (24 * scale).sp
        ), maxLines = 1, softWrap = false).size.width.toDp()
        val fingerprint = measurer.measure("Fingerprint", TextStyle(
            fontSize = (16 * scale).sp
        ), maxLines = 1, softWrap = false).size.width.toDp()
        (maxOf(label + 28.dp, digits) + padding * 2) to
            (maxOf(fingerprint, 24.dp) + padding * 2)
    }
    var padding = 16.dp
    var scale = 1f
    var (codeMin, fingerprintMin) = requiredWidths(scale, padding)
    if (codeMin + fingerprintMin > rowWidth) {
        padding = 12.dp
        val widths = requiredWidths(scale, padding)
        codeMin = widths.first
        fingerprintMin = widths.second
    }
    val stack = codeMin + fingerprintMin > rowWidth && density.fontScale > 1f
    if (!stack && codeMin + fingerprintMin > rowWidth) {
        var low = 0.5f
        var high = 1f
        repeat(8) {
            val middle = (low + high) / 2
            val widths = requiredWidths(middle, padding)
            if (widths.first + widths.second <= rowWidth) low = middle else high = middle
        }
        scale = low
        val widths = requiredWidths(scale, padding)
        codeMin = widths.first
        fingerprintMin = widths.second
    }
    val shape = RoundedCornerShape(T.WidgetRadius)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(T.IdentityGap)) {
        Surface(shape = shape, color = T.Surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).background(T.SurfaceRaised, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.riptide_phone), null, Modifier.size(24.dp), tint = T.OnSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("THIS DEVICE", color = T.OnSurfaceVariant, fontFamily = FontFamily.Monospace,
                        fontSize = (12 * scale).sp, lineHeight = (16 * scale).sp,
                        letterSpacing = (1.3f * scale).sp, fontWeight = FontWeight.Medium)
                    Text(deviceName, color = T.OnSurface, fontSize = (21 * scale).sp, lineHeight = (28 * scale).sp,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
        if (stack) {
            ApprovedCodeTile(code, onCopyCode, Modifier.fillMaxWidth(), scale, padding)
            ApprovedFingerprintTile(onShowFingerprint, Modifier.fillMaxWidth(), scale, padding)
        } else {
            val maxCode = (rowWidth - fingerprintMin).coerceAtLeast(codeMin)
            val codeWidth = (rowWidth * (155f / 293f)).coerceIn(codeMin, maxCode)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(T.IdentityGap)) {
                ApprovedCodeTile(code, onCopyCode, Modifier.width(codeWidth).fillMaxHeight(), scale, padding)
                ApprovedFingerprintTile(onShowFingerprint, Modifier.weight(1f).fillMaxHeight(), scale, padding)
            }
        }
    }
}

@Composable
private fun ApprovedCodeTile(code: String, onCopy: () -> Unit, modifier: Modifier, scale: Float, padding: Dp) {
    val digits = code.filter { it.isDigit() }
    Surface(
        onClick = onCopy,
        enabled = digits.length == 6,
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = if (digits.length == 6)
                "Copy verification code, ${digits.toCharArray().joinToString(" ")}"
            else "Verification code unavailable"
        },
        shape = RoundedCornerShape(T.WidgetRadius),
        color = T.CodeContainer, contentColor = T.OnCodeContainer,
        tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Column(Modifier.heightIn(min = 96.dp).padding(horizontal = padding, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("MY CODE", Modifier.weight(1f), color = T.OnCodeContainer, fontFamily = FontFamily.Monospace,
                    fontSize = (12 * scale).sp, letterSpacing = (1.3f * scale).sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.riptide_copy), null, Modifier.size(20.dp), tint = T.OnCodeContainer)
            }
            Text(code, Modifier.paddingFromBaseline(top = 28.dp), color = T.OnCodeContainer,
                fontFamily = FontFamily.Monospace, fontSize = (24 * scale).sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun ApprovedFingerprintTile(onShow: () -> Unit, modifier: Modifier, scale: Float, padding: Dp) {
    Surface(
        onClick = onShow,
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = "Show this device's full fingerprint"
        },
        shape = RoundedCornerShape(T.WidgetRadius),
        color = T.Surface, contentColor = T.OnSurface,
        tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Column(Modifier.heightIn(min = 96.dp).padding(horizontal = padding, vertical = 16.dp)) {
            Icon(painterResource(R.drawable.riptide_shield), null, Modifier.size(24.dp), tint = T.OnSurfaceVariant)
            Text("Fingerprint", Modifier.paddingFromBaseline(top = 28.dp), color = T.OnSurface,
                fontSize = (16 * scale).sp, maxLines = 1, softWrap = false)
        }
    }
}

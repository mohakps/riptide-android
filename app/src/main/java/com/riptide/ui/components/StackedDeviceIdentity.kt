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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.theme.ApprovedRiptideTokens as T

/**
 * Stateless approved identity composition. No socket, persistence or clipboard side effects.
 * The route supplies live data and handles callbacks. Copy supplied drawable XMLs into res/drawable.
 * There is intentionally no outer card around these three widgets.
 */
@Composable
fun StackedDeviceIdentity(
    deviceName: String,
    code: String,
    onCopyCode: () -> Unit,
    onShowFingerprint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
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
                        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Medium)
                    Text(deviceName, color = T.OnSurface, fontSize = 21.sp, lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Adapt only when needed for accessible text/narrow windows; reference uses a weighted Row.
            if (fontScale > 1.2f || maxWidth < 300.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(T.IdentityGap)) {
                    ApprovedCodeTile(code, onCopyCode, Modifier.fillMaxWidth())
                    ApprovedFingerprintTile(onShowFingerprint, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(T.IdentityGap)) {
                    ApprovedCodeTile(code, onCopyCode, Modifier.weight(.625f).fillMaxHeight())
                    ApprovedFingerprintTile(onShowFingerprint, Modifier.weight(.375f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun ApprovedCodeTile(code: String, onCopy: () -> Unit, modifier: Modifier) {
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
        Column(Modifier.heightIn(min = 96.dp).padding(20.dp)) {
            Text("MY CODE", color = T.OnCodeContainer, fontFamily = FontFamily.Monospace,
                fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(code, Modifier.weight(1f), color = T.OnCodeContainer,
                    fontFamily = FontFamily.Monospace, fontSize = 26.sp, lineHeight = 32.sp)
                Icon(painterResource(R.drawable.riptide_copy), null, Modifier.size(24.dp), tint = T.OnCodeContainer)
            }
        }
    }
}

@Composable
private fun ApprovedFingerprintTile(onShow: () -> Unit, modifier: Modifier) {
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
        Column(Modifier.heightIn(min = 96.dp).padding(20.dp)) {
            Icon(painterResource(R.drawable.riptide_shield), null, Modifier.size(24.dp), tint = T.OnSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("Fingerprint", color = T.OnSurface, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

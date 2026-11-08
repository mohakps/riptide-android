package com.riptide.ui.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.model.ActiveTransfer
import com.riptide.ui.model.BusyPhase
import com.riptide.ui.model.TransferDirection
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors
import com.riptide.util.formatBytes
import com.riptide.util.formatBytesPair

/**
 * States 08 (ready), 09 (transfer), 10 (verifying / waiting for ack).
 * 100% of bytes is not success: verifying shows "Checking SHA-256…"
 * and a send shows "Waiting for PC confirmation" until the ack arrives.
 */
@Composable
fun ActiveTransferHero(
    activeTransfer: ActiveTransfer?,
    busyPhase: BusyPhase?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    receiveFolderName: String = "Downloads/Riptide"
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = RiptideColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            when {
                activeTransfer == null && busyPhase == BusyPhase.PREPARING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = RiptideColors.Primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "PREPARING FILE",
                            color = RiptideColors.OnSurfaceVariant,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 1.3.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Reading and hashing the file.",
                        color = RiptideColors.OnSurface,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                activeTransfer == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.riptide_send),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = RiptideColors.Primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "READY WHEN YOU ARE",
                            color = RiptideColors.OnSurfaceVariant,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 1.3.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Make the move.",
                        color = RiptideColors.OnSurface,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Send a file or request one from your PC. Incoming files save to $receiveFolderName.",
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "One file at a time.",
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                else -> {
                    val animatedProgress by animateFloatAsState(targetValue = activeTransfer.progress, label = "progress")
                    val phaseLabel = when {
                        busyPhase == BusyPhase.VERIFYING -> "VERIFYING FILE"
                        busyPhase == BusyPhase.WAITING_ACK -> "WAITING FOR PC CONFIRMATION"
                        activeTransfer.direction == TransferDirection.SEND -> "SENDING TO PC"
                        else -> "RECEIVING FROM PC"
                    }
                    val phaseIcon = if (activeTransfer.direction == TransferDirection.SEND) R.drawable.riptide_send else R.drawable.riptide_receive
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(phaseIcon),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = RiptideColors.Primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = phaseLabel,
                            color = RiptideColors.OnSurfaceVariant,
                            fontFamily = MonospaceFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 1.3.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeTransfer.filename,
                            color = RiptideColors.OnSurface,
                            fontSize = 21.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${(activeTransfer.progress * 100).toInt()}%",
                            color = RiptideColors.OnSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 21.sp,
                            lineHeight = 28.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (busyPhase == BusyPhase.VERIFYING || busyPhase == BusyPhase.WAITING_ACK) {
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = RiptideColors.Primary,
                            trackColor = RiptideColors.SurfaceRaised
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = RiptideColors.Primary,
                            trackColor = RiptideColors.SurfaceRaised
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Measured metrics. The speed column is sized from
                    // the expected formatted speed values and Transferred
                    // gets the rest, with a 12 dp gap. At ordinary sizes
                    // the pair fits on one line; when it will not fit at
                    // the current font scale, the two metrics stack so
                    // each gets the full card width. Wrapping stays on
                    // as the final accessibility fallback.
                    val speedText = when {
                        busyPhase == BusyPhase.VERIFYING || busyPhase == BusyPhase.WAITING_ACK -> "—"
                        activeTransfer.speedFormatted.isBlank() || activeTransfer.speedFormatted == "0 B/s" -> "—"
                        else -> activeTransfer.speedFormatted
                    }
                    val pairText = formatBytesPair(activeTransfer.transferredBytes, activeTransfer.totalBytes)
                    val transferredDescription =
                        "Transferred ${formatBytes(activeTransfer.transferredBytes)} of ${formatBytes(activeTransfer.totalBytes)}"
                    val speedDescription = if (speedText == "—") "Speed unavailable" else "Speed $speedText"
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val density = LocalDensity.current
                        val measurer = rememberTextMeasurer()
                        val valueStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        // Reserve the widest expected speed so the column
                        // never jumps as the value changes; grow it when
                        // the actual value is wider.
                        val speedWidthPx = remember(speedText, density) {
                            maxOf(
                                measurer.measure("999.9 MB/s", valueStyle).size.width,
                                measurer.measure(speedText, valueStyle).size.width
                            )
                        }
                        val pairWidthPx = remember(pairText, density) {
                            measurer.measure(pairText, valueStyle).size.width
                        }
                        val availablePx = with(density) { maxWidth.toPx() }.toInt()
                        val gapPx = with(density) { 12.dp.toPx() }.toInt()
                        val transferredPx = availablePx - speedWidthPx - gapPx
                        if (pairWidthPx > transferredPx) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Metric(
                                    label = "Transferred",
                                    value = pairText,
                                    contentDescription = transferredDescription
                                )
                                Spacer(Modifier.height(12.dp))
                                Metric(
                                    label = "Speed",
                                    value = speedText,
                                    contentDescription = speedDescription
                                )
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.width(with(density) { transferredPx.toDp() })) {
                                    Metric(
                                        label = "Transferred",
                                        value = pairText,
                                        contentDescription = transferredDescription
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Box(modifier = Modifier.width(with(density) { speedWidthPx.toDp() })) {
                                    Metric(
                                        label = "Speed",
                                        value = speedText,
                                        contentDescription = speedDescription
                                    )
                                }
                            }
                        }
                    }
                    if (busyPhase == BusyPhase.VERIFYING) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Checking SHA-256…",
                            color = RiptideColors.OnSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    } else if (busyPhase == BusyPhase.WAITING_ACK) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Waiting for PC confirmation",
                            color = RiptideColors.OnSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // Time remaining, next to Cancel. Left-aligned with
                    // Transferred, vertically centered against Cancel.
                    // The text wraps and the row grows at narrow widths
                    // and large fonts instead of clipping, ellipsizing,
                    // or shrinking; Cancel stays end-aligned and
                    // reachable. While verifying or waiting for the PC
                    // the phase line above carries the state instead.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (busyPhase != BusyPhase.VERIFYING && busyPhase != BusyPhase.WAITING_ACK) {
                            Text(
                                text = activeTransfer.etaFormatted ?: "Estimating time…",
                                color = RiptideColors.OnSurfaceVariant,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Button(
                            onClick = onCancel,
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceRaised, contentColor = RiptideColors.Error),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

/** One labeled metric: 12/16 label on top, 13/18 monospace value
 * below, aligned by baseline with its sibling. The value wraps as a
 * last resort at extreme text sizes; the full amounts stay available
 * to accessibility services through the description. */
@Composable
private fun Metric(
    label: String,
    value: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = RiptideColors.OnSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = RiptideColors.OnSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.semantics { this.contentDescription = contentDescription }
        )
    }
}

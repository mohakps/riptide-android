package com.riptide.ui.session

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.model.ActiveTransfer
import com.riptide.ui.model.BusyPhase
import com.riptide.ui.model.TransferDirection
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors
import com.riptide.util.formatBytes

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
    modifier: Modifier = Modifier
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
                        text = "Send a file or request one from your PC. Incoming files save to Downloads/Riptide.",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${formatBytes(activeTransfer.transferredBytes)} / ${formatBytes(activeTransfer.totalBytes)}" +
                                    if (busyPhase == BusyPhase.VERIFYING || busyPhase == BusyPhase.WAITING_ACK) "" else " · ${activeTransfer.speedFormatted}",
                                color = RiptideColors.OnSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                            if (busyPhase == BusyPhase.VERIFYING) {
                                Text(
                                    text = "Checking SHA-256…",
                                    color = RiptideColors.OnSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            } else if (busyPhase == BusyPhase.WAITING_ACK) {
                                Text(
                                    text = "Waiting for PC confirmation",
                                    color = RiptideColors.OnSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
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

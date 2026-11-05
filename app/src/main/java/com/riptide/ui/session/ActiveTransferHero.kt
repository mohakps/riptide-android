package com.riptide.ui.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riptide.ui.components.RiptideBadge
import com.riptide.ui.components.RiptideCard
import com.riptide.ui.model.ActiveTransfer
import com.riptide.ui.model.TransferDirection
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

@Composable
fun ActiveTransferHero(activeTransfer: ActiveTransfer?, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    RiptideCard(modifier = modifier.fillMaxWidth()) {
        if (activeTransfer == null) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "READY FOR TRANSFER", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Idle · 1 transfer at a time", style = MaterialTheme.typography.bodyMedium, color = RiptideColors.TextMuted)
            }
        } else {
            val animatedProgress by animateFloatAsState(targetValue = activeTransfer.progress, label = "progress")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (activeTransfer.direction == TransferDirection.SEND) {
                    RiptideBadge(text = "SENDING", containerColor = RiptideColors.PrimaryCoral, contentColor = RiptideColors.PrimaryCoral)
                } else {
                    RiptideBadge(text = "RECEIVING", containerColor = RiptideColors.SuccessMint, contentColor = RiptideColors.SuccessMint)
                }
                Text(text = "${(activeTransfer.progress * 100).toInt()}%", fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.titleMedium, color = RiptideColors.TextPrimary)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = activeTransfer.filename, style = MaterialTheme.typography.titleMedium, color = RiptideColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = RiptideColors.PrimaryCoral, trackColor = RiptideColors.SurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${activeTransfer.speedFormatted} · ${activeTransfer.etaFormatted ?: ""}", fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, RiptideColors.BorderOutline), modifier = Modifier.defaultMinSize(minHeight = 36.dp)) { Text("Cancel", color = RiptideColors.ErrorRed) }
            }
        }
    }
}

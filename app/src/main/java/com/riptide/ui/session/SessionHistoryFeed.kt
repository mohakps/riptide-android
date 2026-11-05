package com.riptide.ui.session

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riptide.ui.components.RiptideBadge
import com.riptide.ui.components.RiptideCard
import com.riptide.ui.model.TransferDirection
import com.riptide.ui.model.TransferRecord
import com.riptide.ui.model.TransferStatus
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

@Composable
fun SessionHistoryFeed(records: List<TransferRecord>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "TRANSFERS IN THIS SESSION", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
        if (records.isEmpty()) {
            Text(text = "No transfers completed in this session.", style = MaterialTheme.typography.bodyMedium, color = RiptideColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            records.forEach { record ->
                RiptideCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = if (record.direction == TransferDirection.SEND) "↑ " else "↓ ", color = RiptideColors.PrimaryCoral, fontFamily = MonospaceFontFamily)
                                Text(text = record.savedFilename, style = MaterialTheme.typography.bodyLarge, color = RiptideColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (record.wasRenamed) {
                                Text(text = "Renamed from \"${record.filename}\" (already existed)", style = MaterialTheme.typography.bodySmall, color = RiptideColors.WarningAmber)
                            }
                        }
                        if (record.status == TransferStatus.SUCCESS && record.sha256Verified) {
                            RiptideBadge(text = "SHA-256 VERIFIED", containerColor = RiptideColors.SuccessMint, contentColor = RiptideColors.SuccessMint)
                        } else {
                            RiptideBadge(text = record.status.name, containerColor = RiptideColors.ErrorRed, contentColor = RiptideColors.ErrorRed)
                        }
                    }
                }
            }
        }
    }
}

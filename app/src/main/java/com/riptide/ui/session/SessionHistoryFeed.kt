package com.riptide.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.model.TransferDirection
import com.riptide.ui.model.TransferRecord
import com.riptide.ui.model.TransferStatus
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors
import com.riptide.util.formatBytes

/**
 * This session's transfers as plain rows (no cards). The actual saved name
 * is prominent; a rename is explained underneath. Only successful,
 * SHA-256-verified rows say Verified. Long names wrap.
 */
@Composable
fun SessionHistoryFeed(records: List<TransferRecord>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "THIS SESSION",
            color = RiptideColors.OnSurfaceVariant,
            fontFamily = MonospaceFontFamily,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.3.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (records.isEmpty()) {
            Text(
                text = "No transfers in this session yet.",
                color = RiptideColors.OnSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            records.forEach { record ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        painter = painterResource(
                            if (record.direction == TransferDirection.SEND) R.drawable.riptide_send else R.drawable.riptide_receive
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = RiptideColors.Success
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.savedFilename,
                            color = RiptideColors.OnSurface,
                            fontSize = 17.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        val direction = if (record.direction == TransferDirection.SEND) "Sent" else "Received"
                        val status = when {
                            record.status == TransferStatus.SUCCESS && record.sha256Verified -> "SHA-256 verified"
                            record.status == TransferStatus.SUCCESS -> "Saved"
                            record.status == TransferStatus.CANCELLED -> "Cancelled"
                            else -> "Failed"
                        }
                        Text(
                            text = "$direction · ${formatBytes(record.sizeBytes)} · $status",
                            color = if (record.status == TransferStatus.SUCCESS && record.sha256Verified) RiptideColors.Success else RiptideColors.OnSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                        if (record.wasRenamed) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Renamed from ${record.filename} · Already existed",
                                color = RiptideColors.OnSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

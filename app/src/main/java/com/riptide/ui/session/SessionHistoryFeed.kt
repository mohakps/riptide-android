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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
 * This session's transfers as plain rows on the page surface. The
 * actual saved name is prominent; a rename is explained underneath.
 * Sent rows are green (#78DC8C arrow and status), Received rows are
 * soft blue (#8AB4F8 arrow and status), filenames are a softer
 * off-white (#D5D2CC) at 16/22 sp medium. The subtitle shows only the
 * status and the file size ("Sent · 130.2 KB"); the separator and the
 * size are muted (#A8A6A1). A failed or cancelled row keeps its
 * existing muted styling and names the outcome ("Failed · 130.2 KB")
 * instead of the direction, so it never reads as a success. The arrow
 * is centered against the combined filename and metadata block, and
 * the block grows when either line wraps.
 */
@Composable
fun SessionHistoryFeed(records: List<TransferRecord>, modifier: Modifier = Modifier, disconnected: Boolean = false) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "THIS SESSION",
            color = RiptideColors.OnSurfaceVariant,
            fontFamily = MonospaceFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 1.3.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (records.isEmpty()) {
            Text(
                text = if (disconnected) "No completed transfers in this session yet."
                    else "No transfers in this session yet.",
                color = RiptideColors.OnSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            records.forEach { record ->
                val isSend = record.direction == TransferDirection.SEND
                val isSuccess = record.status == TransferStatus.SUCCESS
                // The subtitle names the outcome. A success names the
                // direction (Sent / Received), coloured by direction; a
                // failure names the outcome (Failed / Cancelled) in the
                // existing muted styling.
                val statusText = when {
                    isSuccess && isSend -> "Sent"
                    isSuccess -> "Received"
                    record.status == TransferStatus.CANCELLED -> "Cancelled"
                    else -> "Failed"
                }
                val statusColor = when {
                    !isSuccess -> RiptideColors.OnSurfaceVariant
                    isSend -> RiptideColors.Success
                    else -> RiptideColors.ReceivedBlue
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            if (isSend) R.drawable.riptide_send else R.drawable.riptide_receive
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSend) RiptideColors.Success else RiptideColors.ReceivedBlue
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.savedFilename,
                            color = RiptideColors.HistoryFilename,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = statusColor)) { append(statusText) }
                                withStyle(SpanStyle(color = RiptideColors.HistoryMuted)) {
                                    append(" · ${formatBytes(record.sizeBytes)}")
                                }
                            },
                            fontSize = 13.sp,
                            lineHeight = 18.sp
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

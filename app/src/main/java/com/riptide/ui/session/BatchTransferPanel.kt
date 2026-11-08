package com.riptide.ui.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.model.BatchItem
import com.riptide.ui.model.BatchItemPhase
import com.riptide.ui.model.BatchState
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors
import com.riptide.util.formatBytesPair

/**
 * The outgoing batch panel. Same slot as the single-file
 * ActiveTransferHero: surface #1C1C1C, 28 dp corners, 20 dp padding.
 * The left headline counts files ("3 of 5"), the right figure is the
 * overall percent by bytes, and the bar advances by bytes. A small
 * file and a large file each advance the position by one, while the
 * bar advances by bytes. The position stays put during preparation
 * and verification; the filename row names the phase instead.
 */
@Composable
fun BatchTransferPanel(
    batch: BatchState,
    onCancelBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var detailsExpanded by remember { mutableStateOf(false) }
    val active = batch.activeItem
    val overallProgress = batch.overallProgress
    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress ?: 0f, label = "batchProgress"
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = RiptideColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.riptide_files),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = RiptideColors.Primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "SENDING TO PC",
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${batch.activeIndex + 1} of ${batch.items.size}",
                        color = RiptideColors.OnSurface,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.semantics {
                            contentDescription = "File ${batch.activeIndex + 1} of ${batch.items.size}"
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (batch.isVerifying) "Verifying on PC" else batch.batchEtaText,
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = batch.overallPercent?.let { "$it%" } ?: "—",
                        color = RiptideColors.OnSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 29.sp,
                        lineHeight = 34.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "OVERALL",
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (overallProgress != null) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = RiptideColors.Primary,
                    trackColor = RiptideColors.SurfaceRaised
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = RiptideColors.Primary,
                    trackColor = RiptideColors.SurfaceRaised
                )
            }
            if (active != null) {
                Spacer(Modifier.height(16.dp))
                // Tonal inset for the active filename: #272525, 16 dp
                // corners. The filename allows two lines here; the
                // full name is in the details and in the description.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RiptideColors.BatchInset,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.riptide_file),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = RiptideColors.OnSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (active.phase) {
                                    BatchItemPhase.PREPARING -> "Preparing file"
                                    BatchItemPhase.VERIFYING -> "Verifying on PC"
                                    else -> "Sending now"
                                },
                                color = RiptideColors.OnSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = active.name,
                                color = RiptideColors.OnSurface,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics {
                                    contentDescription = active.name
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .clickable { detailsExpanded = !detailsExpanded }
                        .semantics {
                            stateDescription = if (detailsExpanded) "Expanded" else "Collapsed"
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (detailsExpanded) "Hide details" else "View details",
                        color = RiptideColors.OnSurface,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(
                            if (detailsExpanded) R.drawable.riptide_chevron_up
                            else R.drawable.riptide_chevron_down
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = RiptideColors.OnSurfaceVariant
                    )
                }
                Button(
                    onClick = onCancelBatch,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RiptideColors.SurfaceRaised,
                        contentColor = RiptideColors.OnSurface
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel batch", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (detailsExpanded) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = RiptideColors.SurfaceRaised, thickness = 1.dp)
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    BatchMetric(
                        label = "Transferred",
                        value = batch.totalBytes?.let { formatBytesPair(batch.overallTransferred, it) } ?: "—",
                        contentDescription = "Transferred ${batch.overallTransferred} bytes of ${batch.totalBytes?.toString() ?: "unknown"}",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    BatchMetric(
                        label = "Speed",
                        value = batch.overallSpeedFormatted.ifBlank { "—" },
                        contentDescription = if (batch.overallSpeedFormatted.isBlank()) "Speed unavailable" else "Speed ${batch.overallSpeedFormatted}",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    batch.items.forEachIndexed { index, item ->
                        BatchFileRow(
                            item = item,
                            isActive = index == batch.activeIndex
                        )
                    }
                }
            }
        }
    }
}

/** One labeled metric in the expanded details: 12/16 label on top,
 * 13/18 monospace value below. Same style as the single-file panel. */
@Composable
private fun BatchMetric(
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

/** One row in the expanded file list. Sent and queued rows are
 * neutral; the active row doubles as its own progress bar, with the
 * #352628 base and the #563438 fill advancing from the leading edge
 * in proportion to that file's bytes. Only the active row gets the
 * fill. While the PC confirms the file, the row shows Verifying with
 * a full fill. */
@Composable
private fun BatchFileRow(
    item: BatchItem,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val status = when (item.phase) {
        BatchItemPhase.SENT -> "Sent"
        BatchItemPhase.SENDING -> "Sending"
        BatchItemPhase.VERIFYING -> "Verifying"
        BatchItemPhase.PREPARING -> "Preparing"
        BatchItemPhase.FAILED -> "Failed"
        BatchItemPhase.QUEUED -> "Queued"
    }
    if (isActive) {
        // The fill fraction: full while verifying, the file's own
        // progress while sending, and no fill when the size is unknown.
        val fillFraction = when (item.phase) {
            BatchItemPhase.VERIFYING -> 1f
            else -> item.progress
        }
        val animatedFill by animateFloatAsState(
            targetValue = fillFraction ?: 0f, label = "activeRowFill"
        )
        Box(
            modifier = modifier.fillMaxWidth().heightIn(min = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RiptideColors.BatchRowBase)
                .semantics {
                    if (fillFraction != null) {
                        progressBarRangeInfo = ProgressBarRangeInfo(animatedFill, 0f..1f)
                    }
                    contentDescription = "${item.name}, $status"
                }
        ) {
            if (fillFraction != null) {
                Box(
                    modifier = Modifier.fillMaxHeight()
                        .fillMaxWidth(animatedFill)
                        .background(RiptideColors.BatchRowFill)
                )
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        if (item.phase == BatchItemPhase.PREPARING) R.drawable.riptide_file
                        else R.drawable.riptide_send
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (item.phase == BatchItemPhase.PREPARING) RiptideColors.OnSurfaceVariant else RiptideColors.Primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = item.name,
                    color = RiptideColors.OnSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = status,
                    color = RiptideColors.Primary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().heightIn(min = 40.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    if (item.phase == BatchItemPhase.SENT) R.drawable.riptide_check
                    else R.drawable.riptide_file
                ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (item.phase == BatchItemPhase.SENT) RiptideColors.Success else RiptideColors.OnSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.name,
                color = RiptideColors.OnSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = status,
                color = if (item.phase == BatchItemPhase.FAILED) RiptideColors.Error else RiptideColors.OnSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

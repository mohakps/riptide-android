package com.riptide.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.model.BatchItem
import com.riptide.ui.theme.RiptideColors
import com.riptide.util.formatBytes

/**
 * The File queue sheet: the signature modal bottom sheet (as used for
 * Show fingerprint / connection approval — charcoal surface, rounded
 * top corners, centered drag handle, scrim, slide-up). Rows are in
 * original order. Tapping the coral × removes a file immediately:
 * the row stays for Undo, struck through and faded, the × replaced
 * by a white Undo glyph; Undo restores it in place. Removing every
 * file keeps the sheet open for Undo; dismissing with zero included
 * files drops the whole staged queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileQueueSheet(
    queueItems: List<BatchItem>,
    onRemove: (String) -> Unit,
    onUndo: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = RiptideColors.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(
                text = "File queue",
                color = RiptideColors.OnSurface,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Remove files you don't want to send.",
                color = RiptideColors.OnSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(queueItems, key = { it.id }) { item ->
                    FileQueueRow(
                        item = item,
                        onRemove = { onRemove(item.id) },
                        onUndo = { onUndo(item.id) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RiptideColors.Primary,
                    contentColor = RiptideColors.OnPrimary
                )
            ) {
                Text("Close", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FileQueueRow(
    item: BatchItem,
    onRemove: () -> Unit,
    onUndo: () -> Unit
) {
    val sizeText = item.knownSize?.let { formatBytes(it) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 59.dp)
            .background(Color(0xFF252525), RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            // The exclusion state is exposed here; the trailing action
            // keeps its own "Remove [filename]" / "Undo removal of
            // [filename]" label, so the row must not merge descendants.
            .semantics {
                stateDescription = if (item.excluded) "Removed" else "Included"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.riptide_file), null,
            Modifier
                .size(24.dp)
                .alpha(if (item.excluded) 0.48f else 1f),
            tint = RiptideColors.OnSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = RiptideColors.OnSurface,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.excluded) TextDecoration.LineThrough else null,
                modifier = Modifier.alpha(if (item.excluded) 0.48f else 1f)
            )
            if (sizeText != null) {
                Text(
                    text = sizeText,
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.alpha(if (item.excluded) 0.48f else 1f)
                )
            }
        }
        if (item.excluded) {
            IconButton(
                onClick = onUndo,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Undo removal of ${item.name}" }
            ) {
                Icon(
                    painterResource(R.drawable.riptide_undo), null,
                    Modifier.size(24.dp), tint = Color.White
                )
            }
        } else {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Remove ${item.name}" }
            ) {
                Icon(
                    painterResource(R.drawable.riptide_close), null,
                    Modifier.size(24.dp), tint = RiptideColors.Primary
                )
            }
        }
    }
}

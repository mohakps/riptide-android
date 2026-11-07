package com.riptide.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.ui.model.SavedPeer
import com.riptide.ui.theme.RiptideColors

/**
 * Shared saved-peer row (Home recent + Devices full list). Tapping the row
 * joins that host; the overflow menu is a separate 48 dp target and never
 * also joins. No online indicator: a saved PC is not an online PC.
 */
@Composable
fun PeerRow(peer: SavedPeer, onConnect: (SavedPeer) -> Unit, onForget: (SavedPeer) -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = { onConnect(peer) },
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = "Join ${peer.name}"
        },
        shape = RoundedCornerShape(24.dp),
        color = RiptideColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.riptide_pc),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = RiptideColors.OnSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    color = RiptideColors.OnSurface,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = peer.host,
                    color = RiptideColors.OnSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = "More options for ${peer.name}"
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.riptide_more),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = RiptideColors.OnSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Forget", color = RiptideColors.OnSurface) },
                    onClick = { menuOpen = false; onForget(peer) }
                )
            }
        }
    }
}

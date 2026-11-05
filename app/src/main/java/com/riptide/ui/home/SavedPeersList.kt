package com.riptide.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riptide.ui.components.RiptideBadge
import com.riptide.ui.components.RiptideCard
import com.riptide.ui.model.SavedPeer
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

@Composable
fun SavedPeersList(peers: List<SavedPeer>, onConnect: (SavedPeer) -> Unit, onForget: (SavedPeer) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "SAVED PEERS", style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted, modifier = Modifier.padding(bottom = 8.dp))
        if (peers.isEmpty()) {
            Text(text = "No saved peers yet. Connect once to remember a PC.", style = MaterialTheme.typography.bodyMedium, color = RiptideColors.TextMuted, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            peers.forEach { peer ->
                RiptideCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = peer.name, style = MaterialTheme.typography.titleMedium, color = RiptideColors.TextPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                RiptideBadge(text = "KNOWN", containerColor = RiptideColors.SuccessMint, contentColor = RiptideColors.SuccessMint)
                            }
                            Text(text = "${peer.host}:${peer.port}", fontFamily = MonospaceFontFamily, style = MaterialTheme.typography.bodySmall, color = RiptideColors.TextMuted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { onConnect(peer) }, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.SurfaceVariant, contentColor = RiptideColors.TextPrimary), modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text("Connect") }
                            IconButton(onClick = { onForget(peer) }, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Forget peer", tint = RiptideColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

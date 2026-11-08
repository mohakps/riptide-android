package com.riptide.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.ui.components.BottomInfoPanel
import com.riptide.ui.components.PeerRow
import com.riptide.ui.components.RiptideTopBar
import com.riptide.ui.model.SavedPeer
import com.riptide.ui.theme.MonospaceFontFamily
import com.riptide.ui.theme.RiptideColors

/**
 * States 16 (full list) and 18 (empty). Tapping a row joins that PC on
 * Transfer; the row overflow forgets it. Saved identity does not mean
 * the PC is online — no presence indicator is shown.
 */
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onNavigateToSettings: () -> Unit,
    onJoinPeer: (SavedPeer) -> Unit,
    onGoToTransfer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peers by viewModel.savedPeers.collectAsState()
    Scaffold(
        topBar = { RiptideTopBar(onOpenSettings = onNavigateToSettings) },
        containerColor = RiptideColors.Background,
        modifier = modifier
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val gutter = if (maxWidth < 360.dp) 16.dp else 24.dp
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = gutter)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Devices",
                    color = RiptideColors.OnSurface,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Familiar devices. One tap to reconnect.",
                    color = RiptideColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (peers.isEmpty()) {
                    Text(
                        text = "No saved devices yet",
                        color = RiptideColors.OnSurface,
                        fontSize = 21.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Join and approve a PC from Transfer. It will appear here for next time.",
                        color = RiptideColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onGoToTransfer,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiptideColors.Primary, contentColor = RiptideColors.OnPrimary)
                    ) { Text("Go to Transfer", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                } else {
                    Text(
                        text = "RECENTLY USED",
                        color = RiptideColors.OnSurfaceVariant,
                        fontFamily = MonospaceFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 1.3.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        peers.forEach { peer ->
                            PeerRow(peer = peer, onConnect = onJoinPeer, onForget = viewModel::onForgetPeer)
                        }
                    }
                }
                // The tip is the last item of the scrolling body, not a
                // fixed footer: 24 dp above, 24 dp trailing padding
                // below, moving with the list.
                Spacer(modifier = Modifier.height(24.dp))
                BottomInfoPanel(message = "Saved identity does not mean the PC is online.")
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

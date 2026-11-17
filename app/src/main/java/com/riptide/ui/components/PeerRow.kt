package com.riptide.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.riptide.R
import com.riptide.ui.model.SavedPeer
import com.riptide.ui.theme.RiptideColors

@Composable
fun SavedPeerList(peers: List<SavedPeer>, onConnect: (SavedPeer) -> Unit, onForget: (SavedPeer) -> Unit) {
    var expandedHost by remember { mutableStateOf<String?>(null) }
    var deletingPeer by remember { mutableStateOf<SavedPeer?>(null) }
    val forget by rememberUpdatedState(onForget)
    val exit = remember { Animatable(0f) }
    val height = remember { Animatable(1f) }
    LaunchedEffect(deletingPeer) {
        val peer = deletingPeer ?: return@LaunchedEffect
        try {
            exit.snapTo(0f)
            height.snapTo(1f)
            exit.animateTo(1f, tween(145, easing = FastOutLinearInEasing))
            height.animateTo(0f, tween(210, easing = FastOutSlowInEasing))
        } finally {
            // Persist a confirmed tap even if the user leaves during the animation.
            forget(peer)
        }
    }
    val removing = deletingPeer
    LaunchedEffect(peers, removing) {
        if (removing != null && peers.none { it.host == removing.host }) {
            if (expandedHost == removing.host) expandedHost = null
            deletingPeer = null
        }
    }

    Column {
        peers.forEachIndexed { index, peer ->
            key(peer.host) {
                val isDeleting = peer.host == deletingPeer?.host
                val animation = if (isDeleting) {
                    Modifier.clipToBounds().layout { measurable, constraints ->
                        val item = measurable.measure(constraints.copy(minHeight = 0))
                        layout(item.width, (item.height * height.value).toInt()) { item.placeRelative(0, 0) }
                    }.graphicsLayer {
                        translationX = -28.dp.toPx() * exit.value
                        alpha = 1f - exit.value
                    }
                } else Modifier
                Column(animation) {
                    PeerRow(
                        peer = peer,
                        expanded = expandedHost == peer.host,
                        enabled = !isDeleting,
                        deleteEnabled = deletingPeer == null,
                        onConnect = { onConnect(peer) },
                        onToggle = { expandedHost = if (expandedHost == peer.host) null else peer.host },
                        onDelete = { deletingPeer = peer }
                    )
                    if (index < peers.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    peer: SavedPeer,
    expanded: Boolean,
    enabled: Boolean,
    deleteEnabled: Boolean,
    onConnect: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val reveal by animateFloatAsState(
        if (expanded) 1f else 0f,
        tween(240, easing = FastOutSlowInEasing),
        label = "deviceKeypad"
    )
    Column(Modifier.background(
        if (expanded || reveal > 0f) Color(0xFF161616) else Color.Transparent,
        RoundedCornerShape(24.dp)
    )) {
        Surface(
            onClick = onConnect,
            enabled = enabled,
            modifier = Modifier.zIndex(1f).semantics(mergeDescendants = true) {
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
                        fontWeight = FontWeight.Medium,
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
                    onClick = onToggle,
                    enabled = enabled,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (expanded) RiptideColors.SurfaceRaised else Color.Transparent,
                        contentColor = if (expanded) RiptideColors.OnSurface else RiptideColors.OnSurfaceVariant,
                    ),
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "More options for ${peer.name}"
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.riptide_more),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        if (expanded || reveal > 0f) {
            Box(Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
                val keypad = measurable.measure(constraints.copy(minHeight = 0))
                val visibleHeight = (keypad.height * reveal).toInt()
                layout(keypad.width, visibleHeight) {
                    keypad.placeRelative(0, visibleHeight - keypad.height)
                }
            }) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                color = Color(0xFF161616)
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        PeerKey("Trust", R.drawable.riptide_shield, RiptideColors.OnSurfaceVariant, false, {}, Modifier.weight(1f))
                        PeerKey("Delete", R.drawable.riptide_trash, RiptideColors.Primary, expanded && enabled && deleteEnabled, onDelete, Modifier.weight(1f))
                    }
                    Box(Modifier.align(Alignment.Center).width(1.dp).height(20.dp).background(RiptideColors.SurfaceRaised))
                }
            }
            }
        }
    }
}

@Composable
private fun PeerKey(label: String, icon: Int, color: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = color)
    ) {
        Icon(painterResource(icon), null, Modifier.size(18.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

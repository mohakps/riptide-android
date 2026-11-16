package com.riptide.ui.home

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riptide.R
import com.riptide.core.QueuePanelMath
import com.riptide.ui.components.StackedDeviceIdentity
import com.riptide.ui.model.BatchState
import com.riptide.ui.theme.ApprovedRiptideTokens as T
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The Home device-info slot, replaced by a two-panel switch while
 * files are queued. Left: the OUTGOING QUEUE panel (charcoal, 24 dp
 * corners, 188 dp tall). Right: the device-info panel (the existing
 * This device / My code / Fingerprint composition). One progress
 * value drives both: 0 is the queue open, 1 is device info open.
 * Geometry is [QueuePanelMath], ported from the pack's
 * swipe-preview.html: at the 412 dp reference width the row is
 * 364 dp (24 dp gutters), the wide panel 301 dp, the narrow tab
 * 55 dp, with an 8 dp gap.
 *
 * Tap a collapsed tab, or swipe the row, to switch. A tap settles
 * in ~300 ms with an ease-out; a drag follows the finger and commits
 * past 40 dp of travel, otherwise snaps back. A drag or tap that
 * interrupts an animation continues from the visible widths and
 * colours. Reduced motion (animator duration scale off) switches
 * immediately with sharp content.
 */
@Composable
fun QueueDevicePanel(
    queue: BatchState,
    deviceName: String,
    code: String,
    onCopyCode: () -> Unit,
    onShowFingerprint: () -> Unit,
    onViewQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val count = queue.includedCount
    val context = LocalContext.current
    val density = LocalDensity.current
    // Reduced motion: the system animator-duration-scale setting.
    // Read on each composition; the setting rarely changes mid-screen.
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        }.getOrDefault(1f) == 0f
    }
    var showDeviceInfo by remember { mutableStateOf(false) }
    val anim = remember { Animatable(0f) }
    val progress = anim.value
    val scope = rememberCoroutineScope()

    fun settleTo(target: Float) {
        showDeviceInfo = target > 0.5f
        scope.launch {
            if (reducedMotion) {
                anim.snapTo(target)
            } else {
                anim.animateTo(target, tween(300, easing = EaseOutCubic))
            }
        }
    }

    BoxWithConstraints(modifier) {
        val rowWidth = maxWidth
        val narrow = QueuePanelMath.narrowWidth(rowWidth)
        val wide = QueuePanelMath.wideWidth(rowWidth)
        val totalPx = with(density) { (wide - narrow).toPx() }
        val commitPx = with(density) { 40.dp.toPx() }
        var dragStartProgress by remember { mutableStateOf(0f) }
        var dragTotal by remember { mutableStateOf(0f) }
        val draggableState = rememberDraggableState { delta ->
            dragTotal += delta
            // Dragging left (negative delta) moves toward device info.
            scope.launch {
                anim.snapTo((dragStartProgress - dragTotal / totalPx).coerceIn(0f, 1f))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        // Interrupt continues from the visible position.
                        anim.stop()
                        dragStartProgress = anim.value
                        dragTotal = 0f
                    },
                    onDragStopped = {
                        if (abs(dragTotal) > commitPx) {
                            // A deliberate swipe commits in its direction.
                            settleTo(if (dragTotal < 0f) 1f else 0f)
                        } else {
                            // A short or cancelled drag snaps back.
                            settleTo(if (dragStartProgress > 0.5f) 1f else 0f)
                        }
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(QueuePanelMath.GAP)
        ) {
            // Queue panel: charcoal while open, blending to coral as
            // it collapses into its tab.
            Box(
                Modifier
                    .width(QueuePanelMath.queueWidth(rowWidth, progress))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(lerp(T.Surface, T.Primary, progress))
            ) {
                // Open content: slides left and fades as the panel
                // collapses, with a light blur while moving.
                val queueAlpha = QueuePanelMath.queueContentAlpha(progress)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = queueAlpha
                            translationX = with(density) {
                                QueuePanelMath.queueSlideDp(progress).toPx()
                            }
                        }
                        .panelBlur(QueuePanelMath.blurDp(queueAlpha))
                        .padding(20.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.riptide_file_clock), null,
                                Modifier.size(24.dp), tint = T.OnSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "OUTGOING QUEUE", color = T.OnSurfaceVariant,
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                lineHeight = 16.sp, letterSpacing = 1.3.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "$count ${if (count == 1) "file" else "files"}"
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "%02d".format(count), color = T.OnSurface,
                                fontFamily = FontFamily.Monospace, fontSize = 40.sp,
                                lineHeight = 44.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (count == 1) "file" else "files", color = T.OnSurface,
                                fontSize = 17.sp, lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Connect to a device to send them.", color = T.OnSurfaceVariant,
                            fontSize = 14.sp, lineHeight = 18.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClickLabel = "View queue") { onViewQueue() },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                "View queue", color = T.Primary,
                                fontSize = 15.sp, lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                // Collapsed tab: white file-and-clock glyph above a
                // white right-pointing arrow. Full tab tappable. The
                // tap target is only live while the queue is collapsed:
                // at alpha 0 it sits above the open content and must
                // not swallow its taps.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = QueuePanelMath.queueTabAlpha(progress) }
                        .clickable(
                            enabled = progress > 0.5f,
                            role = Role.Button, onClickLabel = "Show file queue"
                        ) { settleTo(0f) }
                        .semantics { contentDescription = "Show file queue" },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painterResource(R.drawable.riptide_file_clock), null,
                            Modifier.size(24.dp), tint = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Icon(
                            painterResource(R.drawable.riptide_arrow), null,
                            Modifier.size(20.dp), tint = Color.White
                        )
                    }
                }
            }
            // Device-info panel: ivory tab while collapsed, blending
            // to the page background as its cards are revealed.
            Box(
                Modifier
                    .width(QueuePanelMath.deviceWidth(rowWidth, progress))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(lerp(T.OnSurface, T.Background, progress))
            ) {
                // Open content: the existing identity composition,
                // forced horizontal so the panel height stays stable
                // while it slides. Slides in from the right and fades.
                val deviceAlpha = QueuePanelMath.deviceContentAlpha(progress)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = deviceAlpha
                            translationX = with(density) {
                                (64.dp * (1f - progress)).toPx()
                            }
                        }
                        .panelBlur(QueuePanelMath.blurDp(deviceAlpha))
                ) {
                    StackedDeviceIdentity(
                        deviceName = deviceName,
                        code = code,
                        onCopyCode = onCopyCode,
                        onShowFingerprint = onShowFingerprint,
                        forceHorizontal = true
                    )
                }
                // Collapsed tab: dark phone glyph above a dark
                // left-pointing arrow on ivory. Full tab tappable.
                // Only live while device info is collapsed, for the
                // same reason as the queue tab above.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = QueuePanelMath.deviceTabAlpha(progress) }
                        .clickable(
                            enabled = progress < 0.5f,
                            role = Role.Button, onClickLabel = "Show device info"
                        ) { settleTo(1f) }
                        .semantics { contentDescription = "Show device info" },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painterResource(R.drawable.riptide_phone), null,
                            Modifier.size(24.dp), tint = Color(0xFF101010)
                        )
                        Spacer(Modifier.height(8.dp))
                        Icon(
                            painterResource(R.drawable.riptide_arrow_left), null,
                            Modifier.size(20.dp), tint = Color(0xFF101010)
                        )
                    }
                }
            }
        }
    }
}

/** Blur for moving panel content. RenderEffect blur needs Android S;
 * below that the content moves without blur (still sharp at rest). */
private fun Modifier.panelBlur(radius: Dp): Modifier =
    if (radius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this

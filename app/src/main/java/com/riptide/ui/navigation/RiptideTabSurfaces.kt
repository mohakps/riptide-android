package com.riptide.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.animation.ValueAnimator
import kotlin.math.abs
import kotlin.coroutines.coroutineContext

@Composable
internal fun RiptideTabSurfaces(
    selected: RiptideTab,
    onSelect: (RiptideTab) -> Unit,
    swipeEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (RiptideTab) -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    val selectedNow by rememberUpdatedState(selected)
    val selectNow by rememberUpdatedState(onSelect)
    var offsets by remember { mutableStateOf(mapOf(selected to 0f)) }
    var dragging by remember { mutableStateOf(false) }
    val motionEnabled = !LocalView.current.isInEditMode && ValueAnimator.areAnimatorsEnabled()

    LaunchedEffect(selected, dragging, motionEnabled) {
        if (dragging) return@LaunchedEffect
        if (!motionEnabled || coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
            offsets = mapOf(selected to 0f)
            return@LaunchedEffect
        }
        if (offsets == mapOf(selected to 0f)) return@LaunchedEffect
        val start = offsets.toMutableMap()
        if (selected !in start) {
            val anchor = start.minBy { abs(it.value) }.key
            start[selected] = if (selected.ordinal > anchor.ordinal) 1f else -1f
        }
        val end = start.keys.associateWith { tab ->
            when {
                tab == selected -> 0f
                tab.ordinal < selected.ordinal -> -1f
                else -> 1f
            }
        }
        animate(0f, 1f, animationSpec = tween(180, easing = FastOutSlowInEasing)) { progress, _ ->
            offsets = start.mapValues { (tab, offset) ->
                offset + (end.getValue(tab) - offset) * progress
            }
        }
        offsets = mapOf(selected to 0f)
    }

    Box(modifier.clipToBounds().pointerInput(swipeEnabled, motionEnabled) {
        if (!swipeEnabled) return@pointerInput
        val edge = 20.dp.toPx()
        val threshold = 40.dp.toPx()
        val tabs = RiptideTab.values()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x < edge || down.position.x > size.width - edge) return@awaitEachGesture
            var horizontal = false
            var target: RiptideTab? = null
            val origin = selectedNow
            val start = offsets
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed || selectedNow != origin) break
                    val delta = change.position - down.position
                    if (!change.pressed) {
                        if (horizontal && abs(delta.x) > threshold) target?.let(selectNow)
                        break
                    }
                    if (!horizontal) {
                        if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) > abs(delta.y) * 1.5f) {
                            horizontal = true
                            dragging = motionEnabled
                        } else if (abs(delta.y) > viewConfiguration.touchSlop) break
                    }
                    if (horizontal) {
                        val direction = if (delta.x < 0) 1 else -1
                        target = tabs.getOrNull(origin.ordinal + direction)
                        if (motionEnabled && size.width > 0) {
                            val shift = delta.x / size.width
                            val originOffset = (start[origin] ?: 0f) + shift
                            offsets = target?.let {
                                mapOf(
                                    origin to originOffset.coerceIn(-1f, 1f),
                                    it to ((start[it] ?: direction.toFloat()) + shift).coerceIn(-1f, 1f)
                                )
                            } ?: start
                        }
                        change.consume()
                    }
                }
            } finally {
                if (horizontal) dragging = false
            }
        }
    }) {
        for (tab in offsets.keys.sortedBy { it.ordinal }) {
            androidx.compose.runtime.key(tab) {
                val semantics = if (tab == selected) Modifier else Modifier.clearAndSetSemantics { }
                Box(Modifier.fillMaxSize().graphicsLayer {
                    translationX = (offsets[tab] ?: 0f) * size.width
                }.then(semantics)) {
                    stateHolder.SaveableStateProvider(tab.name) { content(tab) }
                }
            }
        }
    }
}

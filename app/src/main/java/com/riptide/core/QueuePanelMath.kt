package com.riptide.core

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure geometry for the Home two-panel switch (queue / device info),
 * ported from the pack's swipe-preview.html so the app and the
 * reference agree on every number. One progress value drives both
 * panels: 0 is the queue open, 1 is device info open.
 *
 * At the 412 dp reference width the usable row is 364 dp (24 dp
 * gutters): the wide panel is 301 dp, the narrow tab 55 dp, with an
 * 8 dp gap. On other widths the narrow tab is ~15% of the row and at
 * least 48 dp wide, and the wide panel takes the rest.
 *
 * HTML formulas, ported verbatim:
 * - queue width 301 - 246 * progress; device width 55 + 246 * progress
 * - queue content slides -64 * progress dp from the left
 * - queue content opacity 1 - progress; device content and queue tab
 *   opacity 3 * progress - 2 (invisible until progress passes 2/3);
 *   device tab opacity 1 - 3 * progress
 * - blur on moving content (1 - opacity) * 5 dp, none at rest
 */
object QueuePanelMath {
    /** Gap between the two panels. Constant through the motion. */
    val GAP: Dp = 8.dp

    /** Narrow tab width for a row of [rowWidth]: ~15%, at least 48 dp. */
    fun narrowWidth(rowWidth: Dp): Dp = maxOf(48.dp, rowWidth * 0.15f)

    /** Wide panel width for a row of [rowWidth]. */
    fun wideWidth(rowWidth: Dp): Dp = rowWidth - GAP - narrowWidth(rowWidth)

    /** Queue panel width at [progress] (0 = open, 1 = collapsed). */
    fun queueWidth(rowWidth: Dp, progress: Float): Dp {
        val narrow = narrowWidth(rowWidth)
        val wide = wideWidth(rowWidth)
        return wide - (wide - narrow) * progress.coerceIn(0f, 1f)
    }

    /** Device-info panel width at [progress]. */
    fun deviceWidth(rowWidth: Dp, progress: Float): Dp {
        val narrow = narrowWidth(rowWidth)
        val wide = wideWidth(rowWidth)
        return narrow + (wide - narrow) * progress.coerceIn(0f, 1f)
    }

    /** Queue content's horizontal slide, in dp (negative = left).
     * The zero case returns a clean 0.dp: -64.dp * 0f is -0.0.dp,
     * which is not equal to 0.dp. */
    fun queueSlideDp(progress: Float): Dp {
        val p = progress.coerceIn(0f, 1f)
        return if (p == 0f) 0.dp else -64.dp * p
    }

    /** Queue content opacity. */
    fun queueContentAlpha(progress: Float): Float = (1f - progress).coerceIn(0f, 1f)

    /** Device-info content opacity: invisible until progress > 2/3. */
    fun deviceContentAlpha(progress: Float): Float = (progress * 3f - 2f).coerceIn(0f, 1f)

    /** Queue tab (coral, shown when the queue is collapsed) opacity. */
    fun queueTabAlpha(progress: Float): Float = (progress * 3f - 2f).coerceIn(0f, 1f)

    /** Device tab (ivory, shown when device info is collapsed) opacity. */
    fun deviceTabAlpha(progress: Float): Float = (1f - progress * 3f).coerceIn(0f, 1f)

    /** Blur for moving content at [opacity]: up to 5 dp, none at rest. */
    fun blurDp(opacity: Float): Dp = ((1f - opacity.coerceIn(0f, 1f)) * 5f).dp
}

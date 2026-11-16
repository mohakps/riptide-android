package com.riptide.core

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Home two-panel geometry, ported from the pack's swipe-preview.html.
 * At the 412 dp reference width the usable row is 364 dp (24 dp
 * gutters): the wide panel is 301 dp, the narrow tab 55 dp, with an
 * 8 dp gap. Progress 0 is the queue open, 1 is device info open.
 */
class QueuePanelMathTest {

    private val row = 364.dp

    @Test
    fun testReferenceWidthsAtRest() {
        assertEquals(301.4f, QueuePanelMath.queueWidth(row, 0f).value, 0.5f)
        assertEquals(54.6f, QueuePanelMath.deviceWidth(row, 0f).value, 0.5f)
    }

    @Test
    fun testReferenceWidthsSwitched() {
        assertEquals(54.6f, QueuePanelMath.queueWidth(row, 1f).value, 0.5f)
        assertEquals(301.4f, QueuePanelMath.deviceWidth(row, 1f).value, 0.5f)
    }

    @Test
    fun testWidthsSumToRowAtEveryProgress() {
        for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val sum = QueuePanelMath.queueWidth(row, progress) +
                QueuePanelMath.GAP + QueuePanelMath.deviceWidth(row, progress)
            assertEquals(row.value, sum.value, 0.01f)
        }
    }

    @Test
    fun testNarrowTabHas48dpFloor() {
        // A 200 dp row: 15% would be 30 dp, so the 48 dp floor wins.
        assertEquals(48.dp, QueuePanelMath.narrowWidth(200.dp))
        assertEquals(144.dp, QueuePanelMath.wideWidth(200.dp))
    }

    @Test
    fun testContentAlphas() {
        assertEquals(1f, QueuePanelMath.queueContentAlpha(0f), 0.001f)
        assertEquals(0f, QueuePanelMath.queueContentAlpha(1f), 0.001f)
        assertEquals(0.5f, QueuePanelMath.queueContentAlpha(0.5f), 0.001f)
        // Device content stays invisible until progress passes 2/3.
        assertEquals(0f, QueuePanelMath.deviceContentAlpha(0f), 0.001f)
        assertEquals(0f, QueuePanelMath.deviceContentAlpha(0.5f), 0.001f)
        assertEquals(0f, QueuePanelMath.deviceContentAlpha(2f / 3f), 0.001f)
        assertEquals(1f, QueuePanelMath.deviceContentAlpha(1f), 0.001f)
        // The queue tab fades in on the same curve as device content.
        assertEquals(QueuePanelMath.deviceContentAlpha(0.8f), QueuePanelMath.queueTabAlpha(0.8f), 0.001f)
        // The device tab fades out by progress 1/3.
        assertEquals(1f, QueuePanelMath.deviceTabAlpha(0f), 0.001f)
        assertEquals(0f, QueuePanelMath.deviceTabAlpha(1f / 3f), 0.001f)
        assertEquals(0f, QueuePanelMath.deviceTabAlpha(1f), 0.001f)
    }

    @Test
    fun testQueueSlideAndBlur() {
        assertEquals(0.dp, QueuePanelMath.queueSlideDp(0f))
        assertEquals((-64).dp, QueuePanelMath.queueSlideDp(1f))
        assertEquals((-32).dp, QueuePanelMath.queueSlideDp(0.5f))
        // Blur is up to 5 dp while moving, none at rest.
        assertEquals(0.dp, QueuePanelMath.blurDp(1f))
        assertEquals(5.dp, QueuePanelMath.blurDp(0f))
        assertEquals(2.5.dp, QueuePanelMath.blurDp(0.5f))
    }
}

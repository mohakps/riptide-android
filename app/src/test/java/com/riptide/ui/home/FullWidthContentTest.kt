package com.riptide.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test for FullWidthContent, the layout the Home queue panel
 * uses to measure each content layer once at its full expanded
 * width. The panel-level tests cannot observe a measured width
 * through the card's clip (the visible bounds are always the card's
 * width), so this test renders the layout in a narrow parent with
 * no clip and asserts the child is still measured at the full width.
 * That is the property that keeps the panel height constant and
 * stops the content re-wrapping mid-animation (v0.9.11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullWidthContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testChildIsMeasuredAtFullWidthInsideNarrowParent() {
        composeRule.setContent {
            Box(Modifier.width(100.dp).height(200.dp)) {
                FullWidthContent(
                    fullWidth = 300.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.fillMaxSize().testTag("child"))
                }
            }
        }
        val child = composeRule.onNodeWithTag("child").fetchSemanticsNode()
        val widthDp = child.boundsInRoot.width / composeRule.density.density
        assertEquals(300f, widthDp, 0.01f)
    }

    @Test
    fun testIntrinsicHeightIsAnsweredAtFullWidth() {
        // The row sizes itself with height(IntrinsicSize.Min): it
        // asks for the min intrinsic height at the card's current
        // (possibly collapsed) width. FullWidthContent must answer
        // at the full width. The child below is 150 dp tall at the
        // 300 dp full width (aspect 2:1) but only 50 dp tall at a
        // 100 dp card width; the row must be 150 dp tall, not 50.
        composeRule.setContent {
            Row(
                Modifier
                    .width(100.dp)
                    .height(IntrinsicSize.Min)
                    .testTag("row")
            ) {
                FullWidthContent(
                    fullWidth = 300.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f)
                            .testTag("child")
                    )
                }
            }
        }
        val row = composeRule.onNodeWithTag("row").fetchSemanticsNode()
        val heightDp = row.boundsInRoot.height / composeRule.density.density
        assertEquals(150f, heightDp, 0.01f)
    }
}

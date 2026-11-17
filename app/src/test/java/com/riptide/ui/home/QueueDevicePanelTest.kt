package com.riptide.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riptide.ui.model.BatchItem
import com.riptide.ui.model.BatchState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the v0.9.9 share crash: tapping Riptide in the
 * share menu staged a file, and the first draw of the Home queue panel
 * threw IllegalStateException ("Asking for intrinsic measurements of
 * SubcomposeLayout layouts is not supported"). The panel Row sizes
 * itself with height(IntrinsicSize.Min), and the device-info child
 * contained StackedDeviceIdentity's own BoxWithConstraints, which
 * cannot answer intrinsic measurements. Rendering the panel with the
 * real device-info content reproduces it; the panel-math unit tests
 * never draw anything, so they never caught it. Both panel states
 * are covered: the device-info content is composed and laid out even
 * while its tab is collapsed (alpha 0 does not skip layout), so the
 * queue-open render already exercises it, and the second test drives
 * the switch to device-info open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueDevicePanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun queueOf(vararg names: String): BatchState =
        BatchState(items = names.mapIndexed { i, name ->
            BatchItem(id = "id-$i", uri = "content://com.example/$i", name = name, providerSize = 1024L)
        })

    private fun setPanel(queue: BatchState) {
        composeRule.setContent {
            QueueDevicePanel(
                queue = queue,
                deviceName = "Nothing Phone (2)",
                code = "123-456",
                onCopyCode = {},
                onShowFingerprint = {},
                onViewQueue = {}
            )
        }
    }

    @Test
    fun testQueuedPanelDrawsWithoutThrowing() {
        setPanel(queueOf("photo.jpg"))
        composeRule.onNodeWithText("OUTGOING QUEUE").assertIsDisplayed()
        composeRule.onNodeWithText("01").assertIsDisplayed()
        composeRule.onNodeWithText("View queue").assertIsDisplayed()
    }

    @Test
    fun testDeviceInfoOpenDrawsWithoutThrowing() {
        setPanel(queueOf("photo.jpg"))
        composeRule.onNodeWithContentDescription("Show device info").performClick()
        // The tab settles in ~300 ms; advance past it so the device
        // tab is live and the queue tab is the collapsed one.
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithContentDescription("Show file queue").assertIsEnabled()
        composeRule.onNodeWithText("MY CODE").assertExists()
        composeRule.onNodeWithText("123-456").assertExists()
    }
}

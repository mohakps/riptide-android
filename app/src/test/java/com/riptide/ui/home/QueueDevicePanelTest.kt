package com.riptide.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.riptide.ui.model.BatchItem
import com.riptide.ui.model.BatchState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the Home queue panel.
 *
 * v0.9.9 share crash: tapping Riptide in the share menu staged a file,
 * and the first draw of the panel threw IllegalStateException
 * ("Asking for intrinsic measurements of SubcomposeLayout layouts is
 * not supported"). The panel Row sizes itself with
 * height(IntrinsicSize.Min), and the device-info child contained
 * StackedDeviceIdentity's own BoxWithConstraints, which cannot answer
 * intrinsic measurements. Rendering the panel with the real
 * device-info content reproduces it; the panel-math unit tests never
 * draw anything, so they never caught it. Both panel states are
 * covered: the device-info content is composed and laid out even
 * while its tab is collapsed (alpha 0 does not skip layout), so the
 * queue-open render already exercises it, and the second test drives
 * the switch to device-info open.
 *
 * v0.9.11 panel height / reflow / dead taps: the Row measured each
 * content at its CURRENT (animated) card width, so at the 55 dp tab
 * width the device text wrapped into many lines and the row grew to
 * ~735 dp on the phone, the content re-wrapped mid-animation, and
 * the collapsed-tab overlays (invisible, disabled clickables sitting
 * above the open content) swallowed every tap on "View queue" and on
 * the code / fingerprint tiles. A disabled clickable still consumes
 * the down event: its detectTapAndPress runs regardless of enabled.
 * The height test below cannot catch the height blow-up here
 * (Robolectric's fallback font never wraps: every text line measures
 * 36 dp, so the row is the same height at every progress pre-fix);
 * it guards the constancy property, and the width tests are the ones
 * that fail pre-fix. The absolute 188 dp at default text is the
 * pack's arithmetic (This-device 80 + gap 12 + tiles 96) and is
 * confirmed on the phone, not in this harness.
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

    private fun setPanel(
        queue: BatchState,
        onCopyCode: () -> Unit = {},
        onShowFingerprint: () -> Unit = {},
        onViewQueue: () -> Unit = {}
    ) {
        composeRule.setContent {
            QueueDevicePanel(
                queue = queue,
                deviceName = "Nothing Phone (2)",
                code = "123-456",
                onCopyCode = onCopyCode,
                onShowFingerprint = onShowFingerprint,
                onViewQueue = onViewQueue
            )
        }
    }

    /** The panel row height, read off the device tab (it fills the card). */
    private fun panelHeightDp(): Float {
        val tab = composeRule.onNodeWithContentDescription("Show device info")
            .fetchSemanticsNode()
        return tab.boundsInRoot.height / composeRule.density.density
    }

    /** How many lines a text is laid out on. A text measured at a
     * narrower width than its content wraps; the layout result is
     * the measurement itself, so the card's clip (which shrinks the
     * visible bounds) cannot hide a reflow the way bounds widths
     * would. */
    private fun lineCountOf(text: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        val node = composeRule.onNodeWithText(text).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.firstOrNull()?.lineCount ?: -1
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
        composeRule.onNodeWithContentDescription("Show device info").assertIsNotEnabled()
        composeRule.onNodeWithText("MY CODE").assertExists()
        composeRule.onNodeWithText("123-456").assertExists()
    }

    @Test
    fun testPanelHeightIsConstantAcrossProgress() {
        // Freeze the clock so the 300 ms switch animation only moves
        // when this test advances it (the rule auto-advances by
        // default, which would complete the animation in real time
        // before the mid-animation read).
        composeRule.mainClock.autoAdvance = false
        setPanel(queueOf("photo.jpg"))
        val atOpen = panelHeightDp()
        composeRule.onNodeWithContentDescription("Show device info").performClick()
        composeRule.mainClock.advanceTimeBy(60)
        val atMid = panelHeightDp()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val atDeviceOpen = panelHeightDp()
        assertEquals(atOpen, atMid, 0.01f)
        assertEquals(atOpen, atDeviceOpen, 0.01f)
    }

    @Test
    fun testContentDoesNotReflowMidAnimation() {
        composeRule.mainClock.autoAdvance = false
        setPanel(queueOf("photo.jpg"))
        // Queue open (progress 0): the device content is collapsed
        // to its 48 dp tab. Pre-fix it was measured at that width,
        // so the device name wrapped into many lines; post-fix it is
        // measured at the full wide width and stays on one line.
        assertEquals(1, lineCountOf("Nothing Phone (2)"))
        // Mid-animation: both contents must still be one line each.
        composeRule.onNodeWithContentDescription("Show device info").performClick()
        composeRule.mainClock.advanceTimeBy(60)
        assertEquals(1, lineCountOf("Nothing Phone (2)"))
        assertEquals(1, lineCountOf("Connect a device to send."))
        // Device info open (progress 1): the queue content is
        // collapsed to its tab. Pre-fix it was measured at that
        // width, so the helper wrapped; post-fix it stays one line.
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        assertEquals(1, lineCountOf("Connect a device to send."))
    }

    @Test
    fun testViewQueueTapOpensFileQueueSheet() {
        val queue = queueOf("photo.jpg")
        composeRule.setContent {
            var open by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) {
                QueueDevicePanel(
                    queue = queue,
                    deviceName = "Nothing Phone (2)",
                    code = "123-456",
                    onCopyCode = {},
                    onShowFingerprint = {},
                    onViewQueue = { open = true }
                )
                if (open) {
                    FileQueueSheet(
                        queueItems = queue.items,
                        onRemove = {},
                        onUndo = {},
                        onClose = { open = false }
                    )
                }
            }
        }
        composeRule.onNodeWithText("File queue").assertDoesNotExist()
        // A real touch at the text's centre, as the user's finger
        // does it: performClick drives the same hit-tested path in
        // this Compose version, but the touch is the faithful one.
        val viewQueue = composeRule.onNodeWithText("View queue").fetchSemanticsNode()
        composeRule.onRoot().performTouchInput { click(viewQueue.boundsInRoot.center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("File queue").assertIsDisplayed()
        composeRule.onNodeWithText("photo.jpg").assertIsDisplayed()
    }

    @Test
    fun testDeviceTilesTappableWhenDeviceInfoOpen() {
        var copyCodeClicks = 0
        var fingerprintClicks = 0
        setPanel(
            queueOf("photo.jpg"),
            onCopyCode = { copyCodeClicks++ },
            onShowFingerprint = { fingerprintClicks++ }
        )
        composeRule.onNodeWithContentDescription("Show device info").performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val codeTile = composeRule.onNodeWithContentDescription(
            "Copy verification code, 1 2 3 4 5 6"
        ).fetchSemanticsNode()
        composeRule.onRoot().performTouchInput { click(codeTile.boundsInRoot.center) }
        composeRule.waitForIdle()
        assertEquals(1, copyCodeClicks)
        val fingerprintTile = composeRule.onNodeWithContentDescription(
            "Show this device's full fingerprint"
        ).fetchSemanticsNode()
        composeRule.onRoot().performTouchInput { click(fingerprintTile.boundsInRoot.center) }
        composeRule.waitForIdle()
        assertEquals(1, fingerprintClicks)
    }
}

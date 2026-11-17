package com.riptide.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.riptide.ui.model.SavedPeer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedPeerListTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun keypadTogglesAndDeleteAccessibilityActionRemovesOnlyTheSelectedDevice() {
        val first = SavedPeer("yoga", "192.0.2.1")
        val second = SavedPeer("studio", "192.0.2.2")
        val peers = mutableStateOf(listOf(first, second))
        val joined = mutableListOf<SavedPeer>()
        val deleted = mutableListOf<SavedPeer>()
        compose.setContent {
            SavedPeerList(peers.value, onConnect = { joined.add(it) }, onForget = {
                deleted.add(it)
                peers.value = peers.value - it
            })
        }

        val options = compose.onNodeWithContentDescription("More options for yoga")
        options.performClick()
        compose.onNodeWithText("Trust").assertIsNotEnabled()
        options.performClick()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        options.performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        compose.onNodeWithText("Delete").assertIsDisplayed().assertIsEnabled()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals("Delete must not join a device", emptyList<SavedPeer>(), joined) }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Join yoga").assertDoesNotExist()
        compose.onNodeWithContentDescription("Join studio").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(first), deleted)
            assertEquals(emptyList<SavedPeer>(), joined)
        }
    }
}

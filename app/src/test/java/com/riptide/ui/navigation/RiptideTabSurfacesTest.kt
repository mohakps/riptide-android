package com.riptide.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RiptideTabSurfacesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shortDragSettlesAndQueuedFilesDisableTabSwipesButAllowSelection() {
        val tab = mutableStateOf(RiptideTab.Transfer)
        val swipeEnabled = mutableStateOf(true)
        compose.setContent {
            RiptideTabSurfaces(tab.value, { tab.value = it }, swipeEnabled.value) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(it.label) }
            }
        }

        compose.onRoot().performTouchInput {
            swipe(center, center - Offset(20.dp.toPx(), 0f))
        }
        compose.onNodeWithText("Transfer").assertIsDisplayed()
        compose.onRoot().performTouchInput { swipeLeft(startX = width * .80f, endX = width * .20f) }
        compose.onNodeWithText("Devices").assertIsDisplayed()

        compose.runOnIdle { swipeEnabled.value = false }
        compose.onRoot().performTouchInput { swipeLeft(startX = width * .80f, endX = width * .20f) }
        compose.onNodeWithText("Devices").assertIsDisplayed()
        compose.runOnIdle { tab.value = RiptideTab.Transfer }
        compose.onNodeWithText("Transfer").assertIsDisplayed()
        compose.runOnIdle { tab.value = RiptideTab.Received }
        compose.onNodeWithText("Received").assertIsDisplayed()
    }
}

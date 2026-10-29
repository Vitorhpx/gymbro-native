package com.gymbro.local

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GymBroNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun fourDestinationsAndSettingsAreReachable() {
        compose.onNodeWithText("Your daily brief", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Train").performClick()
        compose.onNodeWithText("PPL · 3 days · 45 min").assertIsDisplayed()
        compose.onNodeWithContentDescription("Eat").performClick()
        compose.onNodeWithText("Fuel the work").assertIsDisplayed()
        compose.onNodeWithContentDescription("Progress").performClick()
        compose.onNodeWithText("Consistency compounds").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings & updates").assertIsDisplayed()
        compose.onNodeWithText("Check for update").assertIsDisplayed()
    }

    @Test fun pageHeaderClearsTheSystemStatusBar() {
        val statusBarHeight = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
        val headerTop = compose.onNodeWithText("YOUR DAILY BRIEF")
            .fetchSemanticsNode()
            .boundsInRoot
            .top

        assertTrue("Header must start below the status bar", headerTop >= statusBarHeight)
    }
}

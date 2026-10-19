package com.nawzblaze.momentwise

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-launch smoke test. It does not grant Usage Access and does not claim
 * that a pause appears over another app.
 */
@RunWith(AndroidJUnit4::class)
class WelcomeLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchShowsTheWelcomeTitle() {
        composeRule.onNodeWithText("A quiet pause before distracting apps").assertIsDisplayed()
    }

    @Test
    fun taglineIsVisible() {
        composeRule.onNodeWithText("Make room for what matters.").assertIsDisplayed()
    }
}

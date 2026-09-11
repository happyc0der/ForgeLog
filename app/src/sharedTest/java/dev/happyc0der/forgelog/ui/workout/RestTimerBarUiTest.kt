package dev.happyc0der.forgelog.ui.workout

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The rest bar's labels, in the same minutes-and-seconds form as every other rest in the app.
 *
 * It printed raw seconds: a three-minute target read "Target 180s", a tap on +15 made it
 * "Target 195s", and running two minutes over read "Rest over by 130s".
 */
@RunWith(AndroidJUnit4::class)
class RestTimerBarUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(state: RestTimerUi) = composeRule.setContent {
        ForgeLogTheme {
            RestTimerBar(state = state, onPause = {}, onResume = {}, onAdjust = {}, onSkip = {})
        }
    }

    @Test
    fun aLongTargetReadsInMinutesAndSeconds() {
        show(RestTimerUi(isActive = true, remainingLabel = "2:10", targetSeconds = 195))

        composeRule.onNodeWithText("Target 3m 15s").assertIsDisplayed()
        // The countdown itself stays a clock.
        composeRule.onNodeWithText("Rest 2:10").assertIsDisplayed()
    }

    @Test
    fun anOverrunReadsInMinutesAndSeconds() {
        show(RestTimerUi(isActive = true, remainingLabel = "0:00", targetSeconds = 180, overrunSeconds = 130))

        composeRule.onNodeWithText("Rest over by 2m 10s").assertIsDisplayed()
    }
}

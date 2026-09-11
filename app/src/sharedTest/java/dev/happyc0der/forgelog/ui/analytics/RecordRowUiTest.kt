package dev.happyc0der.forgelog.ui.analytics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.domain.analytics.ExerciseRecords
import dev.happyc0der.forgelog.domain.analytics.PrCandidate
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Personal bests are held in pounds, whatever they were lifted in. Shown in kilograms they must be
 * converted, not relabelled: the list printed a 220.5 lb best as "220.5 kg".
 */
@RunWith(AndroidJUnit4::class)
class RecordRowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val best = PrCandidate(
        exerciseId = 1L,
        exerciseName = "Bench Press",
        sessionId = 1L,
        achievedAtEpochMs = 0L,
        reps = 1,
        weightLb = 220.462,
        durationSeconds = null,
        estimatedOneRepMaxLb = 220.462,
    )

    @Test
    fun recordsShownInKilogramsAreConverted() {
        composeRule.setContent {
            ForgeLogTheme {
                RecordRow(
                    records = ExerciseRecords(
                        exerciseId = 1L,
                        exerciseName = "Bench Press",
                        heaviestWeight = best,
                        bestEstimatedOneRepMax = best,
                    ),
                    weightUnit = ExerciseUnit.KG,
                )
            }
        }

        // 220.462 lb is 100 kg, not "220.5 kg".
        composeRule.onNodeWithText("Heaviest 100 kg", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("220", substring = true).assertDoesNotExist()
    }
}

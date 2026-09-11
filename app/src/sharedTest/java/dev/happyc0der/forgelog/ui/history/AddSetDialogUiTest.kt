package dev.happyc0der.forgelog.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The set editor as History's "Add set" opens it, for a set done but never logged. */
@RunWith(AndroidJUnit4::class)
class AddSetDialogUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val newSet = SetLog(
        sessionExerciseId = 1L,
        setNumber = 3,
        reps = 5,
        weight = 185.0,
        weightUnit = ExerciseUnit.LB,
        completed = true,
    )

    @Test
    fun addingASetSaysSoAndOffersNothingToDelete() {
        composeRule.setContent {
            ForgeLogTheme {
                SetEditorDialog(set = newSet, isNew = true, onSave = {}, onDelete = null, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Add set 3").assertIsDisplayed()
        composeRule.onNodeWithText("Delete set").assertDoesNotExist()
        // Ticked, since it was done.
        composeRule.onNodeWithText("Completed").assertIsOn()
    }

    @Test
    fun savingHandsBackTheSetAsTyped() {
        var saved: SetLog? = null
        composeRule.setContent {
            ForgeLogTheme {
                SetEditorDialog(set = newSet, isNew = true, onSave = { saved = it }, onDelete = null, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Save").performClick()

        assertEquals(5, saved?.reps)
        assertEquals(185.0, saved?.weight ?: 0.0, 0.0)
        assertEquals(true, saved?.completed)
    }
}

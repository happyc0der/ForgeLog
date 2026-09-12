package dev.happyc0der.forgelog.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.ui.history.SetEditorDialog
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The set editor's two choices are choices, not filters.
 *
 * They were built on the filter dropdown, which always offers a "no filter" entry first. Pressed
 * into service for a required value that entry was given the name of the default -- so the list read
 * "Working, Warmup, Working, Drop, Failure, Custom", with two identical entries doing the same thing.
 */
@RunWith(AndroidJUnit4::class)
class SetEditorDropdownUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun string(id: Int) = context.getString(id)

    private fun setLog() = SetLog(
        id = 7L,
        sessionExerciseId = 1L,
        setNumber = 2,
        setType = SetType.WORKING,
        reps = 5,
        weight = 185.0,
        weightUnit = ExerciseUnit.LB,
        completed = true,
    )

    private fun showEditor() {
        composeRule.setContent {
            ForgeLogTheme {
                SetEditorDialog(set = setLog(), onSave = {}, onDelete = {}, onDismiss = {})
            }
        }
    }

    private fun timesOnScreen(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theSetTypeDropdownOffersEachTypeOnce() {
        showEditor()
        val working = string(R.string.set_type_working)
        // Closed, the field shows the current value and nothing else does.
        assertEquals(1, timesOnScreen(working))

        composeRule.onNodeWithText(working).performClick()
        composeRule.waitForIdle()

        // Open, the field plus one entry in the menu: two, not three.
        assertEquals("Working is offered once", 2, timesOnScreen(working))
        assertEquals("every other type is there", 1, timesOnScreen(string(R.string.set_type_warmup)))
    }

    @Test
    fun theUnitDropdownOffersEachUnitOnce() {
        showEditor()
        val lb = string(R.string.unit_lb)
        assertEquals(1, timesOnScreen(lb))

        composeRule.onNodeWithText(lb).performClick()
        composeRule.waitForIdle()

        assertEquals("lb is offered once", 2, timesOnScreen(lb))
        assertEquals("kg is there", 1, timesOnScreen(string(R.string.unit_kg)))
    }
}

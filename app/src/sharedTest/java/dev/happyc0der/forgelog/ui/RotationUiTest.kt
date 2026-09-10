package dev.happyc0der.forgelog.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.ui.exercise.ExerciseForm
import dev.happyc0der.forgelog.ui.exercise.ExerciseFormState
import dev.happyc0der.forgelog.ui.exercise.ExerciseFormStateSaver
import dev.happyc0der.forgelog.ui.history.SetEditorDialog
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Turning the phone must not throw away what the user has typed.
 *
 * [StateRestorationTester] does what a rotation does to state: it saves, tears the composition down
 * and restores it, so anything held in a plain `remember` is lost exactly as it would be on a real
 * device. Both of these screens failed this before the state they hold was made saveable.
 */
@RunWith(AndroidJUnit4::class)
class RotationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    @Test
    fun theSetEditorKeepsCorrectionsAcrossARotation() {
        val restorer = StateRestorationTester(composeRule)
        restorer.setContent {
            ForgeLogTheme {
                SetEditorDialog(set = setLog(), onSave = {}, onDelete = {}, onDismiss = {})
            }
        }

        // Correct the weight, as someone would after mis-entering it. The field is addressed by
        // its label, which stays put while the value changes.
        val weightField = composeRule.onNode(hasSetTextAction() and hasText("Weight"))
        weightField.performTextClearance()
        weightField.performTextInput("205")
        weightField.assert(hasText("205"))

        restorer.emulateSavedInstanceStateRestore()

        // Eleven fields of hand-entered corrections used to be discarded by turning the phone.
        composeRule.onNode(hasSetTextAction() and hasText("Weight")).assert(hasText("205"))
        composeRule.onNode(hasSetTextAction() and hasText("Reps")).assert(hasText("5"))
    }

    @Test
    fun theNewExerciseFormKeepsWhatWasTypedAcrossARotation() {
        val restorer = StateRestorationTester(composeRule)
        restorer.setContent {
            ForgeLogTheme {
                var form by rememberSaveable(stateSaver = ExerciseFormStateSaver) {
                    mutableStateOf(ExerciseFormState())
                }
                ExerciseForm(
                    state = form,
                    onNameChange = { form = form.copy(name = it) },
                    onCategoryChange = { form = form.copy(category = it) },
                    onUnitChange = { form = form.copy(defaultUnit = it) },
                    onHowToUrlChange = { form = form.copy(howToUrl = it) },
                    onPointersChange = { form = form.copy(defaultPointers = it) },
                )
            }
        }

        composeRule.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Larsen Press")

        restorer.emulateSavedInstanceStateRestore()

        // The whole form used to be discarded, inside a sheet that closed itself as well.
        composeRule.onNode(hasSetTextAction() and hasText("Name")).assert(hasText("Larsen Press"))
    }
}

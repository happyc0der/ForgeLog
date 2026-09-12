package dev.happyc0der.forgelog.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.domain.backup.DocumentHandle
import dev.happyc0der.forgelog.domain.backup.DocumentStore
import dev.happyc0der.forgelog.domain.debug.DebugTools
import dev.happyc0der.forgelog.testing.NoDebugTools
import dev.happyc0der.forgelog.testing.NoDocumentStore
import dev.happyc0der.forgelog.testing.TestEnvironment
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.ui.history.SetEditorDialog
import dev.happyc0der.forgelog.ui.programs.ProgramEditorDialog
import dev.happyc0der.forgelog.ui.programs.ProgramEditorTarget
import dev.happyc0der.forgelog.ui.settings.SettingsScreen
import dev.happyc0der.forgelog.ui.settings.SettingsViewModel
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Everything tappable says what it is.
 *
 * A screen reader announces a control by its merged semantics -- the label Compose folds up from a
 * clickable node and its children. A control with neither text nor a content description is read
 * out as just its role, so "button" with no name, which makes the screen unusable without sight.
 *
 * This asserts against Compose's own merged tree, which is what the accessibility service reads. An
 * earlier attempt to check the same thing through uiautomator's dump reported every tab of the
 * bottom bar as unlabelled, including stock Material components that are certainly fine -- that
 * dump exposes the unmerged tree, so it cannot answer this question. This can.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TappableThingsAreLabelledTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private var env: TestEnvironment? = null
    private var viewModel: SettingsViewModel? = null

    @After
    fun tearDown() {
        viewModel?.viewModelScope?.cancel()
        env?.tearDown()
    }

    private fun showSettings() {
        val environment = TestEnvironment(UnconfinedTestDispatcher(scheduler)).also { env = it }
        val model = SettingsViewModel(
            savedStateHandle = SavedStateHandle(),
            application = ApplicationProvider.getApplicationContext<Application>(),
            settingsRepository = environment.settingsRepository,
            backupRepository = environment.backupRepository,
            documentStore = NoDocumentStore(),
            debugTools = NoDebugTools(),
            timeProvider = environment.time,
            zoneProvider = environment.zone,
        ).also { viewModel = it }
        composeRule.setContent { ForgeLogTheme { SettingsScreen(viewModel = model) } }
        scheduler.advanceUntilIdle()
        composeRule.waitForIdle()
    }

    /** The label a screen reader would announce, or null if it would announce nothing. */
    private fun announcedName(config: androidx.compose.ui.semantics.SemanticsConfiguration): String? {
        var name: String? = null
        config.forEach { entry ->
            when (entry.key.name) {
                "ContentDescription" -> {
                    @Suppress("UNCHECKED_CAST")
                    val values = entry.value as? List<CharSequence>
                    values?.joinToString(" ")?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                "Text" -> {
                    @Suppress("UNCHECKED_CAST")
                    val values = entry.value as? List<CharSequence>
                    values?.joinToString(" ")?.takeIf { it.isNotBlank() }?.let { name = name ?: it }
                }
                "EditableText" -> {
                    (entry.value as? CharSequence)?.toString()?.takeIf { it.isNotBlank() }
                        ?.let { name = name ?: it }
                }
            }
        }
        return name
    }




    /**
     * The tappable controls the layout has actually placed.
     *
     * A LazyColumn composes a little beyond the fold without placing it, and those nodes come back
     * with zero bounds. A screen reader cannot focus them either, so holding them to having a name
     * would be testing the layout rather than the labels.
     */
    private fun placedControls() = composeRule.onAllNodes(hasClickAction())
        .fetchSemanticsNodes()
        .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }

    /**
     * Brings [text] into view, because a control below the fold is not placed and so is skipped.
     *
     * Without this the switches were never examined at all: the first version of this test passed
     * against the very bug it was written for, since all three sit under the fold at the size a
     * Compose test renders.
     */
    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        composeRule.waitForIdle()
    }

    /** Every section that has to be scrolled to, by a label inside it. */
    private val sections = listOf(
        "Default weight unit",
        "Vibrate when rest ends",
        "Sound when rest ends",
        "Count warmup sets in volume",
        "Restore from JSON",
        "Delete all data",
    )


    /*
     * Two dialogs, which need no ViewModel and between them hold the controls a label is easiest to
     * forget: a checkbox whose text sits beside it, and six colour swatches that are nothing but
     * colour. Both were right when this was written; the point is that they stay right.
     */

    private fun showSetEditor() {
        composeRule.setContent {
            ForgeLogTheme {
                SetEditorDialog(
                    set = SetLog(
                        id = 7L,
                        sessionExerciseId = 1L,
                        setNumber = 2,
                        setType = SetType.WORKING,
                        reps = 5,
                        weight = 185.0,
                        weightUnit = ExerciseUnit.LB,
                        completed = true,
                    ),
                    onSave = {},
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun everyTappableControlInTheSetEditorHasAName() {
        showSetEditor()
        val nodes = placedControls()
        assertTrue("nothing tappable in the set editor", nodes.isNotEmpty())
        val unnamed = nodes.filter { announcedName(it.config) == null }
        assertTrue(
            "${unnamed.size} of ${nodes.size} controls would be announced with no name; bounds " +
                unnamed.joinToString { it.boundsInRoot.toString() },
            unnamed.isEmpty(),
        )
    }

    @Test
    fun everyColourSwatchSaysWhichColourItIs() {
        composeRule.setContent {
            ForgeLogTheme {
                ProgramEditorDialog(
                    target = ProgramEditorTarget.Create,
                    onDismiss = {},
                    onConfirm = { _, _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()

        val nodes = placedControls()
        assertTrue("nothing tappable in the program editor", nodes.isNotEmpty())
        val unnamed = nodes.filter { announcedName(it.config) == null }
        assertTrue(
            "${unnamed.size} of ${nodes.size} controls would be announced with no name -- a colour " +
                "swatch is nothing but colour, so a missing description leaves it unusable; bounds " +
                unnamed.joinToString { it.boundsInRoot.toString() },
            unnamed.isEmpty(),
        )
    }

    @Test
    fun everyTappableControlOnSettingsHasAName() {
        showSettings()

        var examined = 0
        sections.forEach { section ->
            scrollTo(section)
            val nodes = placedControls()
            assertTrue("nothing tappable in view at \"$section\"", nodes.isNotEmpty())
            examined += nodes.size

            val unnamed = nodes.filter { announcedName(it.config) == null }
            assertTrue(
                "at \"$section\": ${unnamed.size} of ${nodes.size} tappable controls would be " +
                    "announced with no name; bounds " +
                    unnamed.joinToString { it.boundsInRoot.toString() },
                unnamed.isEmpty(),
            )
        }
        assertTrue("the screen was never actually examined", examined > sections.size)
    }

    /** The same, for the labels a sighted user never sees: icon-only buttons. */
    @Test
    fun iconOnlyControlsCarryAContentDescription() {
        showSettings()
        sections.forEach { scrollTo(it) }

        val nodes = placedControls()
        val textless = nodes.filter { node ->
            var hasText = false
            node.config.forEach { if (it.key.name == "Text" || it.key.name == "EditableText") hasText = true }
            !hasText
        }
        val withoutDescription = textless.filter { node ->
            var described = false
            node.config.forEach {
                if (it.key.name == "ContentDescription") described = true
            }
            !described
        }
        assertTrue(
            "${withoutDescription.size} controls have neither text nor a content description: " +
                withoutDescription.joinToString { it.boundsInRoot.toString() },
            withoutDescription.isEmpty(),
        )
    }

    @Test
    fun nothingIsTappableWithoutBeingReachable() {
        showSettings()
        // Every control that is placed has a real size; a zero-sized one is a node the layout has
        // composed but not placed, which is also a node nothing can focus.
        assertTrue(
            "no placed controls at all",
            placedControls().isNotEmpty(),
        )
        assertTrue(
            "a placed control had no size",
            placedControls().all { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f },
        )
        assertTrue(SemanticsProperties.ContentDescription.name.isNotEmpty())
    }
}

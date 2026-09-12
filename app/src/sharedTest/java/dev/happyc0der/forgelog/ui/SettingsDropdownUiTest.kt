package dev.happyc0der.forgelog.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.backup.DocumentHandle
import dev.happyc0der.forgelog.domain.backup.DocumentStore
import dev.happyc0der.forgelog.domain.debug.DebugTools
import dev.happyc0der.forgelog.testing.TestEnvironment
import dev.happyc0der.forgelog.ui.settings.SettingsScreen
import dev.happyc0der.forgelog.ui.settings.SettingsViewModel
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class NoDebugTools : DebugTools {
    override val isAvailable = false
    override suspend fun seedSampleData() = Unit
}

private class NoDocumentStore : DocumentStore {
    override suspend fun readText(handle: DocumentHandle) = Result.success("")
    override suspend fun writeText(handle: DocumentHandle, content: String) = Result.success(Unit)
}

/**
 * Settings' dropdowns are choices, not filters.
 *
 * They were built on the filter dropdown, which always offers a "no filter" entry first. Given a
 * required value that entry was handed the default's own name, so every list on the screen carried
 * the default twice: "lb, lb, kg", "sec, sec, min", and a week starting "Monday, Monday, Tuesday,
 * ...". The same mistake had already been found and fixed in the set editor, which is why this is
 * tested rather than just corrected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsDropdownUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /*
     * As in WorkoutFlowUiTest: the ViewModel keeps the real main dispatcher, which the Compose rule
     * drives, and only the repositories run on a test dispatcher whose scheduler the test pumps.
     * Putting viewModelScope on a StandardTestDispatcher instead left the screen showing nothing
     * but its title, because nothing here advances that scheduler.
     */
    private val scheduler = TestCoroutineScheduler()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun string(id: Int) = context.getString(id)

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
        composeRule.setContent {
            ForgeLogTheme { SettingsScreen(viewModel = model) }
        }
        scheduler.advanceUntilIdle()
        composeRule.waitForIdle()
    }

    private fun timesOnScreen(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    /** Open the dropdown showing [current], then count what the menu offers. */
    private fun openingShows(current: String, other: String) {
        showSettings()
        assertEquals("$current is the field's value and nothing else", 1, timesOnScreen(current))

        composeRule.onNodeWithText(current).performClick()
        composeRule.waitForIdle()

        // The field plus one entry in the menu: two, not three.
        assertEquals("$current is offered once", 2, timesOnScreen(current))
        assertEquals("$other is offered once", 1, timesOnScreen(other))
    }

    @Test
    fun theWeightUnitDropdownOffersEachUnitOnce() =
        openingShows(current = string(R.string.unit_lb), other = string(R.string.unit_kg))

    @Test
    fun theWeekStartDropdownOffersEachDayOnce() {
        showSettings()
        // Settings is a LazyColumn, so the week card does not exist until it is scrolled into view.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(string(R.string.settings_week_start)))
        composeRule.waitForIdle()
        assertEquals("Monday is the field's value and nothing else", 1, timesOnScreen("Monday"))

        composeRule.onNodeWithText("Monday").performClick()
        composeRule.waitForIdle()

        assertEquals("Monday is offered once", 2, timesOnScreen("Monday"))
        assertEquals("Sunday is offered once", 1, timesOnScreen("Sunday"))
        assertEquals("Wednesday is offered once", 1, timesOnScreen("Wednesday"))
    }
}

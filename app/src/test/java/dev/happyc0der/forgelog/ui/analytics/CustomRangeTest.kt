package dev.happyc0der.forgelog.ui.analytics

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Analytics used to compare the current week against a week N weeks back and nothing else, so an
 * arbitrary span — a training block, a deload, a month — could not be looked at at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomRangeTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<AnalyticsViewModel>()
    private var benchId = 0L

    /** 2024-03-15 12:00 UTC, comfortably inside a month with no DST change in Asia/Kolkata. */
    private val marchFifteenth = 1_710_504_000_000L
    private val dayMs = 24L * 60L * 60L * 1000L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.zone.zoneId = ZoneId.of("Asia/Kolkata")
        env.time.now = marchFifteenth
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel() = AnalyticsViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        workoutSessionRepository = env.sessionRepository,
        exerciseRepository = env.exerciseRepository,
        settingsRepository = env.settingsRepository,
        timeProvider = env.time,
        zoneProvider = env.zone,
    ).also(created::add)

    private suspend fun completedSession(at: Long, weight: Double) {
        env.time.now = at
        val sessionId = env.sessionRepository.startSession(
            programId = null,
            programDayId = null,
            sessionName = "Push",
            exercises = listOf(
                SessionStartExercise(
                    exercise = Exercise(
                        id = benchId,
                        name = "Bench Press",
                        category = ExerciseCategory.PUSH,
                        defaultUnit = ExerciseUnit.LB,
                        howToUrl = null,
                        defaultPointers = null,
                        isArchived = false,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                ),
            ),
        )
        val sessionExerciseId = env.sessionRepository.getSessionDetail(sessionId)!!
            .exercises.single().exercise.id
        env.sessionRepository.upsertSetLog(
            SetLog(
                sessionExerciseId = sessionExerciseId,
                setNumber = 1,
                reps = 5,
                weight = weight,
                weightUnit = ExerciseUnit.LB,
                completed = true,
                completedAt = at,
            ),
        )
        env.time.now = at + 3_600_000L
        env.sessionRepository.completeSession(sessionId)
        env.time.now = marchFifteenth
    }

    @Test
    fun `a custom range counts only the sessions inside it`() = runTest {
        completedSession(at = marchFifteenth - 20 * dayMs, weight = 185.0)
        completedSession(at = marchFifteenth - 2 * dayMs, weight = 195.0)
        completedSession(at = marchFifteenth - 1 * dayMs, weight = 205.0)

        val vm = viewModel()
        // The three days ending today: the last two sessions, not the one from three weeks back.
        vm.setCustomRange(marchFifteenth - 3 * dayMs, marchFifteenth + dayMs)
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitUntil { it.isCustomRange && it.comparison != null }
            assertEquals(2, state.comparison!!.current.sessionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the baseline is the equally long span immediately before`() = runTest {
        // Inside the chosen window.
        completedSession(at = marchFifteenth - 2 * dayMs, weight = 205.0)
        // Inside the window immediately before it.
        completedSession(at = marchFifteenth - 5 * dayMs, weight = 185.0)
        completedSession(at = marchFifteenth - 6 * dayMs, weight = 180.0)

        val vm = viewModel()
        val until = marchFifteenth + dayMs
        vm.setCustomRange(until - 4 * dayMs, until)
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitUntil { it.isCustomRange && it.comparison != null }
            assertEquals(1, state.comparison!!.current.sessionCount)
            assertEquals(2, state.comparison!!.previous.sessionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a week preset leaves the custom range behind`() = runTest {
        val vm = viewModel()
        vm.setCustomRange(marchFifteenth - 10 * dayMs, marchFifteenth)
        advanceUntilIdle()
        vm.uiState.test {
            awaitUntil { it.isCustomRange }
            cancelAndIgnoreRemainingEvents()
        }

        vm.setComparisonOffset(2)
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitUntil { !it.isCustomRange }
            assertFalse(state.isCustomRange)
            assertEquals(2, state.comparisonOffset)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a backwards range is refused rather than producing a negative window`() = runTest {
        val vm = viewModel()
        vm.setCustomRange(marchFifteenth, marchFifteenth - dayMs)
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitUntil { it.comparison != null }
            assertFalse("a backwards range must not take effect", state.isCustomRange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the range is reported back so the screen can name it`() = runTest {
        val vm = viewModel()
        val from = marchFifteenth - 7 * dayMs
        val until = marchFifteenth + dayMs
        vm.setCustomRange(from, until)
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitUntil { it.isCustomRange && it.currentRange != null }
            // currentRange and previousRange were computed and never read by anything before this.
            assertEquals(from, state.currentRange!!.start)
            assertEquals(until, state.currentRange!!.endExclusive)
            assertEquals(from, state.previousRange!!.endExclusive)
            assertTrue(state.previousRange!!.start < from)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    repeat(40) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("no emission matched after 40 items")
}

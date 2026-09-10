package dev.happyc0der.forgelog.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.programExerciseEntity
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The logger had no tests at all, on the screen a workout is actually spent in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveWorkoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<ActiveWorkoutViewModel>()
    private var benchId = 0L
    private var sessionId = 0L
    private var sessionExerciseId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 1_000_000L
        val programDao = env.database.programDao()
        val programId = programDao.insertProgram(programEntity(name = "PPL"))
        val dayId = programDao.insertDay(dayEntity(programId = programId, name = "Push"))
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        programDao.insertProgramExercise(
            programExerciseEntity(
                programDayId = dayId,
                exerciseId = benchId,
                plannedSets = 3,
                targetRepMin = 8,
                targetWeight = 135.0,
                targetRestSeconds = 120,
            ),
        )
        sessionId = env.sessionRepository.startSession(
            programId = programId,
            programDayId = dayId,
            sessionName = "PPL · Push",
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
                    plannedSets = 3,
                    targetRepMin = 8,
                    targetWeight = 135.0,
                    targetRestSeconds = 120,
                ),
            ),
        )
        sessionExerciseId = env.sessionRepository.getSessionDetail(sessionId)!!
            .exercises.single().exercise.id
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel() = ActiveWorkoutViewModel(
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        application = ApplicationProvider.getApplicationContext<Application>(),
        workoutSessionRepository = env.sessionRepository,
        exerciseRepository = env.exerciseRepository,
        timeProvider = env.time,
        settingsRepository = env.settingsRepository,
    ).also(created::add)

    private suspend fun sets() = env.sessionRepository.getSessionDetail(sessionId)!!
        .exercises.single().sets

    @Test
    fun `added sets are numbered in sequence`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { it.detail != null }
            cancelAndIgnoreRemainingEvents()
        }

        vm.addSet(sessionExerciseId)
        vm.addSet(sessionExerciseId)
        vm.addSet(sessionExerciseId)
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), sets().map { it.setNumber })
    }

    @Test
    fun `two taps in the same frame do not produce two sets with one number`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { it.detail != null }
            cancelAndIgnoreRemainingEvents()
        }

        // Exactly the double tap that used to duplicate: uiState has not caught up between them.
        vm.addSet(sessionExerciseId)
        vm.addSet(sessionExerciseId)
        advanceUntilIdle()

        val numbers = sets().map { it.setNumber }
        assertEquals("expected distinct set numbers, got $numbers", numbers.size, numbers.distinct().size)
    }

    @Test
    fun `the first set is prefilled from the plan when there is no history`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { it.detail != null }
            cancelAndIgnoreRemainingEvents()
        }

        vm.addSet(sessionExerciseId)
        advanceUntilIdle()

        val first = sets().single()
        assertEquals(8, first.reps)
        assertEquals(135.0, first.weight ?: 0.0, 0.001)
    }

    @Test
    fun `feeling and notes can be recorded during the session`() = runTest {
        val vm = viewModel()
        vm.onOverallFeeling(4)
        vm.onOverallNotes("  Felt strong  ")
        advanceUntilIdle()

        val session = env.sessionRepository.getSession(sessionId)!!
        assertEquals(4, session.overallFeeling)
        // Trimmed, and still in progress — recording how it is going does not end it.
        assertEquals("Felt strong", session.overallNotes)
        assertEquals(SessionStatus.IN_PROGRESS, session.status)
    }

    @Test
    fun `blank notes clear the field rather than storing whitespace`() = runTest {
        val vm = viewModel()
        vm.onOverallNotes("something")
        advanceUntilIdle()
        vm.onOverallNotes("   ")
        advanceUntilIdle()

        assertNull(env.sessionRepository.getSession(sessionId)!!.overallNotes)
    }

    @Test
    fun `deleting a set removes it and leaves the others`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { it.detail != null }
            cancelAndIgnoreRemainingEvents()
        }
        vm.addSet(sessionExerciseId)
        vm.addSet(sessionExerciseId)
        advanceUntilIdle()
        val toDelete = sets().first()

        vm.deleteSet(toDelete.id)
        advanceUntilIdle()

        val remaining = sets()
        assertEquals(1, remaining.size)
        assertTrue(remaining.none { it.id == toDelete.id })
    }

    @Test
    fun `a pending edit does not resurrect a deleted set`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { it.detail != null }
            cancelAndIgnoreRemainingEvents()
        }
        vm.addSet(sessionExerciseId)
        advanceUntilIdle()
        val set = sets().single()

        // Type into the set, then delete it before the debounce fires.
        vm.onSetText(set, ActiveWorkoutViewModel.FIELD_REPS, "12")
        vm.deleteSet(set.id)
        advanceUntilIdle()

        assertTrue("the debounced write must not bring the row back", sets().isEmpty())
    }

    /*
     * Two things this test must not do, both because FakeTimeProvider is frozen while the test's
     * virtual clock runs, so a started rest timer can never reach zero:
     *
     *  - no advanceUntilIdle, which would chase a countdown that never ends; and
     *  - no leaving the ViewModel alive past the test body, because the rest-completion ticker
     *    stays pending on the shared scheduler and runTest's own cleanup drains it, hanging the
     *    whole run rather than failing it.
     *
     * Cancelling here rather than only in @After is what keeps that from happening.
     */
    @Test
    fun `rest can be started without completing a set`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            val before = awaitUntil { it.detail != null }
            // No set has been ticked, so nothing should be counting down yet.
            assertTrue(!before.restTimer.isActive)

            vm.startRestTimer(sessionExerciseId)

            val state = awaitUntil { it.restTimer.isActive }
            // The plan asked for 120s of rest, and that is what the manual start uses.
            assertEquals(120, state.restTimer.targetSeconds)
            cancelAndIgnoreRemainingEvents()
        }
        vm.viewModelScope.cancel()
    }

    @Test
    fun `finishing completes the session and reports it`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.finish()
            advanceUntilIdle()
            assertEquals(ActiveWorkoutEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(SessionStatus.COMPLETED, env.sessionRepository.getSession(sessionId)!!.status)
    }

    @Test
    fun `abandoning is reported separately from finishing`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.abandon()
            advanceUntilIdle()
            assertEquals(ActiveWorkoutEvent.Abandoned, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val session = env.sessionRepository.getSession(sessionId)!!
        assertEquals(SessionStatus.ABANDONED, session.status)
        assertNull(session.completedAt)
    }

    /*
     * There is deliberately no "a failed write is reported" test here.
     *
     * A write against a closed database does not throw an ordinary exception: Room cancels the
     * calling coroutine, so what surfaces is a JobCancellationException. launchSafely rethrows
     * cancellation on purpose — a cancelled coroutine is control flow, not a failure to show the
     * user — so TestEnvironment.breakDatabase cannot provoke the guard on a write path at all.
     * The guard is real and still worth having; this setup simply cannot demonstrate it, and
     * saying so beats a test that passes for the wrong reason.
     */
}

private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    repeat(40) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("no emission matched after 40 items")
}

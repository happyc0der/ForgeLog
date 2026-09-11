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
import dev.happyc0der.forgelog.domain.model.SessionExercise
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.workout.SetInputField
import dev.happyc0der.forgelog.domain.workout.SetsLeft
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import dev.happyc0der.forgelog.workout.ActiveRest
import dev.happyc0der.forgelog.workout.NoRestStateStore
import dev.happyc0der.forgelog.workout.RestAlert
import dev.happyc0der.forgelog.workout.RestStateStore
import dev.happyc0der.forgelog.workout.RestTimerController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * A fresh countdown holder, as a new process would have. On runTest's background scope, which
     * is cancelled with the test, so a countdown still pending cannot hang it.
     */
    private fun TestScope.newRestTimer(
        store: RestStateStore = NoRestStateStore,
        alert: RestAlert = RestAlert {},
    ) = RestTimerController(backgroundScope, env.time, alert, store = store)

    /** Stands in for the saved rest a real process leaves behind. */
    private class InMemoryRestStore : RestStateStore {
        var saved: ActiveRest? = null
        override fun load(): ActiveRest? = saved
        override fun save(rest: ActiveRest?) {
            saved = rest
        }
    }

    private fun TestScope.viewModel(restTimer: RestTimerController = newRestTimer()) = ActiveWorkoutViewModel(
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
        application = ApplicationProvider.getApplicationContext<Application>(),
        workoutSessionRepository = env.sessionRepository,
        exerciseRepository = env.exerciseRepository,
        programRepository = env.programRepository,
        timeProvider = env.time,
        settingsRepository = env.settingsRepository,
        restTimerController = restTimer,
    ).also(created::add)

    private suspend fun sets() = env.sessionRepository.getSessionDetail(sessionId)!!
        .exercises.single().sets

    @Test
    fun `a timed exercise with a planned weight shows its weight, in the default unit`() = runTest {
        env.settingsRepository.setDefaultWeightUnit(ExerciseUnit.KG)
        val walkId = env.database.exerciseDao().upsert(
            exerciseEntity(name = "Farmer's walk", defaultUnit = ExerciseUnit.SECONDS),
        )
        val walkEntryId = env.sessionRepository.upsertSessionExercise(
            SessionExercise(
                sessionId = sessionId,
                exerciseId = walkId,
                displayNameSnapshot = "Farmer's walk",
                exerciseOrder = 1,
                startedAt = env.time.now,
                targetWeight = 60.0,
                targetDurationSeconds = 40,
            ),
        )
        val vm = viewModel()
        vm.uiState.test {
            val state = awaitUntil { current ->
                current.exercises.any { it.item.exercise.id == walkEntryId && it.targetWeightUnit == ExerciseUnit.KG }
            }
            val walk = state.exercises.single { it.item.exercise.id == walkEntryId }
            assertTrue(vm.isFieldVisible(walk.unit, walk.revealedFields, walk.fieldsInUse, SetInputField.WEIGHT))
            assertTrue(vm.isFieldVisible(walk.unit, walk.revealedFields, walk.fieldsInUse, SetInputField.DURATION))
            assertFalse(vm.isFieldVisible(walk.unit, walk.revealedFields, walk.fieldsInUse, SetInputField.REPS))
            // A loaded lift's planned weight stays in its own unit.
            val bench = state.exercises.single { it.item.exercise.id == sessionExerciseId }
            assertEquals(ExerciseUnit.LB, bench.targetWeightUnit)
            cancelAndIgnoreRemainingEvents()
        }
    }

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

    /** A finished bench session before today's: [reps] at [weight] for each set. */
    private suspend fun lastBench(weight: Double, vararg reps: Int) {
        val earlier = env.sessionRepository.startSession(
            programId = null,
            programDayId = null,
            sessionName = "Earlier",
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
        val entryId = env.sessionRepository.getSessionDetail(earlier)!!.exercises.single().exercise.id
        reps.forEachIndexed { index, count ->
            env.sessionRepository.upsertSetLog(
                dev.happyc0der.forgelog.domain.model.SetLog(
                    sessionExerciseId = entryId,
                    setNumber = index + 1,
                    reps = count,
                    weight = weight,
                    weightUnit = ExerciseUnit.LB,
                    completed = true,
                    completedAt = env.time.now,
                ),
            )
        }
        env.sessionRepository.completeSession(earlier)
    }

    @Test
    fun `a lift that hit every rep last time suggests the next weight until today's set is at it`() = runTest {
        // Today's plan: 3 x 8 at 135. Last time: all three sets reached 8 at 135.
        lastBench(135.0, 8, 8, 8)
        val vm = viewModel()
        try {
            vm.uiState.test {
                val hint = awaitUntil { it.exercises.singleOrNull()?.progression != null }
                    .exercises.single().progression!!
                assertEquals(listOf(140.0), hint.options)
                assertEquals(135.0, hint.fromWeight, 0.0)

                vm.addSet(sessionExerciseId)
                val set = awaitUntil { it.exercises.single().item.sets.isNotEmpty() }
                    .exercises.single().item.sets.single()
                // Prefilled from the plan, 135: still worth saying.
                assertEquals(135.0, set.weight ?: 0.0, 0.0)

                vm.onSetText(set, ActiveWorkoutViewModel.FIELD_WEIGHT, "140")
                awaitUntil { it.exercises.single().progression == null }
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a lift that fell short last time suggests nothing`() = runTest {
        lastBench(135.0, 8, 8, 7)
        val vm = viewModel()
        try {
            vm.uiState.test {
                val state = awaitUntil { it.exercises.singleOrNull()?.previous != null }
                assertNull(state.exercises.single().progression)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `reps typed and a type chosen within the autosave delay are both saved`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val set = addSets(vm, sessionExerciseId, count = 1).single()

        vm.onSetText(set, ActiveWorkoutViewModel.FIELD_REPS, "12")
        vm.onSetType(set, SetType.WARMUP)
        advanceUntilIdle()

        val saved = sets().single()
        assertEquals(12, saved.reps)
        assertEquals(SetType.WARMUP, saved.setType)
    }

    @Test
    fun `a unit changed right after a weight is typed keeps the weight`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val set = addSets(vm, sessionExerciseId, count = 1).single()

        vm.onSetText(set, ActiveWorkoutViewModel.FIELD_WEIGHT, "60")
        vm.onSetUnit(set, ExerciseUnit.KG)
        advanceUntilIdle()

        val saved = sets().single()
        assertEquals(60.0, saved.weight ?: 0.0, 0.0)
        assertEquals(ExerciseUnit.KG, saved.weightUnit)
    }

    /** A superset: no rest planned after this lift. */
    private suspend fun planNoRest() {
        val entry = env.sessionRepository.getSessionDetail(sessionId)!!.exercises.single().exercise
        env.sessionRepository.upsertSessionExercise(entry.copy(targetRestSeconds = 0))
    }

    @Test
    fun `a set with no rest planned ends the rest before it and starts none`() = runTest {
        val vm = viewModel()
        try {
            loaded(vm)
            val (first, second) = addSets(vm, sessionExerciseId, count = 2)
            vm.uiState.test {
                vm.onSetCompleted(first, true)
                // The planned 120 s rest after the first set...
                awaitUntil { it.restTimer.isActive }

                planNoRest()
                vm.onSetCompleted(second, true)
                // ...is over once the next set is done, and none follows it.
                awaitUntil { !it.restTimer.isActive }
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `the rest button, where none is planned, rests for the default`() = runTest {
        planNoRest()
        env.settingsRepository.setDefaultRestSeconds(75)
        val vm = viewModel()
        try {
            loaded(vm)
            vm.startRestTimer(sessionExerciseId)
            runCurrent()
            vm.uiState.test {
                val state = awaitUntil { it.restTimer.isActive }
                assertEquals(75, state.restTimer.targetSeconds)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.viewModelScope.cancel()
        }
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
    /*
     * A killed process rebuilds a running countdown from the last set's completedAt, which is a
     * column. This is the ordinary case, not an exotic one: the phone is face down on the bench
     * for two minutes, which is precisely when Android reclaims the app.
     */

    @Test
    fun `a rest still running is picked back up by a fresh ViewModel`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { it.detail != null }
            cancelAndIgnoreRemainingEvents()
        }
        vm.addSet(sessionExerciseId)
        advanceUntilIdle()
        val set = sets().single()
        // runCurrent, not advanceUntilIdle: completing a set starts a countdown, and the fake
        // clock never lets it finish, so advancing time would chase it forever.
        vm.onSetCompleted(set, true)
        runCurrent()
        vm.viewModelScope.cancel()

        // 30 seconds into a 120-second planned rest, the process dies and a new one starts.
        env.time.now += 30_000L
        val restarted = viewModel()

        restarted.uiState.test {
            val state = awaitUntil { it.restTimer.isActive }
            assertEquals(120, state.restTimer.targetSeconds)
            assertEquals("1:30", state.restTimer.remainingLabel)
            cancelAndIgnoreRemainingEvents()
        }
        restarted.viewModelScope.cancel()
    }

    /**
     * A skipped rest came back after the process died: the logger rebuilt a running countdown from
     * the last set, and its alarm buzzed for a rest the user had already moved on from.
     */
    @Test
    fun `a rest skipped before the process died is not rebuilt`() = runTest {
        val store = InMemoryRestStore()
        val vm = viewModel(newRestTimer(store = store))
        vm.uiState.test {
            awaitUntil { it.detail != null }
            cancelAndIgnoreRemainingEvents()
        }
        vm.addSet(sessionExerciseId)
        advanceUntilIdle()
        vm.onSetCompleted(sets().single(), true)
        runCurrent()
        vm.skipRestTimer()
        runCurrent()
        vm.viewModelScope.cancel()

        // 30 seconds into the 120-second rest -- which a rebuild would have restarted -- the process
        // dies and a new one starts, with what the old one saved.
        env.time.now += 30_000L
        val restarted = viewModel(newRestTimer(store = store))
        restarted.uiState.test {
            val state = awaitUntil { it.detail != null }
            runCurrent()
            assertFalse(state.restTimer.isActive)
            // Nor after the rebuild had its chance to run.
            assertFalse(restarted.uiState.value.restTimer.isActive)
            cancelAndIgnoreRemainingEvents()
        }
        restarted.viewModelScope.cancel()
    }

    @Test
    fun `a rest that already elapsed is not resurrected`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { it.detail != null }
            cancelAndIgnoreRemainingEvents()
        }
        vm.addSet(sessionExerciseId)
        advanceUntilIdle()
        vm.onSetCompleted(sets().single(), true)
        runCurrent()
        vm.viewModelScope.cancel()

        // Reopened the next morning.
        env.time.now += 12L * 60L * 60L * 1000L
        val restarted = viewModel()

        restarted.uiState.test {
            val state = awaitUntil { it.detail != null }
            assertFalse("yesterday's rest must not come back", state.restTimer.isActive)
            cancelAndIgnoreRemainingEvents()
        }
        restarted.viewModelScope.cancel()
    }

    /*
     * Rest is recorded on the set it came after. It used to land one set late: ticking Set 2 wrote
     * the gap since Set 1 onto Set 2, so Set 1 never had a measured rest and the rest after the last
     * set of an exercise was lost. The rest after Set 1 is only known once Set 2 is ticked, so that
     * is when it is written -- onto Set 1.
     */

    /** Adds [count] sets to [exerciseId] and returns them in set order. */
    private suspend fun kotlinx.coroutines.test.TestScope.addSets(
        vm: ActiveWorkoutViewModel,
        exerciseId: Long,
        count: Int,
    ): List<dev.happyc0der.forgelog.domain.model.SetLog> {
        repeat(count) { vm.addSet(exerciseId) }
        advanceUntilIdle()
        return env.sessionRepository.getSessionDetail(sessionId)!!
            .exercises.single { it.exercise.id == exerciseId }
            .sets.sortedBy { it.setNumber }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.loaded(vm: ActiveWorkoutViewModel) {
        vm.uiState.test {
            awaitUntil { it.detail != null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun status() = env.sessionRepository.getSession(sessionId)!!.status

    @Test
    fun `finishing with planned sets left asks first`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val (first) = addSets(vm, sessionExerciseId, count = 1)
        vm.onSetCompleted(first, true)
        runCurrent()

        vm.requestFinish()
        advanceUntilIdle()

        // Three planned, one done.
        assertEquals(listOf(SetsLeft("Bench Press", 2)), vm.finishPrompt.value)
        assertEquals(SessionStatus.IN_PROGRESS, status())

        vm.confirmFinish()
        advanceUntilIdle()
        assertNull(vm.finishPrompt.value)
        assertEquals(SessionStatus.COMPLETED, status())
    }

    @Test
    fun `keep going closes the question and the workout carries on`() = runTest {
        val vm = viewModel()
        loaded(vm)

        vm.requestFinish()
        advanceUntilIdle()
        vm.dismissFinishPrompt()
        advanceUntilIdle()

        assertNull(vm.finishPrompt.value)
        assertEquals(SessionStatus.IN_PROGRESS, status())
    }

    @Test
    fun `finishing with every set done does not ask`() = runTest {
        val vm = viewModel()
        loaded(vm)
        addSets(vm, sessionExerciseId, count = 3).forEach { set ->
            vm.onSetCompleted(set, true)
            runCurrent()
        }

        vm.requestFinish()
        advanceUntilIdle()

        assertNull(vm.finishPrompt.value)
        assertEquals(SessionStatus.COMPLETED, status())
    }

    @Test
    fun `a Done tapped just before Finish counts as done`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val sets = addSets(vm, sessionExerciseId, count = 3)
        sets.take(2).forEach { set ->
            vm.onSetCompleted(set, true)
            runCurrent()
        }

        // The last tick has not been written when Finish is tapped.
        vm.onSetCompleted(sets[2], true)
        vm.requestFinish()
        advanceUntilIdle()

        assertNull(vm.finishPrompt.value)
        assertEquals(SessionStatus.COMPLETED, status())
    }

    @Test
    fun `ticking a set records the rest that just ended on the set before it`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val (first, second) = addSets(vm, sessionExerciseId, count = 2)

        vm.onSetCompleted(first, true)
        runCurrent()
        env.time.now += 95_000L
        vm.onSetCompleted(second, true)
        runCurrent()
        vm.viewModelScope.cancel()

        val after = sets().sortedBy { it.setNumber }
        assertEquals("the 95 s gap is the rest after set 1", 95, after[0].restAfterSetSeconds)
        // Nothing has come after set 2 yet, so it still holds the planned 120, not a measurement.
        assertEquals(120, after[1].restAfterSetSeconds)
    }

    @Test
    fun `the first set ticked in a session records no rest anywhere`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val (first, second) = addSets(vm, sessionExerciseId, count = 2)

        vm.onSetCompleted(first, true)
        runCurrent()
        vm.viewModelScope.cancel()

        val after = sets().sortedBy { it.setNumber }
        assertEquals(120, after[0].restAfterSetSeconds)
        assertEquals(120, after[1].restAfterSetSeconds)
        assertEquals(second.id, after[1].id)
    }

    @Test
    fun `the gap to the next exercise is the rest after the last set of the previous one`() = runTest {
        val plankId = env.sessionRepository.upsertSessionExercise(
            dev.happyc0der.forgelog.domain.model.SessionExercise(
                sessionId = sessionId,
                displayNameSnapshot = "Plank",
                exerciseOrder = 1,
                startedAt = env.time.now,
            ),
        )
        val vm = viewModel()
        loaded(vm)
        val bench = addSets(vm, sessionExerciseId, count = 1).single()
        val plank = addSets(vm, plankId, count = 1).single()

        vm.onSetCompleted(bench, true)
        runCurrent()
        env.time.now += 180_000L
        vm.onSetCompleted(plank, true)
        runCurrent()
        vm.viewModelScope.cancel()

        val benchAfter = env.sessionRepository.getSessionDetail(sessionId)!!
            .exercises.single { it.exercise.id == sessionExerciseId }.sets.single()
        assertEquals(180, benchAfter.restAfterSetSeconds)
    }

    @Test
    fun `the hold of a timed set is not counted as the rest before it`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val (first, second) = addSets(vm, sessionExerciseId, count = 2)
        env.sessionRepository.upsertSetLog(second.copy(durationSeconds = 45))

        vm.onSetCompleted(first, true)
        runCurrent()
        env.time.now += 105_000L
        // Handed the stale row, as a recomposition that has not caught up would: the duration has
        // to come from the database.
        vm.onSetCompleted(second, true)
        runCurrent()
        vm.viewModelScope.cancel()

        assertEquals(60, sets().sortedBy { it.setNumber }[0].restAfterSetSeconds)
    }

    @Test
    fun `a duration typed just before ticking is saved and used`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val (first, second) = addSets(vm, sessionExerciseId, count = 2)
        vm.onSetCompleted(first, true)
        runCurrent()
        env.time.now += 100_000L

        // Ticked inside the autosave delay: the typed value exists only as a draft.
        vm.onSetText(second, ActiveWorkoutViewModel.FIELD_DURATION, "40")
        vm.onSetCompleted(second, true)
        runCurrent()
        vm.viewModelScope.cancel()

        val after = sets().sortedBy { it.setNumber }
        assertEquals(40, after[1].durationSeconds)
        assertTrue(after[1].completed)
        assertEquals(60, after[0].restAfterSetSeconds)
    }

    @Test
    fun `ticking a set that was just deleted does not bring it back`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val set = addSets(vm, sessionExerciseId, count = 1).single()
        env.sessionRepository.deleteSetLog(set.id)

        // The row on screen has not caught up with the delete yet.
        vm.onSetCompleted(set, true)
        runCurrent()
        vm.viewModelScope.cancel()

        assertTrue(sets().isEmpty())
    }

    @Test
    fun `unticking the set that started the rest stops the rest`() = runTest {
        val vm = viewModel()
        try {
            loaded(vm)
            val set = addSets(vm, sessionExerciseId, count = 1).single()
            vm.uiState.test {
                vm.onSetCompleted(set, true)
                awaitUntil { it.restTimer.isActive }

                vm.onSetCompleted(sets().single(), false)
                // A mis-tapped set must not leave rest running.
                awaitUntil { !it.restTimer.isActive }
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            // Always, even when an assertion fails: a live countdown hangs runTest's cleanup.
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `unticking an earlier set leaves the running rest alone`() = runTest {
        val vm = viewModel()
        try {
            loaded(vm)
            val (first, second) = addSets(vm, sessionExerciseId, count = 2)
            vm.uiState.test {
                vm.onSetCompleted(first, true)
                awaitUntil { it.restTimer.isActive }
                env.time.now += 90_000L
                vm.onSetCompleted(second, true)
                runCurrent()

                vm.onSetCompleted(sets().sortedBy { it.setNumber }[0], false)
                val state = awaitUntil { ui ->
                    ui.exercises.flatMap { it.item.sets }.any { it.id == first.id && !it.completed }
                }
                assertTrue(state.restTimer.isActive)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /*
     * Day notes and exercise notes were written in the day builder and then shown nowhere else,
     * so a day's warm-up was out of sight during the workout it was written for.
     */
    @Test
    fun `the program day's notes and the exercise's plan notes are shown while logging`() = runTest {
        val dayId = env.sessionRepository.getSession(sessionId)!!.programDayId!!
        val day = env.programRepository.getDayDetail(dayId)!!
        env.programRepository.upsertDay(day.day.copy(notes = "Warm-up: bike 5 min"))
        env.programRepository.upsertProgramExercise(
            day.exercises.single().programExercise.copy(notes = "Last time: 3x7 at 20 lb"),
        )

        val vm = viewModel()
        vm.uiState.test {
            val state = awaitUntil { it.detail != null && it.dayNotes != null }
            assertEquals("Warm-up: bike 5 min", state.dayNotes)
            assertEquals("Last time: 3x7 at 20 lb", state.exercises.single().planNotes)

            // Read live: an edit made mid-session shows up without restarting it.
            env.programRepository.upsertDay(day.day.copy(notes = "Warm-up: row 4 min"))
            assertEquals("Warm-up: row 4 min", awaitUntil { it.dayNotes == "Warm-up: row 4 min" }.dayNotes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /*
     * The rest outlives the logger. It used to live in the logger's ViewModel, so leaving the
     * logger mid-rest -- to check History, say -- killed the countdown and its alert; on the test
     * device a 30-second rest ran out on the Home screen and nothing buzzed.
     */
    @Test
    fun `rest still alerts when it runs out after the logger is gone`() = runTest {
        var alerts = 0
        val restTimer = newRestTimer { alerts++ }
        val vm = viewModel(restTimer)
        loaded(vm)
        val set = addSets(vm, sessionExerciseId, count = 1).single()
        vm.onSetCompleted(set, true)
        runCurrent()

        // Leaving the logger destroys its ViewModel.
        vm.viewModelScope.cancel()
        env.time.now += 121_000L // the plan's rest is 120 s
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(1, alerts)
    }

    @Test
    fun `an adjusted rest is still adjusted after leaving the logger and coming back`() = runTest {
        val restTimer = newRestTimer()
        val vm = viewModel(restTimer)
        loaded(vm)
        val set = addSets(vm, sessionExerciseId, count = 1).single()
        vm.onSetCompleted(set, true)
        runCurrent()
        vm.adjustRestTimer(-60)
        vm.viewModelScope.cancel()

        val reopened = viewModel(restTimer)
        try {
            reopened.uiState.test {
                val state = awaitUntil { it.restTimer.isActive }
                // Not rebuilt at the planned 120 s: the -60 the user asked for stands.
                assertEquals(60, state.restTimer.targetSeconds)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            reopened.viewModelScope.cancel()
        }
    }

    @Test
    fun `finishing the workout ends its rest`() = runTest {
        var alerts = 0
        val restTimer = newRestTimer { alerts++ }
        val vm = viewModel(restTimer)
        loaded(vm)
        val set = addSets(vm, sessionExerciseId, count = 1).single()
        vm.onSetCompleted(set, true)
        runCurrent()

        vm.finish()
        runCurrent()
        vm.viewModelScope.cancel()
        env.time.now += 121_000L
        advanceTimeBy(2_000L)
        runCurrent()

        assertNull(restTimer.current(sessionId))
        assertEquals("no buzz for a workout that is over", 0, alerts)
    }

    @Test
    fun `sets ticked off in a row after the fact keep their planned rest`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val (first, second, third) = addSets(vm, sessionExerciseId, count = 3)

        // The workout is over and the user is catching up: three ticks, a second or two apart.
        vm.onSetCompleted(first, true)
        env.time.now += 1_500L
        vm.onSetCompleted(second, true)
        env.time.now += 1_500L
        vm.onSetCompleted(third, true)
        runCurrent()
        vm.viewModelScope.cancel()

        val after = sets().sortedBy { it.setNumber }
        assertTrue(after.all { it.completed })
        // Not "rest 1s": nothing was measured, so each keeps the planned 120 s.
        assertEquals(listOf(120, 120, 120), after.map { it.restAfterSetSeconds })
    }

    @Test
    fun `ticks in quick succession are measured one after another`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val (first, second, third) = addSets(vm, sessionExerciseId, count = 3)
        vm.onSetCompleted(first, true)
        runCurrent()

        // Both launched before either has run: the second must see the first's write.
        env.time.now += 60_000L
        vm.onSetCompleted(second, true)
        env.time.now += 30_000L
        vm.onSetCompleted(third, true)
        runCurrent()
        vm.viewModelScope.cancel()

        val after = sets().sortedBy { it.setNumber }
        assertEquals(60, after[0].restAfterSetSeconds)
        // Measured from set 2, which the second tick could only see because it waited: racing,
        // it measured from set 1 as well, overwrote set 1 with 90 and left set 2 unmeasured.
        assertEquals(30, after[1].restAfterSetSeconds)
    }

    @Test
    fun `unticking a set leaves the recorded rest alone`() = runTest {
        val vm = viewModel()
        loaded(vm)
        val (first, second) = addSets(vm, sessionExerciseId, count = 2)
        vm.onSetCompleted(first, true)
        runCurrent()
        env.time.now += 75_000L
        vm.onSetCompleted(second, true)
        runCurrent()

        // Unticking set 2 must not wipe the rest measured for set 1, nor set 2's own value.
        vm.onSetCompleted(sets().sortedBy { it.setNumber }[1], false)
        runCurrent()
        vm.viewModelScope.cancel()

        val after = sets().sortedBy { it.setNumber }
        assertEquals(75, after[0].restAfterSetSeconds)
        assertEquals(120, after[1].restAfterSetSeconds)
        assertFalse(after[1].completed)
        assertNull(after[1].completedAt)
    }

}

private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    repeat(40) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("no emission matched after 40 items")
}

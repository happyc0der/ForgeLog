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
import dev.happyc0der.forgelog.domain.model.hasTargets
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The planner is the link that carries a program's plan into a live session.
 *
 * It was broken for a long time without anything noticing, because the repository tests handed
 * `startSession` its targets directly and so never asked where the planner got them from. Five of
 * the six targets were silently dropped when a day was loaded. These tests start from the program
 * row and assert on the session row, so nothing in between can quietly discard the plan again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartWorkoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<StartWorkoutViewModel>()
    private var programId = 0L
    private var dayId = 0L
    private var benchId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 10_000L
        val programDao = env.database.programDao()
        programId = programDao.insertProgram(programEntity(name = "PPL"))
        dayId = programDao.insertDay(dayEntity(programId = programId, name = "Push Day"))
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        programDao.insertProgramExercise(
            programExerciseEntity(
                programDayId = dayId,
                exerciseId = benchId,
                plannedSets = 4,
                targetRepMin = 6,
                targetRepMax = 8,
                targetWeight = 185.0,
                targetDurationSeconds = 45,
                targetRestSeconds = 150,
            ),
        )
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel(dayIdArg: Long = dayId, adHoc: Boolean = false) = StartWorkoutViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf("programDayId" to dayIdArg, "adHoc" to adHoc),
        ),
        application = ApplicationProvider.getApplicationContext<Application>(),
        programRepository = env.programRepository,
        exerciseRepository = env.exerciseRepository,
        workoutSessionRepository = env.sessionRepository,
        settingsRepository = env.settingsRepository,
    ).also(created::add)

    @Test
    fun `loading a day carries every planned target into the roster`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            val loaded = awaitUntil { it.roster.isNotEmpty() }
            val item = loaded.roster.single()

            // All six, not just rest: this is the assertion that was missing.
            assertEquals(4, item.plannedSets)
            assertEquals(6, item.targetRepMin)
            assertEquals(8, item.targetRepMax)
            assertEquals(185.0, item.targetWeight ?: 0.0, 0.001)
            assertEquals(45, item.targetDurationSeconds)
            assertEquals(150, item.targetRestSeconds)
            assertTrue(item.hasTargets)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A drag reads its indices from the list the screen last drew. Skipping an exercise in the same
     * frame leaves them pointing past the end, which used to throw out of a gesture callback and
     * take the whole session's setup with it.
     */
    @Test
    fun `a move past the end of the roster is ignored rather than thrown`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            val loaded = awaitUntil { it.roster.isNotEmpty() }
            val before = loaded.roster.map { it.localId }

            vm.moveExercise(before.size, 0)
            vm.moveExercise(0, before.size)
            vm.moveExercise(-1, 0)
            advanceUntilIdle()

            assertEquals(before, vm.uiState.value.roster.map { it.localId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the planner shows the day's notes and each exercise's plan notes`() = runTest {
        val programDao = env.database.programDao()
        val notedDay = programDao.insertDay(
            dayEntity(programId = programId, name = "Legs", notes = "Warm-up: bike 4 min"),
        )
        programDao.insertProgramExercise(
            programExerciseEntity(programDayId = notedDay, exerciseId = benchId, notes = "Last time: 3x7"),
        )

        viewModel(dayIdArg = notedDay).uiState.test {
            val loaded = awaitUntil { it.roster.isNotEmpty() }
            assertEquals("Warm-up: bike 4 min", loaded.dayNotes)
            assertEquals("Last time: 3x7", loaded.roster.single().planNotes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `starting the day writes every planned target onto the session`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { it.roster.isNotEmpty() }
            cancelAndIgnoreRemainingEvents()
        }

        var startedId: Long? = null
        vm.events.test {
            vm.confirmStart()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue("expected Started, got $event", event is StartWorkoutEvent.Started)
            startedId = (event as StartWorkoutEvent.Started).sessionId
            cancelAndIgnoreRemainingEvents()
        }

        val logged = env.sessionRepository.getSessionDetail(startedId!!)!!.exercises.single().exercise
        assertEquals(4, logged.plannedSets)
        assertEquals(6, logged.targetRepMin)
        assertEquals(8, logged.targetRepMax)
        assertEquals(185.0, logged.targetWeight ?: 0.0, 0.001)
        assertEquals(45, logged.targetDurationSeconds)
        assertEquals(150, logged.targetRestSeconds)
    }

    @Test
    fun `two quick taps on start begin one workout`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitUntil { it.roster.isNotEmpty() }
            cancelAndIgnoreRemainingEvents()
        }

        vm.confirmStart()
        vm.confirmStart()
        advanceUntilIdle()

        val sessions = env.database.backupDao().allSessions()
        assertEquals(1, sessions.size)
    }

    @Test
    fun `a taken progression suggestion becomes today's target`() = runTest {
        val vm = viewModel()
        var localId = 0L
        vm.uiState.test {
            localId = awaitUntil { it.roster.isNotEmpty() }.roster.single().localId
            cancelAndIgnoreRemainingEvents()
        }

        vm.applyProgression(localId, 190.0)
        advanceUntilIdle()

        vm.uiState.test {
            val item = awaitUntil { it.roster.singleOrNull()?.targetWeight == 190.0 }.roster.single()
            // The weight field is told to start again from the new value.
            assertEquals(1, item.targetsRevision)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an edit in the planner overrides the program for this session only`() = runTest {
        val vm = viewModel()
        var localId = 0L
        vm.uiState.test {
            localId = awaitUntil { it.roster.isNotEmpty() }.roster.single().localId
            cancelAndIgnoreRemainingEvents()
        }

        vm.setTarget(localId, TargetField.WEIGHT, 165.0)
        advanceUntilIdle()

        var startedId: Long? = null
        vm.events.test {
            vm.confirmStart()
            advanceUntilIdle()
            startedId = (awaitItem() as StartWorkoutEvent.Started).sessionId
            cancelAndIgnoreRemainingEvents()
        }

        val logged = env.sessionRepository.getSessionDetail(startedId!!)!!.exercises.single().exercise
        assertEquals(165.0, logged.targetWeight ?: 0.0, 0.001)
        // The template keeps what it always said.
        val template = env.programRepository.getDayDetail(dayId)!!.exercises.single().programExercise
        assertEquals(185.0, template.targetWeight ?: 0.0, 0.001)
    }

    @Test
    fun `an exercise added mid-plan has no targets rather than inherited ones`() = runTest {
        val vm = viewModel(adHoc = true, dayIdArg = 0L)
        vm.addExercise(benchId)
        advanceUntilIdle()

        vm.uiState.test {
            val loaded = awaitUntil { it.roster.isNotEmpty() }
            val item = loaded.roster.single()
            assertNull(item.plannedSets)
            assertNull(item.targetWeight)
            assertNull(item.targetRestSeconds)
            assertFalse(item.hasTargets)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a day that no longer exists reports an error rather than crashing`() = runTest {
        val vm = viewModel(dayIdArg = 9_999L)
        vm.uiState.test {
            val state = awaitUntil { it.errorMessage != null }
            assertNotNull(state.errorMessage)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a database failure while loading a day is reported, not thrown`() = runTest {
        env.breakDatabase()
        val vm = viewModel()
        advanceUntilIdle()

        vm.uiState.test {
            val state = awaitUntil { it.errorMessage != null }
            assertNotNull(state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/** Waits for the first emission satisfying [predicate], so a test need not count intermediates. */
private suspend fun <T> app.cash.turbine.ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    repeat(40) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("no emission matched after 40 items")
}

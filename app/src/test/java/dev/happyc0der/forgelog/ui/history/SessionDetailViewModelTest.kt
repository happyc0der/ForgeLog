package dev.happyc0der.forgelog.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<SessionDetailViewModel>()

    private var sessionId = 0L
    private var sessionExerciseId = 0L
    private var setId = 0L

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.time.now = 50_000L
        val dao = env.database.workoutSessionDao()
        val benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
        sessionId = dao.insertSession(
            sessionEntity(
                sessionName = "Push Day",
                startedAt = 1_000L,
                completedAt = 3_601_000L,
                status = SessionStatus.COMPLETED,
            ),
        )
        sessionExerciseId = dao.insertSessionExercise(
            sessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = benchId,
                displayNameSnapshot = "Bench Press",
            ),
        )
        setId = dao.upsertSetLog(
            setLogEntity(sessionExerciseId = sessionExerciseId, reps = 5, weight = 135.0),
        )
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel(id: Long = sessionId): SessionDetailViewModel = SessionDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to id)),
        application = ApplicationProvider.getApplicationContext<Application>(),
        workoutSessionRepository = env.sessionRepository,
        settingsRepository = env.settingsRepository,
    ).also(created::add)

    @Test
    fun `the session and its summary load`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()
            assertEquals("Push Day", state.detail?.session?.sessionName)
            assertEquals(3_600_000L, state.summary?.durationMs)
            assertEquals(675.0, state.summary?.loadLb ?: 0.0, 0.001)
            assertEquals(1, state.summary?.totalSets)
            assertFalse(state.isMissing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a session that does not exist is reported as missing, not as an error`() = runTest {
        val vm = viewModel(id = 9_999L)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertTrue(state.isMissing)
            assertNull(state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `edit mode toggles`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()
            assertFalse(state.isEditing)

            vm.toggleEditing()
            while (!state.isEditing) state = awaitItem()
            assertTrue(state.isEditing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `overall feeling round-trips to the database`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()

            vm.setOverallFeeling(4)
            while (state.detail?.session?.overallFeeling != 4) state = awaitItem()
            assertEquals(4, state.summary?.overallFeeling)

            // Selecting the same value again clears it, which the UI models as a toggle.
            vm.setOverallFeeling(null)
            while (state.detail?.session?.overallFeeling != null) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `overall notes are trimmed, and blank notes become null`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()

            vm.setOverallNotes("  felt strong  ")
            while (state.detail?.session?.overallNotes == null) state = awaitItem()
            assertEquals("felt strong", state.detail?.session?.overallNotes)

            vm.setOverallNotes("   ")
            while (state.detail?.session?.overallNotes != null) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `per-exercise feeling and notes are written`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()

            vm.setExerciseFeeling(sessionExerciseId, 5)
            vm.setExerciseNotes(sessionExerciseId, "elbow twinge")
            while (state.detail?.exercises?.first()?.exercise?.feeling != 5 ||
                state.detail?.exercises?.first()?.exercise?.exerciseNotes == null
            ) {
                state = awaitItem()
            }
            assertEquals("elbow twinge", state.detail?.exercises?.first()?.exercise?.exerciseNotes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing a set rewrites it and the summary follows`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()
            val original = state.detail!!.exercises.first().sets.first()

            vm.saveSet(original.copy(reps = 8, weight = 145.0, rpe = 9, notes = "last set"))
            while (state.detail?.exercises?.first()?.sets?.first()?.reps != 8) state = awaitItem()

            val updated = state.detail!!.exercises.first().sets.first()
            assertEquals(8, updated.reps)
            assertEquals(145.0, updated.weight ?: 0.0, 0.001)
            assertEquals(9, updated.rpe)
            assertEquals("last set", updated.notes)
            assertEquals(8 * 145.0, state.summary?.loadLb ?: 0.0, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `marking a set incomplete removes it from the summary`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()
            val original = state.detail!!.exercises.first().sets.first()

            vm.saveSet(original.copy(completed = false))
            while (state.summary?.totalSets != 0) state = awaitItem()
            assertEquals(0.0, state.summary?.loadLb ?: -1.0, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing a set's unit is reflected in volume`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()
            val original = state.detail!!.exercises.first().sets.first()

            vm.saveSet(original.copy(weight = 60.0, weightUnit = ExerciseUnit.KG, reps = 5))
            while (state.detail?.exercises?.first()?.sets?.first()?.weightUnit != ExerciseUnit.KG) {
                state = awaitItem()
            }
            // 5 × 60 kg, normalised to pounds.
            assertEquals(5 * 60.0 * 2.2046226218, state.summary?.loadLb ?: 0.0, 0.01)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a warmup set stops counting towards volume`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()
            val original = state.detail!!.exercises.first().sets.first()

            vm.saveSet(original.copy(setType = SetType.WARMUP))
            while (state.summary?.totalSets != 0) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a set removes it`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.detail == null) state = awaitItem()

            vm.deleteSet(setId)
            while (state.detail?.exercises?.first()?.sets?.isNotEmpty() != false) state = awaitItem()
            assertEquals(0, state.detail?.exercises?.first()?.sets?.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting the session emits Deleted`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.deleteSession()
            advanceUntilIdle()
            assertEquals(SessionDetailEvent.Deleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(env.sessionRepository.getSessionDetail(sessionId))
    }

    @Test
    fun `repeating emits the new session id`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.repeatSession()
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is SessionDetailEvent.RepeatStarted)
            assertNotNull(env.sessionRepository.getInProgressSession())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repeating is refused while a session is already running`() = runTest {
        env.database.workoutSessionDao().insertSession(
            sessionEntity(
                sessionName = "Running",
                startedAt = 9_000L,
                completedAt = null,
                status = SessionStatus.IN_PROGRESS,
            ),
        )
        val vm = viewModel()
        vm.events.test {
            vm.repeatSession()
            advanceUntilIdle()
            assertTrue(awaitItem() is SessionDetailEvent.Message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing database surfaces an error state`() = runTest {
        env.breakDatabase()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.errorMessage == null) state = awaitItem()
            assertNotNull(state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

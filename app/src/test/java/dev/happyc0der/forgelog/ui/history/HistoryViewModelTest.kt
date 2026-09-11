package dev.happyc0der.forgelog.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.dayEntity
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.programEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<HistoryViewModel>()
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private var pplId = 0L
    private var pushDayId = 0L
    private var benchId = 0L

    /** Wednesday 11 March 2026, 18:00 local. */
    private val now = LocalDateTime.of(2026, 3, 11, 18, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.zone.zoneId = zone
        env.time.now = now
        pplId = env.database.programDao().insertProgram(programEntity(name = "PPL Strength"))
        pushDayId = env.database.programDao().insertDay(dayEntity(programId = pplId, name = "Push Day"))
        benchId = env.database.exerciseDao().upsert(exerciseEntity(name = "Bench Press"))
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel(): HistoryViewModel = HistoryViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        workoutSessionRepository = env.sessionRepository,
        programRepository = env.programRepository,
        settingsRepository = env.settingsRepository,
        timeProvider = env.time,
        zoneProvider = env.zone,
    ).also(created::add)

    private fun epochAt(day: Int, hour: Int = 12): Long =
        LocalDateTime.of(2026, 3, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private suspend fun session(
        name: String,
        day: Int,
        status: SessionStatus = SessionStatus.COMPLETED,
        programId: Long? = null,
        programDayId: Long? = null,
        exerciseId: Long? = null,
        exerciseName: String = "Bench Press",
    ): Long {
        val dao = env.database.workoutSessionDao()
        val sessionId = dao.insertSession(
            sessionEntity(
                sessionName = name,
                startedAt = epochAt(day, 17),
                completedAt = if (status == SessionStatus.IN_PROGRESS) null else epochAt(day, 18),
                status = status,
                programId = programId,
                programDayId = programDayId,
            ),
        )
        val sessionExerciseId = dao.insertSessionExercise(
            sessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                displayNameSnapshot = exerciseName,
            ),
        )
        dao.upsertSetLog(setLogEntity(sessionExerciseId = sessionExerciseId, reps = 5, weight = 100.0))
        return sessionId
    }

    private suspend fun seedThree() {
        session("PPL Strength · Push Day", day = 11, programId = pplId, programDayId = pushDayId, exerciseId = benchId)
        session("Ad-hoc workout", day = 10, exerciseName = "Nordic Curl")
        session("Quit early", day = 9, status = SessionStatus.ABANDONED)
    }

    @Test
    fun `all sessions are listed newest first and summarised`() = runTest {
        seedThree()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.rows.size < 3) state = awaitItem()
            assertEquals(
                listOf("PPL Strength · Push Day", "Ad-hoc workout", "Quit early"),
                state.rows.map { it.summary.sessionName },
            )
            assertEquals(500.0, state.rows.first().summary.loadLb, 0.001)
            assertEquals(1, state.rows.first().summary.totalSets)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searching narrows by session name`() = runTest {
        seedThree()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.rows.size < 3) state = awaitItem()

            vm.onQueryChange("Ad-hoc")
            while (state.rows.size != 1) state = awaitItem()
            assertEquals("Ad-hoc workout", state.rows.single().summary.sessionName)
            assertTrue(state.isFilterActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searching also matches exercise names`() = runTest {
        seedThree()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.rows.size < 3) state = awaitItem()

            vm.onQueryChange("Nordic")
            while (state.rows.size != 1) state = awaitItem()
            assertEquals("Ad-hoc workout", state.rows.single().summary.sessionName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `status filter narrows the list`() = runTest {
        seedThree()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.rows.size < 3) state = awaitItem()

            vm.onStatusSelected(SessionStatus.ABANDONED)
            while (state.rows.size != 1) state = awaitItem()
            assertEquals("Quit early", state.rows.single().summary.sessionName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a program clears a stale day selection`() = runTest {
        seedThree()
        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            vm.onProgramSelected(pplId)
            vm.onDaySelected(pushDayId)
            var state = awaitItem()
            while (state.filter.programDayId == null) state = awaitItem()

            // Switching program must not leave a day from the old program filtering to nothing.
            vm.onProgramSelected(null)
            while (state.filter.programId != null) state = awaitItem()
            assertEquals(null, state.filter.programDayId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the this-week preset covers the current calendar week`() = runTest {
        seedThree()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.rows.size < 3) state = awaitItem()

            // Monday the 9th through Wednesday the 11th are all in this week.
            vm.onPresetSelected(DateRangePreset.THIS_WEEK)
            while (state.filter.fromEpochMs == null) state = awaitItem()
            assertEquals(3, state.rows.size)

            vm.onPresetSelected(DateRangePreset.LAST_WEEK)
            while (state.rows.isNotEmpty()) state = awaitItem()
            assertEquals(0, state.rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `this week starts on the day Settings says, as Home and Analytics do`() = runTest {
        // Wednesday the 11th. With a Sunday start the week began on the 8th; with Monday, the 9th.
        session(name = "Sunday", day = 8)
        session(name = "Wednesday", day = 11)
        env.settingsRepository.setWeekStartDay(java.time.DayOfWeek.SUNDAY)
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.rows.size < 2) state = awaitItem()

            vm.onPresetSelected(DateRangePreset.THIS_WEEK)
            while (state.filter.fromEpochMs == null) state = awaitItem()
            assertEquals(listOf("Sunday", "Wednesday"), state.rows.map { it.summary.sessionName }.sorted())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the last date choice wins when two come in quick succession`() = runTest {
        seedThree()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.rows.size < 3) state = awaitItem()

            // Both before either has run: "This week" must not land after "clear".
            vm.onPresetSelected(DateRangePreset.THIS_WEEK)
            vm.clearFilters()
            advanceUntilIdle()

            // Nothing new may have been emitted -- clearing restored the defaults -- so read the
            // current value rather than waiting for an emission.
            state = vm.uiState.value
            assertEquals(DateRangePreset.ALL_TIME, state.preset)
            assertEquals(null, state.filter.fromEpochMs)
            assertEquals(3, state.rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing filters restores the full list`() = runTest {
        seedThree()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.rows.size < 3) state = awaitItem()

            vm.onStatusSelected(SessionStatus.ABANDONED)
            while (state.rows.size != 1) state = awaitItem()

            vm.clearFilters()
            while (state.rows.size != 3) state = awaitItem()
            assertTrue(!state.isFilterActive)
            assertEquals(DateRangePreset.ALL_TIME, state.preset)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `logged exercises are offered for filtering, and only once each`() = runTest {
        session("One", day = 11, exerciseId = benchId)
        session("Two", day = 10, exerciseId = benchId)
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.loggedExercises.isEmpty()) state = awaitItem()
            assertEquals(listOf("Bench Press"), state.loggedExercises.map { it.displayName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a session removes it from the list`() = runTest {
        val sessionId = session("Doomed", day = 11)
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.rows.isEmpty()) state = awaitItem()

            vm.deleteSession(sessionId)
            while (state.rows.isNotEmpty()) state = awaitItem()
            assertEquals(0, state.rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repeating a session emits the new session id`() = runTest {
        val sessionId = session("Push Day", day = 11, programId = pplId, exerciseId = benchId)
        val vm = viewModel()

        vm.events.test {
            vm.repeatSession(sessionId)
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is HistoryEvent.RepeatStarted)
            assertNotNull((event as HistoryEvent.RepeatStarted).sessionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repeating is refused while another session is already running`() = runTest {
        session("Running", day = 11, status = SessionStatus.IN_PROGRESS)
        val target = session("Push Day", day = 10)
        val vm = viewModel()

        vm.events.test {
            vm.repeatSession(target)
            advanceUntilIdle()
            assertTrue(awaitItem() is HistoryEvent.Message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repeating a missing session reports a message instead of crashing`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.repeatSession(9_999L)
            advanceUntilIdle()
            assertTrue(awaitItem() is HistoryEvent.Message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving a session as a program day reports success`() = runTest {
        val sessionId = session("Push Day", day = 11, programId = pplId, exerciseId = benchId)
        val vm = viewModel()

        vm.events.test {
            vm.saveAsProgramDay(sessionId, pplId, "Push Day B")
            advanceUntilIdle()
            assertTrue(awaitItem() is HistoryEvent.Message)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, env.programRepository.getProgramDetail(pplId)!!.days.size)
    }

    @Test
    fun `a failing save reports a message rather than crashing the view model`() = runTest {
        val sessionId = session("Push Day", day = 11, programId = pplId, exerciseId = benchId)
        val vm = viewModel()

        vm.events.test {
            // Program 9999 does not exist, so the repository throws inside the coroutine.
            vm.saveAsProgramDay(sessionId, 9_999L, "Nope")
            advanceUntilIdle()
            assertTrue(awaitItem() is HistoryEvent.Message)
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

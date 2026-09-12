package dev.happyc0der.forgelog.ui.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
import dev.happyc0der.forgelog.domain.home.DayPart
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.testing.MainDispatcherRule
import dev.happyc0der.forgelog.data.local.DatabaseRecoveryLog
import dev.happyc0der.forgelog.data.local.NoDatabaseRecoveryLog
import dev.happyc0der.forgelog.data.local.UnreadableDatabase
import dev.happyc0der.forgelog.testing.TestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<HomeViewModel>()
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    /** Wednesday 11 March 2026, 18:00 local. The Monday week it belongs to starts on the 9th. */
    private val wednesdayEvening =
        LocalDateTime.of(2026, 3, 11, 18, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.zone.zoneId = zone
        env.time.now = wednesdayEvening
    }

    @After
    fun tearDown() {
        // viewModelScope outlives the test scope, so its infinite clocks must be stopped by hand.
        created.forEach { it.viewModelScope.cancel() }
        created.clear()
        env.tearDown()
    }

    private fun viewModel(
        recoveryLog: DatabaseRecoveryLog = NoDatabaseRecoveryLog,
    ): HomeViewModel = HomeViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        programRepository = env.programRepository,
        workoutSessionRepository = env.sessionRepository,
        settingsRepository = env.settingsRepository,
        timeProvider = env.time,
        zoneProvider = env.zone,
        databaseRecoveryLog = recoveryLog,
    ).also(created::add)

    /** Remembers one loss, as the real one does across a process restart. */
    private class RecordedLoss(private var value: UnreadableDatabase?) : DatabaseRecoveryLog {
        var reported = false
            private set

        override fun record(unreadable: UnreadableDatabase) {
            value = unreadable
        }

        override fun unreported(): UnreadableDatabase? = value

        override fun markReported() {
            value = null
            reported = true
        }
    }

    private suspend fun logSession(
        name: String,
        startedAt: Long,
        completedAt: Long?,
        status: SessionStatus = SessionStatus.COMPLETED,
        sets: List<Triple<Double, Int, SetType>> = listOf(Triple(100.0, 5, SetType.WORKING)),
        feeling: Int? = null,
    ): Long {
        val dao = env.database.workoutSessionDao()
        val sessionId = dao.insertSession(
            sessionEntity(
                sessionName = name,
                startedAt = startedAt,
                completedAt = completedAt,
                status = status,
                overallFeeling = feeling,
            ),
        )
        val exerciseId = dao.insertSessionExercise(sessionExerciseEntity(sessionId = sessionId))
        sets.forEachIndexed { index, (weight, reps, type) ->
            dao.upsertSetLog(
                setLogEntity(
                    sessionExerciseId = exerciseId,
                    setNumber = index + 1,
                    setType = type,
                    weight = weight,
                    reps = reps,
                ),
            )
        }
        return sessionId
    }

    private fun epochAt(day: Int, hour: Int): Long =
        LocalDateTime.of(2026, 3, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `starts loading and settles into content`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem().isLoading)
            val settled = awaitItem()
            assertTrue(!settled.isLoading)
            assertNull(settled.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `greeting and date come from the injected clock and zone`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            val state = awaitItem()
            assertEquals(DayPart.EVENING, state.dayPart)
            assertEquals(LocalDate.of(2026, 3, 11), state.today)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasPrograms reflects the library and ignores archived programs`() = runTest {
        env.programRepository.upsertProgram(
            WorkoutProgram(name = "PPL", description = null, color = "#A855F7", createdAt = 0, updatedAt = 0),
        )
        val archivedId = env.programRepository.upsertProgram(
            WorkoutProgram(name = "Old", description = null, color = "#A855F7", createdAt = 0, updatedAt = 0),
        )
        env.programRepository.setArchived(archivedId, true)

        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            assertTrue(awaitItem().hasPrograms)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the last completed workout is summarised`() = runTest {
        logSession(
            name = "Push Day",
            startedAt = epochAt(10, 17),
            completedAt = epochAt(10, 18),
            sets = listOf(Triple(100.0, 5, SetType.WORKING), Triple(100.0, 5, SetType.WORKING)),
            feeling = 4,
        )

        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            val last = awaitItem().lastWorkout
            assertNotNull(last)
            assertEquals("Push Day", last?.sessionName)
            assertEquals(3_600_000L, last?.durationMs)
            assertEquals(1_000.0, last?.loadLb ?: 0.0, 0.001)
            assertEquals(2, last?.totalSets)
            assertEquals(4, last?.overallFeeling)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an in-progress session is surfaced with a live elapsed label`() = runTest {
        logSession(
            name = "Pull Day",
            startedAt = wednesdayEvening - 90_000L,
            completedAt = null,
            status = SessionStatus.IN_PROGRESS,
        )

        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            var state = awaitItem()
            while (state.inProgress == null) state = awaitItem()
            assertEquals("Pull Day", state.inProgress?.sessionName)
            // 90 seconds in, formatted by the shared elapsed formatter.
            assertEquals("1:30", state.elapsedLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with no session running the elapsed label is inert`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            val state = awaitItem()
            assertNull(state.inProgress)
            assertEquals("0:00", state.elapsedLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the weekly summary counts only this calendar week`() = runTest {
        // Monday the 9th and Wednesday the 11th are in the week; Sunday the 8th is the week before.
        logSession("Monday", epochAt(9, 17), epochAt(9, 18))
        logSession("Wednesday", epochAt(11, 9), epochAt(11, 10))
        logSession("Last Sunday", epochAt(8, 17), epochAt(8, 18))

        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            var week = awaitItem().week
            while (week.sessionCount == 0) week = awaitItem().week
            assertEquals(2, week.sessionCount)
            assertEquals(2, week.totalSets)
            assertEquals(1_000.0, week.loadLb, 0.001)
            assertEquals(7_200_000L, week.totalDurationMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the week follows the week-start preference`() = runTest {
        // Sunday the 8th is last week for a Monday start, this week for a Sunday start.
        logSession("Sunday", epochAt(8, 17), epochAt(8, 18))
        env.settingsRepository.setWeekStartDay(DayOfWeek.SUNDAY)
        advanceUntilIdle()

        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            var week = awaitItem().week
            while (week.sessionCount == 0) week = awaitItem().week
            assertEquals(1, week.sessionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `abandoned sessions never reach the weekly summary`() = runTest {
        logSession("Quit", epochAt(10, 17), epochAt(10, 18), status = SessionStatus.ABANDONED)

        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            val state = awaitItem()
            assertEquals(0, state.week.sessionCount)
            assertNull(state.lastWorkout)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `warmup sets are excluded from volume until the preference says otherwise`() = runTest {
        logSession(
            name = "Push Day",
            startedAt = epochAt(10, 17),
            completedAt = epochAt(10, 18),
            sets = listOf(Triple(45.0, 10, SetType.WARMUP), Triple(100.0, 5, SetType.WORKING)),
        )

        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            var last = awaitItem().lastWorkout
            while (last == null) last = awaitItem().lastWorkout
            assertEquals(500.0, last.loadLb, 0.001)
            cancelAndIgnoreRemainingEvents()
        }

        env.settingsRepository.setIncludeWarmupInVolume(true)
        advanceUntilIdle()

        val second = viewModel()
        second.uiState.test {
            skipItems(1)
            var last = awaitItem().lastWorkout
            while (last == null || last.loadLb == 500.0) last = awaitItem().lastWorkout
            assertEquals(950.0, last.loadLb, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the weight unit preference reaches the UI state`() = runTest {
        env.settingsRepository.setDefaultWeightUnit(ExerciseUnit.KG)
        advanceUntilIdle()

        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            var state = awaitItem()
            while (state.weightUnit != ExerciseUnit.KG) state = awaitItem()
            assertEquals(ExerciseUnit.KG, state.weightUnit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing database surfaces an error state instead of crashing`() = runTest {
        env.breakDatabase()

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.errorMessage == null) state = awaitItem()
            assertNotNull(state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry clears the error and re-subscribes`() = runTest {
        env.breakDatabase()
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.errorMessage == null) state = awaitItem()

            vm.retry()

            // The database is still closed, so the error comes straight back — the point is that
            // retry re-subscribes rather than leaving a permanently dead screen.
            var afterRetry = awaitItem()
            while (afterRetry.errorMessage == null) afterRetry = awaitItem()
            assertNotNull(afterRetry.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /*
     * SQLite's own corruption handling deletes the database and lets Room build an empty one, so
     * the app comes up looking freshly installed with a training history silently gone. It cannot
     * be refused outright -- starting is what it takes to reach Settings and restore a backup -- so
     * the file is kept aside and the user is told. This is the telling.
     */

    @Test
    fun `an ordinary start says nothing about lost data`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertNull(state.unreadableDatabase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a database that could not be read is reported, with the file that was kept`() = runTest {
        val loss = RecordedLoss(
            UnreadableDatabase(preservedFileName = "forgelog.db.unreadable-123", atEpochMs = 123L),
        )
        val vm = viewModel(recoveryLog = loss)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(
                "forgelog.db.unreadable-123",
                state.unreadableDatabase?.preservedFileName,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the loss is still reported when no copy could be kept`() = runTest {
        val loss = RecordedLoss(UnreadableDatabase(preservedFileName = null, atEpochMs = 9L))
        val vm = viewModel(recoveryLog = loss)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertNotNull("a loss with no preserved copy went unreported", state.unreadableDatabase)
            assertNull(state.unreadableDatabase?.preservedFileName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the notice clears it for good`() = runTest {
        val loss = RecordedLoss(UnreadableDatabase(preservedFileName = "kept.db", atEpochMs = 5L))
        val vm = viewModel(recoveryLog = loss)
        vm.uiState.test {
            var state = awaitItem()
            while (state.unreadableDatabase == null) state = awaitItem()

            vm.dismissUnreadableDatabaseNotice()

            while (state.unreadableDatabase != null) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue("the dismissal was not written down", loss.reported)
        // A fresh ViewModel, as a later launch would build, says nothing.
        val later = viewModel(recoveryLog = loss)
        later.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertNull("the notice came back after being dismissed", state.unreadableDatabase)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

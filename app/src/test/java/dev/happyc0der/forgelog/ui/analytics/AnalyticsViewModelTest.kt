package dev.happyc0der.forgelog.ui.analytics

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.happyc0der.forgelog.data.local.exerciseEntity
import dev.happyc0der.forgelog.data.local.sessionEntity
import dev.happyc0der.forgelog.data.local.sessionExerciseEntity
import dev.happyc0der.forgelog.data.local.setLogEntity
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
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
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalyticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var env: TestEnvironment
    private val created = mutableListOf<AnalyticsViewModel>()
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private var benchId = 0L
    private var squatId = 0L

    /** Wednesday 11 March 2026. The Monday week runs from the 9th. */
    private val now = LocalDateTime.of(2026, 3, 11, 18, 0).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() = runTest {
        env = TestEnvironment(mainDispatcherRule.dispatcher)
        env.zone.zoneId = zone
        env.time.now = now
        benchId = env.database.exerciseDao().upsert(
            exerciseEntity(name = "Bench Press", category = ExerciseCategory.PUSH),
        )
        squatId = env.database.exerciseDao().upsert(
            exerciseEntity(name = "Squat", category = ExerciseCategory.LEGS),
        )
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

    private fun epochAt(month: Int, day: Int, hour: Int = 18): Long =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private suspend fun logSession(
        day: Int,
        month: Int = 3,
        exerciseId: Long = benchId,
        exerciseName: String = "Bench Press",
        reps: Int = 5,
        weight: Double = 100.0,
        rpe: Int? = null,
        setType: SetType = SetType.WORKING,
        status: SessionStatus = SessionStatus.COMPLETED,
        durationMs: Long = 3_600_000L,
    ) {
        val dao = env.database.workoutSessionDao()
        val started = epochAt(month, day, 17)
        val sessionId = dao.insertSession(
            sessionEntity(
                sessionName = "Session $month-$day",
                startedAt = started,
                completedAt = started + durationMs,
                status = status,
            ),
        )
        val sessionExerciseId = dao.insertSessionExercise(
            sessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                displayNameSnapshot = exerciseName,
            ),
        )
        dao.upsertSetLog(
            setLogEntity(
                sessionExerciseId = sessionExerciseId,
                setType = setType,
                reps = reps,
                weight = weight,
                rpe = rpe,
            ),
        )
    }

    @Test
    fun `this week is compared with last week by default`() = runTest {
        logSession(day = 11, weight = 120.0)
        logSession(day = 4, weight = 100.0)

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.comparison?.current?.sessionCount != 1) state = awaitItem()
            assertEquals(1, state.comparisonOffset)
            assertEquals(600.0, state.comparison?.current?.loadLb ?: 0.0, 0.001)
            assertEquals(500.0, state.comparison?.previous?.loadLb ?: 0.0, 0.001)
            assertEquals(0.2, state.comparison?.changeRatio { it.loadLb } ?: 0.0, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the comparison window can be moved further back`() = runTest {
        logSession(day = 11)
        logSession(day = 25, month = 2)

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.comparison?.current?.sessionCount != 1) state = awaitItem()
            // Last week is empty, so there is no baseline to compare against.
            assertEquals(0, state.comparison?.previous?.sessionCount)

            vm.setComparisonOffset(2)
            while (state.comparison?.previous?.sessionCount != 1) state = awaitItem()
            assertEquals(2, state.comparisonOffset)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a missing baseline reports no ratio rather than zero percent`() = runTest {
        logSession(day = 11)
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.comparison?.current?.sessionCount != 1) state = awaitItem()
            assertNull(state.comparison?.changeRatio { it.loadLb })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `volume by day covers the whole week including empty days`() = runTest {
        logSession(day = 9)
        logSession(day = 11)

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.volumeByDay.size != 7) state = awaitItem()
            assertEquals(7, state.volumeByDay.size)
            assertEquals(2, state.volumeByDay.count { it.loadLb > 0.0 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sets are grouped by the library category`() = runTest {
        logSession(day = 11, exerciseId = benchId)
        logSession(day = 10, exerciseId = squatId, exerciseName = "Squat")

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.setsByCategory.size < 2) state = awaitItem()
            assertEquals(1, state.setsByCategory[ExerciseCategory.PUSH])
            assertEquals(1, state.setsByCategory[ExerciseCategory.LEGS])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an exercise is selected automatically so the charts are never blank by default`() = runTest {
        logSession(day = 11)
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.selectedExerciseId == null) state = awaitItem()
            assertEquals(benchId, state.selectedExerciseId)
            assertTrue(state.topSetTrend.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting another exercise swaps both trends`() = runTest {
        logSession(day = 11, exerciseId = benchId, weight = 100.0)
        logSession(day = 10, exerciseId = squatId, exerciseName = "Squat", weight = 300.0)

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.exercises.size < 2) state = awaitItem()

            vm.selectExercise(squatId)
            while (state.selectedExerciseId != squatId) state = awaitItem()
            assertEquals(300.0, state.topSetTrend.single().value, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the charts open on the lift most recently trained with a load, not the first by name`() = runTest {
        // "Ab rollout" sorts first and is the most recent, but a bodyweight lift has nothing to chart.
        val rolloutId = env.database.exerciseDao().upsert(
            exerciseEntity(name = "Ab rollout", category = ExerciseCategory.CORE),
        )
        logSession(day = 10, exerciseId = squatId, exerciseName = "Squat", weight = 300.0)
        val dao = env.database.workoutSessionDao()
        val started = epochAt(3, 11, 17)
        val sessionId = dao.insertSession(
            sessionEntity(sessionName = "Core", startedAt = started, completedAt = started + 600_000L),
        )
        val sessionExerciseId = dao.insertSessionExercise(
            sessionExerciseEntity(sessionId = sessionId, exerciseId = rolloutId, displayNameSnapshot = "Ab rollout"),
        )
        dao.upsertSetLog(
            setLogEntity(
                sessionExerciseId = sessionExerciseId,
                reps = 8,
                weight = null,
                weightUnit = dev.happyc0der.forgelog.domain.model.ExerciseUnit.BODYWEIGHT,
            ),
        )

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.exercises.size < 2) state = awaitItem()
            assertEquals(squatId, state.selectedExerciseId)
            assertTrue(state.topSetTrend.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a bodyweight-only exercise contributes no estimated 1RM points`() = runTest {
        val pullUpId = env.database.exerciseDao().upsert(
            exerciseEntity(name = "Pull-up", category = ExerciseCategory.PULL),
        )
        val dao = env.database.workoutSessionDao()
        val started = epochAt(3, 11, 17)
        val sessionId = dao.insertSession(
            sessionEntity(sessionName = "Pull", startedAt = started, completedAt = started + 600_000L),
        )
        val sessionExerciseId = dao.insertSessionExercise(
            sessionExerciseEntity(sessionId = sessionId, exerciseId = pullUpId, displayNameSnapshot = "Pull-up"),
        )
        dao.upsertSetLog(
            setLogEntity(
                sessionExerciseId = sessionExerciseId,
                reps = 12,
                weight = null,
                weightUnit = dev.happyc0der.forgelog.domain.model.ExerciseUnit.BODYWEIGHT,
            ),
        )

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.selectedExerciseId == null) state = awaitItem()
            vm.selectExercise(pullUpId)
            while (state.selectedExerciseId != pullUpId) state = awaitItem()
            // Most reps is a real record; an estimated 1RM for bodyweight work is not.
            assertEquals(emptyList<Any>(), state.oneRepMaxTrend)
            assertEquals(12, state.selectedExerciseRecords?.mostReps?.reps)
            assertNull(state.selectedExerciseRecords?.bestEstimatedOneRepMax)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `records are listed for every exercise in the window`() = runTest {
        logSession(day = 11, exerciseId = benchId, weight = 185.0)
        logSession(day = 10, exerciseId = squatId, exerciseName = "Squat", weight = 315.0)

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.records.size < 2) state = awaitItem()
            assertEquals(listOf("Bench Press", "Squat"), state.records.map { it.exerciseName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the week-start preference moves the comparison windows`() = runTest {
        // Sunday the 8th: last week under a Monday start, this week under a Sunday start.
        logSession(day = 8)
        env.settingsRepository.setWeekStartDay(DayOfWeek.SUNDAY)
        advanceUntilIdle()

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.comparison?.current?.sessionCount != 1) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty database reports no data rather than a wall of zeros`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertTrue(!state.hasAnyData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `abandoned sessions are excluded from every metric`() = runTest {
        logSession(day = 11, status = SessionStatus.ABANDONED, weight = 999.0)
        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(0, state.comparison?.current?.sessionCount)
            assertEquals(0.0, state.comparison?.current?.loadLb ?: -1.0, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `average RPE is surfaced when it was recorded`() = runTest {
        logSession(day = 11, rpe = 8)
        logSession(day = 10, rpe = 6)

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.comparison?.current?.averageRpe == null) state = awaitItem()
            assertEquals(7.0, state.comparison?.current?.averageRpe ?: 0.0, 0.001)
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

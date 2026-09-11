package dev.happyc0der.forgelog.domain.analytics

import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.time.WeekBoundary
import dev.happyc0der.forgelog.domain.workout.sessionDetail
import dev.happyc0der.forgelog.domain.workout.sessionExercise
import dev.happyc0der.forgelog.domain.workout.setLog
import dev.happyc0der.forgelog.domain.workout.workoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class AnalyticsAggregatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private var nextId = 1L

    private fun epochAt(day: Int, hour: Int = 18): Long =
        LocalDateTime.of(2026, 3, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        id: Long,
        startedAt: Long,
        completedAt: Long?,
        status: SessionStatus = SessionStatus.COMPLETED,
        exerciseId: Long? = 7L,
        sets: List<SetLog>,
    ): SessionDetail = sessionDetail(
        workoutSession(id = id, startedAt = startedAt, completedAt = completedAt, status = status),
        SessionExerciseWithSets(
            exercise = sessionExercise(
                id = nextId++,
                sessionId = id,
                exerciseId = exerciseId,
                displayName = "Bench Press",
            ),
            sets = sets,
        ),
    )

    @Test
    fun `metrics total a window of sessions`() {
        val details = listOf(
            session(1L, epochAt(9), epochAt(9) + 3_600_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 100.0, rpe = 8))),
            session(2L, epochAt(11), epochAt(11) + 1_800_000L, sets = listOf(setLog(id = 2L, reps = 5, weight = 100.0, rpe = 6))),
        )
        val metrics = AnalyticsAggregator.metrics(details)

        assertEquals(2, metrics.sessionCount)
        assertEquals(2, metrics.totalSets)
        assertEquals(1_000.0, metrics.loadLb, 0.001)
        assertEquals(5_400_000L, metrics.totalDurationMs)
        assertEquals(2_700_000L, metrics.averageSessionDurationMs)
        assertEquals(7.0, metrics.averageRpe ?: 0.0, 0.001)
    }

    @Test
    fun `averages are null rather than zero when there is nothing to average`() {
        val metrics = AnalyticsAggregator.metrics(emptyList())
        assertEquals(PeriodMetrics.EMPTY, metrics)
        assertNull(metrics.averageSessionDurationMs)
        assertNull(metrics.averageRpe)
    }

    @Test
    fun `an unrecorded RPE does not drag the average down`() {
        val details = listOf(
            session(
                1L,
                epochAt(9),
                epochAt(9) + 3_600_000L,
                sets = listOf(
                    setLog(id = 1L, reps = 5, weight = 100.0, rpe = 8),
                    setLog(id = 2L, setNumber = 2, reps = 5, weight = 100.0, rpe = null),
                ),
            ),
        )
        // Averaging over the one set that recorded an RPE, not over both.
        assertEquals(8.0, AnalyticsAggregator.metrics(details).averageRpe ?: 0.0, 0.001)
    }

    @Test
    fun `a session with no RPE anywhere reports no average`() {
        val details = listOf(
            session(1L, epochAt(9), epochAt(9) + 100L, sets = listOf(setLog(id = 1L, rpe = null))),
        )
        assertNull(AnalyticsAggregator.metrics(details).averageRpe)
    }

    @Test
    fun `abandoned and unfinished sessions are excluded`() {
        val details = listOf(
            session(1L, epochAt(9), epochAt(9) + 3_600_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 100.0))),
            session(2L, epochAt(10), epochAt(10) + 100L, status = SessionStatus.ABANDONED, sets = listOf(setLog(id = 2L, reps = 5, weight = 999.0))),
            session(3L, epochAt(11), null, status = SessionStatus.IN_PROGRESS, sets = listOf(setLog(id = 3L, reps = 5, weight = 999.0))),
        )
        val metrics = AnalyticsAggregator.metrics(details)
        assertEquals(1, metrics.sessionCount)
        assertEquals(500.0, metrics.loadLb, 0.001)
    }

    @Test
    fun `comparison reports a change ratio, and null when there is no baseline`() {
        val current = listOf(
            session(1L, epochAt(11), epochAt(11) + 100L, sets = listOf(setLog(id = 1L, reps = 5, weight = 120.0))),
        )
        val previous = listOf(
            session(2L, epochAt(4), epochAt(4) + 100L, sets = listOf(setLog(id = 2L, reps = 5, weight = 100.0))),
        )
        val comparison = AnalyticsAggregator.compare(current, previous)
        assertEquals(0.2, comparison.changeRatio { it.loadLb } ?: 0.0, 0.0001)

        val noBaseline = AnalyticsAggregator.compare(current, emptyList())
        assertNull(noBaseline.changeRatio { it.loadLb })
    }

    @Test
    fun `volume by day keeps empty days in place`() {
        val week = WeekBoundary.daysOfWeek(epochAt(11), zone, DayOfWeek.MONDAY)
        val details = listOf(
            session(1L, epochAt(9), epochAt(9), sets = listOf(setLog(id = 1L, reps = 5, weight = 100.0))),
            session(2L, epochAt(11), epochAt(11), sets = listOf(setLog(id = 2L, reps = 5, weight = 200.0))),
        )

        val byDay = AnalyticsAggregator.volumeByDay(details, week)

        assertEquals(7, byDay.size)
        // Monday the 9th and Wednesday the 11th, with the rest at zero rather than missing.
        assertEquals(500.0, byDay[0].loadLb, 0.001)
        assertEquals(0.0, byDay[1].loadLb, 0.001)
        assertEquals(1_000.0, byDay[2].loadLb, 0.001)
        assertEquals(0.0, byDay.drop(3).sumOf { it.loadLb }, 0.001)
    }

    @Test
    fun `a workout past midnight counts on the day it started`() {
        val week = WeekBoundary.daysOfWeek(epochAt(11), zone, DayOfWeek.MONDAY)
        // Monday the 9th, 23:30 to 00:40 on Tuesday.
        val lateNight = session(
            1L,
            startedAt = epochAt(9, hour = 23) + 30 * 60_000L,
            completedAt = epochAt(10, hour = 0) + 40 * 60_000L,
            sets = listOf(setLog(id = 1L, reps = 5, weight = 100.0)),
        )

        val byDay = AnalyticsAggregator.volumeByDay(listOf(lateNight), week)

        assertEquals(500.0, byDay[0].loadLb, 0.001)
        assertEquals(0.0, byDay[1].loadLb, 0.001)
    }

    @Test
    fun `sets by category uses the library category and falls back to other`() {
        val details = listOf(
            session(1L, epochAt(9), epochAt(9), exerciseId = 7L, sets = listOf(setLog(id = 1L))),
            session(2L, epochAt(10), epochAt(10), exerciseId = 8L, sets = listOf(setLog(id = 2L), setLog(id = 3L, setNumber = 2))),
            // An exercise deleted from the library: the sets still happened.
            session(3L, epochAt(11), epochAt(11), exerciseId = null, sets = listOf(setLog(id = 4L))),
        )
        val categories = mapOf(7L to ExerciseCategory.PUSH, 8L to ExerciseCategory.PULL)

        val counts = AnalyticsAggregator.setsByCategory(details, categories)

        assertEquals(1, counts[ExerciseCategory.PUSH])
        assertEquals(2, counts[ExerciseCategory.PULL])
        assertEquals(1, counts[ExerciseCategory.OTHER])
    }

    @Test
    fun `warmup sets are excluded from category counts by default`() {
        val details = listOf(
            session(
                1L,
                epochAt(9),
                epochAt(9),
                sets = listOf(
                    setLog(id = 1L, setType = SetType.WARMUP),
                    setLog(id = 2L, setNumber = 2),
                ),
            ),
        )
        val counts = AnalyticsAggregator.setsByCategory(details, mapOf(7L to ExerciseCategory.PUSH))
        assertEquals(1, counts[ExerciseCategory.PUSH])
    }

    @Test
    fun `the estimated 1RM trend has one point per session, oldest first`() {
        val details = listOf(
            session(2L, epochAt(11), epochAt(11), sets = listOf(setLog(id = 2L, reps = 3, weight = 200.0))),
            session(1L, epochAt(9), epochAt(9), sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0))),
        )
        val trend = AnalyticsAggregator.estimatedOneRepMaxTrend(details, exerciseId = 7L)
        assertEquals(2, trend.size)
        assertEquals(epochAt(9), trend.first().epochMs)
        assertEquals(215.83, trend.first().value, 0.01)
        assertEquals(220.0, trend.last().value, 0.01)
    }

    @Test
    fun `sessions with no valid estimate contribute no trend points`() {
        val details = listOf(
            session(
                1L,
                epochAt(9),
                epochAt(9),
                sets = listOf(setLog(id = 1L, reps = 15, weight = 135.0)),
            ),
            session(
                2L,
                epochAt(10),
                epochAt(10),
                sets = listOf(setLog(id = 2L, reps = 10, weight = null, weightUnit = ExerciseUnit.BODYWEIGHT)),
            ),
        )
        assertEquals(emptyList<TrendPoint>(), AnalyticsAggregator.estimatedOneRepMaxTrend(details, 7L))
    }

    @Test
    fun `the trend only covers the requested exercise`() {
        val details = listOf(
            session(1L, epochAt(9), epochAt(9), exerciseId = 7L, sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0))),
            session(2L, epochAt(10), epochAt(10), exerciseId = 8L, sets = listOf(setLog(id = 2L, reps = 5, weight = 315.0))),
        )
        assertEquals(1, AnalyticsAggregator.estimatedOneRepMaxTrend(details, 7L).size)
        assertEquals(1, AnalyticsAggregator.estimatedOneRepMaxTrend(details, 8L).size)
    }

    @Test
    fun `the top-set trend reports the heaviest working set per session in pounds`() {
        val details = listOf(
            session(
                1L,
                epochAt(9),
                epochAt(9),
                sets = listOf(
                    setLog(id = 1L, setType = SetType.WARMUP, reps = 10, weight = 500.0),
                    setLog(id = 2L, setNumber = 2, reps = 5, weight = 60.0, weightUnit = ExerciseUnit.KG),
                ),
            ),
        )
        val trend = AnalyticsAggregator.topSetTrend(details, 7L)
        assertEquals(1, trend.size)
        // 60 kg normalised, and the warmup ignored despite being heavier.
        assertEquals(132.28, trend.single().value, 0.01)
    }

    @Test
    fun `timed work is summed separately from load`() {
        val details = listOf(
            session(
                1L,
                epochAt(9),
                epochAt(9) + 600_000L,
                sets = listOf(
                    setLog(id = 1L, reps = null, weight = null, durationSeconds = 60, weightUnit = ExerciseUnit.SECONDS),
                    setLog(id = 2L, setNumber = 2, reps = null, weight = null, durationSeconds = 45, weightUnit = ExerciseUnit.SECONDS),
                ),
            ),
        )
        val metrics = AnalyticsAggregator.metrics(details)
        assertEquals(105, metrics.timedSeconds)
        assertEquals(0.0, metrics.loadLb, 0.001)
        assertEquals(2, metrics.totalSets)
    }
}

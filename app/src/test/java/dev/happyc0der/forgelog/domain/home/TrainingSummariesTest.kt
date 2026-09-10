package dev.happyc0der.forgelog.domain.home

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.workout.KG_TO_LB
import dev.happyc0der.forgelog.domain.workout.sessionDetail
import dev.happyc0der.forgelog.domain.workout.sessionExercise
import dev.happyc0der.forgelog.domain.workout.setLog
import dev.happyc0der.forgelog.domain.workout.workoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingSummariesTest {

    private fun exerciseWithSets(
        id: Long,
        sessionId: Long = 1L,
        name: String = "Bench Press",
        order: Int = 0,
        sets: List<dev.happyc0der.forgelog.domain.model.SetLog>,
    ) = SessionExerciseWithSets(
        exercise = sessionExercise(
            id = id,
            sessionId = sessionId,
            exerciseId = id,
            displayName = name,
            exerciseOrder = order,
        ),
        sets = sets,
    )

    @Test
    fun `duration is completedAt minus startedAt`() {
        val detail = sessionDetail(
            workoutSession(id = 1L, startedAt = 1_000L, completedAt = 1_000L + 3_600_000L),
            exerciseWithSets(id = 1L, sets = listOf(setLog())),
        )
        assertEquals(3_600_000L, TrainingSummaries.summarize(detail).durationMs)
    }

    @Test
    fun `an unfinished session has an unknown duration, not a zero one`() {
        val detail = sessionDetail(
            workoutSession(id = 1L, status = SessionStatus.IN_PROGRESS, completedAt = null),
            exerciseWithSets(id = 1L, sets = listOf(setLog())),
        )
        assertNull(TrainingSummaries.summarize(detail).durationMs)
    }

    @Test
    fun `a completedAt before startedAt is reported as unknown rather than negative`() {
        val detail = sessionDetail(
            workoutSession(id = 1L, startedAt = 5_000L, completedAt = 1_000L),
            exerciseWithSets(id = 1L, sets = listOf(setLog())),
        )
        assertNull(TrainingSummaries.summarize(detail).durationMs)
    }

    @Test
    fun `volume sums pounds across mixed-unit exercises`() {
        val detail = sessionDetail(
            workoutSession(id = 1L),
            exerciseWithSets(
                id = 1L,
                sets = listOf(setLog(id = 1L, reps = 5, weight = 100.0, weightUnit = ExerciseUnit.LB)),
            ),
            exerciseWithSets(
                id = 2L,
                order = 1,
                sets = listOf(setLog(id = 2L, reps = 5, weight = 50.0, weightUnit = ExerciseUnit.KG)),
            ),
        )
        val expected = 5 * 100.0 + 5 * (50.0 * KG_TO_LB)
        assertEquals(expected, TrainingSummaries.summarize(detail).loadLb, 0.001)
    }

    @Test
    fun `warmup sets are excluded by default and included on request`() {
        val detail = sessionDetail(
            workoutSession(id = 1L),
            exerciseWithSets(
                id = 1L,
                sets = listOf(
                    setLog(id = 1L, setType = SetType.WARMUP, reps = 10, weight = 45.0),
                    setLog(id = 2L, setNumber = 2, reps = 5, weight = 100.0),
                ),
            ),
        )
        assertEquals(500.0, TrainingSummaries.summarize(detail).loadLb, 0.001)
        assertEquals(1, TrainingSummaries.summarize(detail).totalSets)
        val withWarmup = TrainingSummaries.summarize(detail, includeWarmup = true)
        assertEquals(950.0, withWarmup.loadLb, 0.001)
        assertEquals(2, withWarmup.totalSets)
    }

    @Test
    fun `incomplete sets never count towards volume or set count`() {
        val detail = sessionDetail(
            workoutSession(id = 1L),
            exerciseWithSets(
                id = 1L,
                sets = listOf(
                    setLog(id = 1L, reps = 5, weight = 100.0, completed = true),
                    setLog(id = 2L, setNumber = 2, reps = 5, weight = 100.0, completed = false),
                ),
            ),
        )
        val summary = TrainingSummaries.summarize(detail)
        assertEquals(500.0, summary.loadLb, 0.001)
        assertEquals(1, summary.totalSets)
    }

    @Test
    fun `exercise count only counts exercises that were actually worked`() {
        val detail = sessionDetail(
            workoutSession(id = 1L),
            exerciseWithSets(id = 1L, sets = listOf(setLog(id = 1L))),
            // Planned, opened, but never logged — it did not happen.
            exerciseWithSets(id = 2L, order = 1, sets = listOf(setLog(id = 2L, completed = false))),
            exerciseWithSets(id = 3L, order = 2, sets = emptyList()),
        )
        assertEquals(1, TrainingSummaries.summarize(detail).exerciseCount)
    }

    @Test
    fun `timed work is summed in seconds`() {
        val detail = sessionDetail(
            workoutSession(id = 1L),
            exerciseWithSets(
                id = 1L,
                sets = listOf(
                    setLog(id = 1L, reps = null, weight = null, durationSeconds = 45),
                    setLog(id = 2L, setNumber = 2, reps = null, weight = null, durationSeconds = 30),
                ),
            ),
        )
        val summary = TrainingSummaries.summarize(detail)
        assertEquals(75, summary.volume.totalDurationSeconds)
        assertEquals(0.0, summary.loadLb, 0.001)
    }

    @Test
    fun `totals ignore abandoned and in-progress sessions`() {
        val completed = sessionDetail(
            workoutSession(id = 1L, startedAt = 0L, completedAt = 3_600_000L),
            exerciseWithSets(id = 1L, sets = listOf(setLog(id = 1L, reps = 5, weight = 100.0))),
        )
        val abandoned = sessionDetail(
            workoutSession(id = 2L, status = SessionStatus.ABANDONED, completedAt = 7_200_000L),
            exerciseWithSets(id = 2L, sessionId = 2L, sets = listOf(setLog(id = 2L, reps = 5, weight = 200.0))),
        )
        val running = sessionDetail(
            workoutSession(id = 3L, status = SessionStatus.IN_PROGRESS, completedAt = null),
            exerciseWithSets(id = 3L, sessionId = 3L, sets = listOf(setLog(id = 3L, reps = 5, weight = 300.0))),
        )

        val totals = TrainingSummaries.totals(listOf(completed, abandoned, running))

        assertEquals(1, totals.sessionCount)
        assertEquals(500.0, totals.loadLb, 0.001)
        assertEquals(1, totals.totalSets)
        assertEquals(3_600_000L, totals.totalDurationMs)
    }

    @Test
    fun `totals of an empty week are all zero`() {
        assertEquals(TrainingTotals.EMPTY, TrainingSummaries.totals(emptyList()))
    }

    @Test
    fun `totals accumulate across sessions`() {
        val a = sessionDetail(
            workoutSession(id = 1L, startedAt = 0L, completedAt = 1_800_000L),
            exerciseWithSets(id = 1L, sets = listOf(setLog(id = 1L, reps = 5, weight = 100.0))),
        )
        val b = sessionDetail(
            workoutSession(id = 2L, startedAt = 0L, completedAt = 1_800_000L),
            exerciseWithSets(
                id = 2L,
                sessionId = 2L,
                sets = listOf(
                    setLog(id = 2L, reps = 5, weight = 100.0),
                    setLog(id = 3L, setNumber = 2, reps = 5, weight = 100.0),
                ),
            ),
        )
        val totals = TrainingSummaries.totals(listOf(a, b))
        assertEquals(2, totals.sessionCount)
        assertEquals(3, totals.totalSets)
        assertEquals(1_500.0, totals.loadLb, 0.001)
        assertEquals(3_600_000L, totals.totalDurationMs)
    }

    @Test
    fun `feeling is carried through when it was recorded`() {
        val detail = sessionDetail(
            workoutSession(id = 1L).copy(overallFeeling = 4),
            exerciseWithSets(id = 1L, sets = listOf(setLog())),
        )
        assertEquals(4, TrainingSummaries.summarize(detail).overallFeeling)
        val withoutFeeling = sessionDetail(
            workoutSession(id = 2L),
            exerciseWithSets(id = 2L, sessionId = 2L, sets = listOf(setLog())),
        )
        assertNull(TrainingSummaries.summarize(withoutFeeling).overallFeeling)
    }
}

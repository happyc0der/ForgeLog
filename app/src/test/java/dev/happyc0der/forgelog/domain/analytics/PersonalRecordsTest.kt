package dev.happyc0der.forgelog.domain.analytics

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.workout.KG_TO_LB
import dev.happyc0der.forgelog.domain.workout.sessionDetail
import dev.happyc0der.forgelog.domain.workout.sessionExercise
import dev.happyc0der.forgelog.domain.workout.setLog
import dev.happyc0der.forgelog.domain.workout.workoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordsTest {

    private var nextId = 1L

    private fun session(
        id: Long,
        completedAt: Long,
        status: SessionStatus = SessionStatus.COMPLETED,
        exerciseId: Long? = 7L,
        name: String = "Bench Press",
        sets: List<SetLog>,
    ): SessionDetail = sessionDetail(
        workoutSession(id = id, startedAt = completedAt - 3_600_000L, completedAt = completedAt, status = status),
        SessionExerciseWithSets(
            exercise = sessionExercise(
                id = nextId++,
                sessionId = id,
                exerciseId = exerciseId,
                displayName = name,
            ),
            sets = sets,
        ),
    )

    @Test
    fun `the heaviest completed working set is the weight record`() {
        val history = listOf(
            session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0))),
            session(2L, 2_000L, sets = listOf(setLog(id = 2L, reps = 3, weight = 225.0))),
            session(3L, 3_000L, sets = listOf(setLog(id = 3L, reps = 8, weight = 155.0))),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        assertEquals(225.0, records.heaviestWeight?.weightLb ?: 0.0, 0.001)
        assertEquals(2L, records.heaviestWeight?.sessionId)
    }

    @Test
    fun `kilograms compete fairly with pounds`() {
        val history = listOf(
            session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 200.0))),
            // 100 kg is about 220 lb, so it should win.
            session(
                2L,
                2_000L,
                sets = listOf(setLog(id = 2L, reps = 5, weight = 100.0, weightUnit = ExerciseUnit.KG)),
            ),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        assertEquals(100.0 * KG_TO_LB, records.heaviestWeight?.weightLb ?: 0.0, 0.01)
    }

    @Test
    fun `warmup sets cannot set records`() {
        val history = listOf(
            session(
                1L,
                1_000L,
                sets = listOf(
                    setLog(id = 1L, setType = SetType.WARMUP, reps = 20, weight = 315.0),
                    setLog(id = 2L, setNumber = 2, reps = 5, weight = 185.0),
                ),
            ),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        assertEquals(185.0, records.heaviestWeight?.weightLb ?: 0.0, 0.001)
        assertEquals(5, records.mostReps?.reps)
    }

    @Test
    fun `incomplete sets cannot set records`() {
        val history = listOf(
            session(
                1L,
                1_000L,
                sets = listOf(
                    setLog(id = 1L, reps = 5, weight = 185.0, completed = true),
                    setLog(id = 2L, setNumber = 2, reps = 10, weight = 315.0, completed = false),
                ),
            ),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        assertEquals(185.0, records.heaviestWeight?.weightLb ?: 0.0, 0.001)
    }

    @Test
    fun `abandoned sessions cannot set records`() {
        val history = listOf(
            session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0))),
            session(
                2L,
                2_000L,
                status = SessionStatus.ABANDONED,
                sets = listOf(setLog(id = 2L, reps = 1, weight = 405.0)),
            ),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        assertEquals(185.0, records.heaviestWeight?.weightLb ?: 0.0, 0.001)
    }

    @Test
    fun `best set volume is reps times weight, not the heaviest single`() {
        val history = listOf(
            session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 1, weight = 300.0))),
            session(2L, 2_000L, sets = listOf(setLog(id = 2L, reps = 10, weight = 135.0))),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        assertEquals(1_350.0, records.bestSetVolume?.setVolumeLb ?: 0.0, 0.001)
        assertEquals(300.0, records.heaviestWeight?.weightLb ?: 0.0, 0.001)
    }


    @Test
    fun `a duration exercise records its longest hold and no weight record`() {
        val history = listOf(
            session(
                1L,
                1_000L,
                name = "Plank",
                sets = listOf(
                    setLog(id = 1L, reps = null, weight = null, durationSeconds = 60, weightUnit = ExerciseUnit.SECONDS),
                    setLog(id = 2L, setNumber = 2, reps = null, weight = null, durationSeconds = 95, weightUnit = ExerciseUnit.SECONDS),
                ),
            ),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        assertEquals(95, records.longestDuration?.durationSeconds)
        assertNull(records.heaviestWeight)
        assertNull(records.bestEstimatedOneRepMax)
    }

    @Test
    fun `a bodyweight set sets no estimated 1RM record`() {
        val history = listOf(
            session(
                1L,
                1_000L,
                name = "Pull-up",
                sets = listOf(
                    setLog(id = 1L, reps = 12, weight = null, weightUnit = ExerciseUnit.BODYWEIGHT),
                ),
            ),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        assertEquals(12, records.mostReps?.reps)
        assertNull(records.bestEstimatedOneRepMax)
        assertNull(records.heaviestWeight)
    }

    @Test
    fun `estimated 1RM ignores sets above ten reps`() {
        val history = listOf(
            session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 20, weight = 135.0))),
            session(2L, 2_000L, sets = listOf(setLog(id = 2L, reps = 5, weight = 185.0))),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        // 185 × (1 + 5/30) ≈ 215.8, and the 20-rep set contributes nothing.
        assertEquals(215.83, records.bestEstimatedOneRepMax?.estimatedOneRepMaxLb ?: 0.0, 0.01)
    }

    @Test
    fun `a tie keeps the date the record was first achieved`() {
        val history = listOf(
            session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0, completedAt = 1_000L))),
            session(2L, 5_000L, sets = listOf(setLog(id = 2L, reps = 5, weight = 185.0, completedAt = 5_000L))),
        )
        val records = PersonalRecords.byExercise(history).values.single()
        assertEquals(1L, records.heaviestWeight?.sessionId)
    }

    @Test
    fun `records are kept per exercise`() {
        val history = listOf(
            session(1L, 1_000L, exerciseId = 7L, name = "Bench", sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0))),
            session(2L, 2_000L, exerciseId = 8L, name = "Squat", sets = listOf(setLog(id = 2L, reps = 5, weight = 315.0))),
        )
        val records = PersonalRecords.byExercise(history)
        assertEquals(2, records.size)
        assertEquals(185.0, records.getValue("7").heaviestWeight?.weightLb ?: 0.0, 0.001)
        assertEquals(315.0, records.getValue("8").heaviestWeight?.weightLb ?: 0.0, 0.001)
    }

    @Test
    fun `exercises deleted from the library keep separate records by name`() {
        val history = listOf(
            session(1L, 1_000L, exerciseId = null, name = "Old Press", sets = listOf(setLog(id = 1L, reps = 5, weight = 100.0))),
            session(2L, 2_000L, exerciseId = null, name = "Old Curl", sets = listOf(setLog(id = 2L, reps = 5, weight = 50.0))),
        )
        val records = PersonalRecords.byExercise(history)
        assertEquals(2, records.size)
        assertTrue(records.keys.all { it.startsWith("name:") })
    }

    @Test
    fun `matching a record does not set one`() {
        val existing = PersonalRecords.records(
            listOf(
                PrCandidate(
                    exerciseId = 7L,
                    exerciseName = "Bench",
                    sessionId = 1L,
                    achievedAtEpochMs = 1_000L,
                    reps = 5,
                    weightLb = 185.0,
                    durationSeconds = null,
                    estimatedOneRepMaxLb = 215.83,
                ),
            ),
        )
        val equal = PrCandidate(
            exerciseId = 7L,
            exerciseName = "Bench",
            sessionId = 2L,
            achievedAtEpochMs = 2_000L,
            reps = 5,
            weightLb = 185.0,
            durationSeconds = null,
            estimatedOneRepMaxLb = 215.83,
        )
        assertEquals(emptySet<RecordKind>(), PersonalRecords.beats(existing, equal))
    }

    @Test
    fun `beating a record reports which kinds were beaten`() {
        val existing = PersonalRecords.records(
            listOf(
                PrCandidate(7L, "Bench", 1L, 1_000L, reps = 5, weightLb = 185.0, durationSeconds = null, estimatedOneRepMaxLb = 215.0),
            ),
        )
        val heavier = PrCandidate(7L, "Bench", 2L, 2_000L, reps = 5, weightLb = 195.0, durationSeconds = null, estimatedOneRepMaxLb = 227.5)
        val kinds = PersonalRecords.beats(existing, heavier)
        assertTrue(RecordKind.HEAVIEST_WEIGHT in kinds)
        assertTrue(RecordKind.BEST_SET_VOLUME in kinds)
        assertTrue(RecordKind.BEST_ESTIMATED_1RM in kinds)
        assertTrue(RecordKind.MOST_REPS !in kinds)
    }

    @Test
    fun `the first ever set of an exercise is a record in every applicable kind`() {
        val first = PrCandidate(7L, "Bench", 1L, 1_000L, reps = 5, weightLb = 185.0, durationSeconds = null, estimatedOneRepMaxLb = 215.0)
        val kinds = PersonalRecords.beats(existing = null, candidate = first)
        assertTrue(RecordKind.HEAVIEST_WEIGHT in kinds)
        assertTrue(RecordKind.MOST_REPS in kinds)
        assertTrue(RecordKind.BEST_SET_VOLUME in kinds)
        assertTrue(RecordKind.BEST_ESTIMATED_1RM in kinds)
        // It was not a timed set, so there is no duration record to claim.
        assertTrue(RecordKind.LONGEST_DURATION !in kinds)
    }

    @Test
    fun `an empty history has no records at all`() {
        assertEquals(emptyMap<String, ExerciseRecords>(), PersonalRecords.byExercise(emptyList()))
    }
}

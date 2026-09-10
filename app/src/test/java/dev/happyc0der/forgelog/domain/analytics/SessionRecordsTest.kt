package dev.happyc0der.forgelog.domain.analytics

import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.workout.sessionDetail
import dev.happyc0der.forgelog.domain.workout.sessionExercise
import dev.happyc0der.forgelog.domain.workout.setLog
import dev.happyc0der.forgelog.domain.workout.workoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the completion summary claims the user just achieved.
 *
 * The comparison is deliberately against history *excluding* the session being summarised — judged
 * against a table it is already in, nothing could ever be a record.
 */
class SessionRecordsTest {

    private var nextId = 1L

    private fun session(
        id: Long,
        completedAt: Long,
        status: SessionStatus = SessionStatus.COMPLETED,
        exerciseId: Long? = 7L,
        name: String = "Bench Press",
        sets: List<SetLog>,
    ): SessionDetail = sessionDetail(
        workoutSession(
            id = id,
            startedAt = completedAt - 3_600_000L,
            completedAt = completedAt,
            status = status,
        ),
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

    private fun kinds(records: List<SessionRecord>) = records.map { it.kind }.toSet()

    @Test
    fun `beating the old best is a record and carries what it beat`() {
        val history = listOf(session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0))))
        val today = session(2L, 2_000L, sets = listOf(setLog(id = 2L, reps = 5, weight = 205.0)))

        val records = PersonalRecords.achievedIn(today, history)

        assertTrue(RecordKind.HEAVIEST_WEIGHT in kinds(records))
        val heaviest = records.single { it.kind == RecordKind.HEAVIEST_WEIGHT }
        assertEquals(205.0, heaviest.candidate.weightLb ?: 0.0, 0.001)
        assertEquals(185.0, heaviest.previousBest?.weightLb ?: 0.0, 0.001)
        assertEquals("Bench Press", heaviest.exerciseName)
    }

    @Test
    fun `matching the old best is not a record`() {
        val history = listOf(session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0))))
        val today = session(2L, 2_000L, sets = listOf(setLog(id = 2L, reps = 5, weight = 185.0)))

        assertTrue(PersonalRecords.achievedIn(today, history).isEmpty())
    }

    @Test
    fun `the first time an exercise is done every applicable record is set, with nothing beaten`() {
        val today = session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0)))

        val records = PersonalRecords.achievedIn(today, emptyList())

        assertTrue(RecordKind.HEAVIEST_WEIGHT in kinds(records))
        assertTrue(RecordKind.MOST_REPS in kinds(records))
        assertTrue(RecordKind.BEST_SET_VOLUME in kinds(records))
        // A first entry beats nothing, and the UI must be able to say so rather than inventing a
        // previous best of zero.
        records.forEach { assertNull(it.previousBest) }
        // A loaded barbell set makes no duration claim.
        assertTrue(RecordKind.LONGEST_DURATION !in kinds(records))
    }

    @Test
    fun `three heavier sets are one record, not three`() {
        val history = listOf(session(1L, 1_000L, sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0))))
        val today = session(
            2L,
            2_000L,
            sets = listOf(
                setLog(id = 2L, reps = 5, weight = 195.0),
                setLog(id = 3L, reps = 5, weight = 205.0),
                setLog(id = 4L, reps = 5, weight = 200.0),
            ),
        )

        val records = PersonalRecords.achievedIn(today, history)

        val heaviest = records.filter { it.kind == RecordKind.HEAVIEST_WEIGHT }
        assertEquals(1, heaviest.size)
        // And it is the best of the three, not the first one to clear the bar.
        assertEquals(205.0, heaviest.single().candidate.weightLb ?: 0.0, 0.001)
    }

    @Test
    fun `a warmup cannot set a record`() {
        val today = session(
            1L,
            1_000L,
            sets = listOf(setLog(id = 1L, reps = 5, weight = 315.0, setType = SetType.WARMUP)),
        )

        assertTrue(PersonalRecords.achievedIn(today, emptyList()).isEmpty())
    }

    @Test
    fun `an uncompleted set cannot set a record`() {
        val today = session(
            1L,
            1_000L,
            sets = listOf(setLog(id = 1L, reps = 5, weight = 315.0, completed = false)),
        )

        assertTrue(PersonalRecords.achievedIn(today, emptyList()).isEmpty())
    }

    @Test
    fun `records are kept separate per exercise`() {
        val history = listOf(
            session(1L, 1_000L, exerciseId = 7L, name = "Bench Press", sets = listOf(setLog(id = 1L, reps = 5, weight = 185.0))),
        )
        val today = sessionDetail(
            workoutSession(id = 2L, startedAt = 0L, completedAt = 2_000L, status = SessionStatus.COMPLETED),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 90L, sessionId = 2L, exerciseId = 7L, displayName = "Bench Press"),
                sets = listOf(setLog(id = 2L, reps = 5, weight = 150.0)),
            ),
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 91L, sessionId = 2L, exerciseId = 8L, displayName = "Overhead Press"),
                sets = listOf(setLog(id = 3L, reps = 5, weight = 95.0)),
            ),
        )

        val records = PersonalRecords.achievedIn(today, history)

        // The bench set is lighter than the standing record, so only the press — new to the log —
        // has anything to claim.
        assertTrue(records.none { it.exerciseName == "Bench Press" })
        assertNotNull(records.firstOrNull { it.exerciseName == "Overhead Press" })
    }

    @Test
    fun `an abandoned earlier session does not stand as a record to beat`() {
        val history = listOf(
            session(1L, 1_000L, status = SessionStatus.ABANDONED, sets = listOf(setLog(id = 1L, reps = 5, weight = 405.0))),
        )
        val today = session(2L, 2_000L, sets = listOf(setLog(id = 2L, reps = 5, weight = 185.0)))

        val records = PersonalRecords.achievedIn(today, history)

        assertTrue(RecordKind.HEAVIEST_WEIGHT in kinds(records))
        assertNull(records.single { it.kind == RecordKind.HEAVIEST_WEIGHT }.previousBest)
    }
}

package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Test

class SetsLeftTest {

    private val session = workoutSession(id = 1L, status = SessionStatus.IN_PROGRESS, completedAt = null)

    private fun exercise(
        id: Long,
        name: String,
        order: Int = id.toInt(),
        planned: Int? = null,
        done: Int = 0,
        unticked: Int = 0,
    ) = SessionExerciseWithSets(
        exercise = sessionExercise(id = id, sessionId = 1L, exerciseId = id, displayName = name, exerciseOrder = order)
            .copy(plannedSets = planned),
        sets = (1..done).map { setLog(id = id * 100 + it, sessionExerciseId = id, setNumber = it) } +
            (1..unticked).map {
                setLog(id = id * 100 + done + it, sessionExerciseId = id, setNumber = done + it, completed = false)
            },
    )

    @Test
    fun `planned sets not yet done are left`() {
        val left = SetsLeft.of(
            sessionDetail(session, exercise(1L, "Bench Press", planned = 4, done = 3, unticked = 0)),
        )
        // Planned and never added: nothing unticked sits in the log.
        assertEquals(listOf(SetsLeft("Bench Press", 1, unticked = 0)), left)
    }

    @Test
    fun `an untouched planned exercise has all its sets left`() {
        val left = SetsLeft.of(sessionDetail(session, exercise(1L, "Farmer's walk", planned = 3)))
        assertEquals(listOf(SetsLeft("Farmer's walk", 3)), left)
    }

    @Test
    fun `added sets not ticked are left, planned or not`() {
        val left = SetsLeft.of(
            sessionDetail(
                session,
                // Two extra sets over a plan of three, not ticked.
                exercise(1L, "Bench Press", planned = 3, done = 3, unticked = 2),
                exercise(2L, "Dead hang", planned = null, done = 1, unticked = 1),
            ),
        )
        assertEquals(
            listOf(SetsLeft("Bench Press", 2, unticked = 2), SetsLeft("Dead hang", 1, unticked = 1)),
            left,
        )
    }

    @Test
    fun `nothing is left when every planned and added set is done`() {
        val left = SetsLeft.of(
            sessionDetail(
                session,
                exercise(1L, "Bench Press", planned = 3, done = 3),
                exercise(2L, "Pull-up", planned = null, done = 2),
                // An unplanned exercise nobody touched: nothing was asked of it.
                exercise(3L, "Plank", planned = null),
            ),
        )
        assertEquals(emptyList<SetsLeft>(), left)
    }

    @Test
    fun `a ticked warm-up is not one of the planned sets`() {
        // Squat, 3 x 5, after three ramp-up sets and one working set.
        val detail = sessionDetail(
            session,
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 1L, sessionId = 1L, exerciseId = 1L, displayName = "Squat")
                    .copy(plannedSets = 3),
                sets = (1..3).map { setLog(id = it.toLong(), sessionExerciseId = 1L, setNumber = it, setType = SetType.WARMUP) } +
                    setLog(id = 4L, sessionExerciseId = 1L, setNumber = 4),
            ),
        )
        assertEquals(listOf(SetsLeft("Squat", 2, unticked = 0)), SetsLeft.of(detail))
    }

    @Test
    fun `a warm-up added and not ticked is left too`() {
        val detail = sessionDetail(
            session,
            SessionExerciseWithSets(
                exercise = sessionExercise(id = 1L, sessionId = 1L, exerciseId = 1L, displayName = "Squat")
                    .copy(plannedSets = 1),
                sets = listOf(
                    setLog(id = 1L, sessionExerciseId = 1L, setNumber = 1, setType = SetType.WARMUP, completed = false),
                    setLog(id = 2L, sessionExerciseId = 1L, setNumber = 2),
                ),
            ),
        )
        assertEquals(listOf(SetsLeft("Squat", 1, unticked = 1)), SetsLeft.of(detail))
    }

    @Test
    fun `exercises come in the order they are logged`() {
        val left = SetsLeft.of(
            sessionDetail(
                session,
                exercise(1L, "Second", order = 1, planned = 1),
                exercise(2L, "First", order = 0, planned = 1),
            ),
        )
        assertEquals(listOf("First", "Second"), left.map { it.exerciseName })
    }
}

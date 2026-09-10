package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetPrefillTest {
    @Test
    fun usesLastSetFromThisSessionWhenPresent() {
        val existing = listOf(
            setLog(id = 1L, setNumber = 1, reps = 8, weight = 135.0, setType = SetType.WARMUP),
            setLog(id = 2L, setNumber = 2, reps = 5, weight = 185.0, restAfterSetSeconds = 120),
        )
        val historical = SessionExerciseWithSets(
            exercise = sessionExercise(id = 9L, sessionId = 2L, exerciseId = 5L, displayName = "Bench"),
            sets = listOf(setLog(id = 20L, sessionExerciseId = 9L, setNumber = 3, reps = 3, weight = 225.0)),
        )

        val next = SetPrefill.nextSet(
            sessionExerciseId = 10L,
            existing = existing,
            defaultUnit = ExerciseUnit.KG,
            historical = historical,
        )

        assertEquals(3, next.setNumber)
        assertEquals(SetType.WORKING, next.setType)
        assertEquals(5, next.reps)
        assertEquals(185.0, next.weight)
        assertEquals(ExerciseUnit.LB, next.weightUnit)
        assertEquals(120, next.restAfterSetSeconds)
        assertNull(next.rpe)
        assertNull(next.notes)
    }

    @Test
    fun fallsBackToHistoricalLastSet() {
        val historical = SessionExerciseWithSets(
            exercise = sessionExercise(id = 9L, sessionId = 2L, exerciseId = 5L, displayName = "Bench"),
            sets = listOf(
                setLog(id = 20L, sessionExerciseId = 9L, setNumber = 1, reps = 10, weight = 60.0),
                setLog(id = 21L, sessionExerciseId = 9L, setNumber = 2, reps = 8, weight = 70.0),
            ),
        )

        val next = SetPrefill.nextSet(
            sessionExerciseId = 10L,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.KG,
            historical = historical,
        )

        assertEquals(1, next.setNumber)
        assertEquals(8, next.reps)
        assertEquals(70.0, next.weight)
        assertEquals(ExerciseUnit.LB, next.weightUnit)
    }

    @Test
    fun usesDefaultUnitWhenNoTemplateExists() {
        val next = SetPrefill.nextSet(
            sessionExerciseId = 10L,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.KG,
            historical = null,
        )

        assertEquals(1, next.setNumber)
        assertEquals(SetType.WORKING, next.setType)
        assertEquals(ExerciseUnit.KG, next.weightUnit)
        assertNull(next.reps)
        assertNull(next.weight)
    }
}

/**
 * Prefill priority: what was just logged, then last session, then the plan. The plan comes last
 * because what the lifter actually did beats what was written down — but on a brand-new exercise it
 * is all there is, and an empty row is worse than their own target.
 */
class SetPrefillTargetsTest {

    private val exerciseId = 42L

    @Test
    fun `the plan fills the first set when there is no history at all`() {
        val next = SetPrefill.nextSet(
            sessionExerciseId = exerciseId,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.LB,
            historical = null,
            targets = SetTargets(targetRepMin = 6, targetRepMax = 8, targetWeight = 185.0, targetRestSeconds = 150),
        )
        // The bottom of the range is the number being committed to.
        assertEquals(6, next.reps)
        assertEquals(185.0, next.weight ?: 0.0, 0.001)
        assertEquals(150, next.restAfterSetSeconds)
        assertEquals(1, next.setNumber)
        assertEquals(false, next.completed)
    }

    @Test
    fun `a max-only rep target is used when there is no minimum`() {
        val next = SetPrefill.nextSet(
            sessionExerciseId = exerciseId,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.LB,
            historical = null,
            targets = SetTargets(targetRepMax = 12),
        )
        assertEquals(12, next.reps)
    }

    @Test
    fun `the set just logged wins over the plan`() {
        val logged = setLog(id = 1L, sessionExerciseId = exerciseId, reps = 5, weight = 200.0)
        val next = SetPrefill.nextSet(
            sessionExerciseId = exerciseId,
            existing = listOf(logged),
            defaultUnit = ExerciseUnit.LB,
            historical = null,
            targets = SetTargets(targetRepMin = 6, targetWeight = 185.0),
        )
        assertEquals(5, next.reps)
        assertEquals(200.0, next.weight ?: 0.0, 0.001)
        assertEquals(2, next.setNumber)
    }

    @Test
    fun `last session wins over the plan`() {
        val historical = SessionExerciseWithSets(
            exercise = sessionExercise(id = 9L, sessionId = 1L, exerciseId = 3L, displayName = "Bench"),
            sets = listOf(setLog(id = 5L, sessionExerciseId = 9L, reps = 7, weight = 195.0)),
        )
        val next = SetPrefill.nextSet(
            sessionExerciseId = exerciseId,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.LB,
            historical = historical,
            targets = SetTargets(targetRepMin = 6, targetWeight = 185.0),
        )
        assertEquals(7, next.reps)
        assertEquals(195.0, next.weight ?: 0.0, 0.001)
    }

    @Test
    fun `an uncompleted historical set is not used as a template when a completed one exists`() {
        val historical = SessionExerciseWithSets(
            exercise = sessionExercise(id = 9L, sessionId = 1L, exerciseId = 3L, displayName = "Bench"),
            sets = listOf(
                setLog(id = 5L, sessionExerciseId = 9L, setNumber = 1, reps = 7, weight = 195.0, completed = true),
                setLog(id = 6L, sessionExerciseId = 9L, setNumber = 2, reps = 99, weight = 999.0, completed = false),
            ),
        )
        val next = SetPrefill.nextSet(
            sessionExerciseId = exerciseId,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.LB,
            historical = historical,
            targets = SetTargets(),
        )
        // The abandoned row is not evidence of anything.
        assertEquals(7, next.reps)
        assertEquals(195.0, next.weight ?: 0.0, 0.001)
    }

    @Test
    fun `no plan and no history leaves the fields empty rather than guessing`() {
        val next = SetPrefill.nextSet(
            sessionExerciseId = exerciseId,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.KG,
            historical = null,
            targets = SetTargets(),
        )
        assertNull(next.reps)
        assertNull(next.weight)
        assertEquals(ExerciseUnit.KG, next.weightUnit)
    }

    @Test
    fun `a duration target prefills timed work`() {
        val next = SetPrefill.nextSet(
            sessionExerciseId = exerciseId,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.SECONDS,
            historical = null,
            targets = SetTargets(targetDurationSeconds = 45),
        )
        assertEquals(45, next.durationSeconds)
        assertNull(next.reps)
    }
}

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

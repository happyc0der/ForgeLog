package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetFieldVisibilityTest {
    @Test
    fun loadedWeightShowsRepsAndWeight() {
        assertEquals(
            setOf(SetInputField.REPS, SetInputField.WEIGHT),
            SetFieldVisibility.defaults(ExerciseUnit.LB),
        )
        assertTrue(SetFieldVisibility.isVisible(SetInputField.REPS, ExerciseUnit.KG, emptySet()))
        assertFalse(SetFieldVisibility.isVisible(SetInputField.DURATION, ExerciseUnit.KG, emptySet()))
    }

    @Test
    fun bodyweightShowsRepsOnly() {
        assertEquals(setOf(SetInputField.REPS), SetFieldVisibility.defaults(ExerciseUnit.BODYWEIGHT))
        assertFalse(SetFieldVisibility.isVisible(SetInputField.WEIGHT, ExerciseUnit.BODYWEIGHT, emptySet()))
    }

    @Test
    fun secondsShowsDuration() {
        assertEquals(setOf(SetInputField.DURATION), SetFieldVisibility.defaults(ExerciseUnit.SECONDS))
    }

    @Test
    fun metersShowsDistance() {
        assertEquals(setOf(SetInputField.DISTANCE), SetFieldVisibility.defaults(ExerciseUnit.METERS))
    }

    @Test
    fun revealedFieldsBecomeVisible() {
        val revealed = setOf(SetInputField.DURATION, SetInputField.DISTANCE)
        assertTrue(SetFieldVisibility.isVisible(SetInputField.DURATION, ExerciseUnit.LB, revealed))
        assertTrue(SetFieldVisibility.isVisible(SetInputField.WEIGHT, ExerciseUnit.LB, revealed))
    }

    @Test
    fun aPlannedWeightOnATimedExerciseIsShown() {
        // A farmer's walk: 60 lb for 40 seconds.
        val walk = sessionExercise(id = 1L, sessionId = 1L, exerciseId = 1L, displayName = "Farmer's walk")
            .copy(targetWeight = 60.0, targetDurationSeconds = 40)
        val inUse = SetFieldVisibility.inUse(walk, previousSets = emptyList())
        assertEquals(setOf(SetInputField.WEIGHT, SetInputField.DURATION), inUse)
        assertTrue(SetFieldVisibility.isVisible(SetInputField.WEIGHT, ExerciseUnit.SECONDS, emptySet(), inUse))
        assertFalse(SetFieldVisibility.isVisible(SetInputField.REPS, ExerciseUnit.SECONDS, emptySet(), inUse))
    }

    @Test
    fun aWeightUsedLastSessionIsShownAgain() {
        val pullUp = sessionExercise(id = 1L, sessionId = 1L, exerciseId = 1L, displayName = "Pull-up")
        val lastTime = listOf(setLog(reps = 5, weight = 10.0))
        val inUse = SetFieldVisibility.inUse(pullUp, lastTime)
        assertTrue(SetFieldVisibility.isVisible(SetInputField.WEIGHT, ExerciseUnit.BODYWEIGHT, emptySet(), inUse))
    }

    @Test
    fun nothingPlannedOrUsedAddsNothing() {
        val hang = sessionExercise(id = 1L, sessionId = 1L, exerciseId = 1L, displayName = "Dead hang")
            .copy(targetDurationSeconds = 30)
        val inUse = SetFieldVisibility.inUse(hang, listOf(setLog(reps = null, weight = null, durationSeconds = 30)))
        assertEquals(setOf(SetInputField.DURATION), inUse)
        assertFalse(SetFieldVisibility.isVisible(SetInputField.WEIGHT, ExerciseUnit.SECONDS, emptySet(), inUse))
    }
}

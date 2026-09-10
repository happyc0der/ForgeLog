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
}

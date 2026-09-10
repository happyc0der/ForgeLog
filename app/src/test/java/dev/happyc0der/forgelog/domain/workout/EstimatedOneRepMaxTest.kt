package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimatedOneRepMaxTest {

    @Test
    fun workingSetWithOneToTenRepsAndLoadIsValid() {
        val set = setLog(reps = 5, weight = 225.0, weightUnit = ExerciseUnit.LB)

        assertTrue(EstimatedOneRepMax.isValid(set))
        assertEquals(225.0 * (1.0 + 5.0 / 30.0), EstimatedOneRepMax.pounds(set)!!, 0.0001)
    }

    @Test
    fun singleRepEqualsTheLoadedWeight() {
        val set = setLog(reps = 1, weight = 315.0)

        assertTrue(EstimatedOneRepMax.isValid(set))
        assertEquals(315.0, EstimatedOneRepMax.pounds(set)!!, 0.0001)
    }

    @Test
    fun kilogramSetsConvertBeforeEstimating() {
        val set = setLog(reps = 3, weight = 100.0, weightUnit = ExerciseUnit.KG)
        val expected = 100.0 * KG_TO_LB * (1.0 + 3.0 / 30.0)

        assertTrue(EstimatedOneRepMax.isValid(set))
        assertEquals(expected, EstimatedOneRepMax.pounds(set)!!, 0.0001)
    }

    @Test
    fun failureSetsAreValid() {
        val set = setLog(reps = 4, weight = 185.0, setType = SetType.FAILURE)

        assertTrue(EstimatedOneRepMax.isValid(set))
    }

    @Test
    fun incompleteSetIsInvalid() {
        val set = setLog(completed = false, completedAt = null)

        assertFalse(EstimatedOneRepMax.isValid(set))
        assertNull(EstimatedOneRepMax.pounds(set))
    }

    @Test
    fun warmupSetIsInvalid() {
        val set = setLog(setType = SetType.WARMUP)

        assertFalse(EstimatedOneRepMax.isValid(set))
        assertNull(EstimatedOneRepMax.pounds(set))
    }

    @Test
    fun dropSetIsInvalid() {
        val set = setLog(setType = SetType.DROP, reps = 8, weight = 95.0)

        assertFalse(EstimatedOneRepMax.isValid(set))
    }

    @Test
    fun customSetIsInvalid() {
        val set = setLog(setType = SetType.CUSTOM)

        assertFalse(EstimatedOneRepMax.isValid(set))
    }

    @Test
    fun moreThanTenRepsIsInvalid() {
        val set = setLog(reps = 12, weight = 135.0)

        assertFalse(EstimatedOneRepMax.isValid(set))
        assertNull(EstimatedOneRepMax.pounds(set))
    }

    @Test
    fun zeroOrMissingLoadIsInvalid() {
        assertFalse(EstimatedOneRepMax.isValid(setLog(weight = 0.0)))
        assertFalse(EstimatedOneRepMax.isValid(setLog(weight = null)))
        assertFalse(EstimatedOneRepMax.isValid(setLog(reps = null)))
    }

    @Test
    fun nonLoadedUnitsAreInvalid() {
        assertFalse(
            EstimatedOneRepMax.isValid(
                setLog(weight = 0.0, weightUnit = ExerciseUnit.BODYWEIGHT),
            ),
        )
        assertFalse(
            EstimatedOneRepMax.isValid(
                setLog(weight = 10.0, weightUnit = ExerciseUnit.SECONDS),
            ),
        )
        assertFalse(
            EstimatedOneRepMax.isValid(
                setLog(weight = 10.0, weightUnit = ExerciseUnit.METERS),
            ),
        )
    }
}

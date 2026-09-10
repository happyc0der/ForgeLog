package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeCalculatorTest {

    @Test
    fun completedWorkingSetsContributeLoadVolume() {
        val sets = listOf(
            setLog(id = 1, setNumber = 1, reps = 5, weight = 100.0),
            setLog(id = 2, setNumber = 2, reps = 5, weight = 100.0),
            setLog(id = 3, setNumber = 3, reps = 5, weight = 100.0),
        )

        val volume = VolumeCalculator.calculate(sets)

        assertEquals(1500.0, volume.loadLb, 0.0001)
        assertEquals(3, volume.completedSetCount)
        assertEquals(15, volume.totalReps)
    }

    @Test
    fun warmupSetsAreExcludedFromVolumeByDefault() {
        val sets = listOf(
            setLog(id = 1, setNumber = 1, setType = SetType.WARMUP, reps = 8, weight = 45.0),
            setLog(id = 2, setNumber = 2, reps = 5, weight = 135.0),
        )

        val volume = VolumeCalculator.calculate(sets)

        assertEquals(675.0, volume.loadLb, 0.0001)
        assertEquals(1, volume.completedSetCount)
    }

    @Test
    fun warmupSetsCanBeIncluded() {
        val sets = listOf(
            setLog(id = 1, setNumber = 1, setType = SetType.WARMUP, reps = 8, weight = 45.0),
            setLog(id = 2, setNumber = 2, reps = 5, weight = 135.0),
        )

        val volume = VolumeCalculator.calculate(sets, includeWarmup = true)

        assertEquals(1035.0, volume.loadLb, 0.0001)
        assertEquals(2, volume.completedSetCount)
    }

    @Test
    fun incompleteSetsDoNotCount() {
        val sets = listOf(
            setLog(id = 1, reps = 5, weight = 200.0, completed = false, completedAt = null),
            setLog(id = 2, reps = 3, weight = 200.0, completed = true),
        )

        val volume = VolumeCalculator.calculate(sets)

        assertEquals(600.0, volume.loadLb, 0.0001)
        assertEquals(1, volume.completedSetCount)
    }

    @Test
    fun kilogramSetsAreConvertedToPounds() {
        val sets = listOf(
            setLog(reps = 2, weight = 100.0, weightUnit = ExerciseUnit.KG),
        )

        val volume = VolumeCalculator.calculate(sets)

        assertEquals(2 * 100.0 * KG_TO_LB, volume.loadLb, 0.0001)
    }

    @Test
    fun nonLoadedUnitsDoNotAddLoadVolume() {
        val sets = listOf(
            setLog(reps = 10, weight = null, weightUnit = ExerciseUnit.BODYWEIGHT),
            setLog(reps = null, weight = null, weightUnit = ExerciseUnit.SECONDS, durationSeconds = 40),
            setLog(reps = null, weight = null, weightUnit = ExerciseUnit.METERS, distanceMeters = 12.5),
        )

        val volume = VolumeCalculator.calculate(sets)

        assertEquals(0.0, volume.loadLb, 0.0001)
        assertEquals(3, volume.completedSetCount)
        assertEquals(10, volume.totalReps)
        assertEquals(40, volume.totalDurationSeconds)
        assertEquals(12.5, volume.totalDistanceMeters, 0.0001)
    }

    @Test
    fun sessionVolumeSumsEachExercise() {
        val bench = listOf(setLog(sessionExerciseId = 1, reps = 5, weight = 100.0))
        val squat = listOf(setLog(sessionExerciseId = 2, reps = 3, weight = 200.0))

        val volume = VolumeCalculator.sessionVolume(listOf(bench, squat))

        assertEquals(1100.0, volume.loadLb, 0.0001)
        assertEquals(2, volume.completedSetCount)
        assertEquals(8, volume.totalReps)
    }
}

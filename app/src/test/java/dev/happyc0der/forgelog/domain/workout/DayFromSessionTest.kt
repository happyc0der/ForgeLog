package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayFromSessionTest {

    private val noPlan = DayTargets()

    @Test
    fun `the day asks for what was done in working sets`() {
        val sets = listOf(
            setLog(id = 1, setType = SetType.WARMUP, reps = 10, weight = 95.0),
            setLog(id = 2, reps = 8, weight = 185.0),
            setLog(id = 3, reps = 7, weight = 185.0),
            setLog(id = 4, reps = 6, weight = 195.0),
            // Added and never ticked: it did not happen.
            setLog(id = 5, reps = 12, weight = 225.0, completed = false, completedAt = null),
        )

        val targets = DayFromSession.targets(noPlan, sets, exerciseUnit = ExerciseUnit.LB)

        assertEquals(3, targets.plannedSets)
        assertEquals(6, targets.targetRepMin)
        assertEquals(8, targets.targetRepMax)
        assertEquals(195.0, targets.targetWeight!!, 0.0)
    }

    @Test
    fun `a weight logged in kilograms is carried in the exercise's own unit`() {
        val sets = listOf(setLog(reps = 5, weight = 100.0, weightUnit = ExerciseUnit.KG))

        val targets = DayFromSession.targets(noPlan, sets, exerciseUnit = ExerciseUnit.LB)

        assertEquals("100 kg to the nearest half pound", 220.5, targets.targetWeight!!, 0.0)
    }

    @Test
    fun `a timed lift keeps its longest hold`() {
        val sets = listOf(
            setLog(id = 1, reps = null, weight = null, durationSeconds = 40),
            setLog(id = 2, reps = null, weight = null, durationSeconds = 55),
        )

        val targets = DayFromSession.targets(noPlan, sets, exerciseUnit = ExerciseUnit.SECONDS)

        assertEquals(2, targets.plannedSets)
        assertEquals(55, targets.targetDurationSeconds)
        assertNull(targets.targetRepMin)
        assertNull(targets.targetWeight)
    }

    @Test
    fun `the workout's plan fills in what the log cannot say`() {
        val plan = DayTargets(plannedSets = 4, targetRepMin = 5, targetRepMax = 8, targetWeight = 135.0, targetRestSeconds = 180)

        // Nothing was ticked: the plan stands in full, rest included.
        val untouched = DayFromSession.targets(plan, sets = emptyList(), exerciseUnit = ExerciseUnit.LB)
        assertEquals(plan, untouched)

        // Something was done: the log wins, but rest still comes from the plan.
        val done = DayFromSession.targets(plan, listOf(setLog(reps = 6, weight = 145.0)), ExerciseUnit.LB)
        assertEquals(1, done.plannedSets)
        assertEquals(145.0, done.targetWeight!!, 0.0)
        assertEquals(180, done.targetRestSeconds)
    }
}

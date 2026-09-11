package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {

    private fun plan(sets: Int? = 3, repMin: Int? = 5, repMax: Int? = 5, weight: Double? = null) =
        DayTargets(plannedSets = sets, targetRepMin = repMin, targetRepMax = repMax, targetWeight = weight)

    private fun sets(vararg reps: Int, weight: Double = 155.0, unit: ExerciseUnit = ExerciseUnit.LB) =
        reps.mapIndexed { i, r -> setLog(id = i + 1L, setNumber = i + 1, reps = r, weight = weight, weightUnit = unit) }

    @Test
    fun `a lower-body lift that hit every rep goes up 5 to 10 lb`() {
        // Back squat, 3 x 5 at 155.
        val hint = Progression.hint(plan(), sets(5, 5, 5), ExerciseCategory.LEGS, ExerciseUnit.LB)
        assertEquals(listOf(160.0, 165.0), hint?.options)
        assertEquals(3, hint?.setCount)
        assertEquals(5, hint?.reps)
        assertEquals(155.0, hint?.fromWeight ?: 0.0, 0.0)
    }

    @Test
    fun `an upper-body lift goes up 5 lb`() {
        // Bench, 4 x 4-5 at 95, every set at 5.
        val hint = Progression.hint(
            plan(sets = 4, repMin = 4, repMax = 5),
            sets(5, 5, 5, 5, weight = 95.0),
            ExerciseCategory.PUSH,
            ExerciseUnit.LB,
        )
        assertEquals(listOf(100.0), hint?.options)
    }

    @Test
    fun `one set short of the top of the range earns nothing`() {
        val hint = Progression.hint(
            plan(sets = 4, repMin = 4, repMax = 5),
            sets(4, 5, 5, 5, weight = 95.0),
            ExerciseCategory.PUSH,
            ExerciseUnit.LB,
        )
        assertNull(hint)
    }

    @Test
    fun `fewer sets than planned earns nothing`() {
        assertNull(Progression.hint(plan(sets = 3), sets(5, 5), ExerciseCategory.LEGS, ExerciseUnit.LB))
    }

    @Test
    fun `an extra set beyond the plan does not block the jump`() {
        val hint = Progression.hint(plan(sets = 3), sets(5, 5, 5, 3), ExerciseCategory.LEGS, ExerciseUnit.LB)
        assertEquals(listOf(160.0, 165.0), hint?.options)
    }

    @Test
    fun `warm-ups and lighter ramp-up sets are left out`() {
        val lastTime = listOf(
            setLog(id = 1L, setNumber = 1, reps = 10, weight = 45.0, setType = SetType.WARMUP),
            // A lighter set not marked as a warm-up.
            setLog(id = 2L, setNumber = 2, reps = 3, weight = 135.0),
        ) + sets(5, 5, 5).mapIndexed { i, set -> set.copy(id = 10L + i, setNumber = 3 + i) }
        val hint = Progression.hint(plan(), lastTime, ExerciseCategory.LEGS, ExerciseUnit.LB)
        assertEquals(155.0, hint?.fromWeight ?: 0.0, 0.0)
    }

    @Test
    fun `a fixed count with only a minimum uses it as the top`() {
        val hint = Progression.hint(plan(repMax = null), sets(5, 5, 5), ExerciseCategory.PULL, ExerciseUnit.LB)
        assertEquals(listOf(160.0), hint?.options)
    }

    @Test
    fun `no suggestion once today's plan already asks for the jump`() {
        assertNull(Progression.hint(plan(weight = 160.0), sets(5, 5, 5), ExerciseCategory.LEGS, ExerciseUnit.LB))
        // Still offered while today's plan is the old weight.
        val hint = Progression.hint(plan(weight = 155.0), sets(5, 5, 5), ExerciseCategory.LEGS, ExerciseUnit.LB)
        assertEquals(listOf(160.0, 165.0), hint?.options)
    }

    @Test
    fun `kilograms go up in kilogram steps`() {
        val hint = Progression.hint(plan(), sets(5, 5, 5, weight = 70.0, unit = ExerciseUnit.KG), ExerciseCategory.LEGS, ExerciseUnit.KG)
        assertEquals(listOf(72.5, 75.0), hint?.options)
    }

    @Test
    fun `sets logged in kg are read in the lift's own unit`() {
        // 70 kg is 154.5 lb.
        val hint = Progression.hint(plan(), sets(5, 5, 5, weight = 70.0, unit = ExerciseUnit.KG), ExerciseCategory.PUSH, ExerciseUnit.LB)
        assertEquals(154.5, hint?.fromWeight ?: 0.0, 0.0)
        assertEquals(listOf(159.5), hint?.options)
    }

    @Test
    fun `nothing to suggest without a rep target, a weight, or a loaded unit`() {
        assertNull(Progression.hint(plan(repMin = null, repMax = null), sets(5, 5, 5), ExerciseCategory.LEGS, ExerciseUnit.LB))
        // Pull-ups: bodyweight.
        assertNull(Progression.hint(plan(), sets(5, 5, 5), ExerciseCategory.PULL, ExerciseUnit.BODYWEIGHT))
        val unweighted = sets(5, 5, 5).map { it.copy(weight = null) }
        assertNull(Progression.hint(plan(), unweighted, ExerciseCategory.LEGS, ExerciseUnit.LB))
        assertNull(Progression.hint(plan(), emptyList(), ExerciseCategory.LEGS, ExerciseUnit.LB))
    }

    @Test
    fun `sets that were not ticked do not count`() {
        val lastTime = sets(5, 5, 5).mapIndexed { i, set -> if (i == 2) set.copy(completed = false) else set }
        assertNull(Progression.hint(plan(), lastTime, ExerciseCategory.LEGS, ExerciseUnit.LB))
    }
}

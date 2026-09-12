package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The one thing the app tells the lifter to go and do, held to being safe advice.
 *
 * Everything else here reports what happened; this decides what to load next, and the user acts on
 * it with a barbell. So the properties are about the shape of the advice rather than particular
 * numbers: never lighter than what was just lifted, never a jump bigger than the increment the rule
 * allows, and never offered when the plan already asks for it.
 */
class ProgressionPropertyTest {

    private val loaded = listOf(ExerciseUnit.LB, ExerciseUnit.KG)
    private val unloaded = listOf(ExerciseUnit.BODYWEIGHT, ExerciseUnit.SECONDS, ExerciseUnit.METERS)
    private val categories = ExerciseCategory.entries + listOf(null)

    /** The increments the rule allows, restated here so the test does not ask the code. */
    private fun allowed(category: ExerciseCategory?, unit: ExerciseUnit): List<Double> {
        val lower = category == ExerciseCategory.LEGS
        return when (unit) {
            ExerciseUnit.KG -> if (lower) listOf(2.5, 5.0) else listOf(2.5)
            else -> if (lower) listOf(5.0, 10.0) else listOf(5.0)
        }
    }

    private fun sets(
        reps: List<Int>,
        weight: Double,
        unit: ExerciseUnit,
        completed: Boolean = true,
        type: SetType = SetType.WORKING,
    ) = reps.mapIndexed { index, r ->
        setLog(
            id = index + 1L,
            setNumber = index + 1,
            reps = r,
            weight = weight,
            weightUnit = unit,
            completed = completed,
            setType = type,
        )
    }

    @Test
    fun adviceIsNeverLighterThanWhatWasJustLifted() {
        val random = Random(6543)
        repeat(4_000) { run ->
            val unit = loaded[random.nextInt(loaded.size)]
            val category = categories[random.nextInt(categories.size)]
            val weight = random.nextInt(1, 500) + if (random.nextBoolean()) 0.5 else 0.0
            val top = random.nextInt(1, 15)
            val count = random.nextInt(1, 6)
            val plan = DayTargets(
                plannedSets = count,
                targetRepMin = top,
                targetRepMax = top,
                targetWeight = if (random.nextInt(3) == 0) weight else null,
            )
            val hint = Progression.hint(
                targets = plan,
                lastSets = sets(List(count) { top }, weight, unit),
                category = category,
                unit = unit,
            ) ?: return@repeat

            assertTrue("run $run: no options at all", hint.options.isNotEmpty())
            hint.options.forEach { option ->
                assertTrue(
                    "run $run: suggested $option having lifted ${hint.fromWeight}",
                    option > hint.fromWeight,
                )
            }
            assertEquals(
                "run $run: options are not in ascending order",
                hint.options.sorted(),
                hint.options,
            )
            assertEquals("run $run: the hint reports the wrong unit", unit, hint.unit)
            assertEquals("run $run: the hint forgot what was lifted", weight, hint.fromWeight, 1e-9)
        }
    }

    /** Every jump is one of the increments the rule allows, to within the half-unit rounding. */
    @Test
    fun everyJumpIsOneTheRuleAllows() {
        val random = Random(777)
        repeat(4_000) {
            val unit = loaded[random.nextInt(loaded.size)]
            val category = categories[random.nextInt(categories.size)]
            val weight = random.nextInt(1, 500).toDouble()
            val top = random.nextInt(1, 15)
            val count = random.nextInt(1, 6)
            val hint = Progression.hint(
                targets = DayTargets(plannedSets = count, targetRepMin = top, targetRepMax = top),
                lastSets = sets(List(count) { top }, weight, unit),
                category = category,
                unit = unit,
            ) ?: return@repeat

            val steps = allowed(category, unit)
            assertEquals("a different number of options than the rule has steps", steps.size, hint.options.size)
            hint.options.forEachIndexed { index, option ->
                val jump = option - hint.fromWeight
                assertTrue(
                    "jumped $jump from ${hint.fromWeight} in $unit/$category, expected ${steps[index]}",
                    abs(jump - steps[index]) <= 0.25,
                )
            }
        }
    }

    /** A plan that already asks for the jump is not told to jump again. */
    @Test
    fun adviceRetiresOnceThePlanAsksForIt() {
        val random = Random(31)
        repeat(3_000) {
            val unit = loaded[random.nextInt(loaded.size)]
            val category = categories[random.nextInt(categories.size)]
            val weight = random.nextInt(1, 400).toDouble()
            val top = random.nextInt(1, 12)
            val count = random.nextInt(1, 5)
            val lastSets = sets(List(count) { top }, weight, unit)

            val first = Progression.hint(
                targets = DayTargets(plannedSets = count, targetRepMin = top, targetRepMax = top),
                lastSets = lastSets,
                category = category,
                unit = unit,
            ) ?: return@repeat

            // Take the suggestion: the plan now asks for the lighter option.
            val taken = Progression.hint(
                targets = DayTargets(
                    plannedSets = count,
                    targetRepMin = top,
                    targetRepMax = top,
                    targetWeight = first.options.first(),
                ),
                lastSets = lastSets,
                category = category,
                unit = unit,
            )
            assertNull("kept suggesting a jump the plan already asks for", taken)
        }
    }

    @Test
    fun nothingUnloadedIsEverGivenAWeightToAdd() {
        val random = Random(99)
        repeat(2_000) {
            val unit = unloaded[random.nextInt(unloaded.size)]
            val top = random.nextInt(1, 15)
            val count = random.nextInt(1, 6)
            assertNull(
                "suggested adding weight to a $unit lift",
                Progression.hint(
                    targets = DayTargets(plannedSets = count, targetRepMin = top, targetRepMax = top),
                    lastSets = sets(List(count) { top }, 100.0, unit),
                    category = categories[random.nextInt(categories.size)],
                    unit = unit,
                ),
            )
        }
    }

    /** Short of the rep target, or short of the set count, earns nothing. */
    @Test
    fun fallingShortEarnsNoJump() {
        val random = Random(1001)
        repeat(3_000) {
            val unit = loaded[random.nextInt(loaded.size)]
            val top = random.nextInt(2, 15)
            val planned = random.nextInt(2, 6)
            val weight = random.nextInt(1, 400).toDouble()

            // One set a rep short of the top of the range.
            val oneShort = List(planned) { index -> if (index == 0) top - 1 else top }
            assertNull(
                "a set short of the rep target still earned a jump",
                Progression.hint(
                    targets = DayTargets(plannedSets = planned, targetRepMin = top, targetRepMax = top),
                    lastSets = sets(oneShort, weight, unit),
                    category = null,
                    unit = unit,
                ),
            )

            // Every rep reached, but fewer sets than planned.
            assertNull(
                "fewer sets than planned still earned a jump",
                Progression.hint(
                    targets = DayTargets(plannedSets = planned, targetRepMin = top, targetRepMax = top),
                    lastSets = sets(List(planned - 1) { top }, weight, unit),
                    category = null,
                    unit = unit,
                ),
            )
        }
    }

    /** Warmups and unticked sets are not the claim, so neither earns a jump on its own. */
    @Test
    fun warmupsAndUntickedSetsEarnNothing() {
        val random = Random(2002)
        repeat(2_000) {
            val unit = loaded[random.nextInt(loaded.size)]
            val top = random.nextInt(1, 12)
            val count = random.nextInt(1, 5)
            val weight = random.nextInt(1, 400).toDouble()

            assertNull(
                "a warmup earned a jump",
                Progression.hint(
                    targets = DayTargets(plannedSets = count, targetRepMin = top, targetRepMax = top),
                    lastSets = sets(List(count) { top }, weight, unit, type = SetType.WARMUP),
                    category = null,
                    unit = unit,
                ),
            )
            assertNull(
                "an unticked set earned a jump",
                Progression.hint(
                    targets = DayTargets(plannedSets = count, targetRepMin = top, targetRepMax = top),
                    lastSets = sets(List(count) { top }, weight, unit, completed = false),
                    category = null,
                    unit = unit,
                ),
            )
        }
    }
}

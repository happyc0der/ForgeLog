package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * What the next set arrives pre-filled with, which the lifter will tick without re-reading.
 *
 * That is what makes a wrong prefill worse than a blank one: the number goes into the log as though
 * it were typed. The one that would do real damage is a weight arriving with someone else's unit --
 * a planned 225 lb shown as 225 kg is half a tonne of training history that never happened -- so
 * these hold the weight and its unit to coming from the same place, whichever place that is.
 */
class SetPrefillPropertyTest {

    private val units = ExerciseUnit.entries

    private fun randomSet(random: Random, setNumber: Int): SetLog = setLog(
        id = setNumber.toLong(),
        setNumber = setNumber,
        setType = if (random.nextInt(4) == 0) SetType.WARMUP else SetType.WORKING,
        reps = random.nextInt(1, 15).takeIf { random.nextInt(5) != 0 },
        weight = random.nextInt(1, 300).toDouble().takeIf { random.nextInt(4) != 0 },
        weightUnit = units[random.nextInt(units.size)],
        durationSeconds = random.nextInt(10, 200).takeIf { random.nextInt(3) == 0 },
        distanceMeters = random.nextInt(10, 5_000).toDouble().takeIf { random.nextInt(6) == 0 },
        restAfterSetSeconds = random.nextInt(30, 300).takeIf { random.nextInt(3) != 0 },
        completed = random.nextInt(4) != 0,
    )

    private fun randomTargets(random: Random) = SetTargets(
        targetRepMin = random.nextInt(1, 12).takeIf { random.nextInt(3) != 0 },
        targetRepMax = random.nextInt(1, 20).takeIf { random.nextInt(3) != 0 },
        targetWeight = random.nextInt(1, 400).toDouble().takeIf { random.nextInt(3) != 0 },
        targetDurationSeconds = random.nextInt(10, 300).takeIf { random.nextInt(4) == 0 },
        targetRestSeconds = random.nextInt(30, 300).takeIf { random.nextInt(3) != 0 },
    )

    private class Case(
        val existing: List<SetLog>,
        val historical: SessionExerciseWithSets?,
        val targets: SetTargets,
        val defaultUnit: ExerciseUnit,
    )

    private fun randomCase(random: Random): Case {
        val existing = (1..random.nextInt(0, 4)).map { randomSet(random, it) }
        val history = (1..random.nextInt(0, 4)).map { randomSet(random, it) }
        return Case(
            existing = existing,
            historical = if (random.nextInt(4) == 0) {
                null
            } else {
                SessionExerciseWithSets(
                    exercise = sessionExercise(
                        id = 10L,
                        sessionId = 1L,
                        exerciseId = 1L,
                        displayName = "Bench Press",
                    ),
                    sets = history,
                )
            },
            targets = if (random.nextInt(4) == 0) SetTargets() else randomTargets(random),
            defaultUnit = units[random.nextInt(units.size)],
        )
    }

    private fun prefill(case: Case): SetLog = SetPrefill.nextSet(
        sessionExerciseId = 10L,
        existing = case.existing,
        defaultUnit = case.defaultUnit,
        historical = case.historical,
        targets = case.targets,
    )

    @Test
    fun aPrefilledWeightNeverArrivesInAnotherSourcesUnit() {
        val random = Random(24680)
        repeat(5_000) { run ->
            val case = randomCase(random)
            val filled = prefill(case)

            val current = case.existing.maxByOrNull { it.setNumber }
            val previous = case.historical?.sets?.filter { it.completed }?.maxByOrNull { it.setNumber }
                ?: case.historical?.sets?.maxByOrNull { it.setNumber }

            val expectedUnit = when {
                current?.weight != null -> current.weightUnit
                case.targets.targetWeight != null -> case.defaultUnit
                previous?.weight != null -> previous.weightUnit
                else -> current?.weightUnit ?: previous?.weightUnit ?: case.defaultUnit
            }.asWeightUnit(fallback = case.defaultUnit)

            assertEquals("run $run: the weight came from one place and the unit from another",
                expectedUnit, filled.weightUnit)

            val expectedWeight = current?.weight ?: case.targets.targetWeight ?: previous?.weight
            assertEquals("run $run: the weight is not from the source it should be",
                expectedWeight, filled.weight)
        }
    }

    /** A weight is in pounds or kilograms. A timed or bodyweight lift's own unit is not one. */
    @Test
    fun aPrefilledUnitIsAlwaysOneAWeightCanBeIn() {
        val random = Random(13579)
        repeat(5_000) {
            val filled = prefill(randomCase(random))
            assertTrue(
                "a weight arrived in ${filled.weightUnit}",
                filled.weightUnit == ExerciseUnit.LB || filled.weightUnit == ExerciseUnit.KG,
            )
        }
    }

    @Test
    fun theNextSetFollowsTheLastAndIsNotYetDone() {
        val random = Random(11223)
        repeat(5_000) {
            val case = randomCase(random)
            val filled = prefill(case)

            assertEquals(
                "the next set does not follow the last",
                (case.existing.maxOfOrNull { it.setNumber } ?: 0) + 1,
                filled.setNumber,
            )
            assertTrue("a set number below one", filled.setNumber >= 1)
            assertFalse("a set arrived already ticked", filled.completed)
            assertNull("a set arrived already timestamped", filled.completedAt)
            assertNull("a set arrived with someone else's rpe", filled.rpe)
            assertNull("a set arrived with someone else's rir", filled.rir)
            assertNull("a set arrived with someone else's notes", filled.notes)
        }
    }

    /**
     * Today's plan beats last week's numbers, and a correction made this session beats both.
     *
     * The order matters in both directions: a target is a deliberate statement about this session,
     * so history must not override it, and a weight the lifter has just changed is the most current
     * statement of all.
     */
    @Test
    fun theSessionBeatsThePlanAndThePlanBeatsHistory() {
        val history = SessionExerciseWithSets(
            exercise = sessionExercise(id = 10L, sessionId = 1L, exerciseId = 1L, displayName = "Bench"),
            sets = listOf(setLog(setNumber = 1, reps = 12, weight = 95.0, completed = true)),
        )
        val plan = SetTargets(targetRepMin = 5, targetWeight = 185.0, targetRestSeconds = 180)

        val fromPlan = SetPrefill.nextSet(
            sessionExerciseId = 10L,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.LB,
            historical = history,
            targets = plan,
        )
        assertEquals("history overrode today's plan", 185.0, fromPlan.weight)
        assertEquals(5, fromPlan.reps)
        assertEquals(180, fromPlan.restAfterSetSeconds)

        val corrected = setLog(setNumber = 1, reps = 8, weight = 170.0, completed = true)
        val fromSession = SetPrefill.nextSet(
            sessionExerciseId = 10L,
            existing = listOf(corrected),
            defaultUnit = ExerciseUnit.LB,
            historical = history,
            targets = plan,
        )
        assertEquals("the plan overrode a correction made this session", 170.0, fromSession.weight)
        assertEquals(8, fromSession.reps)
        assertEquals(2, fromSession.setNumber)

        val noPlan = SetPrefill.nextSet(
            sessionExerciseId = 10L,
            existing = emptyList(),
            defaultUnit = ExerciseUnit.LB,
            historical = history,
            targets = SetTargets(),
        )
        assertEquals("with no plan, history should have filled it", 95.0, noPlan.weight)
        assertEquals(12, noPlan.reps)
    }
}

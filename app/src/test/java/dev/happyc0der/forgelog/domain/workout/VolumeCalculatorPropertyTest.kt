package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Volume is the number every screen in the app repeats, so the screens have to agree about it.
 *
 * Home totals a week of it, the summary totals one session, the session screen totals one lift, and
 * Analytics buckets it by day. They all arrive through here, by different routes: one call over
 * every set, or a call per lift added up. Those two have to come out the same, or two screens
 * describe the same workout differently and neither is obviously wrong.
 */
class VolumeCalculatorPropertyTest {

    private fun randomSets(random: Random, count: Int, idBase: Long): List<SetLog> =
        (0 until count).map { index ->
            val timed = random.nextInt(4) == 0
            setLog(
                id = idBase + index,
                setNumber = index + 1,
                setType = when (random.nextInt(6)) {
                    0 -> SetType.WARMUP
                    1 -> SetType.DROP
                    2 -> SetType.FAILURE
                    else -> SetType.WORKING
                },
                reps = if (timed) null else random.nextInt(-2, 15).takeIf { random.nextInt(9) != 0 },
                weight = if (timed) null else random.nextInt(-10, 400).toDouble(),
                weightUnit = when (random.nextInt(4)) {
                    0 -> ExerciseUnit.KG
                    1 -> ExerciseUnit.BODYWEIGHT
                    2 -> ExerciseUnit.SECONDS
                    else -> ExerciseUnit.LB
                },
                durationSeconds = if (timed) random.nextInt(-5, 300) else null,
                distanceMeters = if (random.nextInt(7) == 0) random.nextInt(-5, 5_000).toDouble() else null,
                completed = random.nextInt(5) != 0,
            )
        }

    @Test
    fun aSessionTotalsTheSameWhicheverWayItIsAddedUp() {
        val random = Random(2468)
        repeat(3_000) { run ->
            val perLift = (0 until random.nextInt(1, 6)).map { lift ->
                randomSets(random, random.nextInt(0, 6), idBase = lift * 100L)
            }
            val includeWarmup = random.nextBoolean()

            val byLift = VolumeCalculator.sessionVolume(perLift, includeWarmup)
            val allAtOnce = VolumeCalculator.calculate(perLift.flatten(), includeWarmup)

            assertTrue(
                "run $run: ${byLift.loadLb} by lift against ${allAtOnce.loadLb} all at once",
                abs(byLift.loadLb - allAtOnce.loadLb) < 1e-6,
            )
            assertEquals("run $run: set count", allAtOnce.completedSetCount, byLift.completedSetCount)
            assertEquals("run $run: reps", allAtOnce.totalReps, byLift.totalReps)
            assertEquals("run $run: seconds", allAtOnce.totalDurationSeconds, byLift.totalDurationSeconds)
            assertTrue(
                "run $run: distance",
                abs(byLift.totalDistanceMeters - allAtOnce.totalDistanceMeters) < 1e-6,
            )
        }
    }

    @Test
    fun noTotalIsEverNegative() {
        val random = Random(1357)
        repeat(3_000) {
            val sets = randomSets(random, random.nextInt(0, 10), idBase = 0L)
            listOf(true, false).forEach { includeWarmup ->
                val volume = VolumeCalculator.calculate(sets, includeWarmup)
                assertTrue("load ${volume.loadLb} is negative", volume.loadLb >= 0.0)
                assertTrue("reps ${volume.totalReps} is negative", volume.totalReps >= 0)
                assertTrue("seconds is negative", volume.totalDurationSeconds >= 0)
                assertTrue("distance is negative", volume.totalDistanceMeters >= 0.0)
                assertTrue("set count is negative", volume.completedSetCount >= 0)
            }
        }
    }

    /** Counting warmups can only ever add. */
    @Test
    fun countingWarmupsNeverTotalsLess() {
        val random = Random(999)
        repeat(3_000) {
            val sets = randomSets(random, random.nextInt(0, 10), idBase = 0L)
            val without = VolumeCalculator.calculate(sets, includeWarmup = false)
            val with = VolumeCalculator.calculate(sets, includeWarmup = true)

            assertTrue("counting warmups lowered the load", with.loadLb >= without.loadLb - 1e-9)
            assertTrue("counting warmups lowered the sets", with.completedSetCount >= without.completedSetCount)
            assertTrue("counting warmups lowered the reps", with.totalReps >= without.totalReps)
        }
    }

    /** Logging one more set never makes the workout smaller. */
    @Test
    fun addingASetNeverTotalsLess() {
        val random = Random(5150)
        repeat(3_000) {
            val sets = randomSets(random, random.nextInt(0, 8), idBase = 0L)
            val extra = randomSets(random, 1, idBase = 900L)

            val before = VolumeCalculator.calculate(sets, includeWarmup = true)
            val after = VolumeCalculator.calculate(sets + extra, includeWarmup = true)

            assertTrue("adding a set lowered the load", after.loadLb >= before.loadLb - 1e-9)
            assertTrue("adding a set lowered the reps", after.totalReps >= before.totalReps)
        }
    }

    /**
     * The same lift entered in kilograms and in pounds is the same volume.
     *
     * This is the reason volume is computed in pounds throughout and converted once for display: a
     * session with some lifts in kilograms and some in pounds has to add up, and it can only do
     * that in one unit.
     */
    @Test
    fun thesameLiftWeighsTheSameInEitherUnit() {
        val random = Random(80808)
        repeat(2_000) {
            val reps = random.nextInt(1, 20)
            val kilos = random.nextInt(1, 300).toDouble()

            val inKg = VolumeCalculator.calculate(
                listOf(setLog(reps = reps, weight = kilos, weightUnit = ExerciseUnit.KG)),
            )
            val inLb = VolumeCalculator.calculate(
                listOf(setLog(reps = reps, weight = kilos * KG_TO_LB, weightUnit = ExerciseUnit.LB)),
            )

            assertTrue(
                "$reps x $kilos kg came to ${inKg.loadLb} lb one way and ${inLb.loadLb} the other",
                abs(inKg.loadLb - inLb.loadLb) < 1e-6,
            )
        }
    }

    /** Only loaded units carry weight; a bodyweight or timed set adds none. */
    @Test
    fun anUnloadedSetAddsNoLoad() {
        listOf(ExerciseUnit.BODYWEIGHT, ExerciseUnit.SECONDS, ExerciseUnit.METERS).forEach { unit ->
            val volume = VolumeCalculator.calculate(
                listOf(setLog(reps = 10, weight = 100.0, weightUnit = unit)),
            )
            assertEquals("a $unit set was weighed", 0.0, volume.loadLb, 0.0)
            assertEquals("a $unit set was still a set", 1, volume.completedSetCount)
            assertEquals("a $unit set's reps still counted", 10, volume.totalReps)
        }
    }
}

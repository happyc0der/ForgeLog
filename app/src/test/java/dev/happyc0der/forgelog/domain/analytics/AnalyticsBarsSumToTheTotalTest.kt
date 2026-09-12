package dev.happyc0der.forgelog.domain.analytics

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.time.WeekBoundary
import dev.happyc0der.forgelog.domain.workout.sessionDetail
import dev.happyc0der.forgelog.domain.workout.sessionExercise
import dev.happyc0der.forgelog.domain.workout.setLog
import dev.happyc0der.forgelog.domain.workout.workoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import kotlin.math.abs
import kotlin.random.Random

/**
 * The bars add up to the total printed above them.
 *
 * Analytics shows one window of training twice on the same screen: as a volume figure in the stat
 * tiles, and as a row of bars underneath. They are computed by different code down different paths
 * -- metrics() sums every session, volumeByDay() sorts sessions into buckets and sums each -- and
 * nothing checked that the two agree. If they ever disagree the screen contradicts itself, and
 * there is no way for the reader to tell which half is lying.
 *
 * The bucket width is whatever the screen would choose, so this also covers the day, week and month
 * cases and the alignment that comes with them: week and month buckets start before the range does,
 * which is exactly the sort of edge that loses a session from a bar.
 */
class AnalyticsBarsSumToTheTotalTest {

    private val zones = listOf("Asia/Kolkata", "America/New_York", "Australia/Lord_Howe", "UTC")
        .map(ZoneId::of)

    private val day = 24L * 60 * 60 * 1000

    /** Sessions scattered across [spanDays] from [start], as the range query would return them. */
    private fun sessionsIn(random: Random, start: Long, spanDays: Int): List<SessionDetail> =
        (0 until random.nextInt(0, 14)).map { index ->
            val startedAt = start + random.nextLong(0, spanDays * day)
            val timed = random.nextInt(4) == 0
            sessionDetail(
                workoutSession(
                    id = index + 1L,
                    status = SessionStatus.COMPLETED,
                    startedAt = startedAt,
                    completedAt = startedAt + 3_600_000,
                ),
                *(0 until random.nextInt(1, 4)).map { lift ->
                    SessionExerciseWithSets(
                        exercise = sessionExercise(
                            id = (index + 1L) * 10 + lift,
                            sessionId = index + 1L,
                            exerciseId = lift + 1L,
                            displayName = "Lift ${lift + 1}",
                            exerciseOrder = lift,
                        ),
                        sets = (0 until random.nextInt(1, 5)).map { setIndex ->
                            setLog(
                                id = (index + 1L) * 100 + lift * 10 + setIndex,
                                setNumber = setIndex + 1,
                                setType = if (random.nextInt(5) == 0) SetType.WARMUP else SetType.WORKING,
                                reps = if (timed) null else random.nextInt(1, 13),
                                weight = if (timed) null else random.nextInt(1, 300).toDouble(),
                                weightUnit = if (random.nextBoolean()) ExerciseUnit.LB else ExerciseUnit.KG,
                                durationSeconds = if (timed) random.nextInt(20, 120) else null,
                                completed = random.nextInt(6) != 0,
                            )
                        },
                    )
                }.toTypedArray(),
            )
        }

    @Test
    fun theBarsSumToTheFigureAboveThem() {
        val random = Random(515151)
        var covered = 0
        repeat(2_000) { run ->
            val zone = zones[random.nextInt(zones.size)]
            val weekStart = DayOfWeek.entries[random.nextInt(7)]
            val includeWarmup = random.nextBoolean()
            // Spans that land on each bucket width the screen can choose.
            val spanDays = listOf(1, 7, 14, 15, 60, 200, 400)[random.nextInt(7)]

            val start = WeekBoundary.startOfDay(1_700_000_000_000L + random.nextLong(0, 300 * day), zone)
            val range = WeekBoundary.Range(start, start + spanDays * day)
            val details = sessionsIn(random, start, spanDays)

            val bars = VolumeBuckets.of(range, zone, weekStart)
            val byDay = AnalyticsAggregator.volumeByDay(details, bars.ranges, includeWarmup)
            val totals = AnalyticsAggregator.metrics(details, includeWarmup)

            val barLoad = byDay.sumOf { it.loadLb }
            val barSets = byDay.sumOf { it.setCount }

            assertTrue(
                "run $run ($zone, $weekStart, $spanDays days, ${bars.bucket}): bars total " +
                    "$barLoad against a printed total of ${totals.loadLb}",
                abs(barLoad - totals.loadLb) < 1e-6,
            )
            assertEquals(
                "run $run ($zone, $weekStart, $spanDays days, ${bars.bucket}): bar set counts",
                totals.totalSets,
                barSets,
            )
            if (details.isNotEmpty()) covered++
        }
        assertTrue("every run happened to have no sessions, so this proved nothing", covered > 500)
    }

    /** No session is counted in two bars, whatever the bucket width. */
    @Test
    fun noSessionLandsInTwoBars() {
        val random = Random(626262)
        repeat(1_500) {
            val zone = zones[random.nextInt(zones.size)]
            val weekStart = DayOfWeek.entries[random.nextInt(7)]
            val spanDays = listOf(1, 7, 14, 15, 60, 200, 400)[random.nextInt(7)]
            val start = WeekBoundary.startOfDay(1_700_000_000_000L + random.nextLong(0, 300 * day), zone)
            val range = WeekBoundary.Range(start, start + spanDays * day)
            val details = sessionsIn(random, start, spanDays)

            val bars = VolumeBuckets.of(range, zone, weekStart).ranges
            details.forEach { detail ->
                val landsIn = bars.count { detail.session.startedAt in it }
                assertTrue(
                    "a session at ${detail.session.startedAt} lands in $landsIn bars ($zone, $weekStart)",
                    landsIn == 1,
                )
            }
        }
    }
}

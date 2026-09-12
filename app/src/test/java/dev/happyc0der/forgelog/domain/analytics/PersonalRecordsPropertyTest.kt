package dev.happyc0der.forgelog.domain.analytics

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.workout.sessionDetail
import dev.happyc0der.forgelog.domain.workout.sessionExercise
import dev.happyc0der.forgelog.domain.workout.setLog
import dev.happyc0der.forgelog.domain.workout.workoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A personal best is a claim about the lifter, so a false one is worse than a missed one.
 *
 * The summary announces these the moment a workout ends, which is the moment they are least likely
 * to be questioned and most likely to be believed. So rather than checking particular sessions,
 * these generate random histories and hold the result to what a record means: strictly better than
 * everything before it, one per exercise and kind, and never claimed against a table the session is
 * already in.
 */
class PersonalRecordsPropertyTest {

    private val kinds = RecordKind.entries

    /** The value a kind compares, read straight off the candidate. */
    private fun value(kind: RecordKind, candidate: PrCandidate): Double? = when (kind) {
        RecordKind.HEAVIEST_WEIGHT -> candidate.weightLb
        RecordKind.MOST_REPS -> candidate.reps?.toDouble()
        RecordKind.BEST_SET_VOLUME -> candidate.setVolumeLb
        RecordKind.LONGEST_DURATION -> candidate.durationSeconds?.toDouble()
        RecordKind.BEST_ESTIMATED_1RM -> candidate.estimatedOneRepMaxLb
    }

    private fun randomSession(random: Random, sessionId: Long, lifts: Int): SessionDetail {
        val exercises = (0 until lifts).map { index ->
            val timed = random.nextInt(4) == 0
            SessionExerciseWithSets(
                exercise = sessionExercise(
                    id = sessionId * 100 + index,
                    sessionId = sessionId,
                    exerciseId = if (random.nextInt(8) == 0) null else (index + 1).toLong(),
                    displayName = "Lift ${index + 1}",
                    exerciseOrder = index,
                ),
                sets = (0 until random.nextInt(1, 5)).map { setIndex ->
                    setLog(
                        id = sessionId * 1000 + index * 10 + setIndex,
                        sessionExerciseId = sessionId * 100 + index,
                        setNumber = setIndex + 1,
                        setType = if (random.nextInt(5) == 0) SetType.WARMUP else SetType.WORKING,
                        reps = if (timed) null else random.nextInt(0, 14).takeIf { random.nextInt(8) != 0 },
                        weight = if (timed) null else random.nextInt(0, 400).toDouble(),
                        weightUnit = if (random.nextBoolean()) ExerciseUnit.LB else ExerciseUnit.KG,
                        durationSeconds = if (timed) random.nextInt(0, 300) else null,
                        completed = random.nextInt(6) != 0,
                        completedAt = 1_000_000L + sessionId * 10_000 + setIndex,
                    )
                },
            )
        }
        return sessionDetail(
            workoutSession(
                id = sessionId,
                status = if (random.nextInt(7) == 0) SessionStatus.ABANDONED else SessionStatus.COMPLETED,
                startedAt = 1_000_000L + sessionId * 10_000,
                completedAt = 1_000_000L + sessionId * 10_000 + 5_000,
            ),
            *exercises.toTypedArray(),
        )
    }

    @Test
    fun everyRecordAnnouncedActuallyBeatsWhatCameBefore() {
        val random = Random(31337)
        repeat(1_500) { run ->
            val history = (1L..random.nextInt(1, 6).toLong()).map { randomSession(random, it, random.nextInt(1, 4)) }
            val session = history.last()
            val prior = history.dropLast(1)

            val records = PersonalRecords.achievedIn(session, prior)
            val priorBests = PersonalRecords.byExercise(prior)

            records.forEach { record ->
                val key = record.candidate.exerciseId?.toString() ?: "name:${record.candidate.exerciseName}"
                val before = priorBests[key]
                val beaten = before?.let { existing ->
                    when (record.kind) {
                        RecordKind.HEAVIEST_WEIGHT -> existing.heaviestWeight
                        RecordKind.MOST_REPS -> existing.mostReps
                        RecordKind.BEST_SET_VOLUME -> existing.bestSetVolume
                        RecordKind.LONGEST_DURATION -> existing.longestDuration
                        RecordKind.BEST_ESTIMATED_1RM -> existing.bestEstimatedOneRepMax
                    }
                }
                val now = value(record.kind, record.candidate)
                assertTrue("run $run: a ${record.kind} record with no value", now != null)
                val was = beaten?.let { value(record.kind, it) }
                if (was != null) {
                    assertTrue(
                        "run $run: ${record.kind} announced $now over a previous best of $was",
                        now!! > was,
                    )
                }
                // previousBest says whether this is an improvement or a first; it has to agree.
                assertEquals(
                    "run $run: ${record.kind} disagrees about whether there was a previous best",
                    was != null,
                    record.previousBest != null,
                )
            }
        }
    }

    @Test
    fun oneRecordPerLiftAndKindAtMost() {
        val random = Random(4711)
        repeat(1_500) {
            val history = (1L..random.nextInt(1, 5).toLong()).map { randomSession(random, it, random.nextInt(1, 4)) }
            val records = PersonalRecords.achievedIn(history.last(), history.dropLast(1))

            val seen = records.map { it.exerciseName to it.kind }
            assertEquals("the same record was announced twice", seen.size, seen.toSet().size)
        }
    }

    /**
     * Compared against a table it is already in, a session sets nothing -- which is why the summary
     * has to exclude itself. Getting that wrong would silence every record instead of inventing
     * one, so it is the failure worth pinning.
     */
    @Test
    fun aSessionComparedAgainstItselfSetsNothing() {
        val random = Random(6060)
        repeat(1_000) {
            val history = (1L..random.nextInt(1, 5).toLong()).map { randomSession(random, it, random.nextInt(1, 4)) }
            val session = history.last()

            assertEquals(
                "a session beat a record it already holds",
                emptyList<SessionRecord>(),
                PersonalRecords.achievedIn(session, history),
            )
        }
    }

    /** Nothing a warmup, an unticked set or an abandoned session contains can be a record. */
    @Test
    fun warmupsAndUnfinishedWorkDoNotCount() {
        val random = Random(808)
        repeat(1_000) {
            val session = randomSession(random, 1L, random.nextInt(1, 4))
            val candidates = PersonalRecords.candidates(listOf(session))
            if (session.session.status != SessionStatus.COMPLETED) {
                assertEquals("an abandoned session produced candidates", emptyList<PrCandidate>(), candidates)
                return@repeat
            }
            val counted = session.exercises.sumOf { logged ->
                logged.sets.count { it.completed && it.setType != SetType.WARMUP }
            }
            assertEquals("warmups or unticked sets were counted", counted, candidates.size)
        }
    }

    /** A record keeps the date it was first reached, rather than moving each time it is matched. */
    @Test
    fun aTiedRecordKeepsItsOriginalDate() {
        val random = Random(1212)
        repeat(500) {
            val equal = (1..random.nextInt(2, 6)).map { index ->
                PrCandidate(
                    exerciseId = 1L,
                    exerciseName = "Bench Press",
                    sessionId = index.toLong(),
                    achievedAtEpochMs = 5_000L - index,
                    reps = 5,
                    weightLb = 185.0,
                    durationSeconds = null,
                    estimatedOneRepMaxLb = 215.0,
                )
            }
            val records = PersonalRecords.records(equal.shuffled(random))
            val earliest = equal.minOf { it.achievedAtEpochMs }

            assertEquals(earliest, records.heaviestWeight?.achievedAtEpochMs)
            assertEquals(earliest, records.mostReps?.achievedAtEpochMs)
            assertEquals(earliest, records.bestSetVolume?.achievedAtEpochMs)
            assertNull(records.longestDuration)
        }
    }
}

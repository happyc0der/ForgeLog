package dev.happyc0der.forgelog.domain.analytics

import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.workout.EstimatedOneRepMax
import dev.happyc0der.forgelog.domain.workout.toPounds

/** One set, flattened with the context a record needs. Weights are normalised to pounds. */
data class PrCandidate(
    val exerciseId: Long?,
    val exerciseName: String,
    val sessionId: Long,
    val achievedAtEpochMs: Long,
    val reps: Int?,
    val weightLb: Double?,
    val durationSeconds: Int?,
    val estimatedOneRepMaxLb: Double?,
) {
    /** Volume of this one set, which is what "best set" means for a loaded lift. */
    val setVolumeLb: Double?
        get() = if (reps != null && weightLb != null) reps * weightLb else null
}

/**
 * The bests for one exercise.
 *
 * Every field is nullable because an exercise may have no claim to that kind of record at all: a
 * bodyweight plank has no heaviest weight, and a barbell squat has no longest duration. Null means
 * "not applicable or not recorded" and must never be rendered as zero.
 */
data class ExerciseRecords(
    val exerciseId: Long?,
    val exerciseName: String,
    val heaviestWeight: PrCandidate? = null,
    val mostReps: PrCandidate? = null,
    val bestSetVolume: PrCandidate? = null,
    val longestDuration: PrCandidate? = null,
    val bestEstimatedOneRepMax: PrCandidate? = null,
)

object PersonalRecords {

    /**
     * Flattens completed sessions into record candidates.
     *
     * Only completed sessions and completed sets count, and warmups are excluded: a record is a claim
     * about what the lifter can do, and a warmup set is not that claim. Abandoned sessions are left
     * out for the same reason they are left out of volume.
     */
    fun candidates(history: List<SessionDetail>): List<PrCandidate> =
        history
            .filter { it.session.status == SessionStatus.COMPLETED }
            .flatMap { detail ->
                val achievedAt = detail.session.completedAt ?: detail.session.startedAt
                detail.exercises.flatMap { logged ->
                    logged.sets
                        .filter { it.completed && it.setType != SetType.WARMUP }
                        .map { set ->
                            PrCandidate(
                                exerciseId = logged.exercise.exerciseId,
                                exerciseName = logged.exercise.displayNameSnapshot,
                                sessionId = detail.session.id,
                                achievedAtEpochMs = set.completedAt ?: achievedAt,
                                reps = set.reps?.takeIf { it > 0 },
                                weightLb = set.loadedWeightLb(),
                                durationSeconds = set.durationSeconds?.takeIf { it > 0 },
                                estimatedOneRepMaxLb = EstimatedOneRepMax.pounds(set),
                            )
                        }
                }
            }

    /**
     * Records per exercise, keyed by library id where there is one and by name otherwise.
     *
     * Falling back to the name snapshot matters: an exercise deleted from the library still has a
     * history, and its records should not silently merge with every other deleted exercise.
     */
    fun byExercise(history: List<SessionDetail>): Map<String, ExerciseRecords> =
        candidates(history)
            .groupBy(::recordKey)
            .mapValues { (_, candidates) -> records(candidates) }

    fun records(candidates: List<PrCandidate>): ExerciseRecords {
        val first = candidates.firstOrNull()
        return ExerciseRecords(
            exerciseId = first?.exerciseId,
            exerciseName = first?.exerciseName.orEmpty(),
            // Ties go to the earliest, so a record keeps the date it was first achieved rather than
            // moving every time it is matched.
            heaviestWeight = candidates.bestBy { it.weightLb },
            mostReps = candidates.bestBy { it.reps?.toDouble() },
            bestSetVolume = candidates.bestBy { it.setVolumeLb },
            longestDuration = candidates.bestBy { it.durationSeconds?.toDouble() },
            bestEstimatedOneRepMax = candidates.bestBy { it.estimatedOneRepMaxLb },
        )
    }

    /**
     * The records [session] set, judged against [priorHistory].
     *
     * [priorHistory] must exclude [session] itself, or every set would be compared against a table
     * it is already in and nothing would ever count as a record.
     *
     * At most one entry per exercise and kind: three sets each heavier than the old best is one new
     * heaviest weight, not three. Ordered heaviest-first within an exercise so the headline record
     * leads.
     */
    fun achievedIn(session: SessionDetail, priorHistory: List<SessionDetail>): List<SessionRecord> {
        val prior = byExercise(priorHistory)
        return candidates(listOf(session))
            .groupBy(::recordKey)
            .flatMap { (key, sessionCandidates) ->
                val existing = prior[key]
                RecordKind.entries.mapNotNull { kind ->
                    // The session's own best for this kind is the only one worth comparing: a
                    // lesser set from the same session cannot be the record either way.
                    val best = sessionCandidates.bestBy(kind.selector) ?: return@mapNotNull null
                    if (kind !in beats(existing, best)) return@mapNotNull null
                    SessionRecord(
                        exerciseName = best.exerciseName,
                        kind = kind,
                        candidate = best,
                        previousBest = existing?.let(kind.previous),
                    )
                }
            }
    }

    private fun recordKey(candidate: PrCandidate): String =
        candidate.exerciseId?.toString() ?: "name:${candidate.exerciseName}"

    /**
     * Whether [set] beats the stored records for its exercise.
     *
     * Used to badge a set the moment it is logged, so the comparison is against history *excluding*
     * the set itself.
     */
    fun beats(existing: ExerciseRecords?, candidate: PrCandidate): Set<RecordKind> {
        if (existing == null) {
            return buildSet {
                if (candidate.weightLb != null) add(RecordKind.HEAVIEST_WEIGHT)
                if (candidate.reps != null) add(RecordKind.MOST_REPS)
                if (candidate.durationSeconds != null) add(RecordKind.LONGEST_DURATION)
                if (candidate.setVolumeLb != null) add(RecordKind.BEST_SET_VOLUME)
                if (candidate.estimatedOneRepMaxLb != null) add(RecordKind.BEST_ESTIMATED_1RM)
            }
        }
        return buildSet {
            if (candidate.weightLb beats existing.heaviestWeight?.weightLb) {
                add(RecordKind.HEAVIEST_WEIGHT)
            }
            if (candidate.reps?.toDouble() beats existing.mostReps?.reps?.toDouble()) {
                add(RecordKind.MOST_REPS)
            }
            if (candidate.setVolumeLb beats existing.bestSetVolume?.setVolumeLb) {
                add(RecordKind.BEST_SET_VOLUME)
            }
            if (candidate.durationSeconds?.toDouble() beats
                existing.longestDuration?.durationSeconds?.toDouble()
            ) {
                add(RecordKind.LONGEST_DURATION)
            }
            if (candidate.estimatedOneRepMaxLb beats existing.bestEstimatedOneRepMax?.estimatedOneRepMaxLb) {
                add(RecordKind.BEST_ESTIMATED_1RM)
            }
        }
    }

    /** Strictly greater: matching a record is not setting one. */
    private infix fun Double?.beats(other: Double?): Boolean {
        val value = this ?: return false
        val best = other ?: return true
        return value > best
    }

    private fun List<PrCandidate>.bestBy(selector: (PrCandidate) -> Double?): PrCandidate? =
        filter { selector(it) != null }
            .sortedWith(
                compareByDescending<PrCandidate> { selector(it) }
                    .thenBy { it.achievedAtEpochMs },
            )
            .firstOrNull()

    private fun SetLog.loadedWeightLb(): Double? {
        val weight = weight?.takeIf { it > 0.0 } ?: return null
        return weight.toPounds(weightUnit)
    }
}

/**
 * One record set in one session.
 *
 * [previousBest] is null when there was nothing to beat — a first-ever entry for that exercise is a
 * record, but calling it an improvement would be a lie, and the UI needs to be able to tell.
 */
data class SessionRecord(
    val exerciseName: String,
    val kind: RecordKind,
    val candidate: PrCandidate,
    val previousBest: PrCandidate?,
)

enum class RecordKind {
    HEAVIEST_WEIGHT,
    MOST_REPS,
    BEST_SET_VOLUME,
    LONGEST_DURATION,
    BEST_ESTIMATED_1RM,
    ;

    /** The measure this record ranks by. Null means the set makes no claim of this kind. */
    internal val selector: (PrCandidate) -> Double?
        get() = when (this) {
            HEAVIEST_WEIGHT -> { candidate -> candidate.weightLb }
            MOST_REPS -> { candidate -> candidate.reps?.toDouble() }
            BEST_SET_VOLUME -> { candidate -> candidate.setVolumeLb }
            LONGEST_DURATION -> { candidate -> candidate.durationSeconds?.toDouble() }
            BEST_ESTIMATED_1RM -> { candidate -> candidate.estimatedOneRepMaxLb }
        }

    /** Where this kind's standing record lives on an [ExerciseRecords]. */
    internal val previous: (ExerciseRecords) -> PrCandidate?
        get() = when (this) {
            HEAVIEST_WEIGHT -> ExerciseRecords::heaviestWeight
            MOST_REPS -> ExerciseRecords::mostReps
            BEST_SET_VOLUME -> ExerciseRecords::bestSetVolume
            LONGEST_DURATION -> ExerciseRecords::longestDuration
            BEST_ESTIMATED_1RM -> ExerciseRecords::bestEstimatedOneRepMax
        }
}

package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExercise
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.WorkoutSession

/**
 * The last time this exercise was trained, and the session it happened in.
 *
 * The session is carried because the UI has to say *when*: "Previous session" with no date leaves
 * the user unable to tell last Tuesday from last March.
 */
data class PreviousPerformance(
    val session: WorkoutSession,
    val exercise: SessionExerciseWithSets,
) {
    /** Only sets that were actually completed count as history; an untouched row did not happen. */
    val completedSets get() = exercise.sets.filter { it.completed }
}

object PreviousWorkoutMatcher {

    fun findPreviousExercise(
        currentSession: WorkoutSession,
        currentExercise: SessionExercise,
        history: List<SessionDetail>,
    ): SessionExerciseWithSets? = findPrevious(currentSession, currentExercise, history)?.exercise

    fun findPrevious(
        currentSession: WorkoutSession,
        currentExercise: SessionExercise,
        history: List<SessionDetail>,
    ): PreviousPerformance? {
        val candidates = history
            .asSequence()
            .filter { detail ->
                detail.session.status == SessionStatus.COMPLETED &&
                    detail.session.id != currentSession.id
            }
            .mapNotNull { detail ->
                val match = detail.exercises
                    .filter { it.matches(currentExercise) }
                    .minByOrNull { kotlin.math.abs(it.exercise.exerciseOrder - currentExercise.exerciseOrder) }
                    ?: return@mapNotNull null
                RankedMatch(detail.session, match)
            }
            /*
             * Recency first, then how well the session matches.
             *
             * The other way round — program day, then program, then date — meant a six-week-old
             * session from the matching day outranked yesterday's, and "last time" showed weights
             * the user had long since moved past. Worse, SetPrefill filled today's first set from
             * it. Matching context only breaks ties now: among sessions from the same day, the one
             * from the same program day wins.
             */
            .sortedWith(
                compareByDescending<RankedMatch> { match -> match.dayBucket(zoneDaysMs) }
                    .thenByDescending { match ->
                        sameId(match.session.programDayId, currentSession.programDayId)
                    }
                    .thenByDescending { match ->
                        sameId(match.session.programId, currentSession.programId)
                    }
                    .thenByDescending { match -> match.performedAt },
            )
            .toList()

        return candidates.firstOrNull()?.let { match ->
            PreviousPerformance(session = match.session, exercise = match.exercise)
        }
    }

    private fun SessionExerciseWithSets.matches(current: SessionExercise): Boolean {
        val currentExerciseId = current.exerciseId
        if (currentExerciseId != null && exercise.exerciseId == currentExerciseId) {
            return true
        }
        if (currentExerciseId != null) return false
        return exercise.displayNameSnapshot.trim().equals(
            current.displayNameSnapshot.trim(),
            ignoreCase = true,
        )
    }

    private fun sameId(left: Long?, right: Long?): Boolean =
        left != null && right != null && left == right

    private data class RankedMatch(
        val session: WorkoutSession,
        val exercise: SessionExerciseWithSets,
    ) {
        val performedAt: Long get() = session.completedAt ?: session.startedAt

        /**
         * Which day this happened on, as a whole number of [bucketMs].
         *
         * Bucketing rather than comparing timestamps is what lets context break ties: two sessions
         * on the same day rank equal here, so the one from the matching program day wins.
         */
        fun dayBucket(bucketMs: Long): Long = Math.floorDiv(performedAt, bucketMs)
    }

    /**
     * A fixed 24 hours, deliberately not a calendar day.
     *
     * This is a tie-break bucket, not a date shown to anyone: a session landing in the neighbouring
     * bucket around a DST change picks a different one of two same-day sessions, which is not worth
     * a time zone dependency in the matcher.
     */
    private const val zoneDaysMs = 24L * 60L * 60L * 1000L
}

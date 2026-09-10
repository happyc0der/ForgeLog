package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExercise
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.WorkoutSession

object PreviousWorkoutMatcher {
    fun findPreviousExercise(
        currentSession: WorkoutSession,
        currentExercise: SessionExercise,
        history: List<SessionDetail>,
    ): SessionExerciseWithSets? {
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
            .sortedWith(
                compareByDescending<RankedMatch> { match ->
                    sameId(match.session.programDayId, currentSession.programDayId)
                }.thenByDescending { match ->
                    sameId(match.session.programId, currentSession.programId)
                }.thenByDescending { match ->
                    match.session.completedAt ?: match.session.startedAt
                },
            )
            .toList()

        return candidates.firstOrNull()?.exercise
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
    )
}

package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SetType

/**
 * An exercise in a workout with sets still to do, and how many -- [unticked] of them added to the
 * log and not ticked, the rest planned and never added.
 */
data class SetsLeft(val exerciseName: String, val count: Int, val unticked: Int = 0) {
    companion object {
        /**
         * What is left in [detail], in the order the exercises are logged.
         *
         * Per exercise, the planned working sets not yet done -- or, where more working sets were
         * added than planned, or none were planned, those added and not ticked -- and any warm-up
         * added and not ticked. A plan's sets are working sets, so a ticked warm-up is not one of
         * them: three ramp-up sets before a 3 x 5 used to read as the three sets done.
         */
        fun of(detail: SessionDetail): List<SetsLeft> = detail.exercises
            .sortedBy { it.exercise.exerciseOrder }
            .mapNotNull { logged ->
                val working = logged.sets.filter { it.setType != SetType.WARMUP }
                val workingDone = working.count { it.completed }
                val expected = maxOf(logged.exercise.plannedSets ?: 0, working.size)
                val untickedWarmUps = logged.sets.count { it.setType == SetType.WARMUP && !it.completed }
                (expected - workingDone + untickedWarmUps).takeIf { it > 0 }?.let { left ->
                    SetsLeft(
                        exerciseName = logged.exercise.displayNameSnapshot,
                        count = left,
                        unticked = logged.sets.count { !it.completed },
                    )
                }
            }
    }
}

package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.SessionDetail

/**
 * An exercise in a workout with sets still to do, and how many -- [unticked] of them added to the
 * log and not ticked, the rest planned and never added.
 */
data class SetsLeft(val exerciseName: String, val count: Int, val unticked: Int = 0) {
    companion object {
        /**
         * What is left in [detail], in the order the exercises are logged.
         *
         * Per exercise, the planned sets not yet ticked -- or, where more sets were added than
         * planned, or none were planned, the sets added and not ticked. Whatever the kind: a ticked
         * warm-up counts as done, as it does on the exercise's "2 of 3 sets done" line.
         */
        fun of(detail: SessionDetail): List<SetsLeft> = detail.exercises
            .sortedBy { it.exercise.exerciseOrder }
            .mapNotNull { logged ->
                val done = logged.sets.count { it.completed }
                val expected = maxOf(logged.exercise.plannedSets ?: 0, logged.sets.size)
                (expected - done).takeIf { it > 0 }?.let { left ->
                    SetsLeft(logged.exercise.displayNameSnapshot, left, unticked = logged.sets.size - done)
                }
            }
    }
}

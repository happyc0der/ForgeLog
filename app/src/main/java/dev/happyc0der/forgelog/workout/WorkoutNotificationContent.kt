package dev.happyc0der.forgelog.workout

import dev.happyc0der.forgelog.domain.workout.RestTimerState

/** The workout notification's second line. */
sealed interface WorkoutNotificationLine {
    /** "Tap to return to your workout." */
    data object Default : WorkoutNotificationLine

    /** "Rest until 16:33" */
    data class Resting(val endsAtEpochMs: Long) : WorkoutNotificationLine

    /** "Rest paused, 1:20 left" */
    data class RestPaused(val remainingSeconds: Int) : WorkoutNotificationLine
}

/**
 * What the workout notification shows: its line, and the chronometer the system draws.
 *
 * [chronometerBase] is the workout's start, counting up, or while a rest runs its end, counting
 * down -- past which the system goes on counting as negative overtime, "-0:30", as the Clock app's
 * timer does. That is on purpose rather than switching to "rest over" at the end: the service's own
 * timers pause while the phone sleeps, which is when a rest ends, so the switch would land at some
 * random moment later. The notification changes only when the rest does.
 */
data class WorkoutNotificationContent(
    val line: WorkoutNotificationLine,
    val chronometerBase: Long,
    val countDown: Boolean,
)

object WorkoutNotificationContents {

    fun of(workoutStartedAt: Long, rest: RestTimerState?): WorkoutNotificationContent {
        if (rest == null || !rest.isActive) {
            return WorkoutNotificationContent(WorkoutNotificationLine.Default, workoutStartedAt, countDown = false)
        }
        rest.pausedRemainingSeconds?.let { remaining ->
            // The workout's clock keeps going; the rest's is frozen, so it is stated, not drawn.
            return WorkoutNotificationContent(
                line = WorkoutNotificationLine.RestPaused(remaining),
                chronometerBase = workoutStartedAt,
                countDown = false,
            )
        }
        val endsAt = rest.endsAtEpochMs()
            ?: return WorkoutNotificationContent(WorkoutNotificationLine.Default, workoutStartedAt, countDown = false)
        return WorkoutNotificationContent(
            line = WorkoutNotificationLine.Resting(endsAt),
            chronometerBase = endsAt,
            countDown = true,
        )
    }
}

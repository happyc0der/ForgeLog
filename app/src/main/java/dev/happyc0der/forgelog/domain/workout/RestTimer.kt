package dev.happyc0der.forgelog.domain.workout

/**
 * Rest countdown state.
 *
 * The running timer is anchored to the moment the last set was completed rather than to a tick
 * counter, so it stays correct across a process death, a screen rotation, or the app being
 * backgrounded mid-rest — all of which a counter would silently get wrong.
 *
 * [pausedRemainingSeconds] is the one piece of genuinely transient state: a paused timer has no
 * anchor, only a frozen remainder.
 */
data class RestTimerState(
    val targetSeconds: Int,
    val anchorEpochMs: Long?,
    val pausedRemainingSeconds: Int? = null,
    val isDismissed: Boolean = false,
) {
    val isPaused: Boolean get() = pausedRemainingSeconds != null
    val isActive: Boolean get() = !isDismissed && (anchorEpochMs != null || isPaused)
}

object RestTimer {

    const val ADJUST_STEP_SECONDS = 15
    const val MIN_TARGET_SECONDS = 0
    const val MAX_TARGET_SECONDS = 3600

    /**
     * Seconds left, floored at zero. An overrun reads as 0 rather than a negative number, because
     * "you are 40 seconds over" is the rest timer's job to show separately, not to express as -40.
     */
    fun remainingSeconds(state: RestTimerState, nowEpochMs: Long): Int {
        if (!state.isActive) return 0
        state.pausedRemainingSeconds?.let { return it.coerceAtLeast(0) }
        val anchor = state.anchorEpochMs ?: return state.targetSeconds
        val elapsed = ((nowEpochMs - anchor) / 1000L).coerceAtLeast(0L)
        return (state.targetSeconds - elapsed).coerceAtLeast(0L).toInt()
    }

    /** How far past the target the rest has run, for the "over by" readout. Zero while counting down. */
    fun overrunSeconds(state: RestTimerState, nowEpochMs: Long): Int {
        if (!state.isActive || state.isPaused) return 0
        val anchor = state.anchorEpochMs ?: return 0
        val elapsed = ((nowEpochMs - anchor) / 1000L).coerceAtLeast(0L)
        return (elapsed - state.targetSeconds).coerceAtLeast(0L).toInt()
    }

    fun hasFinished(state: RestTimerState, nowEpochMs: Long): Boolean =
        state.isActive && !state.isPaused && remainingSeconds(state, nowEpochMs) == 0

    /** Freezes the countdown where it stands. Paused twice is still paused, not restarted. */
    fun pause(state: RestTimerState, nowEpochMs: Long): RestTimerState =
        if (state.isPaused) {
            state
        } else {
            state.copy(
                pausedRemainingSeconds = remainingSeconds(state, nowEpochMs),
                anchorEpochMs = null,
            )
        }

    /**
     * Resumes by moving the anchor so the frozen remainder is what is left, rather than restarting
     * the full target.
     */
    fun resume(state: RestTimerState, nowEpochMs: Long): RestTimerState {
        val remaining = state.pausedRemainingSeconds ?: return state
        return state.copy(
            anchorEpochMs = nowEpochMs - (state.targetSeconds - remaining) * 1000L,
            pausedRemainingSeconds = null,
        )
    }

    /**
     * Changes the target by [deltaSeconds], keeping the anchor so the time already rested still
     * counts. Clamped, so repeated taps cannot drive the target negative or absurd.
     */
    fun adjust(state: RestTimerState, deltaSeconds: Int): RestTimerState {
        val target = (state.targetSeconds + deltaSeconds)
            .coerceIn(MIN_TARGET_SECONDS, MAX_TARGET_SECONDS)
        return state.copy(
            targetSeconds = target,
            pausedRemainingSeconds = state.pausedRemainingSeconds
                ?.plus(deltaSeconds)
                ?.coerceIn(0, target),
        )
    }

    fun dismiss(state: RestTimerState): RestTimerState =
        state.copy(isDismissed = true, pausedRemainingSeconds = null)

    /**
     * Starts resting from [anchorEpochMs], which is the completion time of the set just logged.
     *
     * [targetSeconds] comes from the exercise's planned rest where there is one and the user's
     * default otherwise — which is the reason planned rest had to survive into the session at all.
     */
    fun start(targetSeconds: Int, anchorEpochMs: Long): RestTimerState = RestTimerState(
        targetSeconds = targetSeconds.coerceIn(MIN_TARGET_SECONDS, MAX_TARGET_SECONDS),
        anchorEpochMs = anchorEpochMs,
    )

    /**
     * Rebuilds a countdown that was already running, or null if it has nothing left to show.
     *
     * The anchor is the completion time of the set that started the rest, and that is a column in
     * the database — so a countdown can survive the process being killed, which is exactly when it
     * is most likely to happen: the phone goes down on the bench for two minutes and Android
     * reclaims the app.
     *
     * Returns null once the rest has elapsed. Restoring a finished timer would greet the user with
     * "over by 3 hours" from yesterday's session, which is worse than showing nothing.
     */
    fun restore(anchorEpochMs: Long, targetSeconds: Int, nowEpochMs: Long): RestTimerState? {
        val elapsed = (nowEpochMs - anchorEpochMs) / 1000L
        if (elapsed < 0 || elapsed >= targetSeconds) return null
        return start(targetSeconds = targetSeconds, anchorEpochMs = anchorEpochMs)
    }

    /** The rest to suggest: the plan's target if it has one, else the user's default. */
    fun suggestedTarget(plannedRestSeconds: Int?, defaultRestSeconds: Int): Int =
        (plannedRestSeconds ?: defaultRestSeconds)
            .coerceIn(MIN_TARGET_SECONDS, MAX_TARGET_SECONDS)
}

package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.SetLog

/**
 * Rest between sets is derived from [SetLog.completedAt] across the whole session.
 * There is no separate rest-timer clock.
 */
object SessionRest {
    fun lastCompletedAt(
        sets: Sequence<SetLog>,
        excludeSetId: Long? = null,
    ): Long? = sets
        .filter { set ->
            set.completed &&
                set.completedAt != null &&
                (excludeSetId == null || set.id != excludeSetId)
        }
        .maxOfOrNull { it.completedAt!! }

    fun lastCompletedAt(
        sets: Iterable<SetLog>,
        excludeSetId: Long? = null,
    ): Long? = lastCompletedAt(sets.asSequence(), excludeSetId)

    /**
     * The set completed most recently before now, excluding [excludeSetId] -- the set whose "rest
     * after" is the gap that has just ended.
     *
     * Across the whole session, not per exercise: the rest after the last Plank set is the time
     * until the first squat, whichever lift that turns out to be.
     */
    fun previousCompleted(sets: Sequence<SetLog>, excludeSetId: Long): SetLog? = sets
        .filter { it.id != excludeSetId && it.completed && it.completedAt != null }
        .maxByOrNull { it.completedAt ?: 0L }

    /**
     * The rest after a set: from [lastCompletedAt], when it ended, to when the next set *began*.
     *
     * A set is ticked as it ends, so a timed set began [nextSetDurationSeconds] before its tick,
     * and that hold is work, not rest. Without subtracting it, 3 x 60 s planks a minute apart
     * recorded every rest as two minutes. A set with no duration -- reps -- is taken to begin at
     * its tick: the few seconds a set of eight takes are not known, and are small.
     */
    fun restAfterSetSeconds(
        nowEpochMs: Long,
        lastCompletedAt: Long?,
        nextSetDurationSeconds: Int? = null,
    ): Int? {
        if (lastCompletedAt == null) return null
        // Seconds apart is the user ticking off sets already done, not a rest and a set. Measuring
        // it recorded "rest 1s" over the planned rest of every set caught up on.
        if (nowEpochMs - lastCompletedAt < CATCH_UP_GAP_MS) return null
        val gapSeconds = (nowEpochMs - lastCompletedAt) / 1000L
        return (gapSeconds - (nextSetDurationSeconds ?: 0)).coerceAtLeast(0L).toInt()
    }

    /** Ticks closer together than this cannot have a rest and a set between them. */
    const val CATCH_UP_GAP_MS = 10_000L

    fun sinceLastSetMs(nowEpochMs: Long, lastCompletedAt: Long?): Long? {
        if (lastCompletedAt == null) return null
        return (nowEpochMs - lastCompletedAt).coerceAtLeast(0L)
    }
}

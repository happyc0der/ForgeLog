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

    fun restAfterSetSeconds(nowEpochMs: Long, lastCompletedAt: Long?): Int? {
        if (lastCompletedAt == null) return null
        return ((nowEpochMs - lastCompletedAt) / 1000L).toInt().coerceAtLeast(0)
    }

    fun sinceLastSetMs(nowEpochMs: Long, lastCompletedAt: Long?): Long? {
        if (lastCompletedAt == null) return null
        return (nowEpochMs - lastCompletedAt).coerceAtLeast(0L)
    }
}

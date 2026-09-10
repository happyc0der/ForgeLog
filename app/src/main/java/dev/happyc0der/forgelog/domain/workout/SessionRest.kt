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

    fun restAfterSetSeconds(nowEpochMs: Long, lastCompletedAt: Long?): Int? {
        if (lastCompletedAt == null) return null
        return ((nowEpochMs - lastCompletedAt) / 1000L).toInt().coerceAtLeast(0)
    }

    fun sinceLastSetMs(nowEpochMs: Long, lastCompletedAt: Long?): Long? {
        if (lastCompletedAt == null) return null
        return (nowEpochMs - lastCompletedAt).coerceAtLeast(0L)
    }
}

package dev.happyc0der.forgelog.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRestTest {
    @Test
    fun firstCompletionHasNoPreviousRest() {
        val sets = listOf(
            setLog(id = 1L, completed = false, completedAt = null, restAfterSetSeconds = null),
        )
        assertNull(SessionRest.lastCompletedAt(sets, excludeSetId = 1L))
        assertNull(SessionRest.restAfterSetSeconds(nowEpochMs = 5_000L, lastCompletedAt = null))
    }

    @Test
    fun restIsElapsedSinceLatestCompletionAnywhere() {
        val sets = listOf(
            setLog(id = 1L, sessionExerciseId = 10L, completed = true, completedAt = 1_000L),
            setLog(id = 2L, sessionExerciseId = 20L, completed = true, completedAt = 10_000L),
            setLog(id = 3L, sessionExerciseId = 10L, completed = false, completedAt = null),
        )
        val last = SessionRest.lastCompletedAt(sets, excludeSetId = 3L)
        assertEquals(10_000L, last)
        assertEquals(20, SessionRest.restAfterSetSeconds(nowEpochMs = 30_000L, lastCompletedAt = last))
    }

    @Test
    fun excludedSetIsIgnoredWhenFindingPreviousCompletion() {
        val sets = listOf(
            setLog(id = 1L, completed = true, completedAt = 1_000L),
            setLog(id = 2L, completed = true, completedAt = 40_000L),
        )
        assertEquals(1_000L, SessionRest.lastCompletedAt(sets, excludeSetId = 2L))
    }

    @Test
    fun sinceLastSetIsNullBeforeAnyCompletion() {
        assertNull(SessionRest.sinceLastSetMs(nowEpochMs = 5_000L, lastCompletedAt = null))
        assertEquals(4_000L, SessionRest.sinceLastSetMs(nowEpochMs = 5_000L, lastCompletedAt = 1_000L))
    }
}

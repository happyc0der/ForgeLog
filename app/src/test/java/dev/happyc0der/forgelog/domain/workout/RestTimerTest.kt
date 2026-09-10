package dev.happyc0der.forgelog.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerTest {

    private val start = 1_000_000L

    @Test
    fun `a fresh timer has the full target remaining`() {
        val state = RestTimer.start(targetSeconds = 90, anchorEpochMs = start)
        assertEquals(90, RestTimer.remainingSeconds(state, start))
        assertTrue(state.isActive)
        assertFalse(state.isPaused)
    }

    @Test
    fun `remaining counts down in real time`() {
        val state = RestTimer.start(90, start)
        assertEquals(60, RestTimer.remainingSeconds(state, start + 30_000L))
        assertEquals(1, RestTimer.remainingSeconds(state, start + 89_000L))
        assertEquals(0, RestTimer.remainingSeconds(state, start + 90_000L))
    }

    @Test
    fun `an overrun floors remaining at zero and is reported separately`() {
        val state = RestTimer.start(90, start)
        assertEquals(0, RestTimer.remainingSeconds(state, start + 150_000L))
        assertEquals(60, RestTimer.overrunSeconds(state, start + 150_000L))
        assertEquals(0, RestTimer.overrunSeconds(state, start + 30_000L))
    }

    @Test
    fun `finishing is detected exactly at the target`() {
        val state = RestTimer.start(90, start)
        assertFalse(RestTimer.hasFinished(state, start + 89_000L))
        assertTrue(RestTimer.hasFinished(state, start + 90_000L))
        assertTrue(RestTimer.hasFinished(state, start + 200_000L))
    }

    @Test
    fun `the anchor means a backgrounded app resumes with the correct remainder`() {
        // Nothing ticked for two minutes; the timer still knows it has long finished.
        val state = RestTimer.start(180, start)
        assertEquals(60, RestTimer.remainingSeconds(state, start + 120_000L))
    }

    @Test
    fun `pausing freezes the remainder`() {
        val running = RestTimer.start(90, start)
        val paused = RestTimer.pause(running, start + 30_000L)
        assertTrue(paused.isPaused)
        // Time passing while paused changes nothing.
        assertEquals(60, RestTimer.remainingSeconds(paused, start + 30_000L))
        assertEquals(60, RestTimer.remainingSeconds(paused, start + 500_000L))
    }

    @Test
    fun `pausing twice does not restart the countdown`() {
        val paused = RestTimer.pause(RestTimer.start(90, start), start + 30_000L)
        val again = RestTimer.pause(paused, start + 60_000L)
        assertEquals(60, RestTimer.remainingSeconds(again, start + 60_000L))
    }

    @Test
    fun `resuming continues from the frozen remainder, not from the full target`() {
        val paused = RestTimer.pause(RestTimer.start(90, start), start + 30_000L)
        val resumedAt = start + 600_000L
        val resumed = RestTimer.resume(paused, resumedAt)

        assertFalse(resumed.isPaused)
        assertEquals(60, RestTimer.remainingSeconds(resumed, resumedAt))
        assertEquals(50, RestTimer.remainingSeconds(resumed, resumedAt + 10_000L))
    }

    @Test
    fun `resuming a timer that was never paused is a no-op`() {
        val running = RestTimer.start(90, start)
        assertEquals(running, RestTimer.resume(running, start + 10_000L))
    }

    @Test
    fun `adjusting keeps the time already rested`() {
        val state = RestTimer.start(90, start)
        val longer = RestTimer.adjust(state, RestTimer.ADJUST_STEP_SECONDS)
        assertEquals(105, longer.targetSeconds)
        // 30 seconds have already passed and still count.
        assertEquals(75, RestTimer.remainingSeconds(longer, start + 30_000L))
    }

    @Test
    fun `adjusting a paused timer moves its frozen remainder too`() {
        val paused = RestTimer.pause(RestTimer.start(90, start), start + 30_000L)
        val longer = RestTimer.adjust(paused, 15)
        assertEquals(75, RestTimer.remainingSeconds(longer, start + 30_000L))
    }

    @Test
    fun `the target cannot be driven negative or absurd`() {
        var state = RestTimer.start(30, start)
        repeat(10) { state = RestTimer.adjust(state, -15) }
        assertEquals(RestTimer.MIN_TARGET_SECONDS, state.targetSeconds)

        var long = RestTimer.start(3_000, start)
        repeat(100) { long = RestTimer.adjust(long, 60) }
        assertEquals(RestTimer.MAX_TARGET_SECONDS, long.targetSeconds)
    }

    @Test
    fun `a dismissed timer is inert`() {
        val dismissed = RestTimer.dismiss(RestTimer.start(90, start))
        assertFalse(dismissed.isActive)
        assertEquals(0, RestTimer.remainingSeconds(dismissed, start))
        assertFalse(RestTimer.hasFinished(dismissed, start + 200_000L))
    }

    @Test
    fun `the suggested target prefers the plan and falls back to the default`() {
        assertEquals(180, RestTimer.suggestedTarget(plannedRestSeconds = 180, defaultRestSeconds = 90))
        assertEquals(90, RestTimer.suggestedTarget(plannedRestSeconds = null, defaultRestSeconds = 90))
    }

    @Test
    fun `a nonsense planned rest is clamped rather than trusted`() {
        assertEquals(RestTimer.MAX_TARGET_SECONDS, RestTimer.suggestedTarget(99_999, 90))
        assertEquals(RestTimer.MIN_TARGET_SECONDS, RestTimer.suggestedTarget(-60, 90))
    }

    @Test
    fun `a zero target finishes immediately instead of hanging`() {
        val state = RestTimer.start(0, start)
        assertEquals(0, RestTimer.remainingSeconds(state, start))
        assertTrue(RestTimer.hasFinished(state, start))
    }

    /*
     * Restoring after the process was killed. The anchor is the last set's completedAt, which is a
     * column, so a countdown can outlive the process -- and being killed mid-rest is the likely
     * case, not the exotic one: the phone is on the bench and the app is in the background.
     */

    @Test
    fun `a rest still running is restored with the time it has left`() {
        val anchor = 1_000_000L
        val state = RestTimer.restore(anchor, targetSeconds = 120, nowEpochMs = anchor + 30_000L)

        assertNotNull(state)
        assertEquals(90, RestTimer.remainingSeconds(state!!, anchor + 30_000L))
        assertTrue(state.isActive)
        assertFalse(state.isPaused)
    }

    @Test
    fun `a rest that has already elapsed is not restored`() {
        val anchor = 1_000_000L
        // Yesterday's session. "Over by 3 hours" is worse than showing nothing.
        assertNull(RestTimer.restore(anchor, targetSeconds = 120, nowEpochMs = anchor + 10_800_000L))
    }

    @Test
    fun `a rest exactly at its target is not restored`() {
        val anchor = 1_000_000L
        assertNull(RestTimer.restore(anchor, targetSeconds = 120, nowEpochMs = anchor + 120_000L))
    }

    @Test
    fun `a clock that has gone backwards does not restore a timer`() {
        val anchor = 1_000_000L
        assertNull(RestTimer.restore(anchor, targetSeconds = 120, nowEpochMs = anchor - 5_000L))
    }
}

package dev.happyc0der.forgelog.workout

import dev.happyc0der.forgelog.domain.workout.RestTimer
import dev.happyc0der.forgelog.testing.FakeTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The exact alarm has to track the rest precisely: set for when it ends, moved by every +15 or
 * -15, dropped by a pause, a skip, an untick or the end of the workout. An alarm left behind buzzes
 * for a rest that is over; one never set leaves a sleeping phone silent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerControllerTest {

    /** Records what was asked of it; null in [calls] is a cancel. */
    private class FakeAlarm(override var canScheduleExact: Boolean = true) : RestAlarmScheduler {
        val calls = mutableListOf<Long?>()
        override fun schedule(atEpochMs: Long) {
            calls += atEpochMs
        }
        override fun cancel() {
            calls += null
        }
    }

    private val t0 = 1_000_000L
    private val time = FakeTimeProvider(now = t0)

    private fun TestScope.controller(alarm: RestAlarmScheduler, alert: RestAlert = RestAlert {}) =
        RestTimerController(backgroundScope, time, alert, alarm)

    @Test
    fun `starting a rest sets the alarm for the moment it ends`() = runTest {
        val alarm = FakeAlarm()
        val rest = controller(alarm)

        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()

        assertEquals(t0 + 90_000L, alarm.calls.last())
    }

    @Test
    fun `adjusting moves the alarm, pausing drops it, resuming sets it again`() = runTest {
        val alarm = FakeAlarm()
        val rest = controller(alarm)
        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()

        rest.update(1) { RestTimer.adjust(it, 15) }
        runCurrent()
        assertEquals(t0 + 105_000L, alarm.calls.last())

        time.now = t0 + 30_000L
        rest.update(1) { RestTimer.pause(it, time.now) }
        runCurrent()
        assertNull("a paused rest must not buzz", alarm.calls.last())

        // 75 s were left when paused; resumed at t0 + 100 s, it ends 75 s later.
        time.now = t0 + 100_000L
        rest.update(1) { RestTimer.resume(it, time.now) }
        runCurrent()
        assertEquals(t0 + 175_000L, alarm.calls.last())
    }

    @Test
    fun `skipping, unticking and finishing each take the alarm away`() = runTest {
        val alarm = FakeAlarm()
        val rest = controller(alarm)

        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        rest.update(1, RestTimer::dismiss)
        runCurrent()
        assertNull("skipped", alarm.calls.last())

        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        rest.stopIfStartedBy(sessionId = 1, setId = 5)
        runCurrent()
        assertNull("unticked", alarm.calls.last())

        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        rest.clear(sessionId = 1)
        runCurrent()
        assertNull("workout over", alarm.calls.last())
    }

    @Test
    fun `with an exact alarm set, the in-app countdown does not alert as well`() = runTest {
        var alerts = 0
        val rest = controller(FakeAlarm(canScheduleExact = true)) { alerts++ }
        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()

        time.now = t0 + 91_000L
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals("one buzz, from the alarm, not two", 0, alerts)
    }

    @Test
    fun `without exact alarms the in-app countdown raises the alert itself`() = runTest {
        var alerts = 0
        val alarm = FakeAlarm(canScheduleExact = false)
        val rest = controller(alarm) { alerts++ }
        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()

        time.now = t0 + 91_000L
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals(1, alerts)
        assertEquals("nothing scheduled that cannot fire", listOf<Long?>(null), alarm.calls.distinct())
    }
}

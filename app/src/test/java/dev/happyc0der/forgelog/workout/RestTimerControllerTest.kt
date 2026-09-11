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

    /** What an earlier process left, and what this one saves. */
    private class FakeStore(var saved: ActiveRest? = null) : RestStateStore {
        override fun load(): ActiveRest? = saved
        override fun save(rest: ActiveRest?) {
            saved = rest
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

    @Test
    fun `a fresh controller leaves alone an alarm set before the app was killed`() = runTest {
        val alarm = FakeAlarm()
        controller(alarm)
        runCurrent()

        assertEquals("nothing cancelled at start-up", emptyList<Long?>(), alarm.calls)
    }

    @Test
    fun `finishing cancels the alarm even when this process never set it`() = runTest {
        val alarm = FakeAlarm()
        val rest = controller(alarm)
        runCurrent()

        rest.clear(sessionId = 1)
        runCurrent()

        assertEquals(listOf<Long?>(null), alarm.calls)
    }

    @Test
    fun `a rest goes, alarm and all, when its workout stops being in progress`() = runTest {
        val alarm = FakeAlarm()
        val inProgress = kotlinx.coroutines.flow.MutableStateFlow<Long?>(1L)
        val rest = RestTimerController(backgroundScope, time, {}, alarm, inProgressSessionId = inProgress)
        runCurrent()
        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        assertEquals(t0 + 90_000L, alarm.calls.last())

        // Deleted from History mid-rest.
        inProgress.value = null
        runCurrent()

        assertNull(rest.current(sessionId = 1))
        assertNull("the phone must not buzz for a workout that no longer exists", alarm.calls.last())
    }

    @Test
    fun `starting another workout drops the old one's rest`() = runTest {
        val alarm = FakeAlarm()
        val inProgress = kotlinx.coroutines.flow.MutableStateFlow<Long?>(1L)
        val rest = RestTimerController(backgroundScope, time, {}, alarm, inProgressSessionId = inProgress)
        runCurrent()
        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()

        inProgress.value = 2L
        runCurrent()

        assertNull(rest.current(sessionId = 1))
    }

    // --- A rest outliving the process -------------------------------------------------------

    private fun TestScope.restarted(store: FakeStore, alarm: FakeAlarm, alert: RestAlert = RestAlert {}) =
        RestTimerController(backgroundScope, time, alert, alarm, store = store)

    @Test
    fun `a rest skipped before the app was closed stays skipped`() = runTest {
        val skipped = RestTimer.dismiss(RestTimer.start(targetSeconds = 90, anchorEpochMs = t0 - 10_000L))
        val alarm = FakeAlarm()
        val rest = restarted(FakeStore(ActiveRest(1, skipped, startedBySetId = 5)), alarm)
        runCurrent()

        // Present, so the logger does not rebuild a running one, and silent.
        assertEquals(true, rest.current(1)?.state?.isDismissed)
        assertEquals(emptyList<Long?>(), alarm.calls)
    }

    @Test
    fun `a paused rest comes back paused, with no alarm`() = runTest {
        val paused = RestTimer.pause(RestTimer.start(targetSeconds = 90, anchorEpochMs = t0 - 30_000L), t0 - 20_000L)
        val alarm = FakeAlarm()
        val rest = restarted(FakeStore(ActiveRest(1, paused, startedBySetId = 5)), alarm)
        runCurrent()

        assertEquals(80, rest.current(1)?.state?.pausedRemainingSeconds)
        assertEquals(emptyList<Long?>(), alarm.calls)
    }

    @Test
    fun `an extended rest comes back with its end, and its alarm there`() = runTest {
        val extended = RestTimer.adjust(RestTimer.start(targetSeconds = 90, anchorEpochMs = t0 - 30_000L), 15)
        val alarm = FakeAlarm()
        val rest = restarted(FakeStore(ActiveRest(1, extended, startedBySetId = 5)), alarm)
        runCurrent()

        assertEquals(105, rest.current(1)?.state?.targetSeconds)
        assertEquals(t0 - 30_000L + 105_000L, alarm.calls.last())
        // Still the set that started it, so unticking that set still stops it.
        rest.stopIfStartedBy(1, setId = 5)
        runCurrent()
        assertNull(rest.current(1))
    }

    @Test
    fun `a rest that ended while the app was closed is not brought back`() = runTest {
        val over = RestTimer.start(targetSeconds = 90, anchorEpochMs = t0 - 200_000L)
        val alarm = FakeAlarm()
        val alerts = mutableListOf<Unit>()
        val store = FakeStore(ActiveRest(1, over, startedBySetId = 5))
        val rest = restarted(store, alarm, alert = { alerts += Unit })
        runCurrent()
        advanceTimeBy(5_000L)

        // Back as done: nothing shown, and present, so the logger does not rebuild one.
        assertEquals(false, rest.current(1)?.state?.isActive)
        // No alarm for a time already past, and no second alert.
        assertEquals(emptyList<Long?>(), alarm.calls)
        assertEquals(emptyList<Unit>(), alerts)
        assertEquals(true, store.saved?.state?.isDismissed)
    }

    @Test
    fun `every change is saved, and the end of the workout forgets it`() = runTest {
        val store = FakeStore()
        val rest = restarted(store, FakeAlarm())

        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        assertEquals(90, store.saved?.state?.targetSeconds)

        rest.update(1) { RestTimer.adjust(it, 15) }
        runCurrent()
        assertEquals(105, store.saved?.state?.targetSeconds)

        rest.clear(1)
        runCurrent()
        assertNull(store.saved)
    }
}

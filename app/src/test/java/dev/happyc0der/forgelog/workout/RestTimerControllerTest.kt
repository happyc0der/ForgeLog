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
 * The CPU hold has to track the rest precisely: taken until it ends, extended by every +15 or -15,
 * let go by a pause, a skip, an untick or the end of the workout. A hold left behind keeps the phone
 * awake for a rest that is over; one never taken lets the phone sleep through the end of it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerControllerTest {

    /** Records what was asked of it: an end time per hold, null per release. */
    private class FakeWakeLock : RestWakeLock {
        val calls = mutableListOf<Long?>()
        var held = false
            private set

        override fun hold(untilEpochMs: Long) {
            calls += untilEpochMs
            held = true
        }

        override fun release() {
            calls += null
            held = false
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

    private fun TestScope.controller(lock: RestWakeLock, alert: RestAlert = RestAlert {}) =
        RestTimerController(backgroundScope, time, alert, lock)

    @Test
    fun `starting a rest holds the CPU until the moment it ends`() = runTest {
        val lock = FakeWakeLock()
        val rest = controller(lock)

        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()

        assertEquals(t0 + 90_000L, lock.calls.last())
    }

    @Test
    fun `adjusting extends the hold, pausing lets it go, resuming takes it again`() = runTest {
        val lock = FakeWakeLock()
        val rest = controller(lock)
        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()

        rest.update(1) { RestTimer.adjust(it, 15) }
        runCurrent()
        assertEquals(t0 + 105_000L, lock.calls.last())

        time.now = t0 + 30_000L
        rest.update(1) { RestTimer.pause(it, time.now) }
        runCurrent()
        assertNull("a paused rest must not buzz", lock.calls.last())

        // 75 s were left when paused; resumed at t0 + 100 s, it ends 75 s later.
        time.now = t0 + 100_000L
        rest.update(1) { RestTimer.resume(it, time.now) }
        runCurrent()
        assertEquals(t0 + 175_000L, lock.calls.last())
    }

    @Test
    fun `skipping, unticking and finishing each let the CPU go`() = runTest {
        val lock = FakeWakeLock()
        val rest = controller(lock)

        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        rest.update(1, RestTimer::dismiss)
        runCurrent()
        assertNull("skipped", lock.calls.last())

        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        rest.stopIfStartedBy(sessionId = 1, setId = 5)
        runCurrent()
        assertNull("unticked", lock.calls.last())

        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        rest.clear(sessionId = 1)
        runCurrent()
        assertNull("workout over", lock.calls.last())
    }

    @Test
    fun `the countdown raises the alert once, and lets the CPU go afterwards`() = runTest {
        var alerts = 0
        val lock = FakeWakeLock()
        val rest = controller(lock) { alerts++ }
        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        assertEquals("held for as long as the rest runs", t0 + 90_000L, lock.calls.last())

        time.now = t0 + 91_000L
        advanceTimeBy(2_000L)
        runCurrent()

        assertEquals("one buzz", 1, alerts)
        assertEquals("nothing keeps the phone awake once the rest is over", false, lock.held)

        // The rest goes on being shown as overrun; it must not buzz a second time for that.
        time.now = t0 + 120_000L
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals("still one buzz", 1, alerts)
    }

    @Test
    fun `a fresh controller holds nothing before a rest starts`() = runTest {
        val lock = FakeWakeLock()
        controller(lock)
        runCurrent()

        assertEquals("nothing held before a rest starts", false, lock.held)
    }

    @Test
    fun `finishing releases the hold even when this process never took it`() = runTest {
        val lock = FakeWakeLock()
        val rest = controller(lock)
        runCurrent()

        rest.clear(sessionId = 1)
        runCurrent()

        assertEquals(false, lock.held)
    }

    @Test
    fun `a rest goes, CPU hold and all, when its workout stops being in progress`() = runTest {
        val lock = FakeWakeLock()
        val inProgress = kotlinx.coroutines.flow.MutableStateFlow<Long?>(1L)
        val rest = RestTimerController(backgroundScope, time, {}, lock, inProgressSessionId = inProgress)
        runCurrent()
        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()
        assertEquals(t0 + 90_000L, lock.calls.last())

        // Deleted from History mid-rest.
        inProgress.value = null
        runCurrent()

        assertNull(rest.current(sessionId = 1))
        assertNull("the phone must not buzz for a workout that no longer exists", lock.calls.last())
    }

    @Test
    fun `starting another workout drops the old one's rest`() = runTest {
        val lock = FakeWakeLock()
        val inProgress = kotlinx.coroutines.flow.MutableStateFlow<Long?>(1L)
        val rest = RestTimerController(backgroundScope, time, {}, lock, inProgressSessionId = inProgress)
        runCurrent()
        rest.start(sessionId = 1, targetSeconds = 90, anchorEpochMs = t0, startedBySetId = 5)
        runCurrent()

        inProgress.value = 2L
        runCurrent()

        assertNull(rest.current(sessionId = 1))
    }

    // --- A rest outliving the process -------------------------------------------------------

    private fun TestScope.restarted(store: FakeStore, lock: FakeWakeLock, alert: RestAlert = RestAlert {}) =
        RestTimerController(backgroundScope, time, alert, lock, store = store)

    @Test
    fun `a rest skipped before the app was closed stays skipped`() = runTest {
        val skipped = RestTimer.dismiss(RestTimer.start(targetSeconds = 90, anchorEpochMs = t0 - 10_000L))
        val lock = FakeWakeLock()
        val rest = restarted(FakeStore(ActiveRest(1, skipped, startedBySetId = 5)), lock)
        runCurrent()

        // Present, so the logger does not rebuild a running one, and silent.
        assertEquals(true, rest.current(1)?.state?.isDismissed)
        assertEquals("a skipped rest keeps nothing awake", false, lock.held)
    }

    @Test
    fun `a paused rest comes back paused, with nothing held`() = runTest {
        val paused = RestTimer.pause(RestTimer.start(targetSeconds = 90, anchorEpochMs = t0 - 30_000L), t0 - 20_000L)
        val lock = FakeWakeLock()
        val rest = restarted(FakeStore(ActiveRest(1, paused, startedBySetId = 5)), lock)
        runCurrent()

        assertEquals(80, rest.current(1)?.state?.pausedRemainingSeconds)
        assertEquals("a paused rest keeps nothing awake", false, lock.held)
    }

    @Test
    fun `an extended rest comes back with its end, and the CPU held again`() = runTest {
        val extended = RestTimer.adjust(RestTimer.start(targetSeconds = 90, anchorEpochMs = t0 - 30_000L), 15)
        val lock = FakeWakeLock()
        val rest = restarted(FakeStore(ActiveRest(1, extended, startedBySetId = 5)), lock)
        runCurrent()

        assertEquals(105, rest.current(1)?.state?.targetSeconds)
        assertEquals(t0 - 30_000L + 105_000L, lock.calls.last())
        // Still the set that started it, so unticking that set still stops it.
        rest.stopIfStartedBy(1, setId = 5)
        runCurrent()
        assertNull(rest.current(1))
    }

    @Test
    fun `a rest that ended while the app was closed is not brought back`() = runTest {
        val over = RestTimer.start(targetSeconds = 90, anchorEpochMs = t0 - 200_000L)
        val lock = FakeWakeLock()
        val alerts = mutableListOf<Unit>()
        val store = FakeStore(ActiveRest(1, over, startedBySetId = 5))
        val rest = restarted(store, lock, alert = { alerts += Unit })
        runCurrent()
        advanceTimeBy(5_000L)

        // Back as done: nothing shown, and present, so the logger does not rebuild one.
        assertEquals(false, rest.current(1)?.state?.isActive)
        // Nothing held for a time already past, and nothing buzzes.
        assertEquals(false, lock.held)
        assertEquals(emptyList<Unit>(), alerts)
        assertEquals(true, store.saved?.state?.isDismissed)
    }

    @Test
    fun `every change is saved, and the end of the workout forgets it`() = runTest {
        val store = FakeStore()
        val rest = restarted(store, FakeWakeLock())

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

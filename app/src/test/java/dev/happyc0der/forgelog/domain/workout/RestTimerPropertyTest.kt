package dev.happyc0der.forgelog.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The rest countdown, put through random sequences of what the buttons can do.
 *
 * It is the one number in the app that changes on its own while the user watches it, and every
 * control around it -- pause, resume, plus fifteen, minus fifteen -- rewrites the state it is
 * derived from. The cases next door check each control once; this checks that no order of them,
 * at any moment, produces a countdown that reads wrong: negative, past its own target, or still
 * ticking while paused.
 */
class RestTimerPropertyTest {

    private data class Step(val name: String, val apply: (RestTimerState, Long) -> RestTimerState)

    private val steps = listOf(
        Step("pause") { state, now -> RestTimer.pause(state, now) },
        Step("resume") { state, now -> RestTimer.resume(state, now) },
        Step("plus") { state, _ -> RestTimer.adjust(state, RestTimer.ADJUST_STEP_SECONDS) },
        Step("minus") { state, _ -> RestTimer.adjust(state, -RestTimer.ADJUST_STEP_SECONDS) },
    )

    @Test
    fun noSequenceOfControlsMakesTheCountdownReadWrong() {
        val random = Random(90210)
        repeat(4_000) { run ->
            var now = 1_700_000_000_000L
            var state = RestTimer.start(
                targetSeconds = random.nextInt(0, RestTimer.MAX_TARGET_SECONDS + 1),
                anchorEpochMs = now,
            )
            val history = StringBuilder("start(${state.targetSeconds})")

            repeat(random.nextInt(1, 12)) {
                now += random.nextLong(0, 400_000)
                val step = steps[random.nextInt(steps.size)]
                state = step.apply(state, now)
                history.append(" -> ").append(step.name)

                val remaining = RestTimer.remainingSeconds(state, now)
                val overrun = RestTimer.overrunSeconds(state, now)

                assertTrue("$history: remaining $remaining is negative", remaining >= 0)
                assertTrue(
                    "$history: remaining $remaining is past the target ${state.targetSeconds}",
                    remaining <= state.targetSeconds,
                )
                assertTrue("$history: overrun $overrun is negative", overrun >= 0)
                assertTrue(
                    "$history: target ${state.targetSeconds} is out of range",
                    state.targetSeconds in RestTimer.MIN_TARGET_SECONDS..RestTimer.MAX_TARGET_SECONDS,
                )
                assertTrue(
                    "$history: a paused rest is counting down and overrunning at once",
                    !(state.isPaused && overrun > 0),
                )
                if (RestTimer.hasFinished(state, now)) {
                    assertEquals("$history: finished but with time left", 0, remaining)
                }
            }
            assertTrue(run >= 0)
        }
    }

    @Test
    fun aPausedRestDoesNotTick() {
        val random = Random(11)
        repeat(2_000) {
            val start = 1_700_000_000_000L
            val target = random.nextInt(1, RestTimer.MAX_TARGET_SECONDS + 1)
            val pausedAt = start + random.nextLong(0, target * 1000L)
            val paused = RestTimer.pause(RestTimer.start(target, start), pausedAt)

            val atPause = RestTimer.remainingSeconds(paused, pausedAt)
            val muchLater = RestTimer.remainingSeconds(paused, pausedAt + 9_000_000L)

            assertEquals("a paused rest moved while nobody touched it", atPause, muchLater)
        }
    }

    @Test
    fun pausingAndResumingKeepsWhatWasLeft() {
        val random = Random(22)
        repeat(2_000) {
            val start = 1_700_000_000_000L
            val target = random.nextInt(1, RestTimer.MAX_TARGET_SECONDS + 1)
            val at = start + random.nextLong(0, target * 1000L)
            val running = RestTimer.start(target, start)

            val before = RestTimer.remainingSeconds(running, at)
            val resumed = RestTimer.resume(RestTimer.pause(running, at), at)
            val after = RestTimer.remainingSeconds(resumed, at)

            assertEquals("pausing and resuming lost or gained a second", before, after)
        }
    }

    /** A rest that ended while the app was gone is not rebuilt; see RestTimer.restore. */
    @Test
    fun aFinishedRestIsNeverRestored() {
        val random = Random(33)
        repeat(2_000) {
            val anchor = 1_700_000_000_000L
            val target = random.nextInt(1, RestTimer.MAX_TARGET_SECONDS + 1)
            val now = anchor + random.nextLong(-10_000, target * 2000L)

            val restored = RestTimer.restore(anchor, target, now)

            val elapsed = (now - anchor) / 1000L
            if (elapsed < 0 || elapsed >= target) {
                assertNull("rebuilt a rest that had already run out", restored)
            } else {
                assertTrue("dropped a rest that was still running", restored != null)
                assertTrue(
                    "a rebuilt rest reads wrong",
                    RestTimer.remainingSeconds(restored!!, now) in 0..target,
                )
            }
        }
    }
}

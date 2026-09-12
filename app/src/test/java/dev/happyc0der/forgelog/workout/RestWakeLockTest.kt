package dev.happyc0der.forgelog.workout

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.happyc0der.forgelog.testing.FakeTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

/**
 * The hold that keeps the rest countdown running while the screen is off.
 *
 * A wake lock left behind keeps the phone's CPU awake indefinitely, which is about the worst thing a
 * training app could do to a battery -- so what matters here is as much when it is released as when
 * it is taken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestWakeLockTest {

    private val t0 = 1_000_000L
    private val time = FakeTimeProvider(now = t0)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val lock = AndroidRestWakeLock(context, time)

    private fun latest() = ShadowPowerManager.getLatestWakeLock()

    @Test
    fun `holding keeps the CPU awake while the rest runs`() {
        lock.hold(t0 + 90_000L)

        assertNotNull("a rest under way must hold the CPU", latest())
        assertTrue(latest()!!.isHeld)
    }

    @Test
    fun `releasing lets it go`() {
        lock.hold(t0 + 90_000L)
        lock.release()

        assertFalse(latest()!!.isHeld)
    }

    @Test
    fun `a hold whose end has already passed is not taken at all`() {
        lock.hold(t0 - 10_000L)

        assertFalse("nothing left to wait for", latest()?.isHeld == true)
    }

    @Test
    fun `releasing without holding is harmless`() {
        lock.release()
        lock.release()

        assertFalse(latest()?.isHeld == true)
    }

    /**
     * Not reference counted, so a rest that is lengthened or resumed replaces its hold rather than
     * stacking one on top: a single release then still lets the CPU go.
     */
    @Test
    fun `a second hold does not stack`() {
        lock.hold(t0 + 30_000L)
        lock.hold(t0 + 60_000L)
        lock.release()

        assertFalse(latest()!!.isHeld)
        assertFalse(shadowOf(latest()!!).isReferenceCounted)
    }

    @Test
    fun `the hold says who it belongs to`() {
        lock.hold(t0 + 30_000L)

        assertEquals("ForgeLog:rest", shadowOf(latest()!!).tag)
    }
}

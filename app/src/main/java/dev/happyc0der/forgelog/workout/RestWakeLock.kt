package dev.happyc0der.forgelog.workout

import android.content.Context
import android.os.PowerManager
import dev.happyc0der.forgelog.domain.time.TimeProvider

/**
 * Keeps the CPU awake while a rest counts down, so the countdown reaches zero on time.
 *
 * A rest ends while the phone is face down with its screen off, which is exactly when Android puts
 * the CPU to sleep -- and a countdown built on coroutine delays sleeps with it, so the alert used to
 * arrive whenever something else happened to wake the phone.
 *
 * This was an exact alarm, which solves the same problem. It is a wake lock instead because
 * USE_EXACT_ALARM is a restricted permission on Google Play: review blocks a release that declares
 * it unless exact timing is the app's core purpose -- an alarm clock, a calendar, a timer app -- and
 * ForgeLog is a training log that happens to have a rest timer. The alternative, SCHEDULE_EXACT_ALARM,
 * is denied by default on Android 13 and up and would leave the alert silent until the user found the
 * right page in system settings. WAKE_LOCK is a normal permission with no such gate.
 *
 * Held only while a rest is actually running -- a minute or two at a time, and never while paused,
 * skipped or between sets -- and always with a timeout, so it cannot outlive the rest that asked for
 * it even if something goes wrong. The workout's foreground service is running throughout, so the
 * process stays alive to see the countdown through.
 */
interface RestWakeLock {
    /**
     * Keep the CPU awake until [untilEpochMs], a little past it so the final tick lands. Calling it
     * again replaces the previous hold, which is how a rest that is lengthened or resumed extends it.
     */
    fun hold(untilEpochMs: Long)

    fun release()
}

/** Holds nothing: for tests, and anywhere a countdown does not have to survive the screen going off. */
object NoRestWakeLock : RestWakeLock {
    override fun hold(untilEpochMs: Long) = Unit
    override fun release() = Unit
}

class AndroidRestWakeLock(
    context: Context,
    private val timeProvider: TimeProvider,
) : RestWakeLock {

    /*
     * Not reference counted, so a hold that replaces another does not stack: every acquire is
     * satisfied by a single release, whatever order pauses, adjustments and skips arrive in.
     */
    private val lock: PowerManager.WakeLock? =
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
            ?.apply { setReferenceCounted(false) }

    override fun hold(untilEpochMs: Long) {
        val remaining = untilEpochMs - timeProvider.nowEpochMs() + GRACE_MS
        if (remaining <= 0L) {
            release()
            return
        }
        // A timeout on every acquire: the lock releases itself even if the rest that asked for it is
        // somehow never heard from again.
        runCatching { lock?.acquire(remaining.coerceAtMost(MAX_HOLD_MS)) }
    }

    override fun release() {
        runCatching { if (lock?.isHeld == true) lock.release() }
    }

    private companion object {
        const val TAG = "ForgeLog:rest"

        /** Past the end, so the tick that reaches zero still runs before the CPU is let go. */
        const val GRACE_MS = 5_000L

        /**
         * A ceiling on any one hold. The longest rest the timer will take is an hour
         * ([dev.happyc0der.forgelog.domain.workout.RestTimer.MAX_TARGET_SECONDS]); this is that plus
         * room for the grace, and nothing should ever reach it.
         */
        const val MAX_HOLD_MS = 61L * 60L * 1000L
    }
}

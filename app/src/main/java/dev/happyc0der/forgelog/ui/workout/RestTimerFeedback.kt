package dev.happyc0der.forgelog.ui.workout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Tells the user rest is over without them having to watch the screen — which is the point, since
 * the phone is usually face down on a bench at that moment.
 *
 * Both signals are best-effort: a device with no vibrator, or a stream the system has muted, should
 * not produce an error, so failures are swallowed deliberately rather than surfaced.
 */
internal object RestTimerFeedback {

    private const val VIBRATION_MS = 400L
    private const val TONE_MS = 250L

    fun signal(context: Context, vibrate: Boolean, sound: Boolean) {
        if (vibrate) vibrate(context)
        if (sound) beep()
    }

    private fun vibrate(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            if (vibrator?.hasVibrator() != true) return
            val effect = VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            /*
             * Tagged as an alarm, which is what a rest timer is.
             *
             * Without a usage, Android files the vibration as touch feedback, and a phone with
             * touch haptics turned down drops it silently -- the system logged every rest-end on
             * the test device as "ignored_for_settings, usage: TOUCH". The user switched this on
             * in Settings to be told rest is over while the phone is face down; the system Clock
             * app's own timer vibrates under alarm usage for the same reason.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        }
    }

    private fun beep() {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS.toInt())
            /*
             * Released after the tone has played, not on the next line.
             *
             * startTone returns immediately and plays asynchronously, and release() stops whatever
             * is playing -- so releasing straight away, as this used to, cut the beep off before it
             * was audible. It is still released promptly, so nothing holds the stream open between
             * sets.
             */
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { generator.release() } },
                TONE_MS + RELEASE_GRACE_MS,
            )
        }
    }

    private const val TONE_VOLUME = 80
    private const val RELEASE_GRACE_MS = 100L
}

package dev.happyc0der.forgelog.ui.workout

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
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
    private const val TONE_MS = 250

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
            vibrator.vibrate(
                VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        }
    }

    private fun beep() {
        runCatching {
            // Released straight after the tone so nothing holds the audio stream open between sets.
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS)
            generator.release()
        }
    }

    private const val TONE_VOLUME = 80
}

package dev.happyc0der.forgelog.workout

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.EntryPointAccessors
import dev.happyc0der.forgelog.di.SettingsEntryPoint
import dev.happyc0der.forgelog.domain.settings.AppSettings
import dev.happyc0der.forgelog.ui.workout.RestTimerFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Wakes the phone when rest ends.
 *
 * A rest ends while the phone is face down with its screen off, which is exactly when Android puts
 * the CPU to sleep -- and an in-app countdown, built on coroutine delays, sleeps with it. The alert
 * then arrived whenever something else happened to wake the phone. An exact alarm is what the
 * system Clock app's own timer relies on: it wakes the device at the moment asked for.
 */
interface RestAlarmScheduler {
    /** False when exact alarms are not allowed; the in-app countdown then raises the alert. */
    val canScheduleExact: Boolean
    fun schedule(atEpochMs: Long)
    fun cancel()
}

class AndroidRestAlarmScheduler(private val context: Context) : RestAlarmScheduler {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    override val canScheduleExact: Boolean
        get() = alarmManager != null &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RestAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun schedule(atEpochMs: Long) {
        // One pending intent, so each schedule replaces the last rather than stacking alerts.
        runCatching {
            alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pendingIntent)
        }
    }

    override fun cancel() {
        runCatching { alarmManager?.cancel(pendingIntent) }
    }

    private companion object {
        const val REQUEST_CODE = 7201
    }
}

/**
 * Buzzes and/or beeps as the settings say, when the rest alarm goes off.
 *
 * Works with the app's process gone, too: the alarm starts it. Settings are read through the entry
 * point rather than injected, which keeps this a plain receiver.
 */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // The defaults if the settings cannot be read: an exception here escaped the
                // coroutine and crashed the app in the background, and the rest ended in silence.
                val settings = runCatching {
                    EntryPointAccessors
                        .fromApplication(appContext, SettingsEntryPoint::class.java)
                        .settingsRepository()
                        .settings
                        .first()
                }.getOrElse { AppSettings() }
                RestTimerFeedback.signal(
                    context = appContext,
                    vibrate = settings.restTimerVibration,
                    sound = settings.restTimerSound,
                )
            } finally {
                pending.finish()
            }
        }
    }
}

package dev.happyc0der.forgelog.workout

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.happyc0der.forgelog.MainActivity
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutForegroundService : LifecycleService() {

    @Inject
    lateinit var workoutSessionRepository: WorkoutSessionRepository

    private var currentSession: WorkoutSession? = null

    /**
     * Whether this service has already entered the foreground.
     *
     * startForeground is for entering that state, once; a later change to the workout is an update
     * through NotificationManager.notify.
     */
    private var inForeground = false

    /** Built once: the intent is identical on every post. */
    private val contentIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(buildNotification(sessionName = null, startedAt = null))
        lifecycleScope.launch {
            workoutSessionRepository.observeInProgressSession()
                // Posted when the workout changes -- which one, or its name -- not on every write
                // to its row.
                .distinctUntilChanged { old, new ->
                    old?.id == new?.id && old?.sessionName == new?.sessionName && old?.startedAt == new?.startedAt
                }
                .collect { session ->
                    currentSession = session
                    if (session == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        inForeground = false
                        stopSelf()
                    } else {
                        postNotification(session)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(
            buildNotification(sessionName = currentSession?.sessionName, startedAt = currentSession?.startedAt),
        )
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun postNotification(session: WorkoutSession) {
        val notification = buildNotification(
            sessionName = session.sessionName,
            startedAt = session.startedAt,
        )
        if (inForeground) {
            updateNotification(notification)
        } else {
            startInForeground(notification)
        }
    }

    /**
     * A plain update to a notification already showing.
     *
     * Silently does nothing if the user declined notifications: the service still needs to run, and
     * a missing permission is not a reason to crash a workout.
     */
    private fun updateNotification(notification: Notification) {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted) return
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startInForeground(notification: Notification) {
        inForeground = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * The elapsed time is a chronometer the system draws from [startedAt].
     *
     * It used to be text the service rewrote and re-posted once a second for the length of the
     * workout -- some 3,600 notifications an hour, each redrawing the shade and the lock screen, for
     * a number that changed by one. The system ticks a chronometer itself, and the service posts
     * only when the workout changes.
     */
    private fun buildNotification(sessionName: String?, startedAt: Long?): Notification {
        val title = sessionName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.workout_notification_title)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(title)
            .setContentText(getString(R.string.workout_notification_text))
            .apply {
                if (startedAt != null) {
                    setWhen(startedAt)
                    setShowWhen(true)
                    setUsesChronometer(true)
                }
            }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.workout_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            description = getString(R.string.workout_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "workout_timer"
        const val NOTIFICATION_ID = 41

        fun start(context: Context) {
            val intent = Intent(context, WorkoutForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutForegroundService::class.java))
        }
    }
}

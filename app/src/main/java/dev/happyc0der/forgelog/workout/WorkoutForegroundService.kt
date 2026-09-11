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
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.workout.formatElapsed
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutForegroundService : LifecycleService() {

    @Inject
    lateinit var workoutSessionRepository: WorkoutSessionRepository

    @Inject
    lateinit var timeProvider: TimeProvider

    private var tickerJob: Job? = null
    private var currentSession: WorkoutSession? = null

    /**
     * Whether this service has already entered the foreground.
     *
     * startForeground is for entering that state, once. Every later tick is an update and goes
     * through NotificationManager.notify -- calling startForeground once a second instead meant
     * 3,600 of them an hour, each rebuilding a PendingIntent, for a label that changes by one
     * second.
     */
    private var inForeground = false

    /** Rebuilt never: the intent is identical on every tick. */
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
        startInForeground(buildNotification(sessionName = null, elapsedLabel = formatElapsed(0L)))
        lifecycleScope.launch {
            workoutSessionRepository.observeInProgressSession().collect { session ->
                currentSession = session
                if (session == null) {
                    stopTicker()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    inForeground = false
                    stopSelf()
                } else {
                    postNotification(session)
                    startTicker()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(buildNotification(sessionName = currentSession?.sessionName, elapsedLabel = elapsedLabel()))
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        stopTicker()
        super.onDestroy()
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = lifecycleScope.launch {
            while (true) {
                currentSession?.let(::postNotification)
                delay(1_000)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun elapsedLabel(): String {
        val session = currentSession ?: return formatElapsed(0L)
        return formatElapsed(timeProvider.nowEpochMs() - session.startedAt)
    }

    private fun postNotification(session: WorkoutSession) {
        val notification = buildNotification(
            sessionName = session.sessionName,
            elapsedLabel = formatElapsed(timeProvider.nowEpochMs() - session.startedAt),
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

    private fun buildNotification(sessionName: String?, elapsedLabel: String): Notification {
        val title = sessionName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.workout_notification_title)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setContentTitle(title)
            .setContentText(getString(R.string.workout_notification_elapsed, elapsedLabel))
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

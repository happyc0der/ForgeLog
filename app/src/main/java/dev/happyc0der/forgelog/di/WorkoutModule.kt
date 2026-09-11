package dev.happyc0der.forgelog.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.ui.workout.RestTimerFeedback
import dev.happyc0der.forgelog.workout.AndroidRestAlarmScheduler
import dev.happyc0der.forgelog.workout.RestTimerController
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Module
@InstallIn(SingletonComponent::class)
object WorkoutModule {

    /**
     * One for the app, on a scope that is never cancelled: outliving the logger screen is the
     * whole point of it. The rest-end alert is normally the exact alarm; [alert] is the fallback
     * for when exact alarms are not allowed. Either way the settings are read when it fires, so
     * switching vibration or sound off mid-rest is honoured.
     */
    @Provides
    @Singleton
    fun provideRestTimerController(
        @ApplicationContext context: Context,
        timeProvider: TimeProvider,
        settingsRepository: SettingsRepository,
        workoutSessionRepository: WorkoutSessionRepository,
        @MainDispatcher mainDispatcher: CoroutineDispatcher,
    ): RestTimerController = RestTimerController(
        scope = CoroutineScope(SupervisorJob() + mainDispatcher),
        timeProvider = timeProvider,
        alarm = AndroidRestAlarmScheduler(context),
        inProgressSessionId = workoutSessionRepository.observeInProgressSession()
            .map { it?.id }
            .distinctUntilChanged(),
        alert = {
            val settings = settingsRepository.settings.first()
            RestTimerFeedback.signal(
                context = context,
                vibrate = settings.restTimerVibration,
                sound = settings.restTimerSound,
            )
        },
    )
}

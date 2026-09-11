package dev.happyc0der.forgelog.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.ui.workout.RestTimerFeedback
import dev.happyc0der.forgelog.workout.RestTimerController
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

@Module
@InstallIn(SingletonComponent::class)
object WorkoutModule {

    /**
     * One for the app, on a scope that is never cancelled: outliving the logger screen is the
     * whole point of it. The alert reads the settings when it fires, so switching vibration or
     * sound off mid-rest is honoured.
     */
    @Provides
    @Singleton
    fun provideRestTimerController(
        @ApplicationContext context: Context,
        timeProvider: TimeProvider,
        settingsRepository: SettingsRepository,
        @MainDispatcher mainDispatcher: CoroutineDispatcher,
    ): RestTimerController = RestTimerController(
        scope = CoroutineScope(SupervisorJob() + mainDispatcher),
        timeProvider = timeProvider,
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

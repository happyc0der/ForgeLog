package dev.happyc0der.forgelog.domain.settings

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Ignored unless [unit] is a loaded-weight unit; bodyweight and time are not weight defaults. */
    suspend fun setDefaultWeightUnit(unit: ExerciseUnit)

    /** Coerced into [AppSettings.MIN_REST_SECONDS]..[AppSettings.MAX_REST_SECONDS]. */
    suspend fun setDefaultRestSeconds(seconds: Int)

    suspend fun setDurationInputUnit(unit: DurationInputUnit)

    /** Rest is typed separately from an exercise's own duration; see [AppSettings.restInputUnit]. */
    suspend fun setRestInputUnit(unit: DurationInputUnit)
    suspend fun setWeekStartDay(day: DayOfWeek)
    suspend fun setIncludeWarmupInVolume(include: Boolean)
    suspend fun setRestTimerVibration(enabled: Boolean)
    suspend fun setRestTimerSound(enabled: Boolean)
}

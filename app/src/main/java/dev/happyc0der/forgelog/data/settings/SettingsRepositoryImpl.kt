package dev.happyc0der.forgelog.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.happyc0der.forgelog.di.IoDispatcher
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.settings.AppSettings
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .map { it.toAppSettings() }
        .flowOn(ioDispatcher)

    override suspend fun setDefaultWeightUnit(unit: ExerciseUnit) {
        if (!unit.isLoadedWeight) return
        edit { it[SettingsKeys.DEFAULT_WEIGHT_UNIT] = unit.storageValue }
    }

    override suspend fun setDefaultRestSeconds(seconds: Int) = edit {
        it[SettingsKeys.DEFAULT_REST_SECONDS] =
            seconds.coerceIn(AppSettings.MIN_REST_SECONDS, AppSettings.MAX_REST_SECONDS)
    }

    override suspend fun setDurationInputUnit(unit: DurationInputUnit) = edit {
        it[SettingsKeys.DURATION_INPUT_UNIT] = unit.name
    }

    override suspend fun setRestInputUnit(unit: DurationInputUnit) = edit {
        it[SettingsKeys.REST_INPUT_UNIT] = unit.name
    }

    override suspend fun setWeekStartDay(day: DayOfWeek) = edit {
        it[SettingsKeys.WEEK_START_DAY] = day.name
    }

    override suspend fun setIncludeWarmupInVolume(include: Boolean) = edit {
        it[SettingsKeys.INCLUDE_WARMUP_IN_VOLUME] = include
    }

    override suspend fun setRestTimerVibration(enabled: Boolean) = edit {
        it[SettingsKeys.REST_TIMER_VIBRATION] = enabled
    }

    override suspend fun setRestTimerSound(enabled: Boolean) = edit {
        it[SettingsKeys.REST_TIMER_SOUND] = enabled
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        withContext(ioDispatcher) { dataStore.edit(block) }
    }
}

/**
 * A stored value that no longer parses falls back to the default rather than throwing.
 * Settings are a convenience; a bad enum string from an older build must not make the app
 * unlaunchable, and the domain's `fromStorage` helpers throw by design.
 */
internal fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        defaultWeightUnit = this[SettingsKeys.DEFAULT_WEIGHT_UNIT]
            ?.let { stored -> ExerciseUnit.entries.firstOrNull { it.storageValue == stored } }
            ?.takeIf { it.isLoadedWeight }
            ?: defaults.defaultWeightUnit,
        defaultRestSeconds = this[SettingsKeys.DEFAULT_REST_SECONDS]
            ?.coerceIn(AppSettings.MIN_REST_SECONDS, AppSettings.MAX_REST_SECONDS)
            ?: defaults.defaultRestSeconds,
        durationInputUnit = this[SettingsKeys.DURATION_INPUT_UNIT]
            ?.let { stored -> DurationInputUnit.entries.firstOrNull { it.name == stored } }
            ?: defaults.durationInputUnit,
        restInputUnit = this[SettingsKeys.REST_INPUT_UNIT]
            ?.let { stored -> DurationInputUnit.entries.firstOrNull { it.name == stored } }
            ?: defaults.restInputUnit,
        weekStartDay = this[SettingsKeys.WEEK_START_DAY]
            ?.let { stored -> DayOfWeek.entries.firstOrNull { it.name == stored } }
            ?: defaults.weekStartDay,
        includeWarmupInVolume = this[SettingsKeys.INCLUDE_WARMUP_IN_VOLUME]
            ?: defaults.includeWarmupInVolume,
        restTimerVibration = this[SettingsKeys.REST_TIMER_VIBRATION] ?: defaults.restTimerVibration,
        restTimerSound = this[SettingsKeys.REST_TIMER_SOUND] ?: defaults.restTimerSound,
    )
}

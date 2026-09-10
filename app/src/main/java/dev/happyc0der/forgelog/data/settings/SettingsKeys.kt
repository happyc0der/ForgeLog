package dev.happyc0der.forgelog.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Preference keys. [DURATION_INPUT_UNIT] deliberately keeps the name the old SharedPreferences
 * store used, so [LegacyDurationUnitMigration] can carry the existing value across.
 */
internal object SettingsKeys {
    val DURATION_INPUT_UNIT = stringPreferencesKey("duration_input_unit")
    val DEFAULT_WEIGHT_UNIT = stringPreferencesKey("default_weight_unit")
    val DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
    val WEEK_START_DAY = stringPreferencesKey("week_start_day")
    val INCLUDE_WARMUP_IN_VOLUME = booleanPreferencesKey("include_warmup_in_volume")
    val REST_TIMER_VIBRATION = booleanPreferencesKey("rest_timer_vibration")
    val REST_TIMER_SOUND = booleanPreferencesKey("rest_timer_sound")

    const val LEGACY_PREFS_NAME = "forgelog_ui"
}

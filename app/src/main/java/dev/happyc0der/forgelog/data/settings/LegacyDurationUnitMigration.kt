package dev.happyc0der.forgelog.data.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences

/**
 * Carries the duration-unit preference over from the old SharedPreferences store.
 *
 * This is hand-written rather than `SharedPreferencesMigration` on purpose: that helper removes
 * the keys it migrates, and `ui/input/DurationUnitPreference` still reads them directly until the
 * Settings screen owns the value. Deleting the source here would silently reset the user's toggle
 * the first time anything else touched DataStore. Copying without cleanup lets both readers
 * coexist; the legacy store goes away with its last reader.
 */
internal class LegacyDurationUnitMigration(
    private val context: Context,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[SettingsKeys.DURATION_INPUT_UNIT] == null && legacyValue() != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacy = legacyValue() ?: return currentData
        return currentData.toMutablePreferences().apply {
            this[SettingsKeys.DURATION_INPUT_UNIT] = legacy
        }
    }

    override suspend fun cleanUp() = Unit

    private fun legacyValue(): String? = context.applicationContext
        .getSharedPreferences(SettingsKeys.LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        .getString(SettingsKeys.DURATION_INPUT_UNIT.name, null)
}

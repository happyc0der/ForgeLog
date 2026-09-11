package dev.happyc0der.forgelog.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.happyc0der.forgelog.data.settings.LegacyDurationUnitMigration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    private const val SETTINGS_FILE = "forgelog_settings.preferences_pb"

    /**
     * One [DataStore] for the whole process. DataStore enforces a single active instance per
     * file, so this must stay a singleton — constructing a second one for the same file throws
     * at runtime, not at compile time.
     */
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // A settings file that no longer parses starts again from the defaults. Without this every
        // read threw, so every screen that reads a setting showed an error, and Retry read the same
        // broken file. Settings are conveniences; training data lives in the database.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        migrations = listOf(LegacyDurationUnitMigration(context)),
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { context.dataStoreFile(SETTINGS_FILE) },
    )
}

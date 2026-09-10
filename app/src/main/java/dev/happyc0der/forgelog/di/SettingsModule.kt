package dev.happyc0der.forgelog.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
        migrations = listOf(LegacyDurationUnitMigration(context)),
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { context.dataStoreFile(SETTINGS_FILE) },
    )
}

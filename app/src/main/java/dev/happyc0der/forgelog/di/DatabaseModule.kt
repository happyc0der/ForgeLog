package dev.happyc0der.forgelog.di

import android.content.Context
import androidx.room.Room
import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.ForgeLogMigrations
import dev.happyc0der.forgelog.BuildConfig
import dev.happyc0der.forgelog.data.local.dao.BackupDao
import dev.happyc0der.forgelog.data.local.dao.ExerciseDao
import dev.happyc0der.forgelog.data.local.dao.ProgramDao
import dev.happyc0der.forgelog.data.local.dao.WorkoutSessionDao
import dev.happyc0der.forgelog.domain.time.SystemTimeProvider
import dev.happyc0der.forgelog.domain.time.SystemZoneProvider
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.time.ZoneProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): ForgeLogDatabase = Room.databaseBuilder(
        context,
        ForgeLogDatabase::class.java,
        "forgelog.db",
    )
        .addMigrations(*ForgeLogMigrations.ALL)
        .build()

    @Provides
    fun provideExerciseDao(database: ForgeLogDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideProgramDao(database: ForgeLogDatabase): ProgramDao = database.programDao()

    @Provides
    fun provideWorkoutSessionDao(database: ForgeLogDatabase): WorkoutSessionDao =
        database.workoutSessionDao()

    @Provides
    fun provideBackupDao(database: ForgeLogDatabase): BackupDao = database.backupDao()

    /**
     * Stamped into every backup file so a file can be traced to the build that wrote it, which is
     * the first thing worth knowing when a restore misbehaves.
     */
    @Provides
    @AppVersion
    fun provideAppVersion(): String = BuildConfig.VERSION_NAME

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()

    @Provides
    @Singleton
    fun provideZoneProvider(): ZoneProvider = SystemZoneProvider()
}

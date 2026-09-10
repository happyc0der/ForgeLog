package com.example.forgelog.di

import android.content.Context
import androidx.room.Room
import com.example.forgelog.data.local.ForgeLogDatabase
import com.example.forgelog.data.local.ForgeLogMigrations
import com.example.forgelog.data.local.dao.ExerciseDao
import com.example.forgelog.data.local.dao.ProgramDao
import com.example.forgelog.data.local.dao.WorkoutSessionDao
import com.example.forgelog.domain.time.SystemTimeProvider
import com.example.forgelog.domain.time.TimeProvider
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
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()
}

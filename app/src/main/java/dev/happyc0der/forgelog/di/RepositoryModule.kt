package dev.happyc0der.forgelog.di

import dev.happyc0der.forgelog.data.repository.ExerciseRepositoryImpl
import dev.happyc0der.forgelog.data.repository.ProgramRepositoryImpl
import dev.happyc0der.forgelog.data.repository.WorkoutSessionRepositoryImpl
import dev.happyc0der.forgelog.data.backup.AndroidDocumentStore
import dev.happyc0der.forgelog.data.backup.BackupRepositoryImpl
import dev.happyc0der.forgelog.data.settings.SettingsRepositoryImpl
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.backup.BackupRepository
import dev.happyc0der.forgelog.domain.backup.DocumentStore
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindProgramRepository(impl: ProgramRepositoryImpl): ProgramRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutSessionRepository(
        impl: WorkoutSessionRepositoryImpl,
    ): WorkoutSessionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindDocumentStore(impl: AndroidDocumentStore): DocumentStore
}

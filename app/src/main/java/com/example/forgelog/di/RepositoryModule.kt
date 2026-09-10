package com.example.forgelog.di

import com.example.forgelog.data.repository.ExerciseRepositoryImpl
import com.example.forgelog.data.repository.ProgramRepositoryImpl
import com.example.forgelog.data.repository.WorkoutSessionRepositoryImpl
import com.example.forgelog.domain.repository.ExerciseRepository
import com.example.forgelog.domain.repository.ProgramRepository
import com.example.forgelog.domain.repository.WorkoutSessionRepository
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
}

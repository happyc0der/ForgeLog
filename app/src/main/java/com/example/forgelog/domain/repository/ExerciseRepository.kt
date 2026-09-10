package com.example.forgelog.domain.repository

import com.example.forgelog.domain.model.Exercise
import com.example.forgelog.domain.model.ExerciseCategory
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeExercises(includeArchived: Boolean = false): Flow<List<Exercise>>
    fun observeExercisesByCategory(
        category: ExerciseCategory,
        includeArchived: Boolean = false,
    ): Flow<List<Exercise>>
    fun observeExercise(id: Long): Flow<Exercise?>
    suspend fun getExercise(id: Long): Exercise?
    suspend fun upsert(exercise: Exercise): Long
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun hasSessionHistory(id: Long): Boolean
    suspend fun deleteIfUnusedInSessions(id: Long)
}

package dev.happyc0der.forgelog.domain.repository

import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeExercises(includeArchived: Boolean = false): Flow<List<Exercise>>
    suspend fun getExercise(id: Long): Exercise?
    suspend fun upsert(exercise: Exercise): Long

    /** Case-insensitive exact-name lookup, for warning about duplicates. */
    suspend fun findByName(name: String): Exercise?
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun hasSessionHistory(id: Long): Boolean
    suspend fun deleteIfUnusedInSessions(id: Long)
}

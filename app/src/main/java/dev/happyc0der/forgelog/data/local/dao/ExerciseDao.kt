package dev.happyc0der.forgelog.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.happyc0der.forgelog.data.local.entity.ExerciseEntity
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query(
        """
        SELECT * FROM exercises
        WHERE (:includeArchived = 1) OR isArchived = 0
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeExercises(includeArchived: Boolean): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT * FROM exercises
        WHERE category = :category
          AND ((:includeArchived = 1) OR isArchived = 0)
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeExercisesByCategory(
        category: ExerciseCategory,
        includeArchived: Boolean,
    ): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeExercise(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExercise(id: Long): ExerciseEntity?

    /** COLLATE NOCASE so "bench press" and "Bench Press" are recognised as the same name. */
    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?

    @Upsert
    suspend fun upsert(entity: ExerciseEntity): Long

    @Query(
        """
        UPDATE exercises
        SET isArchived = :archived, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: Long)
}

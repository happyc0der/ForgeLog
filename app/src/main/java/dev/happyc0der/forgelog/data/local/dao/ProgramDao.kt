package dev.happyc0der.forgelog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.happyc0der.forgelog.data.local.entity.ProgramDayEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutProgramEntity
import dev.happyc0der.forgelog.data.local.relation.ProgramDayDetailEntity
import dev.happyc0der.forgelog.data.local.relation.ProgramDetailEntity
import dev.happyc0der.forgelog.data.local.relation.ProgramSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Query(
        """
        SELECT * FROM workout_programs
        WHERE (:includeArchived = 1) OR isArchived = 0
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observePrograms(includeArchived: Boolean): Flow<List<WorkoutProgramEntity>>

    @Query(
        """
        SELECT p.*,
            (SELECT COUNT(*) FROM program_days d WHERE d.programId = p.id) AS dayCount,
            (
                SELECT MAX(s.completedAt) FROM workout_sessions s
                WHERE s.programId = p.id AND s.status = 'completed'
            ) AS lastPerformedAt,
            (
                SELECT COUNT(*) FROM workout_sessions s2
                WHERE s2.programId = p.id AND s2.status = 'completed'
            ) AS completedSessionCount
        FROM workout_programs p
        WHERE (:includeArchived = 1) OR p.isArchived = 0
        ORDER BY p.name COLLATE NOCASE ASC
        """,
    )
    fun observeProgramSummaries(includeArchived: Boolean): Flow<List<ProgramSummaryEntity>>

    @Query("SELECT * FROM workout_programs WHERE id = :id")
    fun observeProgram(id: Long): Flow<WorkoutProgramEntity?>

    @Query("SELECT * FROM workout_programs WHERE id = :id")
    suspend fun getProgram(id: Long): WorkoutProgramEntity?

    @Transaction
    @Query("SELECT * FROM workout_programs WHERE id = :id")
    fun observeProgramDetail(id: Long): Flow<ProgramDetailEntity?>

    @Query(
        """
        SELECT * FROM program_days
        WHERE programId = :programId
        ORDER BY dayOrder ASC
        """,
    )
    fun observeDays(programId: Long): Flow<List<ProgramDayEntity>>

    @Transaction
    @Query("SELECT * FROM program_days WHERE id = :dayId")
    fun observeDayDetail(dayId: Long): Flow<ProgramDayDetailEntity?>

    @Query(
        """
        SELECT * FROM program_exercises
        WHERE programDayId = :dayId
        ORDER BY exerciseOrder ASC
        """,
    )
    fun observeProgramExercises(dayId: Long): Flow<List<ProgramExerciseEntity>>

    @Transaction
    @Query("SELECT * FROM program_days WHERE id = :dayId")
    suspend fun getDayDetail(dayId: Long): ProgramDayDetailEntity?

    @Transaction
    @Query("SELECT * FROM workout_programs WHERE id = :id")
    suspend fun getProgramDetail(id: Long): ProgramDetailEntity?

    @Upsert
    suspend fun upsertProgram(entity: WorkoutProgramEntity): Long

    @Query(
        """
        UPDATE workout_programs
        SET isArchived = :archived, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long)

    @Query("DELETE FROM workout_programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Upsert
    suspend fun upsertDay(entity: ProgramDayEntity): Long

    @Query("DELETE FROM program_days WHERE id = :id")
    suspend fun deleteDay(id: Long)

    @Upsert
    suspend fun upsertProgramExercise(entity: ProgramExerciseEntity): Long

    @Query("DELETE FROM program_exercises WHERE id = :id")
    suspend fun deleteProgramExercise(id: Long)

    @Query("DELETE FROM program_exercises WHERE exerciseId = :exerciseId")
    suspend fun deleteProgramExercisesByExerciseId(exerciseId: Long)

    @Insert
    suspend fun insertProgram(entity: WorkoutProgramEntity): Long

    @Insert
    suspend fun insertDay(entity: ProgramDayEntity): Long

    @Insert
    suspend fun insertProgramExercise(entity: ProgramExerciseEntity): Long

    @Query("UPDATE program_days SET dayOrder = :dayOrder WHERE id = :id")
    suspend fun updateDayOrder(id: Long, dayOrder: Int)

    @Query("UPDATE program_exercises SET exerciseOrder = :exerciseOrder WHERE id = :id")
    suspend fun updateProgramExerciseOrder(id: Long, exerciseOrder: Int)

    @Query("SELECT COUNT(*) FROM program_exercises WHERE exerciseId = :exerciseId")
    suspend fun countProgramExercisesForExercise(exerciseId: Long): Int
}

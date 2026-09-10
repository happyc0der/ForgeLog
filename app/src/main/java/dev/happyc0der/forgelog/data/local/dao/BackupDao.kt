package dev.happyc0der.forgelog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.happyc0der.forgelog.data.local.entity.ExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramDayEntity
import dev.happyc0der.forgelog.data.local.entity.ProgramExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SessionExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SetLogEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutProgramEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutSessionEntity
import dev.happyc0der.forgelog.data.local.relation.SessionDetailEntity

/**
 * Whole-table reads and writes, used only by backup, restore and delete-all.
 *
 * Kept separate from the feature DAOs so that "read every row" and "delete every row" are not
 * sitting next to the queries the app uses normally, where they would be easy to call by accident.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM exercises ORDER BY id ASC")
    suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM workout_programs ORDER BY id ASC")
    suspend fun allPrograms(): List<WorkoutProgramEntity>

    @Query("SELECT * FROM program_days ORDER BY id ASC")
    suspend fun allProgramDays(): List<ProgramDayEntity>

    @Query("SELECT * FROM program_exercises ORDER BY id ASC")
    suspend fun allProgramExercises(): List<ProgramExerciseEntity>

    @Query("SELECT * FROM workout_sessions ORDER BY id ASC")
    suspend fun allSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM session_exercises ORDER BY id ASC")
    suspend fun allSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_logs ORDER BY id ASC")
    suspend fun allSetLogs(): List<SetLogEntity>

    /** Half-open window, matching the rest of the app. Null bounds mean "no bound". */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE (:fromEpochMs IS NULL OR startedAt >= :fromEpochMs)
          AND (:untilEpochMs IS NULL OR startedAt < :untilEpochMs)
        ORDER BY startedAt ASC
        """,
    )
    suspend fun sessionDetailsBetween(
        fromEpochMs: Long?,
        untilEpochMs: Long?,
    ): List<SessionDetailEntity>

    @Insert
    suspend fun insertExercises(entities: List<ExerciseEntity>)

    @Insert
    suspend fun insertPrograms(entities: List<WorkoutProgramEntity>)

    @Insert
    suspend fun insertProgramDays(entities: List<ProgramDayEntity>)

    @Insert
    suspend fun insertProgramExercises(entities: List<ProgramExerciseEntity>)

    @Insert
    suspend fun insertSessions(entities: List<WorkoutSessionEntity>)

    @Insert
    suspend fun insertSessionExercises(entities: List<SessionExerciseEntity>)

    @Insert
    suspend fun insertSetLogs(entities: List<SetLogEntity>)

    @Query("DELETE FROM set_logs")
    suspend fun deleteAllSetLogs()

    @Query("DELETE FROM session_exercises")
    suspend fun deleteAllSessionExercises()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM program_exercises")
    suspend fun deleteAllProgramExercises()

    @Query("DELETE FROM program_days")
    suspend fun deleteAllProgramDays()

    @Query("DELETE FROM workout_programs")
    suspend fun deleteAllPrograms()

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises()
}

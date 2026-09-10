package dev.happyc0der.forgelog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.happyc0der.forgelog.data.local.entity.SessionExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SetLogEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutSessionEntity
import dev.happyc0der.forgelog.data.local.relation.SessionDetailEntity
import dev.happyc0der.forgelog.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<WorkoutSessionEntity>>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = :status
        ORDER BY startedAt DESC
        """,
    )
    fun observeSessionsByStatus(status: SessionStatus): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<WorkoutSessionEntity?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSessionDetail(id: Long): Flow<SessionDetailEntity?>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = 'in_progress'
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    fun observeInProgressSession(): Flow<WorkoutSessionEntity?>

    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = 'in_progress'
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    fun observeInProgressSessionDetail(): Flow<SessionDetailEntity?>

    @Query(
        """
        SELECT * FROM session_exercises
        WHERE sessionId = :sessionId
        ORDER BY exerciseOrder ASC
        """,
    )
    fun observeSessionExercises(sessionId: Long): Flow<List<SessionExerciseEntity>>

    @Query(
        """
        SELECT * FROM set_logs
        WHERE sessionExerciseId = :sessionExerciseId
        ORDER BY setNumber ASC
        """,
    )
    fun observeSetLogs(sessionExerciseId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: Long): WorkoutSessionEntity?

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = 'in_progress'
        ORDER BY startedAt DESC
        LIMIT 1
        """,
    )
    suspend fun getInProgressSession(): WorkoutSessionEntity?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionDetail(id: Long): SessionDetailEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = 'completed'
          AND id != :excludeSessionId
        ORDER BY completedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentCompletedSessionDetails(
        excludeSessionId: Long,
        limit: Int,
    ): List<SessionDetailEntity>

    @Insert
    suspend fun insertSession(entity: WorkoutSessionEntity): Long

    @Insert
    suspend fun insertSessionExercise(entity: SessionExerciseEntity): Long

    @Upsert
    suspend fun upsertSession(entity: WorkoutSessionEntity): Long

    @Upsert
    suspend fun upsertSessionExercise(entity: SessionExerciseEntity): Long

    @Upsert
    suspend fun upsertSetLog(entity: SetLogEntity): Long

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSetLog(id: Long)

    @Query("SELECT COUNT(*) FROM session_exercises WHERE exerciseId = :exerciseId")
    suspend fun countSessionExercisesForExercise(exerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE programId = :programId")
    suspend fun countSessionsForProgram(programId: Long): Int
}

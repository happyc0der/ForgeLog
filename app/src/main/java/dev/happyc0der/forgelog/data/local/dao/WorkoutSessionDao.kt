package dev.happyc0der.forgelog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.happyc0der.forgelog.data.local.entity.SessionExerciseEntity
import dev.happyc0der.forgelog.data.local.entity.SetLogEntity
import dev.happyc0der.forgelog.data.local.entity.WorkoutSessionEntity
import dev.happyc0der.forgelog.data.local.relation.LoggedExerciseEntity
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

    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = 'completed'
        ORDER BY completedAt DESC
        LIMIT 1
        """,
    )
    fun observeLastCompletedSessionDetail(): Flow<SessionDetailEntity?>

    /**
     * Completed sessions in a half-open window, `[from, until)`, matching
     * [dev.happyc0der.forgelog.domain.time.WeekBoundary.Range] so adjacent weeks cannot
     * double-count a session logged exactly on a boundary.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = 'completed'
          AND completedAt >= :fromEpochMs
          AND completedAt < :untilEpochMs
        ORDER BY completedAt DESC
        """,
    )
    fun observeCompletedSessionDetailsBetween(
        fromEpochMs: Long,
        untilEpochMs: Long,
    ): Flow<List<SessionDetailEntity>>

    /**
     * History list, every filter optional.
     *
     * Each filter is written as `:param IS NULL OR ...` so one query serves every combination
     * rather than assembling SQL by hand. Text search covers the session name and the exercise
     * name *snapshots*, which is what makes searching old sessions reliable: renaming an exercise
     * in the library never rewrites what a past workout was called.
     *
     * Ordered and filtered by `startedAt`, because "when did I train" is when the session began.
     */
    @Transaction
    @Query(
        """
        SELECT s.* FROM workout_sessions s
        WHERE (:status IS NULL OR s.status = :status)
          AND (:programId IS NULL OR s.programId = :programId)
          AND (:programDayId IS NULL OR s.programDayId = :programDayId)
          AND (:fromEpochMs IS NULL OR s.startedAt >= :fromEpochMs)
          AND (:untilEpochMs IS NULL OR s.startedAt < :untilEpochMs)
          AND (
            :exerciseId IS NULL OR EXISTS (
                SELECT 1 FROM session_exercises e
                WHERE e.sessionId = s.id AND e.exerciseId = :exerciseId
            )
          )
          AND (
            :query = '' OR s.sessionName LIKE '%' || :query || '%' OR EXISTS (
                SELECT 1 FROM session_exercises e2
                WHERE e2.sessionId = s.id AND e2.displayNameSnapshot LIKE '%' || :query || '%'
            )
          )
        ORDER BY s.startedAt DESC
        """,
    )
    fun observeSessionHistory(
        query: String,
        status: SessionStatus?,
        programId: Long?,
        programDayId: Long?,
        exerciseId: Long?,
        fromEpochMs: Long?,
        untilEpochMs: Long?,
    ): Flow<List<SessionDetailEntity>>

    /** Distinct exercises that appear anywhere in history, for the history filter picker. */
    @Query(
        """
        SELECT DISTINCT e.exerciseId AS exerciseId, e.displayNameSnapshot AS displayName
        FROM session_exercises e
        WHERE e.exerciseId IS NOT NULL
        ORDER BY e.displayNameSnapshot COLLATE NOCASE ASC
        """,
    )
    fun observeLoggedExercises(): Flow<List<LoggedExerciseEntity>>

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

    /*
     * Single-field edits are targeted UPDATEs rather than read-modify-write upserts.
     *
     * Reading a row, copying it and upserting it back is not atomic: setting a feeling and typing a
     * note in quick succession had the second write holding a pre-feeling copy of the row, silently
     * undoing the first. A statement that touches only the column being edited cannot lose a
     * concurrent edit to a different column.
     */

    @Query("UPDATE workout_sessions SET overallFeeling = :feeling, updatedAt = :now WHERE id = :id")
    suspend fun updateOverallFeeling(id: Long, feeling: Int?, now: Long)

    @Query("UPDATE workout_sessions SET overallNotes = :notes, updatedAt = :now WHERE id = :id")
    suspend fun updateOverallNotes(id: Long, notes: String?, now: Long)

    @Query("UPDATE session_exercises SET feeling = :feeling WHERE id = :id")
    suspend fun updateExerciseFeeling(id: Long, feeling: Int?)

    @Query("UPDATE session_exercises SET exerciseNotes = :notes WHERE id = :id")
    suspend fun updateExerciseNotes(id: Long, notes: String?)

    @Query(
        """
        UPDATE workout_sessions
        SET status = :status, completedAt = :completedAt, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(id: Long, status: SessionStatus, completedAt: Long?, now: Long)

    @Query(
        """
        UPDATE workout_sessions
        SET expandedSessionExerciseId = :sessionExerciseId, updatedAt = :now
        WHERE id = :id
        """,
    )
    suspend fun updateExpandedExercise(id: Long, sessionExerciseId: Long?, now: Long)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSetLog(id: Long)

    @Query("SELECT COUNT(*) FROM session_exercises WHERE exerciseId = :exerciseId")
    suspend fun countSessionExercisesForExercise(exerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_sessions")
    suspend fun observeSessionsCount(): Int

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE programId = :programId")
    suspend fun countSessionsForProgram(programId: Long): Int
}

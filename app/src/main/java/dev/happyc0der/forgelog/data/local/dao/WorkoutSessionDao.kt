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
        WHERE status = 'completed'
        ORDER BY completedAt DESC
        LIMIT 1
        """,
    )
    fun observeLastCompletedSessionDetail(): Flow<SessionDetailEntity?>

    /**
     * Completed sessions that started in a half-open window, `[from, until)`, matching
     * [dev.happyc0der.forgelog.domain.time.WeekBoundary.Range] so adjacent weeks cannot
     * double-count a session logged exactly on a boundary.
     *
     * By start, as History dates a session. This went by the finish, so a workout from 23:30 on
     * the last day of a week to 00:40 was listed under that week in History and counted in the
     * next on Home and in Analytics.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE status = 'completed'
          AND startedAt >= :fromEpochMs
          AND startedAt < :untilEpochMs
        ORDER BY startedAt DESC
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
     *
     * The text search declares `ESCAPE '\'` and the repository escapes the term before it gets
     * here. Without it a search for "50%" matched any session containing "50", and a search for
     * "_" matched everything with at least one character.
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
            :query = '' OR s.sessionName LIKE '%' || :query || '%' ESCAPE '\' OR EXISTS (
                SELECT 1 FROM session_exercises e2
                WHERE e2.sessionId = s.id
                  AND e2.displayNameSnapshot LIKE '%' || :query || '%' ESCAPE '\'
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

    /**
     * The most recent completed sessions in which any of [exerciseIds] was logged.
     *
     * For "last time" and records, which are about particular lifts: the most recent sessions of any
     * kind can be taken up entirely by others -- swims imported every evening, a run of pull days --
     * and the last bench session fall outside them.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_sessions s
        WHERE s.status = 'completed'
          AND s.id != :excludeSessionId
          AND EXISTS (
            SELECT 1 FROM session_exercises e
            WHERE e.sessionId = s.id AND e.exerciseId IN (:exerciseIds)
          )
        ORDER BY s.completedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getCompletedSessionDetailsWithExercises(
        exerciseIds: List<Long>,
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

    /** Only the rest column, so recording rest cannot overwrite an edit to the set's numbers. */
    @Query("UPDATE set_logs SET restAfterSetSeconds = :seconds WHERE id = :id")
    suspend fun updateSetRestAfter(id: Long, seconds: Int?)

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

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSetLog(id: Long)

    @Query("SELECT * FROM set_logs WHERE id = :id")
    suspend fun getSetLog(id: Long): SetLogEntity?

    /** Moves the sets after a deleted one up a place. See WorkoutSessionRepository.deleteSetLog. */
    @Query(
        """
        UPDATE set_logs SET setNumber = setNumber - 1
        WHERE sessionExerciseId = :sessionExerciseId AND setNumber > :deletedSetNumber
        """,
    )
    suspend fun closeSetNumberGap(sessionExerciseId: Long, deletedSetNumber: Int)

    @Query("SELECT COUNT(*) FROM session_exercises WHERE exerciseId = :exerciseId")
    suspend fun countSessionExercisesForExercise(exerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE programId = :programId")
    suspend fun countSessionsForProgram(programId: Long): Int
}

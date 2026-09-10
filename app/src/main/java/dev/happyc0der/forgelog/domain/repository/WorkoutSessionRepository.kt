package dev.happyc0der.forgelog.domain.repository

import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExercise
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface WorkoutSessionRepository {
    fun observeSessions(): Flow<List<WorkoutSession>>
    fun observeSessionsByStatus(status: SessionStatus): Flow<List<WorkoutSession>>
    fun observeSession(id: Long): Flow<WorkoutSession?>
    fun observeSessionDetail(id: Long): Flow<SessionDetail?>
    fun observeInProgressSession(): Flow<WorkoutSession?>
    fun observeLastCompletedSessionDetail(): Flow<SessionDetail?>

    /** Completed sessions in the half-open window `[fromEpochMs, untilEpochMs)`. */
    fun observeCompletedSessionDetailsBetween(
        fromEpochMs: Long,
        untilEpochMs: Long,
    ): Flow<List<SessionDetail>>
    fun observeInProgressSessionDetail(): Flow<SessionDetail?>
    fun observeSessionExercises(sessionId: Long): Flow<List<SessionExercise>>
    fun observeSetLogs(sessionExerciseId: Long): Flow<List<SetLog>>
    suspend fun getSession(id: Long): WorkoutSession?
    suspend fun getSessionDetail(id: Long): SessionDetail?
    suspend fun getInProgressSession(): WorkoutSession?
    suspend fun getRecentCompletedDetails(
        excludeSessionId: Long = 0L,
        limit: Int = 40,
    ): List<SessionDetail>
    suspend fun upsertSession(session: WorkoutSession): Long
    suspend fun upsertSessionExercise(sessionExercise: SessionExercise): Long
    suspend fun upsertSetLog(setLog: SetLog): Long
    suspend fun deleteSession(id: Long)
    suspend fun deleteSessionExercise(id: Long)
    suspend fun deleteSetLog(id: Long)
    suspend fun findPreviousPerformance(
        currentSession: WorkoutSession,
        currentExercise: SessionExercise,
    ): SessionExerciseWithSets?
    suspend fun startSession(
        programId: Long?,
        programDayId: Long?,
        sessionName: String,
        exercises: List<SessionStartExercise>,
    ): Long
    suspend fun abandonSession(sessionId: Long)
    suspend fun completeSession(sessionId: Long)
    suspend fun updateExpandedExercise(sessionId: Long, sessionExerciseId: Long?)
}

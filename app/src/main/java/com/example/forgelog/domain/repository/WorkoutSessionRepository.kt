package com.example.forgelog.domain.repository

import com.example.forgelog.domain.model.SessionDetail
import com.example.forgelog.domain.model.SessionExercise
import com.example.forgelog.domain.model.SessionExerciseWithSets
import com.example.forgelog.domain.model.SessionStartExercise
import com.example.forgelog.domain.model.SessionStatus
import com.example.forgelog.domain.model.SetLog
import com.example.forgelog.domain.model.WorkoutSession
import kotlinx.coroutines.flow.Flow

interface WorkoutSessionRepository {
    fun observeSessions(): Flow<List<WorkoutSession>>
    fun observeSessionsByStatus(status: SessionStatus): Flow<List<WorkoutSession>>
    fun observeSession(id: Long): Flow<WorkoutSession?>
    fun observeSessionDetail(id: Long): Flow<SessionDetail?>
    fun observeInProgressSession(): Flow<WorkoutSession?>
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

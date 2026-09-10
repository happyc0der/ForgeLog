package dev.happyc0der.forgelog.data.repository

import androidx.room.withTransaction
import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.dao.WorkoutSessionDao
import dev.happyc0der.forgelog.data.mapper.toDomain
import dev.happyc0der.forgelog.data.mapper.toEntity
import dev.happyc0der.forgelog.di.IoDispatcher
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExercise
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.workout.PreviousWorkoutMatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutSessionRepositoryImpl @Inject constructor(
    private val database: ForgeLogDatabase,
    private val workoutSessionDao: WorkoutSessionDao,
    private val timeProvider: TimeProvider,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WorkoutSessionRepository {

    override fun observeSessions(): Flow<List<WorkoutSession>> =
        workoutSessionDao.observeSessions()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeSessionsByStatus(status: SessionStatus): Flow<List<WorkoutSession>> =
        workoutSessionDao.observeSessionsByStatus(status)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeSession(id: Long): Flow<WorkoutSession?> =
        workoutSessionDao.observeSession(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeSessionDetail(id: Long): Flow<SessionDetail?> =
        workoutSessionDao.observeSessionDetail(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeInProgressSession(): Flow<WorkoutSession?> =
        workoutSessionDao.observeInProgressSession()
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeInProgressSessionDetail(): Flow<SessionDetail?> =
        workoutSessionDao.observeInProgressSessionDetail()
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeLastCompletedSessionDetail(): Flow<SessionDetail?> =
        workoutSessionDao.observeLastCompletedSessionDetail()
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeCompletedSessionDetailsBetween(
        fromEpochMs: Long,
        untilEpochMs: Long,
    ): Flow<List<SessionDetail>> =
        workoutSessionDao.observeCompletedSessionDetailsBetween(fromEpochMs, untilEpochMs)
            .map { details -> details.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeSessionExercises(sessionId: Long): Flow<List<SessionExercise>> =
        workoutSessionDao.observeSessionExercises(sessionId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeSetLogs(sessionExerciseId: Long): Flow<List<SetLog>> =
        workoutSessionDao.observeSetLogs(sessionExerciseId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getSession(id: Long): WorkoutSession? = withContext(ioDispatcher) {
        workoutSessionDao.getSession(id)?.toDomain()
    }

    override suspend fun getSessionDetail(id: Long): SessionDetail? = withContext(ioDispatcher) {
        workoutSessionDao.getSessionDetail(id)?.toDomain()
    }

    override suspend fun getInProgressSession(): WorkoutSession? = withContext(ioDispatcher) {
        workoutSessionDao.getInProgressSession()?.toDomain()
    }

    override suspend fun getRecentCompletedDetails(
        excludeSessionId: Long,
        limit: Int,
    ): List<SessionDetail> = withContext(ioDispatcher) {
        workoutSessionDao.getRecentCompletedSessionDetails(excludeSessionId, limit)
            .map { it.toDomain() }
    }

    override suspend fun upsertSession(session: WorkoutSession): Long = withContext(ioDispatcher) {
        val now = timeProvider.nowEpochMs()
        val stamped = if (session.id == 0L) {
            session.copy(createdAt = now, updatedAt = now)
        } else {
            session.copy(updatedAt = now)
        }
        workoutSessionDao.upsertSession(stamped.toEntity())
    }

    override suspend fun upsertSessionExercise(sessionExercise: SessionExercise): Long =
        withContext(ioDispatcher) {
            workoutSessionDao.upsertSessionExercise(sessionExercise.toEntity())
        }

    override suspend fun upsertSetLog(setLog: SetLog): Long = withContext(ioDispatcher) {
        workoutSessionDao.upsertSetLog(setLog.toEntity())
    }

    override suspend fun deleteSession(id: Long) = withContext(ioDispatcher) {
        workoutSessionDao.deleteSession(id)
    }

    override suspend fun deleteSessionExercise(id: Long) = withContext(ioDispatcher) {
        workoutSessionDao.deleteSessionExercise(id)
    }

    override suspend fun deleteSetLog(id: Long) = withContext(ioDispatcher) {
        workoutSessionDao.deleteSetLog(id)
    }

    override suspend fun findPreviousPerformance(
        currentSession: WorkoutSession,
        currentExercise: SessionExercise,
    ): SessionExerciseWithSets? = withContext(ioDispatcher) {
        val history = getRecentCompletedDetails(currentSession.id)
        PreviousWorkoutMatcher.findPreviousExercise(
            currentSession = currentSession,
            currentExercise = currentExercise,
            history = history,
        )
    }

    override suspend fun startSession(
        programId: Long?,
        programDayId: Long?,
        sessionName: String,
        exercises: List<SessionStartExercise>,
    ): Long = withContext(ioDispatcher) {
        database.withTransaction {
            val now = timeProvider.nowEpochMs()
            val sessionId = workoutSessionDao.insertSession(
                WorkoutSession(
                    programDayId = programDayId,
                    programId = programId,
                    sessionName = sessionName,
                    startedAt = now,
                    status = SessionStatus.IN_PROGRESS,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity(),
            )
            exercises.forEachIndexed { index, planned ->
                val pointers = planned.pointersOverride ?: planned.exercise.defaultPointers
                workoutSessionDao.insertSessionExercise(
                    SessionExercise(
                        sessionId = sessionId,
                        exerciseId = planned.exercise.id,
                        displayNameSnapshot = planned.exercise.name,
                        exerciseOrder = index,
                        startedAt = now,
                        howToUrlSnapshot = planned.exercise.howToUrl,
                        pointersSnapshot = pointers,
                    ).toEntity(),
                )
            }
            val created = workoutSessionDao.getSessionDetail(sessionId)?.toDomain()
            val firstId = created?.exercises?.firstOrNull()?.exercise?.id
            if (firstId != null) {
                val session = created.session.copy(
                    expandedSessionExerciseId = firstId,
                    updatedAt = now,
                )
                workoutSessionDao.upsertSession(session.toEntity())
            }
            sessionId
        }
    }

    override suspend fun abandonSession(sessionId: Long) = withContext(ioDispatcher) {
        val session = workoutSessionDao.getSession(sessionId)?.toDomain() ?: return@withContext
        val now = timeProvider.nowEpochMs()
        workoutSessionDao.upsertSession(
            session.copy(
                status = SessionStatus.ABANDONED,
                completedAt = now,
                updatedAt = now,
            ).toEntity(),
        )
    }

    override suspend fun completeSession(sessionId: Long) = withContext(ioDispatcher) {
        val session = workoutSessionDao.getSession(sessionId)?.toDomain() ?: return@withContext
        val now = timeProvider.nowEpochMs()
        workoutSessionDao.upsertSession(
            session.copy(
                status = SessionStatus.COMPLETED,
                completedAt = now,
                updatedAt = now,
            ).toEntity(),
        )
    }

    override suspend fun updateExpandedExercise(
        sessionId: Long,
        sessionExerciseId: Long?,
    ) = withContext(ioDispatcher) {
        val session = workoutSessionDao.getSession(sessionId)?.toDomain() ?: return@withContext
        workoutSessionDao.upsertSession(
            session.copy(
                expandedSessionExerciseId = sessionExerciseId,
                updatedAt = timeProvider.nowEpochMs(),
            ).toEntity(),
        )
    }

}

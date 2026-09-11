package dev.happyc0der.forgelog.data.repository

import androidx.room.withTransaction
import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.dao.WorkoutSessionDao
import dev.happyc0der.forgelog.data.mapper.toDomain
import dev.happyc0der.forgelog.data.mapper.toEntity
import dev.happyc0der.forgelog.di.IoDispatcher
import dev.happyc0der.forgelog.domain.history.HistoryFilter
import dev.happyc0der.forgelog.domain.history.LoggedExercise
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExercise
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.workout.PreviousPerformance
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

    override fun observeSessionDetail(id: Long): Flow<SessionDetail?> =
        workoutSessionDao.observeSessionDetail(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeInProgressSession(): Flow<WorkoutSession?> =
        workoutSessionDao.observeInProgressSession()
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeLastCompletedSessionDetail(): Flow<SessionDetail?> =
        workoutSessionDao.observeLastCompletedSessionDetail()
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeSessionHistory(filter: HistoryFilter): Flow<List<SessionDetail>> =
        workoutSessionDao.observeSessionHistory(
            query = filter.normalizedQuery,
            status = filter.status,
            programId = filter.programId,
            programDayId = filter.programDayId,
            exerciseId = filter.exerciseId,
            fromEpochMs = filter.fromEpochMs,
            untilEpochMs = filter.untilEpochMs,
        )
            .map { details -> details.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeLoggedExercises(): Flow<List<LoggedExercise>> =
        workoutSessionDao.observeLoggedExercises()
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeCompletedSessionDetailsBetween(
        fromEpochMs: Long,
        untilEpochMs: Long,
    ): Flow<List<SessionDetail>> =
        workoutSessionDao.observeCompletedSessionDetailsBetween(fromEpochMs, untilEpochMs)
            .map { details -> details.map { it.toDomain() } }
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

    override suspend fun getCompletedDetailsWithExercises(
        exerciseIds: Collection<Long>,
        excludeSessionId: Long,
        limit: Int,
    ): List<SessionDetail> = withContext(ioDispatcher) {
        if (exerciseIds.isEmpty()) return@withContext emptyList()
        workoutSessionDao.getCompletedSessionDetailsWithExercises(
            exerciseIds = exerciseIds.distinct(),
            excludeSessionId = excludeSessionId,
            limit = limit,
        ).map { it.toDomain() }
    }

    override suspend fun upsertSession(session: WorkoutSession): Long = withContext(ioDispatcher) {
        val now = timeProvider.nowEpochMs()
        val stamped = if (session.id == 0L) {
            session.copy(createdAt = now, updatedAt = now)
        } else {
            session.copy(updatedAt = now)
        }
        workoutSessionDao.upsertSession(stamped.toEntity()).orExistingId(stamped.id)
    }

    override suspend fun upsertSessionExercise(sessionExercise: SessionExercise): Long =
        withContext(ioDispatcher) {
            workoutSessionDao.upsertSessionExercise(sessionExercise.toEntity()).orExistingId(sessionExercise.id)
        }

    override suspend fun upsertSetLog(setLog: SetLog): Long = withContext(ioDispatcher) {
        workoutSessionDao.upsertSetLog(setLog.toEntity()).orExistingId(setLog.id)
    }

    override suspend fun deleteSession(id: Long) = withContext(ioDispatcher) {
        workoutSessionDao.deleteSession(id)
    }

    override suspend fun setRestAfter(setLogId: Long, seconds: Int?) = withContext(ioDispatcher) {
        workoutSessionDao.updateSetRestAfter(setLogId, seconds)
    }

    /**
     * The sets after the deleted one move up a place: a set's number is its position. Deleting set 2
     * of 3 used to leave "Set 1" and "Set 3", and the next set added became "Set 4".
     */
    override suspend fun deleteSetLog(id: Long) = withContext(ioDispatcher) {
        database.withTransaction {
            val deleted = workoutSessionDao.getSetLog(id) ?: return@withTransaction
            workoutSessionDao.deleteSetLog(id)
            workoutSessionDao.closeSetNumberGap(deleted.sessionExerciseId, deleted.setNumber)
        }
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
                        // Snapshotted, like the name and pointers: editing the program later must
                        // not rewrite what this session was aiming for.
                        plannedSets = planned.plannedSets,
                        targetRepMin = planned.targetRepMin,
                        targetRepMax = planned.targetRepMax,
                        targetWeight = planned.targetWeight,
                        targetDurationSeconds = planned.targetDurationSeconds,
                        targetRestSeconds = planned.targetRestSeconds,
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

    override suspend fun setOverallFeeling(sessionId: Long, feeling: Int?) =
        withContext(ioDispatcher) {
            workoutSessionDao.updateOverallFeeling(sessionId, feeling, timeProvider.nowEpochMs())
        }

    override suspend fun setOverallNotes(sessionId: Long, notes: String?) =
        withContext(ioDispatcher) {
            workoutSessionDao.updateOverallNotes(sessionId, notes, timeProvider.nowEpochMs())
        }

    override suspend fun setExerciseFeeling(sessionExerciseId: Long, feeling: Int?) =
        withContext(ioDispatcher) {
            workoutSessionDao.updateExerciseFeeling(sessionExerciseId, feeling)
        }

    override suspend fun setExerciseNotes(sessionExerciseId: Long, notes: String?) =
        withContext(ioDispatcher) {
            workoutSessionDao.updateExerciseNotes(sessionExerciseId, notes)
        }

    override suspend fun repeatSession(sessionId: Long): Long? = withContext(ioDispatcher) {
        val source = workoutSessionDao.getSessionDetail(sessionId)?.toDomain()
            ?: return@withContext null
        database.withTransaction {
            val now = timeProvider.nowEpochMs()
            val newSessionId = workoutSessionDao.insertSession(
                WorkoutSession(
                    programDayId = source.session.programDayId,
                    programId = source.session.programId,
                    sessionName = source.session.sessionName,
                    startedAt = now,
                    status = SessionStatus.IN_PROGRESS,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity(),
            )
            source.exercises
                .sortedBy { it.exercise.exerciseOrder }
                .forEachIndexed { index, logged ->
                    // The lift as the library has it now: a new session snapshots the current
                    // name and how-to link, as starting from a program does. The old session's
                    // were copied, so a lift renamed since came back under its old name. Pointers
                    // and targets stay the old session's: they may have been the program's own.
                    val current = logged.exercise.exerciseId
                        ?.let { id -> database.exerciseDao().getExercise(id) }
                    workoutSessionDao.insertSessionExercise(
                        logged.exercise.copy(
                            id = 0L,
                            sessionId = newSessionId,
                            exerciseOrder = index,
                            startedAt = now,
                            displayNameSnapshot = current?.name ?: logged.exercise.displayNameSnapshot,
                            // The library's link, or none if it has been removed there.
                            howToUrlSnapshot = if (current != null) current.howToUrl else logged.exercise.howToUrlSnapshot,
                            // Carried-over notes and feeling would describe the old session.
                            exerciseNotes = null,
                            feeling = null,
                        ).toEntity(),
                    )
                }
            // Open the first exercise so the logger does not start fully collapsed.
            val created = workoutSessionDao.getSessionDetail(newSessionId)?.toDomain()
            val firstId = created?.exercises?.firstOrNull()?.exercise?.id
            if (created != null && firstId != null) {
                workoutSessionDao.upsertSession(
                    created.session.copy(
                        expandedSessionExerciseId = firstId,
                        updatedAt = now,
                    ).toEntity(),
                )
            }
            newSessionId
        }
    }

    /**
     * Abandoning leaves [WorkoutSession.completedAt] null.
     *
     * A null completion time is what marks a duration as unknown, and an abandoned session has no
     * meaningful duration — stamping one made the history list show a figure that
     * [dev.happyc0der.forgelog.domain.home.SessionSummary] documents as having to be blank.
     */
    override suspend fun abandonSession(sessionId: Long) = withContext(ioDispatcher) {
        workoutSessionDao.updateStatus(
            id = sessionId,
            status = SessionStatus.ABANDONED,
            completedAt = null,
            now = timeProvider.nowEpochMs(),
        )
    }

    override suspend fun completeSession(sessionId: Long) = withContext(ioDispatcher) {
        val now = timeProvider.nowEpochMs()
        workoutSessionDao.updateStatus(
            id = sessionId,
            status = SessionStatus.COMPLETED,
            completedAt = now,
            now = now,
        )
    }

    override suspend fun updateExpandedExercise(
        sessionId: Long,
        sessionExerciseId: Long?,
    ) = withContext(ioDispatcher) {
        workoutSessionDao.updateExpandedExercise(
            id = sessionId,
            sessionExerciseId = sessionExerciseId,
            now = timeProvider.nowEpochMs(),
        )
    }

}

package dev.happyc0der.forgelog.data.repository

import androidx.room.withTransaction
import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.dao.ProgramDao
import dev.happyc0der.forgelog.data.local.dao.WorkoutSessionDao
import dev.happyc0der.forgelog.data.mapper.toDomain
import dev.happyc0der.forgelog.data.mapper.toEntity
import dev.happyc0der.forgelog.di.IoDispatcher
import dev.happyc0der.forgelog.domain.library.LibraryCopyNames
import dev.happyc0der.forgelog.domain.model.ProgramDay
import dev.happyc0der.forgelog.domain.model.ProgramDayDetail
import dev.happyc0der.forgelog.domain.model.ProgramDetail
import dev.happyc0der.forgelog.domain.model.ProgramExercise
import dev.happyc0der.forgelog.domain.model.ProgramSummary
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.workout.DayFromSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgramRepositoryImpl @Inject constructor(
    private val database: ForgeLogDatabase,
    private val programDao: ProgramDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val timeProvider: TimeProvider,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ProgramRepository {

    override fun observePrograms(includeArchived: Boolean): Flow<List<WorkoutProgram>> =
        programDao.observePrograms(includeArchived)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeProgramSummaries(includeArchived: Boolean): Flow<List<ProgramSummary>> =
        programDao.observeProgramSummaries(includeArchived)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeProgramDetail(id: Long): Flow<ProgramDetail?> =
        programDao.observeProgramDetail(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override fun observeDays(programId: Long): Flow<List<ProgramDay>> =
        programDao.observeDays(programId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeDayDetail(dayId: Long): Flow<ProgramDayDetail?> =
        programDao.observeDayDetail(dayId)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun getProgram(id: Long): WorkoutProgram? = withContext(ioDispatcher) {
        programDao.getProgram(id)?.toDomain()
    }

    override suspend fun getProgramDetail(id: Long): ProgramDetail? = withContext(ioDispatcher) {
        programDao.getProgramDetail(id)?.toDomain()
    }

    override suspend fun getDayDetail(dayId: Long): ProgramDayDetail? = withContext(ioDispatcher) {
        programDao.getDayDetail(dayId)?.toDomain()
    }

    override suspend fun upsertProgram(program: WorkoutProgram): Long = withContext(ioDispatcher) {
        val now = timeProvider.nowEpochMs()
        val stamped = if (program.id == 0L) {
            program.copy(createdAt = now, updatedAt = now)
        } else {
            program.copy(updatedAt = now)
        }
        programDao.upsertProgram(stamped.toEntity()).orExistingId(stamped.id)
    }

    override suspend fun setArchived(id: Long, archived: Boolean) = withContext(ioDispatcher) {
        programDao.setArchived(id, archived, timeProvider.nowEpochMs())
    }

    override suspend fun hasSessionHistory(programId: Long): Boolean = withContext(ioDispatcher) {
        workoutSessionDao.countSessionsForProgram(programId) > 0
    }

    override suspend fun deleteProgram(id: Long) = withContext(ioDispatcher) {
        programDao.deleteProgram(id)
    }

    override suspend fun duplicateProgram(programId: Long): Long = withContext(ioDispatcher) {
        database.withTransaction {
            val detail = programDao.getProgramDetail(programId)
                ?: error("Program $programId was not found.")
            val now = timeProvider.nowEpochMs()
            val source = detail.program
            val newProgramId = programDao.insertProgram(
                source.copy(
                    id = 0L,
                    name = LibraryCopyNames.programCopy(source.name),
                    isArchived = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            detail.days
                .sortedBy { it.day.dayOrder }
                .forEach { dayDetail ->
                    val newDayId = programDao.insertDay(
                        dayDetail.day.copy(
                            id = 0L,
                            programId = newProgramId,
                        ),
                    )
                    dayDetail.exercises
                        .sortedBy { it.programExercise.exerciseOrder }
                        .forEach { exerciseDetail ->
                            programDao.insertProgramExercise(
                                exerciseDetail.programExercise.copy(
                                    id = 0L,
                                    programDayId = newDayId,
                                ),
                            )
                        }
                }
            newProgramId
        }
    }

    override suspend fun createDayFromSession(
        programId: Long,
        sessionId: Long,
        dayName: String,
    ): Long = withContext(ioDispatcher) {
        database.withTransaction {
            val session = workoutSessionDao.getSessionDetail(sessionId)
                ?: error("Session $sessionId was not found.")
            programDao.getProgram(programId) ?: error("Program $programId was not found.")
            val newDayId = programDao.insertDay(
                ProgramDay(
                    programId = programId,
                    name = dayName,
                    dayOrder = programDao.nextDayOrder(programId),
                    notes = null,
                ).toEntity(),
            )
            // A program day references library exercises; a session only keeps name snapshots. An
            // exercise deleted since the session was logged therefore cannot be carried over, and
            // is skipped rather than silently inventing a new library entry. A lift logged twice
            // becomes one entry, its targets drawn from both.
            session.toDomain().exercises
                .sortedBy { it.exercise.exerciseOrder }
                .filter { it.exercise.exerciseId != null }
                .groupBy { it.exercise.exerciseId!! }
                .entries
                .forEachIndexed { index, (exerciseId, logged) ->
                    val targets = DayFromSession.targets(
                        plan = logged.first().exercise,
                        sets = logged.flatMap { it.sets },
                        exerciseUnit = database.exerciseDao().getExercise(exerciseId)?.toDomain()?.defaultUnit,
                    )
                    programDao.insertProgramExercise(
                        ProgramExercise(
                            programDayId = newDayId,
                            exerciseId = exerciseId,
                            exerciseOrder = index,
                            plannedSets = targets.plannedSets,
                            targetRepMin = targets.targetRepMin,
                            targetRepMax = targets.targetRepMax,
                            targetWeight = targets.targetWeight,
                            targetDurationSeconds = targets.targetDurationSeconds,
                            targetRestSeconds = targets.targetRestSeconds,
                        ).toEntity(),
                    )
                }
            newDayId
        }
    }

    override suspend fun appendDay(programId: Long, name: String): Long =
        withContext(ioDispatcher) {
            database.withTransaction {
                programDao.insertDay(
                    ProgramDay(
                        programId = programId,
                        name = name,
                        dayOrder = programDao.nextDayOrder(programId),
                    ).toEntity(),
                )
            }
        }

    override suspend fun upsertDay(day: ProgramDay): Long = withContext(ioDispatcher) {
        programDao.upsertDay(day.toEntity()).orExistingId(day.id)
    }

    override suspend fun deleteDay(id: Long) = withContext(ioDispatcher) {
        programDao.deleteDay(id)
    }

    override suspend fun duplicateDay(dayId: Long): Long = withContext(ioDispatcher) {
        database.withTransaction {
            val detail = programDao.getDayDetail(dayId)
                ?: error("Program day $dayId was not found.")
            val newDayId = programDao.insertDay(
                detail.day.copy(
                    id = 0L,
                    name = LibraryCopyNames.dayCopy(detail.day.name),
                    dayOrder = programDao.nextDayOrder(detail.day.programId),
                ),
            )
            detail.exercises
                .sortedBy { it.programExercise.exerciseOrder }
                .forEach { exerciseDetail ->
                    programDao.insertProgramExercise(
                        exerciseDetail.programExercise.copy(
                            id = 0L,
                            programDayId = newDayId,
                        ),
                    )
                }
            newDayId
        }
    }

    override suspend fun reorderDays(orderedDayIds: List<Long>) = withContext(ioDispatcher) {
        database.withTransaction {
            orderedDayIds.forEachIndexed { index, id ->
                programDao.updateDayOrder(id, index)
            }
        }
    }

    override suspend fun appendProgramExercise(programDayId: Long, exerciseId: Long): Long =
        withContext(ioDispatcher) {
            database.withTransaction {
                programDao.insertProgramExercise(
                    ProgramExercise(
                        programDayId = programDayId,
                        exerciseId = exerciseId,
                        exerciseOrder = programDao.nextProgramExerciseOrder(programDayId),
                    ).toEntity(),
                )
            }
        }

    override suspend fun upsertProgramExercise(programExercise: ProgramExercise): Long =
        withContext(ioDispatcher) {
            programDao.upsertProgramExercise(programExercise.toEntity()).orExistingId(programExercise.id)
        }

    override suspend fun deleteProgramExercise(id: Long) = withContext(ioDispatcher) {
        programDao.deleteProgramExercise(id)
    }

    override suspend fun reorderProgramExercises(
        orderedProgramExerciseIds: List<Long>,
    ) = withContext(ioDispatcher) {
        database.withTransaction {
            orderedProgramExerciseIds.forEachIndexed { index, id ->
                programDao.updateProgramExerciseOrder(id, index)
            }
        }
    }
}

package dev.happyc0der.forgelog.data.repository

import androidx.room.withTransaction
import dev.happyc0der.forgelog.data.local.ForgeLogDatabase
import dev.happyc0der.forgelog.data.local.dao.ExerciseDao
import dev.happyc0der.forgelog.data.local.dao.ProgramDao
import dev.happyc0der.forgelog.data.local.dao.WorkoutSessionDao
import dev.happyc0der.forgelog.data.mapper.toDomain
import dev.happyc0der.forgelog.data.mapper.toEntity
import dev.happyc0der.forgelog.di.IoDispatcher
import dev.happyc0der.forgelog.domain.library.ExerciseDeletePolicy
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val database: ForgeLogDatabase,
    private val exerciseDao: ExerciseDao,
    private val programDao: ProgramDao,
    private val workoutSessionDao: WorkoutSessionDao,
    private val timeProvider: TimeProvider,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ExerciseRepository {

    override fun observeExercises(includeArchived: Boolean): Flow<List<Exercise>> =
        exerciseDao.observeExercises(includeArchived)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeExercisesByCategory(
        category: ExerciseCategory,
        includeArchived: Boolean,
    ): Flow<List<Exercise>> =
        exerciseDao.observeExercisesByCategory(category, includeArchived)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeExercise(id: Long): Flow<Exercise?> =
        exerciseDao.observeExercise(id)
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun getExercise(id: Long): Exercise? = withContext(ioDispatcher) {
        exerciseDao.getExercise(id)?.toDomain()
    }

    override suspend fun findByName(name: String): Exercise? = withContext(ioDispatcher) {
        exerciseDao.findByName(name.trim())?.toDomain()
    }

    override suspend fun upsert(exercise: Exercise): Long = withContext(ioDispatcher) {
        val now = timeProvider.nowEpochMs()
        val stamped = if (exercise.id == 0L) {
            exercise.copy(createdAt = now, updatedAt = now)
        } else {
            exercise.copy(updatedAt = now)
        }
        exerciseDao.upsert(stamped.toEntity())
    }

    override suspend fun setArchived(id: Long, archived: Boolean) = withContext(ioDispatcher) {
        exerciseDao.setArchived(id, archived, timeProvider.nowEpochMs())
    }

    override suspend fun hasSessionHistory(id: Long): Boolean = withContext(ioDispatcher) {
        workoutSessionDao.countSessionExercisesForExercise(id) > 0
    }

    override suspend fun deleteIfUnusedInSessions(id: Long) = withContext(ioDispatcher) {
        database.withTransaction {
            val hasHistory = workoutSessionDao.countSessionExercisesForExercise(id) > 0
            if (!ExerciseDeletePolicy.canHardDelete(hasHistory)) {
                error("Exercise $id has session history and cannot be deleted.")
            }
            programDao.deleteProgramExercisesByExerciseId(id)
            exerciseDao.deleteById(id)
        }
    }
}

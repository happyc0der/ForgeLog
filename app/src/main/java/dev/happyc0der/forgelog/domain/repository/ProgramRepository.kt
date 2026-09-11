package dev.happyc0der.forgelog.domain.repository

import dev.happyc0der.forgelog.domain.model.ProgramDay
import dev.happyc0der.forgelog.domain.model.ProgramDayDetail
import dev.happyc0der.forgelog.domain.model.ProgramDetail
import dev.happyc0der.forgelog.domain.model.ProgramExercise
import dev.happyc0der.forgelog.domain.model.ProgramSummary
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import kotlinx.coroutines.flow.Flow

interface ProgramRepository {
    fun observePrograms(includeArchived: Boolean = false): Flow<List<WorkoutProgram>>
    fun observeProgramSummaries(includeArchived: Boolean = false): Flow<List<ProgramSummary>>
    fun observeProgramDetail(id: Long): Flow<ProgramDetail?>
    fun observeDays(programId: Long): Flow<List<ProgramDay>>
    fun observeDayDetail(dayId: Long): Flow<ProgramDayDetail?>
    suspend fun getProgram(id: Long): WorkoutProgram?
    suspend fun getProgramDetail(id: Long): ProgramDetail?
    suspend fun getDayDetail(dayId: Long): ProgramDayDetail?
    suspend fun upsertProgram(program: WorkoutProgram): Long
    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun hasSessionHistory(programId: Long): Boolean
    suspend fun deleteProgram(id: Long)
    suspend fun duplicateProgram(programId: Long): Long
    suspend fun upsertDay(day: ProgramDay): Long
    suspend fun deleteDay(id: Long)
    suspend fun duplicateDay(dayId: Long): Long
    suspend fun reorderDays(orderedDayIds: List<Long>)

    /**
     * Adds a day named [name] to the end of [programId], choosing the next order inside a
     * transaction — for the same reason [appendProgramExercise] does.
     */
    suspend fun appendDay(programId: Long, name: String): Long
    /**
     * Adds [exerciseId] to the end of [programDayId], choosing the next order inside a transaction.
     *
     * Computing the order in the caller and then inserting is not atomic: two adds in quick
     * succession — picking from the library while an inline-created exercise is still being written —
     * both read the same count and persist the same exerciseOrder.
     */
    suspend fun appendProgramExercise(programDayId: Long, exerciseId: Long): Long

    suspend fun upsertProgramExercise(programExercise: ProgramExercise): Long
    suspend fun deleteProgramExercise(id: Long)
    suspend fun reorderProgramExercises(orderedProgramExerciseIds: List<Long>)

    /**
     * Creates a new day in [programId] from what was actually logged in [sessionId]: its exercises
     * in order, with targets worked out by [dev.happyc0der.forgelog.domain.workout.DayFromSession].
     *
     * Only exercises that still exist in the library can be carried over: a program day points at
     * library exercises, while a session keeps name snapshots, so an exercise deleted since the
     * session was logged has nothing to point at. Returns the new day id.
     */
    suspend fun createDayFromSession(programId: Long, sessionId: Long, dayName: String): Long
}

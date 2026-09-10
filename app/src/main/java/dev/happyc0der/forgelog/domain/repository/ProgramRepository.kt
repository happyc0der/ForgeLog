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
    fun observeProgram(id: Long): Flow<WorkoutProgram?>
    fun observeProgramDetail(id: Long): Flow<ProgramDetail?>
    fun observeDays(programId: Long): Flow<List<ProgramDay>>
    fun observeDayDetail(dayId: Long): Flow<ProgramDayDetail?>
    fun observeProgramExercises(dayId: Long): Flow<List<ProgramExercise>>
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
    suspend fun upsertProgramExercise(programExercise: ProgramExercise): Long
    suspend fun deleteProgramExercise(id: Long)
    suspend fun reorderProgramExercises(orderedProgramExerciseIds: List<Long>)
}

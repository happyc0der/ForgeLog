package dev.happyc0der.forgelog.data.local.relation

import androidx.room.ColumnInfo
import androidx.room.Embedded
import dev.happyc0der.forgelog.data.local.entity.WorkoutProgramEntity

data class ProgramSummaryEntity(
    @Embedded val program: WorkoutProgramEntity,
    @ColumnInfo(name = "dayCount") val dayCount: Int,
    /** Null until the program has been completed at least once. */
    @ColumnInfo(name = "lastPerformedAt") val lastPerformedAt: Long?,
    @ColumnInfo(name = "completedSessionCount") val completedSessionCount: Int,
)

/** An exercise that appears in logged history, used to populate the history filter. */
data class LoggedExerciseEntity(
    @ColumnInfo(name = "exerciseId") val exerciseId: Long,
    @ColumnInfo(name = "displayName") val displayName: String,
)

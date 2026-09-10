package dev.happyc0der.forgelog.data.local.relation

import androidx.room.ColumnInfo
import androidx.room.Embedded
import dev.happyc0der.forgelog.data.local.entity.WorkoutProgramEntity

data class ProgramSummaryEntity(
    @Embedded val program: WorkoutProgramEntity,
    @ColumnInfo(name = "dayCount") val dayCount: Int,
)

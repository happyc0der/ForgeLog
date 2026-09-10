package com.example.forgelog.data.local.relation

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.example.forgelog.data.local.entity.WorkoutProgramEntity

data class ProgramSummaryEntity(
    @Embedded val program: WorkoutProgramEntity,
    @ColumnInfo(name = "dayCount") val dayCount: Int,
)

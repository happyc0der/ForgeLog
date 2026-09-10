package com.example.forgelog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "program_exercises",
    foreignKeys = [
        ForeignKey(
            entity = ProgramDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["programDayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("programDayId"),
        Index("exerciseId"),
        Index(value = ["programDayId", "exerciseOrder"]),
    ],
)
data class ProgramExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val programDayId: Long,
    val exerciseId: Long,
    val exerciseOrder: Int,
    val plannedSets: Int?,
    val targetRepMin: Int?,
    val targetRepMax: Int?,
    val targetWeight: Double?,
    val targetDurationSeconds: Int?,
    val targetRestSeconds: Int?,
    val defaultPointersOverride: String?,
    val notes: String?,
)

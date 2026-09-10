package com.example.forgelog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_programs",
    indices = [
        Index("name"),
        Index("isArchived"),
    ],
)
data class WorkoutProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String?,
    val color: String,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

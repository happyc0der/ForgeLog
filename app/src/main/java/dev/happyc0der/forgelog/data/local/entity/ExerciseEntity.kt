package dev.happyc0der.forgelog.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit

@Entity(
    tableName = "exercises",
    indices = [
        Index("name"),
        Index("category"),
        Index("isArchived"),
    ],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val category: ExerciseCategory,
    val defaultUnit: ExerciseUnit,
    val howToUrl: String?,
    val defaultPointers: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

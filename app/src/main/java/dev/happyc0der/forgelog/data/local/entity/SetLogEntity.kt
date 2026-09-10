package dev.happyc0der.forgelog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetType

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionExerciseId"),
        Index(value = ["sessionExerciseId", "setNumber"]),
    ],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionExerciseId: Long,
    val setNumber: Int,
    val setType: SetType,
    val reps: Int?,
    val weight: Double?,
    val weightUnit: ExerciseUnit,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val restAfterSetSeconds: Int?,
    val rpe: Int?,
    val rir: Int?,
    val completed: Boolean,
    val notes: String?,
    val completedAt: Long?,
)

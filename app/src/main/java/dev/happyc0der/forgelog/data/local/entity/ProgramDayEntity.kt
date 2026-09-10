package dev.happyc0der.forgelog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "program_days",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("programId"),
        Index(value = ["programId", "dayOrder"]),
    ],
)
data class ProgramDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val programId: Long,
    val name: String,
    val dayOrder: Int,
    val notes: String?,
)

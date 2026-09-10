package dev.happyc0der.forgelog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.happyc0der.forgelog.domain.model.RestTimerType
import dev.happyc0der.forgelog.domain.model.SessionStatus

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ProgramDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["programDayId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("programId"),
        Index("programDayId"),
        Index("status"),
        Index("startedAt"),
        Index("completedAt"),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val programDayId: Long?,
    val programId: Long?,
    val sessionName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: SessionStatus,
    val overallFeeling: Int?,
    val overallNotes: String?,
    /** Legacy unused column. No longer written as of the global session timer change. */
    val restBetweenExercisesSeconds: Int,
    val expandedSessionExerciseId: Long?,
    /** Legacy unused column. Manual rest timer is gone; leftover storage only. */
    val restTimerType: RestTimerType,
    /** Legacy unused column. Manual rest timer is gone; leftover storage only. */
    val restTimerStartedAt: Long?,
    /** Legacy unused column. Manual rest timer is gone; leftover storage only. */
    val restTimerDurationSeconds: Int?,
    /** Legacy unused column. Manual rest timer is gone; leftover storage only. */
    val restTimerPausedRemainingSeconds: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

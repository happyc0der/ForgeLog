package dev.happyc0der.forgelog.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("exerciseId"),
        Index(value = ["sessionId", "exerciseOrder"]),
    ],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val exerciseId: Long?,
    val displayNameSnapshot: String,
    val exerciseOrder: Int,
    /** Set once at session create. Not a live per-exercise clock. */
    val startedAt: Long,
    /** Legacy unused column. Exercises are no longer explicitly marked done. */
    val completedAt: Long?,
    /** Legacy unused column. Between-exercise rest is now rest after the previous set. */
    val restBeforeExerciseSeconds: Int?,
    val howToUrlSnapshot: String?,
    val pointersSnapshot: String?,
    val exerciseNotes: String?,
    val feeling: Int?,
    /*
     * Targets copied from the program exercise when the session started.
     *
     * They are snapshots for the same reason the name and pointers are: editing a program later
     * must not rewrite what a past session was aiming for. Null means the plan did not specify
     * this target, which is different from a target of zero.
     */
    val plannedSets: Int? = null,
    val targetRepMin: Int? = null,
    val targetRepMax: Int? = null,
    val targetWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetRestSeconds: Int? = null,
)

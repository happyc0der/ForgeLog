package com.example.forgelog.domain.model

data class WorkoutSession(
    val id: Long = 0L,
    val programDayId: Long? = null,
    val programId: Long? = null,
    val sessionName: String,
    /** Live global session clock. Elapsed time is now − this value. */
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    val overallFeeling: Int? = null,
    val overallNotes: String? = null,
    /** Legacy unused column. No longer written; rest is derived from [SetLog.completedAt]. */
    val restBetweenExercisesSeconds: Int = 0,
    val expandedSessionExerciseId: Long? = null,
    /** Legacy unused column. Manual rest timer is gone; leftover storage only. */
    val restTimerType: RestTimerType = RestTimerType.NONE,
    /** Legacy unused column. Manual rest timer is gone; leftover storage only. */
    val restTimerStartedAt: Long? = null,
    /** Legacy unused column. Manual rest timer is gone; leftover storage only. */
    val restTimerDurationSeconds: Int? = null,
    /** Legacy unused column. Manual rest timer is gone; leftover storage only. */
    val restTimerPausedRemainingSeconds: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SessionExercise(
    val id: Long = 0L,
    val sessionId: Long,
    val exerciseId: Long? = null,
    val displayNameSnapshot: String,
    val exerciseOrder: Int,
    /** Set once at session create. Not a live per-exercise clock. */
    val startedAt: Long,
    /** Legacy unused column. Exercises are no longer explicitly marked done. */
    val completedAt: Long? = null,
    /** Legacy unused column. Between-exercise rest is now rest after the previous set. */
    val restBeforeExerciseSeconds: Int? = null,
    val howToUrlSnapshot: String? = null,
    val pointersSnapshot: String? = null,
    val exerciseNotes: String? = null,
    val feeling: Int? = null,
)

data class SetLog(
    val id: Long = 0L,
    val sessionExerciseId: Long,
    val setNumber: Int,
    val setType: SetType = SetType.WORKING,
    val reps: Int? = null,
    val weight: Double? = null,
    val weightUnit: ExerciseUnit,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val restAfterSetSeconds: Int? = null,
    val rpe: Int? = null,
    val rir: Int? = null,
    val completed: Boolean = false,
    val notes: String? = null,
    val completedAt: Long? = null,
)

package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExercise
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.model.WorkoutSession

internal fun setLog(
    id: Long = 1L,
    sessionExerciseId: Long = 10L,
    setNumber: Int = 1,
    setType: SetType = SetType.WORKING,
    reps: Int? = 5,
    weight: Double? = 100.0,
    weightUnit: ExerciseUnit = ExerciseUnit.LB,
    durationSeconds: Int? = null,
    distanceMeters: Double? = null,
    restAfterSetSeconds: Int? = null,
    rpe: Int? = null,
    rir: Int? = null,
    notes: String? = null,
    completed: Boolean = true,
    completedAt: Long? = 1_000L,
): SetLog = SetLog(
    id = id,
    sessionExerciseId = sessionExerciseId,
    setNumber = setNumber,
    setType = setType,
    reps = reps,
    weight = weight,
    weightUnit = weightUnit,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    restAfterSetSeconds = restAfterSetSeconds,
    rpe = rpe,
    rir = rir,
    notes = notes,
    completed = completed,
    completedAt = completedAt,
)

internal fun sessionExercise(
    id: Long,
    sessionId: Long,
    exerciseId: Long?,
    displayName: String,
    exerciseOrder: Int = 0,
    startedAt: Long = 1_000L,
): SessionExercise = SessionExercise(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    displayNameSnapshot = displayName,
    exerciseOrder = exerciseOrder,
    startedAt = startedAt,
)

internal fun workoutSession(
    id: Long,
    programId: Long? = 1L,
    programDayId: Long? = 10L,
    status: SessionStatus = SessionStatus.COMPLETED,
    startedAt: Long = 1_000L,
    completedAt: Long? = 2_000L,
): WorkoutSession = WorkoutSession(
    id = id,
    programDayId = programDayId,
    programId = programId,
    sessionName = "Session $id",
    startedAt = startedAt,
    completedAt = completedAt,
    status = status,
    createdAt = startedAt,
    updatedAt = completedAt ?: startedAt,
)

internal fun sessionDetail(
    session: WorkoutSession,
    vararg exercises: SessionExerciseWithSets,
): SessionDetail = SessionDetail(
    session = session,
    exercises = exercises.toList(),
)

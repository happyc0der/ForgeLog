package com.example.forgelog.data.mapper

import com.example.forgelog.data.local.entity.ExerciseEntity
import com.example.forgelog.data.local.entity.ProgramDayEntity
import com.example.forgelog.data.local.entity.ProgramExerciseEntity
import com.example.forgelog.data.local.entity.SessionExerciseEntity
import com.example.forgelog.data.local.entity.SetLogEntity
import com.example.forgelog.data.local.entity.WorkoutProgramEntity
import com.example.forgelog.data.local.entity.WorkoutSessionEntity
import com.example.forgelog.data.local.relation.ProgramDayDetailEntity
import com.example.forgelog.data.local.relation.ProgramDetailEntity
import com.example.forgelog.data.local.relation.ProgramExerciseDetailEntity
import com.example.forgelog.data.local.relation.ProgramSummaryEntity
import com.example.forgelog.data.local.relation.SessionDetailEntity
import com.example.forgelog.data.local.relation.SessionExerciseWithSetsEntity
import com.example.forgelog.domain.model.Exercise
import com.example.forgelog.domain.model.ProgramDay
import com.example.forgelog.domain.model.ProgramDayDetail
import com.example.forgelog.domain.model.ProgramDetail
import com.example.forgelog.domain.model.ProgramExercise
import com.example.forgelog.domain.model.ProgramExerciseDetail
import com.example.forgelog.domain.model.ProgramSummary
import com.example.forgelog.domain.model.SessionDetail
import com.example.forgelog.domain.model.SessionExercise
import com.example.forgelog.domain.model.SessionExerciseWithSets
import com.example.forgelog.domain.model.SetLog
import com.example.forgelog.domain.model.WorkoutProgram
import com.example.forgelog.domain.model.WorkoutSession

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    category = category,
    defaultUnit = defaultUnit,
    howToUrl = howToUrl,
    defaultPointers = defaultPointers,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    category = category,
    defaultUnit = defaultUnit,
    howToUrl = howToUrl,
    defaultPointers = defaultPointers,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun WorkoutProgramEntity.toDomain(): WorkoutProgram = WorkoutProgram(
    id = id,
    name = name,
    description = description,
    color = color,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun WorkoutProgram.toEntity(): WorkoutProgramEntity = WorkoutProgramEntity(
    id = id,
    name = name,
    description = description,
    color = color,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProgramDayEntity.toDomain(): ProgramDay = ProgramDay(
    id = id,
    programId = programId,
    name = name,
    dayOrder = dayOrder,
    notes = notes,
)

fun ProgramDay.toEntity(): ProgramDayEntity = ProgramDayEntity(
    id = id,
    programId = programId,
    name = name,
    dayOrder = dayOrder,
    notes = notes,
)

fun ProgramExerciseEntity.toDomain(): ProgramExercise = ProgramExercise(
    id = id,
    programDayId = programDayId,
    exerciseId = exerciseId,
    exerciseOrder = exerciseOrder,
    plannedSets = plannedSets,
    targetRepMin = targetRepMin,
    targetRepMax = targetRepMax,
    targetWeight = targetWeight,
    targetDurationSeconds = targetDurationSeconds,
    targetRestSeconds = targetRestSeconds,
    defaultPointersOverride = defaultPointersOverride,
    notes = notes,
)

fun ProgramExercise.toEntity(): ProgramExerciseEntity = ProgramExerciseEntity(
    id = id,
    programDayId = programDayId,
    exerciseId = exerciseId,
    exerciseOrder = exerciseOrder,
    plannedSets = plannedSets,
    targetRepMin = targetRepMin,
    targetRepMax = targetRepMax,
    targetWeight = targetWeight,
    targetDurationSeconds = targetDurationSeconds,
    targetRestSeconds = targetRestSeconds,
    defaultPointersOverride = defaultPointersOverride,
    notes = notes,
)

fun WorkoutSessionEntity.toDomain(): WorkoutSession = WorkoutSession(
    id = id,
    programDayId = programDayId,
    programId = programId,
    sessionName = sessionName,
    startedAt = startedAt,
    completedAt = completedAt,
    status = status,
    overallFeeling = overallFeeling,
    overallNotes = overallNotes,
    restBetweenExercisesSeconds = restBetweenExercisesSeconds,
    expandedSessionExerciseId = expandedSessionExerciseId,
    restTimerType = restTimerType,
    restTimerStartedAt = restTimerStartedAt,
    restTimerDurationSeconds = restTimerDurationSeconds,
    restTimerPausedRemainingSeconds = restTimerPausedRemainingSeconds,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun WorkoutSession.toEntity(): WorkoutSessionEntity = WorkoutSessionEntity(
    id = id,
    programDayId = programDayId,
    programId = programId,
    sessionName = sessionName,
    startedAt = startedAt,
    completedAt = completedAt,
    status = status,
    overallFeeling = overallFeeling,
    overallNotes = overallNotes,
    restBetweenExercisesSeconds = restBetweenExercisesSeconds,
    expandedSessionExerciseId = expandedSessionExerciseId,
    restTimerType = restTimerType,
    restTimerStartedAt = restTimerStartedAt,
    restTimerDurationSeconds = restTimerDurationSeconds,
    restTimerPausedRemainingSeconds = restTimerPausedRemainingSeconds,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SessionExerciseEntity.toDomain(): SessionExercise = SessionExercise(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    displayNameSnapshot = displayNameSnapshot,
    exerciseOrder = exerciseOrder,
    startedAt = startedAt,
    completedAt = completedAt,
    restBeforeExerciseSeconds = restBeforeExerciseSeconds,
    howToUrlSnapshot = howToUrlSnapshot,
    pointersSnapshot = pointersSnapshot,
    exerciseNotes = exerciseNotes,
    feeling = feeling,
)

fun SessionExercise.toEntity(): SessionExerciseEntity = SessionExerciseEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    displayNameSnapshot = displayNameSnapshot,
    exerciseOrder = exerciseOrder,
    startedAt = startedAt,
    completedAt = completedAt,
    restBeforeExerciseSeconds = restBeforeExerciseSeconds,
    howToUrlSnapshot = howToUrlSnapshot,
    pointersSnapshot = pointersSnapshot,
    exerciseNotes = exerciseNotes,
    feeling = feeling,
)

fun SetLogEntity.toDomain(): SetLog = SetLog(
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
    completed = completed,
    notes = notes,
    completedAt = completedAt,
)

fun SetLog.toEntity(): SetLogEntity = SetLogEntity(
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
    completed = completed,
    notes = notes,
    completedAt = completedAt,
)

fun ProgramSummaryEntity.toDomain(): ProgramSummary = ProgramSummary(
    program = program.toDomain(),
    dayCount = dayCount,
)

fun ProgramExerciseDetailEntity.toDomain(): ProgramExerciseDetail = ProgramExerciseDetail(
    programExercise = programExercise.toDomain(),
    exercise = exercise.toDomain(),
)

fun ProgramDayDetailEntity.toDomain(): ProgramDayDetail = ProgramDayDetail(
    day = day.toDomain(),
    exercises = exercises
        .map { it.toDomain() }
        .sortedBy { it.programExercise.exerciseOrder },
)

fun ProgramDetailEntity.toDomain(): ProgramDetail = ProgramDetail(
    program = program.toDomain(),
    days = days
        .map { it.toDomain() }
        .sortedBy { it.day.dayOrder },
)

fun SessionExerciseWithSetsEntity.toDomain(): SessionExerciseWithSets = SessionExerciseWithSets(
    exercise = exercise.toDomain(),
    sets = sets.map { it.toDomain() }.sortedBy { it.setNumber },
)

fun SessionDetailEntity.toDomain(): SessionDetail = SessionDetail(
    session = session.toDomain(),
    exercises = exercises
        .map { it.toDomain() }
        .sortedBy { it.exercise.exerciseOrder },
)

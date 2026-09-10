package com.example.forgelog.domain.model

data class ProgramExerciseDetail(
    val programExercise: ProgramExercise,
    val exercise: Exercise,
)

data class ProgramDayDetail(
    val day: ProgramDay,
    val exercises: List<ProgramExerciseDetail>,
)

data class ProgramDetail(
    val program: WorkoutProgram,
    val days: List<ProgramDayDetail>,
)

data class SessionExerciseWithSets(
    val exercise: SessionExercise,
    val sets: List<SetLog>,
)

data class SessionDetail(
    val session: WorkoutSession,
    val exercises: List<SessionExerciseWithSets>,
)

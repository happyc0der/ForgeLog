package com.example.forgelog.domain.model

const val DEFAULT_PROGRAM_COLOR = "#A855F7"

data class WorkoutProgram(
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val color: String,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ProgramDay(
    val id: Long = 0L,
    val programId: Long,
    val name: String,
    val dayOrder: Int,
    val notes: String? = null,
)

data class ProgramExercise(
    val id: Long = 0L,
    val programDayId: Long,
    val exerciseId: Long,
    val exerciseOrder: Int,
    val plannedSets: Int? = null,
    val targetRepMin: Int? = null,
    val targetRepMax: Int? = null,
    val targetWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetRestSeconds: Int? = null,
    val defaultPointersOverride: String? = null,
    val notes: String? = null,
)

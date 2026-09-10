package dev.happyc0der.forgelog.domain.model

data class Exercise(
    val id: Long = 0L,
    val name: String,
    val category: ExerciseCategory,
    val defaultUnit: ExerciseUnit,
    val howToUrl: String? = null,
    val defaultPointers: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

fun Exercise.toSessionExercise(
    sessionId: Long,
    exerciseOrder: Int,
    startedAt: Long,
): SessionExercise = SessionExercise(
    sessionId = sessionId,
    exerciseId = id,
    displayNameSnapshot = name,
    exerciseOrder = exerciseOrder,
    startedAt = startedAt,
    howToUrlSnapshot = howToUrl,
    pointersSnapshot = defaultPointers,
)

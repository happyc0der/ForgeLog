package dev.happyc0der.forgelog.domain.model

data class SessionStartExercise(
    val exercise: Exercise,
    val pointersOverride: String? = null,
    val targetRestSeconds: Int? = null,
)

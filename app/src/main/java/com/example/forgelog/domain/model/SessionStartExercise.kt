package com.example.forgelog.domain.model

data class SessionStartExercise(
    val exercise: Exercise,
    val pointersOverride: String? = null,
    val targetRestSeconds: Int? = null,
)

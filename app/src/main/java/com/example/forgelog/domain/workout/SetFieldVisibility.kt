package com.example.forgelog.domain.workout

import com.example.forgelog.domain.model.ExerciseUnit

enum class SetInputField {
    REPS,
    WEIGHT,
    DURATION,
    DISTANCE,
}

object SetFieldVisibility {
    fun defaults(unit: ExerciseUnit): Set<SetInputField> = when (unit) {
        ExerciseUnit.LB,
        ExerciseUnit.KG,
        -> setOf(SetInputField.REPS, SetInputField.WEIGHT)
        ExerciseUnit.BODYWEIGHT -> setOf(SetInputField.REPS)
        ExerciseUnit.SECONDS -> setOf(SetInputField.DURATION)
        ExerciseUnit.METERS -> setOf(SetInputField.DISTANCE)
    }

    fun isVisible(
        field: SetInputField,
        unit: ExerciseUnit,
        revealed: Set<SetInputField>,
    ): Boolean = field in defaults(unit) || field in revealed
}

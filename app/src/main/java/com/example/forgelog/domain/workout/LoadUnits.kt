package com.example.forgelog.domain.workout

import com.example.forgelog.domain.model.ExerciseUnit

const val KG_TO_LB = 2.2046226218

fun Double.toPounds(unit: ExerciseUnit): Double? = when (unit) {
    ExerciseUnit.LB -> this
    ExerciseUnit.KG -> this * KG_TO_LB
    ExerciseUnit.BODYWEIGHT,
    ExerciseUnit.SECONDS,
    ExerciseUnit.METERS,
    -> null
}

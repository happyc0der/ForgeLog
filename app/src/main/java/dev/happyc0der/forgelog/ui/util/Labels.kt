package dev.happyc0der.forgelog.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetType

@Composable
fun ExerciseCategory.label(): String = stringResource(
    when (this) {
        ExerciseCategory.PUSH -> R.string.category_push
        ExerciseCategory.PULL -> R.string.category_pull
        ExerciseCategory.LEGS -> R.string.category_legs
        ExerciseCategory.CORE -> R.string.category_core
        ExerciseCategory.CONDITIONING -> R.string.category_conditioning
        ExerciseCategory.MOBILITY -> R.string.category_mobility
        ExerciseCategory.OTHER -> R.string.category_other
    },
)

@Composable
fun ExerciseUnit.label(): String = stringResource(
    when (this) {
        ExerciseUnit.LB -> R.string.unit_lb
        ExerciseUnit.KG -> R.string.unit_kg
        ExerciseUnit.BODYWEIGHT -> R.string.unit_bodyweight
        ExerciseUnit.SECONDS -> R.string.unit_seconds
        ExerciseUnit.METERS -> R.string.unit_meters
    },
)

@Composable
fun SetType.label(): String = stringResource(
    when (this) {
        SetType.WARMUP -> R.string.set_type_warmup
        SetType.WORKING -> R.string.set_type_working
        SetType.DROP -> R.string.set_type_drop
        SetType.FAILURE -> R.string.set_type_failure
        SetType.CUSTOM -> R.string.set_type_custom
    },
)

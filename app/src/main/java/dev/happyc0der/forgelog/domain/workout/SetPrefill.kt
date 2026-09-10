package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType

object SetPrefill {
    fun nextSet(
        sessionExerciseId: Long,
        existing: List<SetLog>,
        defaultUnit: ExerciseUnit,
        historical: SessionExerciseWithSets?,
    ): SetLog {
        val template = existing.maxByOrNull { it.setNumber }
            ?: historical?.sets?.maxByOrNull { it.setNumber }
        val setNumber = (existing.maxOfOrNull { it.setNumber } ?: 0) + 1
        return SetLog(
            sessionExerciseId = sessionExerciseId,
            setNumber = setNumber,
            setType = template?.setType ?: SetType.WORKING,
            reps = template?.reps,
            weight = template?.weight,
            weightUnit = template?.weightUnit ?: defaultUnit,
            durationSeconds = template?.durationSeconds,
            distanceMeters = template?.distanceMeters,
            restAfterSetSeconds = template?.restAfterSetSeconds,
            rpe = null,
            rir = null,
            completed = false,
            notes = null,
            completedAt = null,
        )
    }
}

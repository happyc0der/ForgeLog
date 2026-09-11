package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseTargets
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog

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

    /**
     * The fields the plan or the previous session gave a value, which are shown whatever the unit.
     *
     * A timed exercise shows only its duration, so a planned weight -- a 60 lb farmer's walk, a
     * 25 lb back-extension hold -- was filled into every set and hidden: it could be neither seen
     * nor changed without "More fields" on every visit. This is worked out from the plan and the
     * last session, which stay put while logging, not from the sets being edited: a field that
     * disappeared as soon as its value was cleared could not be typed into.
     */
    fun inUse(targets: ExerciseTargets, previousSets: List<SetLog>): Set<SetInputField> = buildSet {
        if (targets.targetRepMin != null || targets.targetRepMax != null || previousSets.any { it.reps != null }) {
            add(SetInputField.REPS)
        }
        if (targets.targetWeight != null || previousSets.any { it.weight != null }) {
            add(SetInputField.WEIGHT)
        }
        if (targets.targetDurationSeconds != null || previousSets.any { it.durationSeconds != null }) {
            add(SetInputField.DURATION)
        }
        if (previousSets.any { it.distanceMeters != null }) add(SetInputField.DISTANCE)
    }

    fun isVisible(
        field: SetInputField,
        unit: ExerciseUnit,
        revealed: Set<SetInputField>,
        inUse: Set<SetInputField> = emptySet(),
    ): Boolean = field in defaults(unit) || field in revealed || field in inUse
}

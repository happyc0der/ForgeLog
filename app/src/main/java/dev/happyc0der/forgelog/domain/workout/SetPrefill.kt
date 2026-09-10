package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType

/** The plan for an exercise, as far as prefilling a new set is concerned. */
data class SetTargets(
    val targetRepMin: Int? = null,
    val targetRepMax: Int? = null,
    val targetWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetRestSeconds: Int? = null,
) {
    val isEmpty: Boolean
        get() = targetRepMin == null && targetRepMax == null && targetWeight == null &&
            targetDurationSeconds == null && targetRestSeconds == null

    /** The bottom of a rep range is the honest prefill: it is the number being committed to. */
    val prefillReps: Int? get() = targetRepMin ?: targetRepMax
}

object SetPrefill {
    /**
     * Values for the next set, in priority order: the set just logged, then the same exercise in the
     * previous session, then the plan.
     *
     * The plan comes last deliberately. What the lifter actually did beats what was written down —
     * but on the very first set of a brand-new exercise there is nothing else, and an empty row is
     * worse than the target they set themselves.
     */
    fun nextSet(
        sessionExerciseId: Long,
        existing: List<SetLog>,
        defaultUnit: ExerciseUnit,
        historical: SessionExerciseWithSets?,
        targets: SetTargets = SetTargets(),
    ): SetLog {
        val template = existing.maxByOrNull { it.setNumber }
            ?: historical?.sets?.filter { it.completed }?.maxByOrNull { it.setNumber }
            ?: historical?.sets?.maxByOrNull { it.setNumber }
        val setNumber = (existing.maxOfOrNull { it.setNumber } ?: 0) + 1
        return SetLog(
            sessionExerciseId = sessionExerciseId,
            setNumber = setNumber,
            setType = template?.setType ?: SetType.WORKING,
            reps = template?.reps ?: targets.prefillReps,
            weight = template?.weight ?: targets.targetWeight,
            weightUnit = template?.weightUnit ?: defaultUnit,
            durationSeconds = template?.durationSeconds ?: targets.targetDurationSeconds,
            distanceMeters = template?.distanceMeters,
            restAfterSetSeconds = template?.restAfterSetSeconds ?: targets.targetRestSeconds,
            rpe = null,
            rir = null,
            completed = false,
            notes = null,
            completedAt = null,
        )
    }
}

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

/**
 * This unit if it measures weight, otherwise [fallback] (itself only if it does), otherwise lb.
 *
 * A set's weight is in pounds or kilograms. Timed and bodyweight exercises used to hand their own
 * unit down as the weight's, so a 25 lb back-extension hold was stored as "25 Seconds": displayed as
 * lb, and skipped by every calculation that converts a weight.
 */
fun ExerciseUnit.asWeightUnit(fallback: ExerciseUnit): ExerciseUnit = when {
    isLoadedWeight -> this
    fallback.isLoadedWeight -> fallback
    else -> ExerciseUnit.LB
}

object SetPrefill {
    /**
     * Values for the next set, in priority order: the set just logged in this session, then the
     * plan for today, then the same exercise in the previous session.
     *
     * The plan beats history. A target is a deliberate statement about *this* session — a deload, a
     * jump, a new rep range — and having last week's numbers override it means retyping the weight
     * on every set. History still fills anything the plan is silent about, and still fills
     * everything when there is no plan at all.
     *
     * What is already logged in this session beats both: mid-session the lifter has usually just
     * corrected the weight, and that correction is the most current statement of intent.
     */
    /**
     * [defaultUnit] is the unit a planned weight is in: the exercise's own for a loaded lift, and
     * the user's default weight unit for anything else.
     */
    fun nextSet(
        sessionExerciseId: Long,
        existing: List<SetLog>,
        defaultUnit: ExerciseUnit,
        historical: SessionExerciseWithSets?,
        targets: SetTargets = SetTargets(),
    ): SetLog {
        val current = existing.maxByOrNull { it.setNumber }
        val previous = historical?.sets?.filter { it.completed }?.maxByOrNull { it.setNumber }
            ?: historical?.sets?.maxByOrNull { it.setNumber }
        val setNumber = (existing.maxOfOrNull { it.setNumber } ?: 0) + 1

        val weight = current?.weight ?: targets.targetWeight ?: previous?.weight
        return SetLog(
            sessionExerciseId = sessionExerciseId,
            setNumber = setNumber,
            setType = current?.setType ?: previous?.setType ?: SetType.WORKING,
            reps = current?.reps ?: targets.prefillReps ?: previous?.reps,
            weight = weight,
            // The unit follows whichever source supplied the weight. A plan records a bare number
            // against the exercise's own unit, so taking history's unit for a planned weight would
            // turn 225 lb into 225 kg.
            weightUnit = when {
                current?.weight != null -> current.weightUnit
                targets.targetWeight != null -> defaultUnit
                previous?.weight != null -> previous.weightUnit
                else -> current?.weightUnit ?: previous?.weightUnit ?: defaultUnit
            }.asWeightUnit(fallback = defaultUnit),
            durationSeconds = current?.durationSeconds
                ?: targets.targetDurationSeconds
                ?: previous?.durationSeconds,
            distanceMeters = current?.distanceMeters ?: previous?.distanceMeters,
            restAfterSetSeconds = current?.restAfterSetSeconds
                ?: targets.targetRestSeconds
                ?: previous?.restAfterSetSeconds,
            rpe = null,
            rir = null,
            completed = false,
            notes = null,
            completedAt = null,
        )
    }
}

package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseTargets
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import kotlin.math.roundToLong

/** A program exercise's targets, as [DayFromSession] works them out. */
data class DayTargets(
    override val plannedSets: Int? = null,
    override val targetRepMin: Int? = null,
    override val targetRepMax: Int? = null,
    override val targetWeight: Double? = null,
    override val targetDurationSeconds: Int? = null,
    override val targetRestSeconds: Int? = null,
) : ExerciseTargets

/**
 * What a program day should ask for, taken from a workout that was actually done.
 *
 * "Save as program day" used to copy only which exercises were done, so the new day asked for
 * nothing at all -- no sets, reps, weight or rest -- and every target had to be typed in again. The
 * workout's own log wins wherever it says something, since keeping what was done is the point of
 * saving it; its plan fills in what the log cannot say, like rest.
 */
object DayFromSession {

    /**
     * [plan] is the workout's snapshot of its targets; [sets] every set logged for the exercise;
     * [exerciseUnit] the library exercise's own unit, which a program's weight is read in.
     */
    fun targets(plan: ExerciseTargets, sets: List<SetLog>, exerciseUnit: ExerciseUnit?): DayTargets {
        val working = sets.filter { it.completed && it.setType != SetType.WARMUP }
        val reps = working.mapNotNull { set -> set.reps?.takeIf { it > 0 } }
        val targetUnit = exerciseUnit?.takeIf { it.isLoadedWeight }
        val weights = working.mapNotNull { set ->
            set.weight?.takeIf { it > 0.0 }?.let { convert(it, from = set.weightUnit, to = targetUnit) }
        }
        val durations = working.mapNotNull { set -> set.durationSeconds?.takeIf { it > 0 } }
        return DayTargets(
            plannedSets = working.size.takeIf { it > 0 } ?: plan.plannedSets,
            targetRepMin = reps.minOrNull() ?: plan.targetRepMin,
            targetRepMax = reps.maxOrNull() ?: plan.targetRepMax,
            // The heaviest working set: the weight the day is now built around.
            targetWeight = weights.maxOrNull() ?: plan.targetWeight,
            targetDurationSeconds = durations.maxOrNull() ?: plan.targetDurationSeconds,
            // Measured rests are noisy -- a phone call, a busy rack -- so the plan's rest is kept.
            targetRestSeconds = plan.targetRestSeconds,
        )
    }

    /** Into the exercise's own unit, to the nearest half: 100 kg is 220.5 lb, not 220.462. */
    private fun convert(weight: Double, from: ExerciseUnit, to: ExerciseUnit?): Double = when {
        to == null || !from.isLoadedWeight || from == to -> weight
        to == ExerciseUnit.LB -> roundToHalf(weight * KG_TO_LB)
        else -> roundToHalf(weight / KG_TO_LB)
    }

    private fun roundToHalf(value: Double): Double = (value * 2).roundToLong() / 2.0
}

package dev.happyc0der.forgelog.domain.workout

import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseTargets
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A lift that has earned more weight: [setCount] sets reached [reps] at [fromWeight] last time, so
 * [options] are the weights to go up to, lightest first. All weights are in [unit], lb or kg.
 */
data class ProgressionHint(
    val setCount: Int,
    val reps: Int,
    val fromWeight: Double,
    val options: List<Double>,
    val unit: ExerciseUnit,
)

/**
 * The progression rule the program is run by: when every prescribed set reached the top of its rep
 * range, add 5 lb to an upper-body lift and 5-10 lb to a lower-body one (2.5 and 2.5-5 kg).
 *
 * Only a suggestion -- clean form is not something a log can show -- and only for lifts it can
 * judge: a loaded lift with a rep target and a previous session. Timed and bodyweight work has no
 * weight to add.
 */
object Progression {

    /**
     * [targets] is today's plan; [lastSets] the sets logged for the exercise last time; [unit] the
     * weight unit the plan's weight is in. Null when there is nothing to suggest, including when
     * today's target already asks for at least the lighter suggestion.
     */
    fun hint(
        targets: ExerciseTargets,
        lastSets: List<SetLog>,
        category: ExerciseCategory?,
        unit: ExerciseUnit,
    ): ProgressionHint? {
        if (!unit.isLoadedWeight) return null
        val top = targets.targetRepMax ?: targets.targetRepMin ?: return null
        val loaded = lastSets.filter { set ->
            set.completed && set.setType != SetType.WARMUP &&
                (set.weight ?: 0.0) > 0.0 && set.weightUnit.isLoadedWeight
        }
        if (loaded.isEmpty()) return null
        // The sets done at the heaviest weight are the ones that count: lighter ramp-up sets not
        // marked as warm-ups neither earn nor block a jump.
        val weighed = loaded.map { set -> set to inUnit(set.weight ?: 0.0, from = set.weightUnit, to = unit) }
        val heaviest = weighed.maxOf { it.second }
        val working = weighed.filter { abs(it.second - heaviest) < WEIGHT_TOLERANCE }.map { it.first }
        val required = targets.plannedSets?.takeIf { it > 0 } ?: working.size
        if (working.size < required) return null
        if (working.count { (it.reps ?: 0) >= top } < required) return null

        val options = increments(category, unit).map { roundToHalf(heaviest + it) }
        // Already there: the plan, or an earlier tap on a suggestion, asks for the jump or more.
        val planned = targets.targetWeight
        if (planned != null && planned >= options.first() - WEIGHT_TOLERANCE) return null
        return ProgressionHint(
            setCount = required,
            reps = top,
            fromWeight = heaviest,
            options = options,
            unit = unit,
        )
    }

    private fun increments(category: ExerciseCategory?, unit: ExerciseUnit): List<Double> {
        val lower = category == ExerciseCategory.LEGS
        return when (unit) {
            ExerciseUnit.KG -> if (lower) listOf(2.5, 5.0) else listOf(2.5)
            else -> if (lower) listOf(5.0, 10.0) else listOf(5.0)
        }
    }

    private fun inUnit(weight: Double, from: ExerciseUnit, to: ExerciseUnit): Double = when {
        from == to -> weight
        to == ExerciseUnit.LB -> roundToHalf(weight * KG_TO_LB)
        else -> roundToHalf(weight / KG_TO_LB)
    }

    private fun roundToHalf(value: Double): Double = (value * 2).roundToLong() / 2.0

    private const val WEIGHT_TOLERANCE = 0.01
}

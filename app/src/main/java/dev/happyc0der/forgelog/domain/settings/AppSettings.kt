package dev.happyc0der.forgelog.domain.settings

import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import java.time.DayOfWeek

/**
 * Every user preference in the app, read as one snapshot.
 *
 * The defaults here are the app's behaviour on a fresh install, so they are the single place to
 * look when asking "what does ForgeLog do before anyone touches Settings".
 */
data class AppSettings(
    /** Unit pre-filled for new loaded sets. Only [ExerciseUnit.LB] or [ExerciseUnit.KG]. */
    val defaultWeightUnit: ExerciseUnit = ExerciseUnit.LB,
    /** Fallback rest suggestion when a program exercise has no target rest. */
    val defaultRestSeconds: Int = DEFAULT_REST_SECONDS,
    /** Whether duration fields are typed in seconds or minutes. */
    val durationInputUnit: DurationInputUnit = DurationInputUnit.SECONDS,
    /** First day of the training week, used by history grouping and analytics. */
    val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    /** Whether warmup sets count towards reported volume. Off matches most lifters' mental model. */
    val includeWarmupInVolume: Boolean = false,
    val restTimerVibration: Boolean = true,
    val restTimerSound: Boolean = false,
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 90
        const val MIN_REST_SECONDS = 0
        const val MAX_REST_SECONDS = 3600
    }
}

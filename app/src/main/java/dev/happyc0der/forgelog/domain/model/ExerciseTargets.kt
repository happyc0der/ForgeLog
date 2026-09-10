package dev.happyc0der.forgelog.domain.model

/**
 * The six things a plan can say about one exercise. Null means "not planned", never zero.
 *
 * Carried by the program template ([ProgramExercise]), by the planner's in-memory roster, and by
 * the session snapshot ([SessionExercise]). They are one interface rather than three identical
 * field lists because the predicate below was previously written out twice and the two copies
 * disagreed — the session's copy omitted rest, so a rest-only plan rendered as no plan at all.
 */
interface ExerciseTargets {
    val plannedSets: Int?
    val targetRepMin: Int?
    val targetRepMax: Int?
    val targetWeight: Double?
    val targetDurationSeconds: Int?
    val targetRestSeconds: Int?
}

/** True when the plan said anything at all about this exercise. */
val ExerciseTargets.hasTargets: Boolean
    get() = plannedSets != null ||
        targetRepMin != null ||
        targetRepMax != null ||
        targetWeight != null ||
        targetDurationSeconds != null ||
        targetRestSeconds != null

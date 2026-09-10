package dev.happyc0der.forgelog.domain.model

/**
 * One exercise as the planner hands it to the repository when a session starts.
 *
 * Targets travel with it so they can be snapshotted onto the session. The planner is free to adjust
 * them for today only — nothing here writes back to the program.
 */
data class SessionStartExercise(
    val exercise: Exercise,
    val pointersOverride: String? = null,
    val plannedSets: Int? = null,
    val targetRepMin: Int? = null,
    val targetRepMax: Int? = null,
    val targetWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetRestSeconds: Int? = null,
)

package dev.happyc0der.forgelog.domain.model

data class ProgramSummary(
    val program: WorkoutProgram,
    val dayCount: Int,
    /** Most recent completion, or null if this program has never been finished. */
    val lastPerformedAt: Long?,
    val completedSessionCount: Int,
)

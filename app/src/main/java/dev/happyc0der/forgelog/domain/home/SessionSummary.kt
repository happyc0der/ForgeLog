package dev.happyc0der.forgelog.domain.home

import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.workout.VolumeCalculator
import dev.happyc0der.forgelog.domain.workout.WorkoutVolume

/**
 * What one finished session amounted to.
 *
 * [durationMs] is null for a session with no completion time — an abandoned session, or one still
 * running. Null means "not known", and the UI must render it as such rather than as zero.
 */
data class SessionSummary(
    val sessionId: Long,
    val sessionName: String,
    /**
     * When the workout began: the time a session is dated by everywhere it is shown. History and
     * the week buckets already used it, while the summary and Home dated a session by its finish,
     * so one workout read "09:04" in History and "09:07" on its own summary.
     */
    val startedAt: Long,
    val completedAt: Long?,
    val durationMs: Long?,
    val volume: WorkoutVolume,
    val exerciseCount: Int,
    val overallFeeling: Int?,
) {
    val totalSets: Int get() = volume.completedSetCount
    val loadLb: Double get() = volume.loadLb
}

/**
 * Totals for a window of sessions, used by the weekly card on Home.
 *
 * Only completed sessions count. An in-progress session has no meaningful duration yet, and an
 * abandoned one is explicitly not training the user finished — counting either would quietly
 * inflate the week.
 */
data class TrainingTotals(
    val sessionCount: Int,
    val totalDurationMs: Long,
    val totalSets: Int,
    val loadLb: Double,
    val timedSeconds: Int,
) {
    companion object {
        val EMPTY = TrainingTotals(
            sessionCount = 0,
            totalDurationMs = 0L,
            totalSets = 0,
            loadLb = 0.0,
            timedSeconds = 0,
        )
    }
}

object TrainingSummaries {

    /**
     * Volume is computed from the session's own set logs through [VolumeCalculator], the same code
     * path the rest of the app uses, so Home can never disagree with a session's own detail screen
     * about what a workout was worth.
     */
    fun summarize(detail: SessionDetail, includeWarmup: Boolean = false): SessionSummary {
        val session = detail.session
        return SessionSummary(
            sessionId = session.id,
            sessionName = session.sessionName,
            startedAt = session.startedAt,
            completedAt = session.completedAt,
            durationMs = session.completedAt?.let { end -> (end - session.startedAt).takeIf { it >= 0 } },
            volume = VolumeCalculator.sessionVolume(
                sessionExercises = detail.exercises.map { it.sets },
                includeWarmup = includeWarmup,
            ),
            exerciseCount = detail.exercises.count { it.sets.any { set -> set.completed } },
            overallFeeling = session.overallFeeling,
        )
    }

    fun totals(details: List<SessionDetail>, includeWarmup: Boolean = false): TrainingTotals {
        val completed = details.filter { it.session.status == SessionStatus.COMPLETED }
        if (completed.isEmpty()) return TrainingTotals.EMPTY
        val summaries = completed.map { summarize(it, includeWarmup) }
        return TrainingTotals(
            sessionCount = summaries.size,
            totalDurationMs = summaries.sumOf { it.durationMs ?: 0L },
            totalSets = summaries.sumOf { it.totalSets },
            loadLb = summaries.sumOf { it.loadLb },
            timedSeconds = summaries.sumOf { it.volume.totalDurationSeconds },
        )
    }
}

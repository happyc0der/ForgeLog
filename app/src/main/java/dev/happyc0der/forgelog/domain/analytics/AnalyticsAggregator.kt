package dev.happyc0der.forgelog.domain.analytics

import dev.happyc0der.forgelog.domain.home.TrainingSummaries
import dev.happyc0der.forgelog.domain.home.TrainingTotals
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.time.WeekBoundary
import dev.happyc0der.forgelog.domain.workout.EstimatedOneRepMax
import dev.happyc0der.forgelog.domain.workout.VolumeCalculator
import dev.happyc0der.forgelog.domain.workout.toPounds

/**
 * Metrics for one window of training.
 *
 * Averages are null rather than zero when there is nothing to average. "No sessions, so no average
 * duration" is a different statement from "your average session was zero minutes", and the spec is
 * explicit that missing data is marked as missing.
 */
data class PeriodMetrics(
    val sessionCount: Int,
    val totalDurationMs: Long,
    val totalSets: Int,
    val loadLb: Double,
    val timedSeconds: Int,
    val averageSessionDurationMs: Long?,
    val averageRpe: Double?,
) {
    companion object {
        val EMPTY = PeriodMetrics(
            sessionCount = 0,
            totalDurationMs = 0L,
            totalSets = 0,
            loadLb = 0.0,
            timedSeconds = 0,
            averageSessionDurationMs = null,
            averageRpe = null,
        )
    }
}

/** One bar in the per-day chart. */
data class DayVolume(
    val range: WeekBoundary.Range,
    val loadLb: Double,
    val setCount: Int,
)

/** A point on the estimated-1RM trend. */
data class TrendPoint(
    val epochMs: Long,
    val value: Double,
)

/** Two windows side by side, which is what the Analytics screen is for. */
data class PeriodComparison(
    val current: PeriodMetrics,
    val previous: PeriodMetrics,
) {
    /** Null when there is nothing to compare against, so the UI can say so instead of showing 0%. */
    fun changeRatio(selector: (PeriodMetrics) -> Double): Double? {
        val before = selector(previous)
        val now = selector(current)
        if (before <= 0.0) return null
        return (now - before) / before
    }
}

object AnalyticsAggregator {

    fun metrics(details: List<SessionDetail>, includeWarmup: Boolean = false): PeriodMetrics {
        val completed = details.filter { it.session.status == SessionStatus.COMPLETED }
        if (completed.isEmpty()) return PeriodMetrics.EMPTY

        val totals: TrainingTotals = TrainingSummaries.totals(completed, includeWarmup)
        val durations = completed.mapNotNull { detail ->
            detail.session.completedAt?.let { end ->
                (end - detail.session.startedAt).takeIf { it >= 0 }
            }
        }
        // RPE is averaged over the sets that actually recorded one. Treating a blank RPE as zero
        // would drag the average down every time the user did not bother to log it.
        val rpes = completed
            .flatMap { detail -> detail.exercises.flatMap { it.sets } }
            .filter { it.completed && (includeWarmup || it.setType != SetType.WARMUP) }
            .mapNotNull { it.rpe }

        return PeriodMetrics(
            sessionCount = totals.sessionCount,
            totalDurationMs = totals.totalDurationMs,
            totalSets = totals.totalSets,
            loadLb = totals.loadLb,
            timedSeconds = totals.timedSeconds,
            averageSessionDurationMs = durations.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            averageRpe = rpes.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    fun compare(
        current: List<SessionDetail>,
        previous: List<SessionDetail>,
        includeWarmup: Boolean = false,
    ): PeriodComparison = PeriodComparison(
        current = metrics(current, includeWarmup),
        previous = metrics(previous, includeWarmup),
    )

    /**
     * Volume per day across [days].
     *
     * Days with no training are kept, with zero volume, because a training week is partly about which
     * days were empty — dropping them would redraw the chart as if the week were shorter.
     */
    fun volumeByDay(
        details: List<SessionDetail>,
        days: List<WeekBoundary.Range>,
        includeWarmup: Boolean = false,
    ): List<DayVolume> = days.map { range ->
        // The day it started on, as History and the rest of Analytics count it.
        val inDay = details.filter { detail ->
            detail.session.status == SessionStatus.COMPLETED && detail.session.startedAt in range
        }
        val volume = VolumeCalculator.sessionVolume(
            sessionExercises = inDay.flatMap { detail -> detail.exercises.map { it.sets } },
            includeWarmup = includeWarmup,
        )
        DayVolume(range = range, loadLb = volume.loadLb, setCount = volume.completedSetCount)
    }

    /**
     * Completed sets per category.
     *
     * An exercise deleted from the library has no category any more, so its sets land in
     * [ExerciseCategory.OTHER] rather than being dropped — the sets still happened.
     */
    fun setsByCategory(
        details: List<SessionDetail>,
        categoryByExerciseId: Map<Long, ExerciseCategory>,
        includeWarmup: Boolean = false,
    ): Map<ExerciseCategory, Int> {
        val counts = mutableMapOf<ExerciseCategory, Int>()
        details
            .filter { it.session.status == SessionStatus.COMPLETED }
            .forEach { detail ->
                detail.exercises.forEach { logged ->
                    val category = logged.exercise.exerciseId
                        ?.let(categoryByExerciseId::get)
                        ?: ExerciseCategory.OTHER
                    val sets = logged.sets.count { set ->
                        set.completed && (includeWarmup || set.setType != SetType.WARMUP)
                    }
                    if (sets > 0) counts[category] = (counts[category] ?: 0) + sets
                }
            }
        return counts
    }

    /**
     * Best estimated 1RM per session for one exercise, oldest first.
     *
     * Sets that cannot support the estimate — bodyweight, duration-only, above ten reps — are
     * excluded by [EstimatedOneRepMax] rather than approximated, so the trend has no invented points.
     */
    fun estimatedOneRepMaxTrend(
        details: List<SessionDetail>,
        exerciseId: Long,
    ): List<TrendPoint> = details
        .filter { it.session.status == SessionStatus.COMPLETED }
        .mapNotNull { detail ->
            val best = detail.exercises
                .filter { it.exercise.exerciseId == exerciseId }
                .flatMap { it.sets }
                .mapNotNull { EstimatedOneRepMax.pounds(it) }
                .maxOrNull()
                ?: return@mapNotNull null
            TrendPoint(
                epochMs = detail.session.startedAt,
                value = best,
            )
        }
        .sortedBy { it.epochMs }

    /** Heaviest completed working set per session for one exercise, oldest first. */
    fun topSetTrend(details: List<SessionDetail>, exerciseId: Long): List<TrendPoint> = details
        .filter { it.session.status == SessionStatus.COMPLETED }
        .mapNotNull { detail ->
            val best = detail.exercises
                .filter { it.exercise.exerciseId == exerciseId }
                .flatMap { it.sets }
                .filter { it.completed && it.setType != SetType.WARMUP }
                .mapNotNull { set -> set.weight?.takeIf { it > 0.0 }?.toPounds(set.weightUnit) }
                .maxOrNull()
                ?: return@mapNotNull null
            TrendPoint(
                epochMs = detail.session.startedAt,
                value = best,
            )
        }
        .sortedBy { it.epochMs }
}

package dev.happyc0der.forgelog.ui.analytics

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.analytics.AnalyticsAggregator
import dev.happyc0der.forgelog.domain.analytics.DayVolume
import dev.happyc0der.forgelog.domain.analytics.ExerciseRecords
import dev.happyc0der.forgelog.domain.analytics.PeriodComparison
import dev.happyc0der.forgelog.domain.analytics.PersonalRecords
import dev.happyc0der.forgelog.domain.analytics.TrendPoint
import dev.happyc0der.forgelog.domain.history.LoggedExercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.time.WeekBoundary
import dev.happyc0der.forgelog.domain.time.ZoneProvider
import dev.happyc0der.forgelog.ui.common.reportErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import javax.inject.Inject

/** How far back the trend charts and records reach. */
enum class TrendWindow(val weeks: Int) {
    TWELVE_WEEKS(12),
    SIX_MONTHS(26),
    ONE_YEAR(52),
}

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    /** True while an arbitrary range is in force, so the UI stops claiming "this week". */
    val isCustomRange: Boolean = false,
    val errorMessage: String? = null,
    /** How many weeks back the "previous" window sits. 1 is last week. */
    val comparisonOffset: Int = 1,
    val currentRange: WeekBoundary.Range? = null,
    val previousRange: WeekBoundary.Range? = null,
    val comparison: PeriodComparison? = null,
    val volumeByDay: List<DayVolume> = emptyList(),
    val setsByCategory: Map<ExerciseCategory, Int> = emptyMap(),
    val trendWindow: TrendWindow = TrendWindow.TWELVE_WEEKS,
    val exercises: List<LoggedExercise> = emptyList(),
    val selectedExerciseId: Long? = null,
    val oneRepMaxTrend: List<TrendPoint> = emptyList(),
    val topSetTrend: List<TrendPoint> = emptyList(),
    val records: List<ExerciseRecords> = emptyList(),
    val selectedExerciseRecords: ExerciseRecords? = null,
    val weightUnit: ExerciseUnit = ExerciseUnit.LB,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    val hasAnyData: Boolean
        get() = (comparison?.current?.sessionCount ?: 0) > 0 ||
            (comparison?.previous?.sessionCount ?: 0) > 0 ||
            records.isNotEmpty()
}

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val application: Application,
    private val workoutSessionRepository: WorkoutSessionRepository,
    exerciseRepository: ExerciseRepository,
    settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val zoneProvider: ZoneProvider,
) : ViewModel() {

    private val comparisonOffset = MutableStateFlow(1)

    /**
     * An arbitrary span, when the week-over-week presets do not cover what the user wants.
     *
     * Its comparison window is the equally long span immediately before it, which is the only
     * baseline that makes sense for a range the app did not choose.
     */
    private val customRange = MutableStateFlow<WeekBoundary.Range?>(null)
    private val trendWindow = MutableStateFlow(TrendWindow.TWELVE_WEEKS)
    private val selectedExerciseId = MutableStateFlow<Long?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val retryToken = MutableStateFlow(0)

    /**
     * Ranges are recomputed only when the week boundary or the chosen offset changes, not on every
     * emission, so the Room queries below are not re-subscribed needlessly.
     */
    private val ranges: Flow<Pair<WeekBoundary.Range, WeekBoundary.Range>> = combine(
        comparisonOffset,
        settingsRepository.settings.map { it.weekStartDay }.distinctUntilChanged(),
        customRange,
        retryToken,
    ) { offset, weekStart, custom, _ ->
        if (custom != null) {
            val length = custom.endExclusive - custom.start
            custom to WeekBoundary.Range(
                start = custom.start - length,
                endExclusive = custom.start,
            )
        } else {
            val now = timeProvider.nowEpochMs()
            val zone = zoneProvider.zone()
            WeekBoundary.weekRange(now, zone, weekStart) to
                WeekBoundary.weekRangeOffset(now, zone, offset, weekStart)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val windowSessions: Flow<Triple<List<SessionDetail>, List<SessionDetail>, Pair<WeekBoundary.Range, WeekBoundary.Range>>> =
        ranges.flatMapLatest { (current, previous) ->
            combine(
                workoutSessionRepository.observeCompletedSessionDetailsBetween(
                    current.start,
                    current.endExclusive,
                ),
                workoutSessionRepository.observeCompletedSessionDetailsBetween(
                    previous.start,
                    previous.endExclusive,
                ),
            ) { currentSessions, previousSessions ->
                Triple(currentSessions, previousSessions, current to previous)
            }
        }

    /**
     * History for the trend charts and records.
     *
     * A bounded window rather than everything ever logged: the charts only draw the window, and
     * reading years of set logs to render twelve weeks would get slower every month the app is used.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val trendSessions: Flow<List<SessionDetail>> = combine(
        trendWindow,
        settingsRepository.settings.map { it.weekStartDay }.distinctUntilChanged(),
        retryToken,
    ) { window, weekStart, _ ->
        val now = timeProvider.nowEpochMs()
        val zone = zoneProvider.zone()
        WeekBoundary.Range(
            start = WeekBoundary.weekRangeOffset(now, zone, window.weeks - 1, weekStart).start,
            endExclusive = WeekBoundary.dayRange(now, zone).endExclusive,
        )
    }
        .distinctUntilChanged()
        .flatMapLatest { range ->
            workoutSessionRepository.observeCompletedSessionDetailsBetween(
                range.start,
                range.endExclusive,
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val data: Flow<AnalyticsData> = combine(
        windowSessions,
        trendSessions,
        exerciseRepository.observeExercises(includeArchived = true),
        settingsRepository.settings,
        selectedExerciseId,
    ) { windows, trend, exercises, settings, selected ->
        val (currentSessions, previousSessions, rangePair) = windows
        val zone = zoneProvider.zone()
        val categories = exercises.associate { it.id to it.category }
        val recordsByKey = PersonalRecords.byExercise(trend)
        // An exercise the user trained in the window but has since deleted still appears, by name.
        val loggedExercises = trend
            .flatMap { detail -> detail.exercises }
            .mapNotNull { logged ->
                logged.exercise.exerciseId?.let {
                    LoggedExercise(it, logged.exercise.displayNameSnapshot)
                }
            }
            .distinctBy { it.exerciseId }
            .sortedBy { it.displayName.lowercase() }
        val effectiveSelection = selected ?: loggedExercises.firstOrNull()?.exerciseId

        AnalyticsData(
            comparison = AnalyticsAggregator.compare(
                current = currentSessions,
                previous = previousSessions,
                includeWarmup = settings.includeWarmupInVolume,
            ),
            ranges = rangePair,
            volumeByDay = AnalyticsAggregator.volumeByDay(
                details = currentSessions,
                days = WeekBoundary.daysOfWeek(
                    rangePair.first.start,
                    zone,
                    settings.weekStartDay,
                ),
                includeWarmup = settings.includeWarmupInVolume,
            ),
            setsByCategory = AnalyticsAggregator.setsByCategory(
                details = currentSessions,
                categoryByExerciseId = categories,
                includeWarmup = settings.includeWarmupInVolume,
            ),
            exercises = loggedExercises,
            selectedExerciseId = effectiveSelection,
            oneRepMaxTrend = effectiveSelection
                ?.let { AnalyticsAggregator.estimatedOneRepMaxTrend(trend, it) }
                .orEmpty(),
            topSetTrend = effectiveSelection
                ?.let { AnalyticsAggregator.topSetTrend(trend, it) }
                .orEmpty(),
            records = recordsByKey.values.sortedBy { it.exerciseName.lowercase() },
            selectedExerciseRecords = effectiveSelection?.let { recordsByKey[it.toString()] },
            weightUnit = settings.defaultWeightUnit,
            weekStart = settings.weekStartDay,
        )
    }.reportErrors(AnalyticsData.EMPTY) { reportError(it) }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        data,
        comparisonOffset,
        trendWindow,
        errorMessage,
        customRange,
    ) { analytics, offset, window, error, custom ->
        AnalyticsUiState(
            isLoading = false,
            isCustomRange = custom != null,
            errorMessage = error,
            comparisonOffset = offset,
            currentRange = analytics.ranges?.first,
            previousRange = analytics.ranges?.second,
            comparison = analytics.comparison,
            volumeByDay = analytics.volumeByDay,
            setsByCategory = analytics.setsByCategory,
            trendWindow = window,
            exercises = analytics.exercises,
            selectedExerciseId = analytics.selectedExerciseId,
            oneRepMaxTrend = analytics.oneRepMaxTrend,
            topSetTrend = analytics.topSetTrend,
            records = analytics.records,
            selectedExerciseRecords = analytics.selectedExerciseRecords,
            weightUnit = analytics.weightUnit,
            weekStart = analytics.weekStart,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalyticsUiState(),
    )

    /** 1 compares with last week, 2 with the week before that, and so on. */
    fun setComparisonOffset(weeksAgo: Int) {
        // Choosing a preset leaves the custom range behind, or the chips would appear to do
        // nothing.
        customRange.value = null
        comparisonOffset.value = weeksAgo.coerceIn(1, MAX_COMPARISON_OFFSET)
    }

    /** An arbitrary span, compared against the equally long span immediately before it. */
    fun setCustomRange(fromEpochMs: Long, untilEpochMs: Long) {
        if (untilEpochMs <= fromEpochMs) return
        customRange.value = WeekBoundary.Range(start = fromEpochMs, endExclusive = untilEpochMs)
    }

    fun clearCustomRange() {
        customRange.value = null
    }

    fun setTrendWindow(window: TrendWindow) {
        trendWindow.value = window
    }

    fun selectExercise(exerciseId: Long?) {
        selectedExerciseId.value = exerciseId
    }

    fun retry() {
        errorMessage.value = null
        retryToken.update { it + 1 }
    }

    private fun reportError(throwable: Throwable) {
        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
    }

    private companion object {
        const val MAX_COMPARISON_OFFSET = 52
    }
}

private data class AnalyticsData(
    val comparison: PeriodComparison?,
    val ranges: Pair<WeekBoundary.Range, WeekBoundary.Range>?,
    val volumeByDay: List<DayVolume>,
    val setsByCategory: Map<ExerciseCategory, Int>,
    val exercises: List<LoggedExercise>,
    val selectedExerciseId: Long?,
    val oneRepMaxTrend: List<TrendPoint>,
    val topSetTrend: List<TrendPoint>,
    val records: List<ExerciseRecords>,
    val selectedExerciseRecords: ExerciseRecords?,
    val weightUnit: ExerciseUnit,
    val weekStart: DayOfWeek,
) {
    companion object {
        val EMPTY = AnalyticsData(
            comparison = null,
            ranges = null,
            volumeByDay = emptyList(),
            setsByCategory = emptyMap(),
            exercises = emptyList(),
            selectedExerciseId = null,
            oneRepMaxTrend = emptyList(),
            topSetTrend = emptyList(),
            records = emptyList(),
            selectedExerciseRecords = null,
            weightUnit = ExerciseUnit.LB,
            weekStart = DayOfWeek.MONDAY,
        )
    }
}

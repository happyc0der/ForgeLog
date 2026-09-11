package dev.happyc0der.forgelog.ui.history

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.history.HistoryFilter
import dev.happyc0der.forgelog.domain.history.LoggedExercise
import dev.happyc0der.forgelog.domain.home.SessionSummary
import dev.happyc0der.forgelog.domain.home.TrainingSummaries
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.ProgramDay
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.time.WeekBoundary
import dev.happyc0der.forgelog.domain.time.ZoneProvider
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.common.reportErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One session in the list, already summarised so the row does no work while scrolling. */
data class HistoryRow(
    val summary: SessionSummary,
    val status: SessionStatus,
    val startedAt: Long,
    val day: LocalDate,
)

/**
 * Quick date presets, plus [CUSTOM] for an arbitrary span chosen from the calendar.
 *
 * [CUSTOM] is never selectable directly — picking it opens the range picker, and it becomes the
 * active preset only once [HistoryViewModel.setDateRange] has been given real dates.
 */
enum class DateRangePreset {
    ALL_TIME,
    THIS_WEEK,
    LAST_WEEK,
    LAST_30_DAYS,
    CUSTOM,
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val filter: HistoryFilter = HistoryFilter(),
    val preset: DateRangePreset = DateRangePreset.ALL_TIME,
    val rows: List<HistoryRow> = emptyList(),
    val programs: List<WorkoutProgram> = emptyList(),
    val days: List<ProgramDay> = emptyList(),
    val loggedExercises: List<LoggedExercise> = emptyList(),
    val weightUnit: ExerciseUnit = ExerciseUnit.LB,
    val today: LocalDate? = null,
) {
    val isFilterActive: Boolean get() = filter.isActive
}

sealed interface HistoryEvent {
    data class Message(val value: String) : HistoryEvent
    data class RepeatStarted(val sessionId: Long) : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val application: Application,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val programRepository: ProgramRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val zoneProvider: ZoneProvider,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter())
    private val preset = MutableStateFlow(DateRangePreset.ALL_TIME)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val retryToken = MutableStateFlow(0)
    private var presetJob: Job? = null

    private val eventsChannel = Channel<HistoryEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows: Flow<List<HistoryRow>> = combine(
        filter,
        settingsRepository.settings.map { it.includeWarmupInVolume }.distinctUntilChanged(),
        retryToken,
    ) { activeFilter, includeWarmup, _ -> activeFilter to includeWarmup }
        .flatMapLatest { (activeFilter, includeWarmup) ->
            workoutSessionRepository.observeSessionHistory(activeFilter)
                .map { details ->
                    val zone = zoneProvider.zone()
                    details.map { detail ->
                        HistoryRow(
                            summary = TrainingSummaries.summarize(detail, includeWarmup),
                            status = detail.session.status,
                            startedAt = detail.session.startedAt,
                            // atZone, not LocalDate.ofInstant: that needs API 34, and the app
                            // runs from 26.
                            day = java.time.Instant.ofEpochMilli(detail.session.startedAt)
                                .atZone(zone)
                                .toLocalDate(),
                        )
                    }
                }
                .reportErrors(emptyList()) { reportError(it) }
        }

    /** Days are only meaningful once a program is chosen, so the query follows the selection. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val days: Flow<List<ProgramDay>> = filter
        .map { it.programId }
        .distinctUntilChanged()
        .flatMapLatest { programId ->
            if (programId == null) flowOf(emptyList()) else programRepository.observeDays(programId)
        }

    /**
     * Every source is gathered before the error catch, not after.
     *
     * Catching only on the rows flow was not enough: a failure in the programs or logged-exercises
     * flow killed the whole combine, so the screen emitted nothing at all and the error state could
     * never render. One catch around all of them is what makes the error branch reachable.
     */
    private val data: Flow<HistoryData> = combine(
        combine(filter, preset, rows, days) { activeFilter, activePreset, historyRows, programDays ->
            HistoryPartial(activeFilter, activePreset, historyRows, programDays)
        },
        programRepository.observePrograms(includeArchived = true),
        workoutSessionRepository.observeLoggedExercises(),
        settingsRepository.settings,
    ) { partial, programs, logged, settings ->
        HistoryData(
            partial = partial,
            programs = programs,
            loggedExercises = logged,
            weightUnit = settings.defaultWeightUnit,
        )
    }.reportErrors(HistoryData.EMPTY) { reportError(it) }

    val uiState: StateFlow<HistoryUiState> = combine(
        data,
        errorMessage,
    ) { historyData, error ->
        HistoryUiState(
            isLoading = false,
            errorMessage = error,
            filter = historyData.partial.filter,
            preset = historyData.partial.preset,
            rows = historyData.partial.rows,
            programs = historyData.programs,
            days = historyData.partial.days,
            loggedExercises = historyData.loggedExercises,
            weightUnit = historyData.weightUnit,
            today = java.time.Instant.ofEpochMilli(timeProvider.nowEpochMs())
                .atZone(zoneProvider.zone())
                .toLocalDate(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun onQueryChange(value: String) = filter.update { it.copy(query = value) }

    fun onStatusSelected(status: SessionStatus?) = filter.update { it.copy(status = status) }

    /** Clearing the program also clears the day, which would otherwise filter to nothing. */
    fun onProgramSelected(programId: Long?) = filter.update {
        it.copy(programId = programId, programDayId = null)
    }

    fun onDaySelected(programDayId: Long?) = filter.update { it.copy(programDayId = programDayId) }

    fun onExerciseSelected(exerciseId: Long?) = filter.update { it.copy(exerciseId = exerciseId) }

    fun onPresetSelected(value: DateRangePreset) {
        // The screen opens the picker instead; the preset only becomes CUSTOM once setDateRange
        // has been given real dates, so the chip cannot show as selected while the filter is
        // still whatever it was.
        if (value == DateRangePreset.CUSTOM) return
        // The latest tap wins: the setting is read asynchronously, so "This week" then "All time"
        // in quick succession could otherwise finish the other way round.
        presetJob?.cancel()
        presetJob = launchSafely(::reportAsMessage) {
            val now = timeProvider.nowEpochMs()
            val zone = zoneProvider.zone()
            // The week starts where Settings says. It was always Monday here while Home and
            // Analytics followed the setting, so with a Sunday start "This week" in History was a
            // different week from theirs.
            val weekStart = settingsRepository.settings.first().weekStartDay
            val range = when (value) {
                DateRangePreset.ALL_TIME -> null
                DateRangePreset.THIS_WEEK -> WeekBoundary.weekRange(now, zone, weekStart)
                DateRangePreset.LAST_WEEK -> WeekBoundary.weekRangeOffset(now, zone, weeksAgo = 1, weekStart = weekStart)
                DateRangePreset.LAST_30_DAYS -> WeekBoundary.Range(
                    start = WeekBoundary.startOfDay(now, zone) - THIRTY_DAYS_MS,
                    endExclusive = WeekBoundary.dayRange(now, zone).endExclusive,
                )
                DateRangePreset.CUSTOM -> return@launchSafely
            }
            preset.value = value
            filter.update { it.copy(fromEpochMs = range?.start, untilEpochMs = range?.endExclusive) }
        }
    }

    /** Explicit range, for when the presets do not cover what the user wants. */
    fun setDateRange(fromEpochMs: Long?, untilEpochMs: Long?) {
        presetJob?.cancel()
        preset.value = if (fromEpochMs == null && untilEpochMs == null) {
            DateRangePreset.ALL_TIME
        } else {
            DateRangePreset.CUSTOM
        }
        filter.update { it.copy(fromEpochMs = fromEpochMs, untilEpochMs = untilEpochMs) }
    }

    fun clearFilters() {
        presetJob?.cancel()
        preset.value = DateRangePreset.ALL_TIME
        filter.value = HistoryFilter()
    }

    fun retry() {
        errorMessage.value = null
        retryToken.update { it + 1 }
    }

    fun deleteSession(sessionId: Long) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.deleteSession(sessionId)
            eventsChannel.send(
                HistoryEvent.Message(application.getString(R.string.history_session_deleted)),
            )
        }
    }

    fun repeatSession(sessionId: Long) {
        launchSafely(::reportAsMessage) {
            val existing = workoutSessionRepository.getInProgressSession()
            if (existing != null) {
                eventsChannel.send(
                    HistoryEvent.Message(
                        application.getString(R.string.workout_session_in_progress),
                    ),
                )
                return@launchSafely
            }
            val newId = workoutSessionRepository.repeatSession(sessionId)
            if (newId == null) {
                eventsChannel.send(
                    HistoryEvent.Message(application.getString(R.string.history_session_missing)),
                )
            } else {
                eventsChannel.send(HistoryEvent.RepeatStarted(newId))
            }
        }
    }

    fun saveAsProgramDay(sessionId: Long, programId: Long, dayName: String) {
        launchSafely(::reportAsMessage) {
            programRepository.createDayFromSession(programId, sessionId, dayName)
            eventsChannel.send(
                HistoryEvent.Message(application.getString(R.string.history_saved_as_day)),
            )
        }
    }

    private fun reportError(throwable: Throwable) {
        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
    }

    private fun reportAsMessage(throwable: Throwable) {
        viewModelScope.launch {
            eventsChannel.send(
                HistoryEvent.Message(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.state_error_generic),
                ),
            )
        }
    }

    private companion object {
        const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
    }
}

private data class HistoryPartial(
    val filter: HistoryFilter,
    val preset: DateRangePreset,
    val rows: List<HistoryRow>,
    val days: List<ProgramDay>,
) {
    companion object {
        val EMPTY = HistoryPartial(HistoryFilter(), DateRangePreset.ALL_TIME, emptyList(), emptyList())
    }
}

private data class HistoryData(
    val partial: HistoryPartial,
    val programs: List<WorkoutProgram>,
    val loggedExercises: List<LoggedExercise>,
    val weightUnit: ExerciseUnit,
) {
    companion object {
        val EMPTY = HistoryData(HistoryPartial.EMPTY, emptyList(), emptyList(), ExerciseUnit.LB)
    }
}

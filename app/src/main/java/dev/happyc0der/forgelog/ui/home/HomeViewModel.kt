package dev.happyc0der.forgelog.ui.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.home.DayPart
import dev.happyc0der.forgelog.domain.home.Greeting
import dev.happyc0der.forgelog.domain.home.SessionSummary
import dev.happyc0der.forgelog.domain.home.TrainingSummaries
import dev.happyc0der.forgelog.domain.home.TrainingTotals
import dev.happyc0der.forgelog.data.local.DatabaseRecoveryLog
import dev.happyc0der.forgelog.data.local.UnreadableDatabase
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.settings.AppSettings
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.time.WeekBoundary
import dev.happyc0der.forgelog.domain.time.ZoneProvider
import dev.happyc0der.forgelog.domain.workout.formatElapsed
import dev.happyc0der.forgelog.ui.common.ONE_MINUTE_MS
import dev.happyc0der.forgelog.ui.common.reportErrors
import dev.happyc0der.forgelog.ui.common.ticker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val hasPrograms: Boolean = false,
    /** Null only before the first clock emission. */
    val today: LocalDate? = null,
    val dayPart: DayPart = DayPart.MORNING,
    val inProgress: WorkoutSession? = null,
    val elapsedLabel: String = "0:00",
    val lastWorkout: SessionSummary? = null,
    val week: TrainingTotals = TrainingTotals.EMPTY,
    /** Unit the user reads volume in. Volume is always computed in pounds and converted for display. */
    val weightUnit: ExerciseUnit = ExerciseUnit.LB,
    /**
     * Set when the database could not be read on a previous start and the app began again empty.
     * Shown until dismissed, because it is the only sign the user gets that anything was lost.
     */
    val unreadableDatabase: UnreadableDatabase? = null,
)

/** Everything Home reads from storage, gathered so the clock can be combined separately. */
private data class HomeData(
    val hasPrograms: Boolean,
    val inProgress: WorkoutSession?,
    val lastWorkout: SessionSummary?,
    val week: TrainingTotals,
    val settings: AppSettings,
) {
    companion object {
        val EMPTY = HomeData(
            hasPrograms = false,
            inProgress = null,
            lastWorkout = null,
            week = TrainingTotals.EMPTY,
            settings = AppSettings(),
        )
    }
}

private data class HomeClock(
    val today: LocalDate,
    val dayPart: DayPart,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val programRepository: ProgramRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val zoneProvider: ZoneProvider,
    private val databaseRecoveryLog: DatabaseRecoveryLog,
) : ViewModel() {

    private val errorMessage = MutableStateFlow<String?>(null)

    /**
     * Read once at construction: a database that failed to open did so before this screen existed,
     * and cannot start failing while it is on show.
     */
    private val unreadableDatabase = MutableStateFlow(databaseRecoveryLog.unreported())

    /** Bumped by [retry] to re-subscribe after a failure, since `catch` ends the source flow. */
    private val retryToken = MutableStateFlow(0)

    /**
     * A minute is fine for the date and the greeting: both change on an hour or day boundary, and
     * polling every second to notice would be sixty wasted wake-ups per minute.
     */
    private val minuteClock: Flow<Long> = ticker(timeProvider, ONE_MINUTE_MS)

    /**
     * The week window only has to be recomputed when the calendar week or the user's week-start
     * preference actually changes, so [distinctUntilChanged] keeps the minute clock from
     * re-subscribing the Room query sixty times an hour.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val weekTotals: Flow<TrainingTotals> = combine(
        minuteClock,
        settingsRepository.settings,
    ) { now, settings ->
        WeekBoundary.weekRange(now, zoneProvider.zone(), settings.weekStartDay) to
            settings.includeWarmupInVolume
    }
        .distinctUntilChanged()
        .flatMapLatest { (range, includeWarmup) ->
            workoutSessionRepository
                .observeCompletedSessionDetailsBetween(range.start, range.endExclusive)
                .map { details -> TrainingSummaries.totals(details, includeWarmup) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val data: Flow<HomeData> = retryToken.flatMapLatest {
        combine(
            programRepository.observePrograms(includeArchived = false),
            workoutSessionRepository.observeInProgressSession(),
            workoutSessionRepository.observeLastCompletedSessionDetail(),
            settingsRepository.settings,
            weekTotals,
        ) { programs, inProgress, lastDetail, settings, week ->
            HomeData(
                hasPrograms = programs.isNotEmpty(),
                inProgress = inProgress,
                lastWorkout = lastDetail?.let {
                    TrainingSummaries.summarize(it, settings.includeWarmupInVolume)
                },
                week = week,
                settings = settings,
            )
        }.reportErrors(HomeData.EMPTY) { reportError(it) }
    }

    /**
     * The one-second clock runs only while a session is live, and restarts only when the session
     * itself changes — an idle Home does no per-second work at all.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val elapsedLabel: Flow<String> = data
        .map { it.inProgress?.startedAt }
        .distinctUntilChanged()
        .flatMapLatest { startedAt ->
            if (startedAt == null) {
                flowOf(ZERO_ELAPSED)
            } else {
                ticker(timeProvider).map { now -> formatElapsed(now - startedAt) }
            }
        }

    private val clock: Flow<HomeClock> = minuteClock
        .map { now ->
            val zone = zoneProvider.zone()
            HomeClock(
                today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
                dayPart = Greeting.dayPart(now, zone),
            )
        }
        .distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> = combine(
        data,
        elapsedLabel,
        clock,
        errorMessage,
        unreadableDatabase,
    ) { homeData, elapsed, homeClock, error, unreadable ->
        HomeUiState(
            isLoading = false,
            errorMessage = error,
            hasPrograms = homeData.hasPrograms,
            today = homeClock.today,
            dayPart = homeClock.dayPart,
            inProgress = homeData.inProgress,
            elapsedLabel = elapsed,
            lastWorkout = homeData.lastWorkout,
            week = homeData.week,
            weightUnit = homeData.settings.defaultWeightUnit,
            unreadableDatabase = unreadable,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /** The user has read the notice that their data could not be recovered. */
    fun dismissUnreadableDatabaseNotice() {
        databaseRecoveryLog.markReported()
        unreadableDatabase.value = null
    }

    fun retry() {
        errorMessage.value = null
        retryToken.value += 1
    }

    private fun reportError(throwable: Throwable) {
        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
    }

    private companion object {
        const val ZERO_ELAPSED = "0:00"
    }
}

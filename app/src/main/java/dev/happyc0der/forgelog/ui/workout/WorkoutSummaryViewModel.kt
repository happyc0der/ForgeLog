package dev.happyc0der.forgelog.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.analytics.PersonalRecords
import dev.happyc0der.forgelog.domain.analytics.SessionRecord
import dev.happyc0der.forgelog.domain.home.SessionSummary
import dev.happyc0der.forgelog.domain.home.TrainingSummaries
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.settings.AppSettings
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.workout.VolumeCalculator
import dev.happyc0der.forgelog.domain.workout.toPounds
import dev.happyc0der.forgelog.ui.common.reportErrors
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.navigation.WorkoutSummaryRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** One exercise as it was actually performed, for the summary's per-exercise breakdown. */
data class SummaryExerciseUi(
    val name: String,
    val completedSets: Int,
    val volumeLb: Double,
    val topSetLabel: String?,
    /**
     * What the exercise added up to when it moved no load: total time held, reps, or distance.
     * Shown in place of volume, which for a plank or a pull-up is always zero.
     */
    val unloadedTotalLabel: String? = null,
)

data class WorkoutSummaryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val summary: SessionSummary? = null,
    val exercises: List<SummaryExerciseUi> = emptyList(),
    val records: List<SessionRecord> = emptyList(),
    val weightUnit: ExerciseUnit = ExerciseUnit.LB,
)

/**
 * What the workout came to, and anything about it that was a personal best.
 *
 * Reads the session back from the database rather than being handed totals by the logger, so the
 * summary describes what was actually saved.
 */
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val workoutSessionRepository: WorkoutSessionRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<WorkoutSummaryRoute>().sessionId
    private val errorMessage = MutableStateFlow<String?>(null)
    private val retryToken = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val data = retryToken.flatMapLatest {
        workoutSessionRepository.observeSessionDetail(sessionId).flatMapLatest { detail ->
            flow {
                if (detail == null) {
                    emit(null)
                } else {
                    // Excluding this session is what makes a record a record: compared against a
                    // table it is already in, nothing the user just did could ever beat anything.
                    val history = workoutSessionRepository.getRecentCompletedDetails(
                        excludeSessionId = sessionId,
                    )
                    emit(detail to PersonalRecords.achievedIn(detail, history))
                }
            }
        }
    }.reportErrors(null) { reportError(it) }

    val uiState: StateFlow<WorkoutSummaryUiState> = combine(
        data,
        settingsRepository.settings.reportErrors(AppSettings()) { reportError(it) },
        errorMessage,
    ) { loaded, settings, error ->
        if (loaded == null) {
            WorkoutSummaryUiState(
                isLoading = false,
                errorMessage = error ?: application.getString(R.string.workout_session_missing),
            )
        } else {
            val (detail, records) = loaded
            WorkoutSummaryUiState(
                isLoading = false,
                errorMessage = error,
                summary = TrainingSummaries.summarize(detail, settings.includeWarmupInVolume),
                exercises = detail.exercises.map { logged ->
                    val totals = VolumeCalculator.calculate(logged.sets, settings.includeWarmupInVolume)
                    SummaryExerciseUi(
                        name = logged.exercise.displayNameSnapshot,
                        completedSets = logged.sets.count { it.completed },
                        volumeLb = totals.loadLb,
                        topSetLabel = topSetLabel(logged.sets),
                        unloadedTotalLabel = when {
                            totals.totalDurationSeconds > 0 -> Formatters.seconds(totals.totalDurationSeconds)
                            totals.totalReps > 0 ->
                                application.getString(R.string.session_detail_reps_value, totals.totalReps)
                            totals.totalDistanceMeters > 0.0 ->
                                Formatters.distanceMeters(totals.totalDistanceMeters)
                            else -> null
                        },
                    )
                },
                records = records,
                weightUnit = settings.defaultWeightUnit,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutSummaryUiState(),
    )

    fun retry() {
        errorMessage.value = null
        // reportErrors ends the upstream, so recovering means re-subscribing rather than merely
        // clearing the message.
        retryToken.update { it + 1 }
    }

    private fun reportError(throwable: Throwable) {
        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
    }

    /**
     * The best working set actually completed, in whatever the exercise measures: the heaviest set
     * of a loaded lift, else the longest hold, else the most reps, else the longest distance.
     *
     * It knew only weight × reps, so every timed and bodyweight exercise summarised as a dash.
     */
    private fun topSetLabel(sets: List<SetLog>): String? {
        val working = sets.filter { it.completed && it.setType != SetType.WARMUP }
        working
            .filter { it.weight != null && it.reps != null }
            // Compared in pounds, so a 100 kg set is not ranked below a 200 lb one.
            .maxByOrNull { set -> set.weight?.toPounds(set.weightUnit) ?: set.weight ?: 0.0 }
            ?.let { heaviest ->
                return application.getString(
                    R.string.summary_top_set,
                    heaviest.reps ?: 0,
                    Formatters.weight(heaviest.weight ?: 0.0, heaviest.weightUnit),
                )
            }
        working.mapNotNull { it.durationSeconds }.filter { it > 0 }.maxOrNull()
            ?.let { return Formatters.seconds(it) }
        working.mapNotNull { it.reps }.filter { it > 0 }.maxOrNull()
            ?.let { return application.getString(R.string.session_detail_reps_value, it) }
        working.mapNotNull { it.distanceMeters }.filter { it > 0.0 }.maxOrNull()
            ?.let { return Formatters.distanceMeters(it) }
        return null
    }
}

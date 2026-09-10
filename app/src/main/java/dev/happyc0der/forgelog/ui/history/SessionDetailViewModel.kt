package dev.happyc0der.forgelog.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.home.SessionSummary
import dev.happyc0der.forgelog.domain.home.TrainingSummaries
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.common.reportErrors
import dev.happyc0der.forgelog.ui.navigation.SessionDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isEditing: Boolean = false,
    val detail: SessionDetail? = null,
    val summary: SessionSummary? = null,
    val weightUnit: ExerciseUnit = ExerciseUnit.LB,
    /** True when the session row itself is gone, which is different from a load failure. */
    val isMissing: Boolean = false,
)

sealed interface SessionDetailEvent {
    data class Message(val value: String) : SessionDetailEvent
    data object Deleted : SessionDetailEvent
    data class RepeatStarted(val sessionId: Long) : SessionDetailEvent
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val workoutSessionRepository: WorkoutSessionRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val sessionId = savedStateHandle.toRoute<SessionDetailRoute>().sessionId
    private val isEditing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val eventsChannel = Channel<SessionDetailEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<SessionDetailUiState> = combine(
        workoutSessionRepository.observeSessionDetail(sessionId)
            .reportErrors(null) { reportError(it) },
        settingsRepository.settings,
        isEditing,
        errorMessage,
    ) { detail, settings, editing, error ->
        SessionDetailUiState(
            isLoading = false,
            errorMessage = error,
            isEditing = editing,
            detail = detail,
            summary = detail?.let {
                TrainingSummaries.summarize(it, settings.includeWarmupInVolume)
            },
            weightUnit = settings.defaultWeightUnit,
            isMissing = detail == null && error == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionDetailUiState(),
    )

    fun toggleEditing() {
        isEditing.value = !isEditing.value
    }

    /*
     * Field edits go straight to a targeted UPDATE in the repository rather than copying the row
     * held in uiState. The snapshot in uiState lags the database by a Flow emission, so two quick
     * edits — a feeling then a note — would both write from the same stale copy and the second
     * would undo the first.
     */

    fun setOverallFeeling(feeling: Int?) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.setOverallFeeling(sessionId, feeling)
        }
    }

    fun setOverallNotes(notes: String) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.setOverallNotes(sessionId, notes.trim().ifBlank { null })
        }
    }

    fun setExerciseFeeling(sessionExerciseId: Long, feeling: Int?) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.setExerciseFeeling(sessionExerciseId, feeling)
        }
    }

    fun setExerciseNotes(sessionExerciseId: Long, notes: String) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.setExerciseNotes(
                sessionExerciseId,
                notes.trim().ifBlank { null },
            )
        }
    }

    /** Saves an edited set. The caller hands back a whole [SetLog] so partial edits cannot half-apply. */
    fun saveSet(set: SetLog) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.upsertSetLog(set)
            eventsChannel.send(
                SessionDetailEvent.Message(application.getString(R.string.session_detail_set_saved)),
            )
        }
    }

    fun deleteSet(setId: Long) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.deleteSetLog(setId)
        }
    }

    fun deleteSession() {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.deleteSession(sessionId)
            eventsChannel.send(SessionDetailEvent.Deleted)
        }
    }

    fun repeatSession() {
        launchSafely(::reportAsMessage) {
            val existing = workoutSessionRepository.getInProgressSession()
            if (existing != null) {
                eventsChannel.send(
                    SessionDetailEvent.Message(
                        application.getString(R.string.workout_session_in_progress),
                    ),
                )
                return@launchSafely
            }
            val newId = workoutSessionRepository.repeatSession(sessionId)
            if (newId == null) {
                eventsChannel.send(
                    SessionDetailEvent.Message(
                        application.getString(R.string.history_session_missing),
                    ),
                )
            } else {
                eventsChannel.send(SessionDetailEvent.RepeatStarted(newId))
            }
        }
    }

    private fun reportError(throwable: Throwable) {
        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
    }

    private fun reportAsMessage(throwable: Throwable) {
        viewModelScope.launch {
            eventsChannel.send(
                SessionDetailEvent.Message(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.state_error_generic),
                ),
            )
        }
    }
}

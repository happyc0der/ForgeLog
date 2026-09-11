package dev.happyc0der.forgelog.ui.programs

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.DEFAULT_PROGRAM_COLOR
import dev.happyc0der.forgelog.domain.model.ProgramSummary
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.common.reportErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgramsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val includeArchived: Boolean = false,
    val programs: List<ProgramSummary> = emptyList(),
)

sealed interface ProgramsEvent {
    data class Message(val value: String) : ProgramsEvent
}

/** A delete waiting on its confirmation, and whether the program has workouts logged from it. */
data class PendingProgramDelete(val programId: Long, val hasHistory: Boolean)

@HiltViewModel
class ProgramsViewModel @Inject constructor(
    private val application: Application,
    private val programRepository: ProgramRepository,
) : ViewModel() {
    private val includeArchived = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    /** Bumped by [retry] to re-subscribe after a failure, since `catch` ends the source flow. */
    private val retryToken = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val summaries = combine(includeArchived, retryToken) { archived, _ -> archived }
        .flatMapLatest { archived ->
            programRepository.observeProgramSummaries(archived)
                .reportErrors(emptyList()) { reportError(it) }
        }

    val uiState: StateFlow<ProgramsUiState> = combine(
        includeArchived,
        summaries,
        errorMessage,
    ) { showArchived, programs, error ->
        ProgramsUiState(
            isLoading = false,
            errorMessage = error,
            includeArchived = showArchived,
            programs = programs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProgramsUiState(),
    )

    private val eventsChannel = Channel<ProgramsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private val _pendingDelete = MutableStateFlow<PendingProgramDelete?>(null)

    /** Held here rather than in the screen, so the question survives a rotation. */
    val pendingDelete: StateFlow<PendingProgramDelete?> = _pendingDelete.asStateFlow()

    fun onToggleArchived() {
        includeArchived.value = !includeArchived.value
    }

    fun retry() {
        errorMessage.value = null
        retryToken.value += 1
    }

    private fun reportError(throwable: Throwable) {
        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
    }

    private fun reportAsMessage(throwable: Throwable) {
        viewModelScope.launch {
            eventsChannel.send(
                ProgramsEvent.Message(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.state_error_generic),
                ),
            )
        }
    }

    fun createProgram(name: String, description: String, color: String = DEFAULT_PROGRAM_COLOR) {
        launchSafely(::reportAsMessage) {
            programRepository.upsertProgram(
                WorkoutProgram(
                    name = name,
                    description = description.ifBlank { null },
                    color = color,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )
        }
    }

    fun renameProgram(
        program: WorkoutProgram,
        name: String,
        description: String,
        color: String = program.color,
    ) {
        launchSafely(::reportAsMessage) {
            programRepository.upsertProgram(
                program.copy(
                    name = name,
                    description = description.ifBlank { null },
                    color = color,
                ),
            )
        }
    }

    fun duplicate(programId: Long) {
        launchSafely(::reportAsMessage) {
            programRepository.duplicateProgram(programId)
            eventsChannel.send(
                ProgramsEvent.Message(application.getString(R.string.program_duplicated)),
            )
        }
    }

    fun setArchived(programId: Long, archived: Boolean) {
        launchSafely(::reportAsMessage) {
            programRepository.setArchived(programId, archived)
            eventsChannel.send(
                ProgramsEvent.Message(
                    application.getString(
                        if (archived) R.string.program_archived else R.string.program_unarchived,
                    ),
                ),
            )
        }
    }

    /**
     * Asks before deleting, in words that depend on whether workouts were logged from it.
     *
     * The screen used to look that up itself, on its own coroutine scope, where a failed read went
     * to the uncaught-exception handler and closed the app.
     */
    fun requestDelete(programId: Long) {
        launchSafely(::reportAsMessage) {
            val hasHistory = programRepository.hasSessionHistory(programId)
            _pendingDelete.value = PendingProgramDelete(programId, hasHistory)
        }
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val pending = _pendingDelete.value ?: return
        _pendingDelete.value = null
        delete(pending.programId)
    }

    private fun delete(programId: Long) {
        launchSafely(::reportAsMessage) {
            programRepository.deleteProgram(programId)
            eventsChannel.send(
                ProgramsEvent.Message(application.getString(R.string.program_deleted)),
            )
        }
    }
}

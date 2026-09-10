package com.example.forgelog.ui.programs

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forgelog.R
import com.example.forgelog.domain.model.DEFAULT_PROGRAM_COLOR
import com.example.forgelog.domain.model.ProgramSummary
import com.example.forgelog.domain.model.WorkoutProgram
import com.example.forgelog.domain.repository.ProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

@HiltViewModel
class ProgramsViewModel @Inject constructor(
    private val application: Application,
    private val programRepository: ProgramRepository,
) : ViewModel() {
    private val includeArchived = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgramsUiState> = combine(
        includeArchived,
        includeArchived.flatMapLatest(programRepository::observeProgramSummaries),
    ) { showArchived, programs ->
        ProgramsUiState(
            isLoading = false,
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

    fun onToggleArchived() {
        includeArchived.value = !includeArchived.value
    }

    fun createProgram(name: String, description: String) {
        viewModelScope.launch {
            programRepository.upsertProgram(
                WorkoutProgram(
                    name = name,
                    description = description.ifBlank { null },
                    color = DEFAULT_PROGRAM_COLOR,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )
        }
    }

    fun renameProgram(program: WorkoutProgram, name: String, description: String) {
        viewModelScope.launch {
            programRepository.upsertProgram(
                program.copy(
                    name = name,
                    description = description.ifBlank { null },
                ),
            )
        }
    }

    fun duplicate(programId: Long) {
        viewModelScope.launch {
            programRepository.duplicateProgram(programId)
            eventsChannel.send(
                ProgramsEvent.Message(application.getString(R.string.program_duplicated)),
            )
        }
    }

    fun setArchived(programId: Long, archived: Boolean) {
        viewModelScope.launch {
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

    suspend fun hasSessionHistory(programId: Long): Boolean =
        programRepository.hasSessionHistory(programId)

    fun delete(programId: Long) {
        viewModelScope.launch {
            programRepository.deleteProgram(programId)
            eventsChannel.send(
                ProgramsEvent.Message(application.getString(R.string.program_deleted)),
            )
        }
    }
}

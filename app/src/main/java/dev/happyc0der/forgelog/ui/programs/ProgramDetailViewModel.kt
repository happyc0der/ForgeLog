package dev.happyc0der.forgelog.ui.programs

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.ProgramDay
import dev.happyc0der.forgelog.domain.model.ProgramDetail
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.ui.navigation.ProgramDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgramDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val detail: ProgramDetail? = null,
)

sealed interface ProgramDetailEvent {
    data class Message(val value: String) : ProgramDetailEvent
    data class OpenDay(val dayId: Long) : ProgramDetailEvent
}

@HiltViewModel
class ProgramDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val programRepository: ProgramRepository,
) : ViewModel() {
    private val programId = savedStateHandle.toRoute<ProgramDetailRoute>().programId

    val uiState: StateFlow<ProgramDetailUiState> = programRepository.observeProgramDetail(programId)
        .map { detail ->
            if (detail == null) {
                ProgramDetailUiState(
                    isLoading = false,
                    errorMessage = application.getString(R.string.program_missing),
                )
            } else {
                ProgramDetailUiState(isLoading = false, detail = detail)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgramDetailUiState(),
        )

    private val eventsChannel = Channel<ProgramDetailEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    fun createDay(name: String) {
        viewModelScope.launch {
            val detail = programRepository.getProgramDetail(programId) ?: return@launch
            val id = programRepository.upsertDay(
                ProgramDay(
                    programId = programId,
                    name = name,
                    dayOrder = detail.days.size,
                ),
            )
            eventsChannel.send(ProgramDetailEvent.OpenDay(id))
        }
    }

    fun renameDay(day: ProgramDay, name: String) {
        viewModelScope.launch {
            programRepository.upsertDay(day.copy(name = name))
        }
    }

    fun duplicateDay(dayId: Long) {
        viewModelScope.launch {
            programRepository.duplicateDay(dayId)
            eventsChannel.send(
                ProgramDetailEvent.Message(application.getString(R.string.program_day_duplicated)),
            )
        }
    }

    fun deleteDay(dayId: Long) {
        viewModelScope.launch {
            programRepository.deleteDay(dayId)
        }
    }

    fun moveDay(from: Int, to: Int) {
        val days = uiState.value.detail?.days?.map { it.day } ?: return
        if (from !in days.indices || to !in days.indices) return
        val reordered = days.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch {
            programRepository.reorderDays(reordered.map { it.id })
        }
    }

    fun persistDayOrder() = Unit
}

package com.example.forgelog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forgelog.domain.model.WorkoutSession
import com.example.forgelog.domain.repository.ProgramRepository
import com.example.forgelog.domain.repository.WorkoutSessionRepository
import com.example.forgelog.domain.time.TimeProvider
import com.example.forgelog.domain.workout.formatElapsed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val hasPrograms: Boolean = false,
    val inProgress: WorkoutSession? = null,
    val elapsedLabel: String = "0:00",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    programRepository: ProgramRepository,
    workoutSessionRepository: WorkoutSessionRepository,
    timeProvider: TimeProvider,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        programRepository.observePrograms(includeArchived = false),
        workoutSessionRepository.observeInProgressSession(),
        ticker(timeProvider),
    ) { programs, session, now ->
        HomeUiState(
            isLoading = false,
            hasPrograms = programs.isNotEmpty(),
            inProgress = session,
            elapsedLabel = session?.let { formatElapsed(now - it.startedAt) } ?: "0:00",
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}

internal fun ticker(timeProvider: TimeProvider) = flow {
    while (true) {
        emit(timeProvider.nowEpochMs())
        delay(1_000)
    }
}

package dev.happyc0der.forgelog.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

sealed interface ColdStartState {
    data object Loading : ColdStartState
    data class Ready(val session: WorkoutSession?) : ColdStartState
}

@HiltViewModel
class WorkoutResumeViewModel @Inject constructor(
    workoutSessionRepository: WorkoutSessionRepository,
) : ViewModel() {
    private val consumed = AtomicBoolean(false)

    val coldStart: StateFlow<ColdStartState> = workoutSessionRepository.observeInProgressSession()
        .map<WorkoutSession?, ColdStartState> { session -> ColdStartState.Ready(session) }
        // This runs at cold start, before any screen exists to show an error. A failure means only
        // that no session is offered for resuming, which is recoverable; letting it escape would
        // take the app down on launch, which is not.
        .catch { emit(ColdStartState.Ready(null)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ColdStartState.Loading,
        )

    fun consumeIfNeeded(state: ColdStartState): WorkoutSession? {
        val ready = state as? ColdStartState.Ready ?: return null
        if (!consumed.compareAndSet(false, true)) return null
        return ready.session
    }
}

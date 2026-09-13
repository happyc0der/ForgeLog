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
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.common.reportErrors
import dev.happyc0der.forgelog.ui.navigation.ProgramDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Job

data class ProgramDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val detail: ProgramDetail? = null,
    /** Day ids in the order being dragged, before it is committed. Null when not dragging. */
    val draftOrder: List<Long>? = null,
) {
    /** Days in the order to display: the in-flight drag order if there is one, else the stored one. */
    val days: List<ProgramDay>
        get() {
            val stored = detail?.days?.map { it.day }.orEmpty()
            val order = draftOrder ?: return stored
            val byId = stored.associateBy { it.id }
            return order.mapNotNull(byId::get) + stored.filter { it.id !in order }
        }
}

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
    private val draftDayOrder = MutableStateFlow<List<Long>?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProgramDetailUiState> = combine(
        programRepository.observeProgramDetail(programId)
            .reportErrors(null) { reportError(it) },
        draftDayOrder,
        errorMessage,
    ) { detail, draft, error ->
        when {
            error != null -> ProgramDetailUiState(isLoading = false, errorMessage = error)
            detail == null -> ProgramDetailUiState(
                isLoading = false,
                errorMessage = application.getString(R.string.program_missing),
            )
            else -> ProgramDetailUiState(isLoading = false, detail = detail, draftOrder = draft)
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgramDetailUiState(),
        )

    private val eventsChannel = Channel<ProgramDetailEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private fun reportError(throwable: Throwable) {
        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
    }

    private fun reportAsMessage(throwable: Throwable) {
        viewModelScope.launch {
            eventsChannel.send(
                ProgramDetailEvent.Message(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.state_error_generic),
                ),
            )
        }
    }

    /*
     * The creates and duplicates below are guarded against a second tap before their coroutine
     * starts, the way ExerciseEditorViewModel.save is and for the same reason: the confirm handler
     * calls the ViewModel and closes the dialog, or the menu, both synchronously, so there is no
     * enabled check to rely on and two taps can land in one frame before any recomposition. Each one
     * made the thing twice.
     */

    private var createJob: Job? = null
    private var duplicateJob: Job? = null
    private var duplicatingId: Long? = null

    fun createDay(name: String) {
        if (createJob?.isActive == true) return
        createJob = launchSafely(::reportAsMessage) {
            // The repository picks the order inside a transaction. Counting the days here and then
            // inserting has the same two failure modes as anywhere else: a gap left by a deleted
            // day makes the count collide with a position still in use, and two quick creates both
            // read the same count.
            val id = programRepository.appendDay(programId, name)
            eventsChannel.send(ProgramDetailEvent.OpenDay(id))
        }
    }

    fun renameDay(day: ProgramDay, name: String) {
        launchSafely(::reportAsMessage) {
            programRepository.upsertDay(day.copy(name = name))
        }
    }

    fun duplicateDay(dayId: Long) {
        // By id, so duplicating a different day while this one is still writing is not
        // mistaken for a double tap and dropped.
        if (duplicateJob?.isActive == true && duplicatingId == dayId) return
        duplicatingId = dayId
        duplicateJob = launchSafely(::reportAsMessage) {
            programRepository.duplicateDay(dayId)
            eventsChannel.send(
                ProgramDetailEvent.Message(application.getString(R.string.program_day_duplicated)),
            )
        }
    }

    fun deleteDay(dayId: Long) {
        launchSafely(::reportAsMessage) {
            programRepository.deleteDay(dayId)
        }
    }

    /**
     * Reorders a draft list only. The database is written once, by [persistDayOrder], when the drag
     * ends.
     *
     * Writing inside the drag wrote N transactions for one gesture, and each one recomputed from a
     * uiState that the previous write had already invalidated, so a fast drag could land in the wrong
     * order. The draft is also what the UI reads while dragging, so the row follows the finger instead
     * of waiting for a round trip through Room.
     */
    fun moveDay(from: Int, to: Int) {
        val current = draftDayOrder.value
            ?: uiState.value.detail?.days?.map { it.day.id }
            ?: return
        if (from !in current.indices || to !in current.indices) return
        draftDayOrder.value = current.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * The draft is dropped whether the write succeeds or fails, as the day builder already does.
     *
     * Kept on failure, it went on showing an order the database had refused -- alongside the message
     * saying so -- until the screen was left, so the list read as saved when it was not.
     */
    fun persistDayOrder() {
        val order = draftDayOrder.value ?: return
        launchSafely(::reportAsMessage) {
            try {
                programRepository.reorderDays(order)
            } finally {
                draftDayOrder.value = null
            }
        }
    }
}

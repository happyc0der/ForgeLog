package dev.happyc0der.forgelog.ui.programs

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.library.HowToUrl
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ProgramDayDetail
import dev.happyc0der.forgelog.domain.model.ProgramExerciseDetail
import dev.happyc0der.forgelog.domain.model.ProgramExercise
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.ui.exercise.ExerciseFormState
import dev.happyc0der.forgelog.ui.navigation.ProgramDayBuilderRoute
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

data class ProgramDayBuilderUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val detail: ProgramDayDetail? = null,
    /** Program-exercise ids in the order being dragged, before it is committed. */
    val draftOrder: List<Long>? = null,
) {
    /** Exercises in display order: the in-flight drag order if there is one, else the stored one. */
    val exercises: List<ProgramExerciseDetail>
        get() {
            val stored = detail?.exercises.orEmpty()
            val order = draftOrder ?: return stored
            val byId = stored.associateBy { it.programExercise.id }
            return order.mapNotNull(byId::get) +
                stored.filter { it.programExercise.id !in order }
        }
}

sealed interface ProgramDayBuilderEvent {
    data class Message(val value: String) : ProgramDayBuilderEvent
    data class Duplicated(val dayId: Long) : ProgramDayBuilderEvent
}

@HiltViewModel
class ProgramDayBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val programRepository: ProgramRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private val dayId = savedStateHandle.toRoute<ProgramDayBuilderRoute>().dayId
    private val draftExerciseOrder = MutableStateFlow<List<Long>?>(null)

    val uiState: StateFlow<ProgramDayBuilderUiState> = combine(
        programRepository.observeDayDetail(dayId),
        draftExerciseOrder,
    ) { detail, draft ->
        if (detail == null) {
            ProgramDayBuilderUiState(
                isLoading = false,
                errorMessage = application.getString(R.string.program_day_missing),
            )
        } else {
            ProgramDayBuilderUiState(isLoading = false, detail = detail, draftOrder = draft)
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgramDayBuilderUiState(),
        )

    private val eventsChannel = Channel<ProgramDayBuilderEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch {
            val exercise = exerciseRepository.getExercise(exerciseId) ?: return@launch
            val order = uiState.value.detail?.exercises?.size ?: 0
            programRepository.upsertProgramExercise(
                ProgramExercise(
                    programDayId = dayId,
                    exerciseId = exercise.id,
                    exerciseOrder = order,
                ),
            )
            eventsChannel.send(
                ProgramDayBuilderEvent.Message(application.getString(R.string.program_exercise_added)),
            )
        }
    }

    fun createExerciseAndAdd(form: ExerciseFormState): Boolean {
        val name = form.name.trim()
        if (name.isEmpty()) return false
        val url = HowToUrl.normalize(form.howToUrl).getOrNull()
        if (form.howToUrl.isNotBlank() && url == null) return false
        viewModelScope.launch {
            val newId = exerciseRepository.upsert(
                Exercise(
                    name = name,
                    category = form.category,
                    defaultUnit = form.defaultUnit,
                    howToUrl = url,
                    defaultPointers = form.defaultPointers.trim().ifBlank { null },
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )
            addExercise(newId)
        }
        return true
    }

    fun saveProgramExercise(exercise: ProgramExercise) {
        viewModelScope.launch {
            programRepository.upsertProgramExercise(exercise)
        }
    }

    fun removeProgramExercise(id: Long) {
        viewModelScope.launch {
            programRepository.deleteProgramExercise(id)
            eventsChannel.send(
                ProgramDayBuilderEvent.Message(application.getString(R.string.program_exercise_removed)),
            )
        }
    }

    /** Reorders a draft list only; [persistExerciseOrder] writes once when the drag ends. */
    fun moveExercise(from: Int, to: Int) {
        val current = draftExerciseOrder.value
            ?: uiState.value.detail?.exercises?.map { it.programExercise.id }
            ?: return
        if (from !in current.indices || to !in current.indices) return
        draftExerciseOrder.value = current.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun persistExerciseOrder() {
        val order = draftExerciseOrder.value ?: return
        viewModelScope.launch {
            runCatching { programRepository.reorderProgramExercises(order) }
                .onFailure { throwable ->
                    eventsChannel.send(
                        ProgramDayBuilderEvent.Message(
                            throwable.message?.takeIf { it.isNotBlank() }
                                ?: application.getString(R.string.state_error_generic),
                        ),
                    )
                }
            draftExerciseOrder.value = null
        }
    }

    fun duplicateDay() {
        viewModelScope.launch {
            val newId = programRepository.duplicateDay(dayId)
            eventsChannel.send(ProgramDayBuilderEvent.Duplicated(newId))
            eventsChannel.send(
                ProgramDayBuilderEvent.Message(application.getString(R.string.program_day_duplicated)),
            )
        }
    }
}

fun parseOptionalInt(raw: String): Result<Int?> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return Result.success(null)
    return trimmed.toIntOrNull()
        ?.let { Result.success(it) }
        ?: Result.failure(IllegalArgumentException("invalid"))
}

fun parseOptionalDouble(raw: String): Result<Double?> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return Result.success(null)
    return trimmed.toDoubleOrNull()
        ?.let { Result.success(it) }
        ?: Result.failure(IllegalArgumentException("invalid"))
}

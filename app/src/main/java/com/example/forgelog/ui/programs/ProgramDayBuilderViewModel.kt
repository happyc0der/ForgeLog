package com.example.forgelog.ui.programs

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.forgelog.R
import com.example.forgelog.domain.library.HowToUrl
import com.example.forgelog.domain.model.Exercise
import com.example.forgelog.domain.model.ProgramDayDetail
import com.example.forgelog.domain.model.ProgramExercise
import com.example.forgelog.domain.repository.ExerciseRepository
import com.example.forgelog.domain.repository.ProgramRepository
import com.example.forgelog.ui.exercise.ExerciseFormState
import com.example.forgelog.ui.navigation.ProgramDayBuilderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
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
)

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

    val uiState: StateFlow<ProgramDayBuilderUiState> = programRepository.observeDayDetail(dayId)
        .map { detail ->
            if (detail == null) {
                ProgramDayBuilderUiState(
                    isLoading = false,
                    errorMessage = application.getString(R.string.program_day_missing),
                )
            } else {
                ProgramDayBuilderUiState(isLoading = false, detail = detail)
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

    fun moveExercise(from: Int, to: Int) {
        val exercises = uiState.value.detail?.exercises?.map { it.programExercise } ?: return
        if (from !in exercises.indices || to !in exercises.indices) return
        val reordered = exercises.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch {
            programRepository.reorderProgramExercises(reordered.map { it.id })
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

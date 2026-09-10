package dev.happyc0der.forgelog.ui.exercise

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.library.HowToUrl
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.ui.navigation.ExerciseEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isCreate: Boolean = true,
    val errorMessage: String? = null,
    val form: ExerciseFormState = ExerciseFormState(),
)

sealed interface ExerciseEditorEvent {
    data class Saved(val exerciseId: Long) : ExerciseEditorEvent
}

@HiltViewModel
class ExerciseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private val exerciseId = savedStateHandle.toRoute<ExerciseEditorRoute>().exerciseId
        .takeIf { it > 0L }

    private val _uiState = MutableStateFlow(ExerciseEditorUiState(isCreate = exerciseId == null))
    val uiState: StateFlow<ExerciseEditorUiState> = _uiState.asStateFlow()

    private val eventsChannel = Channel<ExerciseEditorEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        if (exerciseId == null) {
            _uiState.update { it.copy(isLoading = false, isCreate = true) }
        } else {
            viewModelScope.launch {
                val exercise = exerciseRepository.getExercise(exerciseId)
                if (exercise == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = application.getString(R.string.program_missing),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isCreate = false,
                            form = ExerciseFormState(
                                name = exercise.name,
                                category = exercise.category,
                                defaultUnit = exercise.defaultUnit,
                                howToUrl = exercise.howToUrl.orEmpty(),
                                defaultPointers = exercise.defaultPointers.orEmpty(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(form = it.form.copy(name = value, nameError = null)) }
    }

    fun onCategoryChange(value: ExerciseCategory) {
        _uiState.update { it.copy(form = it.form.copy(category = value)) }
    }

    fun onUnitChange(value: ExerciseUnit) {
        _uiState.update { it.copy(form = it.form.copy(defaultUnit = value)) }
    }

    fun onHowToUrlChange(value: String) {
        _uiState.update { it.copy(form = it.form.copy(howToUrl = value, howToUrlError = null)) }
    }

    fun onPointersChange(value: String) {
        _uiState.update { it.copy(form = it.form.copy(defaultPointers = value)) }
    }

    fun save() {
        val current = _uiState.value.form
        val name = current.name.trim()
        if (name.isEmpty()) {
            _uiState.update {
                it.copy(form = it.form.copy(nameError = application.getString(R.string.exercise_name_required)))
            }
            return
        }
        val urlResult = HowToUrl.normalize(current.howToUrl)
        val url = urlResult.getOrElse { error ->
            _uiState.update {
                it.copy(form = it.form.copy(howToUrlError = error.message))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val existing = exerciseId?.let { exerciseRepository.getExercise(it) }
            val savedId = exerciseRepository.upsert(
                Exercise(
                    id = exerciseId ?: 0L,
                    name = name,
                    category = current.category,
                    defaultUnit = current.defaultUnit,
                    howToUrl = url,
                    defaultPointers = current.defaultPointers.trim().ifBlank { null },
                    isArchived = existing?.isArchived ?: false,
                    createdAt = existing?.createdAt ?: 0L,
                    updatedAt = existing?.updatedAt ?: 0L,
                ),
            )
            _uiState.update { it.copy(isSaving = false) }
            eventsChannel.send(ExerciseEditorEvent.Saved(exerciseId ?: savedId))
        }
    }
}

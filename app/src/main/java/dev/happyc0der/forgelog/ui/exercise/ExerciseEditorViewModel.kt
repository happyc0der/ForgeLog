package dev.happyc0der.forgelog.ui.exercise

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.library.HowToUrl
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.ui.navigation.ExerciseEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
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

    /** A failed save: shown as a snackbar, with the form left as it was typed. */
    data class Message(val value: String) : ExerciseEditorEvent
}

@HiltViewModel
class ExerciseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val exerciseId = savedStateHandle.toRoute<ExerciseEditorRoute>().exerciseId
        .takeIf { it > 0L }
    private var duplicateCheckJob: Job? = null
    private var saveJob: Job? = null

    /** Set once the user picks a unit, so the default from Settings arriving late cannot undo it. */
    private var unitChosen = false

    private val _uiState = MutableStateFlow(ExerciseEditorUiState(isCreate = exerciseId == null))
    val uiState: StateFlow<ExerciseEditorUiState> = _uiState.asStateFlow()

    private val eventsChannel = Channel<ExerciseEditorEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        if (exerciseId == null) {
            _uiState.update { it.copy(isLoading = false, isCreate = true) }
            // A new exercise starts in the weight unit chosen in Settings. It was always lb, so
            // with kg set as the default every new lift had to be switched by hand.
            viewModelScope.launch {
                val unit = runCatching { settingsRepository.settings.first().defaultWeightUnit }
                    .getOrNull() ?: return@launch
                if (!unitChosen) _uiState.update { it.copy(form = it.form.copy(defaultUnit = unit)) }
            }
        } else {
            load(exerciseId)
        }
    }

    /** Loads the exercise being edited. A failure is the screen's error state, with a retry. */
    private fun load(exerciseId: Long) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        launchSafely(::reportLoadFailure) {
            val exercise = exerciseRepository.getExercise(exerciseId)
            if (exercise == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        // Was reporting a missing *program* for a missing exercise.
                        errorMessage = application.getString(R.string.exercise_missing),
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

    fun retry() {
        exerciseId?.let(::load)
    }

    private fun reportLoadFailure(throwable: Throwable) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = throwable.message?.takeIf { message -> message.isNotBlank() }
                    ?: application.getString(R.string.state_error_generic),
            )
        }
    }

    /**
     * Warns when the name is already taken, without blocking the save.
     *
     * Two variations can legitimately share a name, so this is advice rather than a rule — but
     * saving a second "Bench Press" by accident used to be completely silent.
     */
    private fun checkDuplicateName(value: String) {
        duplicateCheckJob?.cancel()
        val candidate = value.trim()
        if (candidate.isEmpty()) {
            _uiState.update { it.copy(form = it.form.copy(duplicateNameWarning = null)) }
            return
        }
        // Advice only, so a failed lookup is simply no warning rather than an error.
        duplicateCheckJob = launchSafely(onError = {}) {
            val clash = exerciseRepository.findByName(candidate)
                ?.takeIf { it.id != exerciseId }
            _uiState.update { state ->
                state.copy(
                    form = state.form.copy(
                        duplicateNameWarning = clash?.let {
                            application.getString(R.string.exercise_duplicate_name)
                        },
                    ),
                )
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(form = it.form.copy(name = value, nameError = null)) }
        checkDuplicateName(value)
    }

    fun onCategoryChange(value: ExerciseCategory) {
        _uiState.update { it.copy(form = it.form.copy(category = value)) }
    }

    fun onUnitChange(value: ExerciseUnit) {
        unitChosen = true
        _uiState.update { it.copy(form = it.form.copy(defaultUnit = value)) }
    }

    fun onHowToUrlChange(value: String) {
        _uiState.update { it.copy(form = it.form.copy(howToUrl = value, howToUrlError = null)) }
    }

    fun onPointersChange(value: String) {
        _uiState.update { it.copy(form = it.form.copy(defaultPointers = value)) }
    }

    fun save() {
        // Guarded before the coroutine, not inside it: isSaving was previously set after the first
        // suspension point, so two taps in one frame both got past the button's enabled check and
        // created two exercises.
        if (_uiState.value.isSaving || saveJob?.isActive == true) return
        val current = _uiState.value.form
        val name = current.name.trim()
        if (name.isEmpty()) {
            _uiState.update {
                it.copy(form = it.form.copy(nameError = application.getString(R.string.exercise_name_required)))
            }
            return
        }
        val urlResult = HowToUrl.normalize(current.howToUrl)
        val url = urlResult.getOrElse {
            _uiState.update {
                // The domain's message is hardcoded English; the user-facing string is the resource.
                it.copy(
                    form = it.form.copy(
                        howToUrlError = application.getString(R.string.exercise_how_to_invalid),
                    ),
                )
            }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        saveJob = launchSafely(::reportSaveFailure) {
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

    /**
     * Releases the guard, or a failed save would leave the button disabled for good, and says so
     * in a snackbar. It used to set the screen's error state, which replaced the form -- and
     * everything typed into it -- with an error whose only way out was back.
     */
    private fun reportSaveFailure(throwable: Throwable) {
        _uiState.update { it.copy(isSaving = false) }
        viewModelScope.launch {
            eventsChannel.send(
                ExerciseEditorEvent.Message(
                    throwable.message?.takeIf { message -> message.isNotBlank() }
                        ?: application.getString(R.string.state_error_generic),
                ),
            )
        }
    }
}

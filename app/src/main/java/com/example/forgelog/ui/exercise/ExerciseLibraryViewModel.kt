package com.example.forgelog.ui.exercise

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.forgelog.R
import com.example.forgelog.domain.library.ExerciseDeletePolicy
import com.example.forgelog.domain.model.Exercise
import com.example.forgelog.domain.model.ExerciseCategory
import com.example.forgelog.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseLibraryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val query: String = "",
    val category: ExerciseCategory? = null,
    val includeArchived: Boolean = false,
    val exercises: List<Exercise> = emptyList(),
)

sealed interface ExerciseLibraryEvent {
    data class Message(val value: String) : ExerciseLibraryEvent
    data class OpenHowTo(val url: String) : ExerciseLibraryEvent
}

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val application: Application,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<ExerciseCategory?>(null)
    private val includeArchived = MutableStateFlow(false)
    private val loadError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exerciseRepository.observeExercises(includeArchived = true),
        query,
        category,
        includeArchived,
        loadError,
    ) { exercises, search, selectedCategory, showArchived, error ->
        val filtered = exercises
            .filter { exercise -> showArchived || !exercise.isArchived }
            .filter { exercise -> selectedCategory == null || exercise.category == selectedCategory }
            .filter { exercise ->
                search.isBlank() || exercise.name.contains(search.trim(), ignoreCase = true)
            }
        ExerciseLibraryUiState(
            isLoading = false,
            errorMessage = error,
            query = search,
            category = selectedCategory,
            includeArchived = showArchived,
            exercises = filtered,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseLibraryUiState(),
    )

    private val eventsChannel = Channel<ExerciseLibraryEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategorySelected(value: ExerciseCategory?) {
        category.value = value
    }

    fun onToggleArchived() {
        includeArchived.update { !it }
    }

    fun onOpenHowTo(exercise: Exercise) {
        val url = exercise.howToUrl ?: return
        viewModelScope.launch {
            eventsChannel.send(ExerciseLibraryEvent.OpenHowTo(url))
        }
    }

    fun archive(exercise: Exercise, archived: Boolean) {
        viewModelScope.launch {
            exerciseRepository.setArchived(exercise.id, archived)
            eventsChannel.send(
                ExerciseLibraryEvent.Message(
                    application.getString(
                        if (archived) R.string.exercise_archived else R.string.exercise_unarchived,
                    ),
                ),
            )
        }
    }

    fun delete(exercise: Exercise) {
        viewModelScope.launch {
            val hasHistory = exerciseRepository.hasSessionHistory(exercise.id)
            if (!ExerciseDeletePolicy.canHardDelete(hasHistory)) {
                eventsChannel.send(
                    ExerciseLibraryEvent.Message(
                        application.getString(R.string.exercise_archive_instead),
                    ),
                )
                return@launch
            }
            exerciseRepository.deleteIfUnusedInSessions(exercise.id)
            eventsChannel.send(
                ExerciseLibraryEvent.Message(application.getString(R.string.exercise_deleted)),
            )
        }
    }
}

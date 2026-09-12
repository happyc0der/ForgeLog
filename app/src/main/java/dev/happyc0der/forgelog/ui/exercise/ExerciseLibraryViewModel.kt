package dev.happyc0der.forgelog.ui.exercise

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.library.ExerciseDeletePolicy
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.common.reportErrors
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
    /**
     * How many exercises match the search and category but are held back by the archive filter.
     *
     * An empty list on its own cannot tell "you have none of these" from "the ones you have are
     * hidden", and the two need opposite advice: add one, or turn the filter on. Guessing wrong
     * invites the user to re-create a lift they already own, under a second entry.
     */
    val hiddenArchivedCount: Int = 0,
)

/** An exercise the user asked to delete, and how many program days it would be taken out of. */
data class PendingExerciseDelete(val exercise: Exercise, val programDays: Int)

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
    private val _pendingDelete = MutableStateFlow<PendingExerciseDelete?>(null)

    /** The delete awaiting confirmation, found out before the dialog is shown. */
    val pendingDelete: StateFlow<PendingExerciseDelete?> = _pendingDelete

    /** Bumped by [retry] to re-subscribe after a failure, since `catch` ends the source flow. */
    private val retryToken = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val exercises = retryToken.flatMapLatest {
        exerciseRepository.observeExercises(includeArchived = true)
            .reportErrors(emptyList()) { reportError(it) }
    }

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exercises,
        query,
        category,
        includeArchived,
        loadError,
    ) { exercises, search, selectedCategory, showArchived, error ->
        val matching = exercises
            .filter { exercise -> selectedCategory == null || exercise.category == selectedCategory }
            .filter { exercise ->
                search.isBlank() || exercise.name.contains(search.trim(), ignoreCase = true)
            }
        val filtered = matching.filter { exercise -> showArchived || !exercise.isArchived }
        ExerciseLibraryUiState(
            isLoading = false,
            errorMessage = error,
            query = search,
            category = selectedCategory,
            includeArchived = showArchived,
            exercises = filtered,
            hiddenArchivedCount = matching.size - filtered.size,
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

    fun retry() {
        loadError.value = null
        retryToken.update { it + 1 }
    }

    private fun reportError(throwable: Throwable) {
        loadError.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
    }

    private fun reportAsMessage(throwable: Throwable) {
        viewModelScope.launch {
            eventsChannel.send(
                ExerciseLibraryEvent.Message(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.state_error_generic),
                ),
            )
        }
    }

    fun onOpenHowTo(exercise: Exercise) {
        val url = exercise.howToUrl ?: return
        viewModelScope.launch {
            eventsChannel.send(ExerciseLibraryEvent.OpenHowTo(url))
        }
    }

    fun archive(exercise: Exercise, archived: Boolean) {
        launchSafely(::reportAsMessage) {
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

    /**
     * Looks before asking. The dialog used to say "It is not used in any logged session" before
     * anything had checked -- so an exercise with history was confirmed, then refused -- and never
     * said that deleting it also takes it out of every program day that lists it.
     */
    fun requestDelete(exercise: Exercise) {
        launchSafely(::reportAsMessage) {
            if (!ExerciseDeletePolicy.canHardDelete(exerciseRepository.hasSessionHistory(exercise.id))) {
                eventsChannel.send(
                    ExerciseLibraryEvent.Message(application.getString(R.string.exercise_archive_instead)),
                )
                return@launchSafely
            }
            _pendingDelete.value = PendingExerciseDelete(
                exercise = exercise,
                programDays = exerciseRepository.programDaysUsing(exercise.id),
            )
        }
    }

    fun cancelDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val pending = _pendingDelete.value ?: return
        _pendingDelete.value = null
        delete(pending.exercise)
    }

    fun delete(exercise: Exercise) {
        launchSafely(::reportAsMessage) {
            val hasHistory = exerciseRepository.hasSessionHistory(exercise.id)
            if (!ExerciseDeletePolicy.canHardDelete(hasHistory)) {
                eventsChannel.send(
                    ExerciseLibraryEvent.Message(
                        application.getString(R.string.exercise_archive_instead),
                    ),
                )
                return@launchSafely
            }
            exerciseRepository.deleteIfUnusedInSessions(exercise.id)
            eventsChannel.send(
                ExerciseLibraryEvent.Message(application.getString(R.string.exercise_deleted)),
            )
        }
    }
}

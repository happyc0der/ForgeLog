package dev.happyc0der.forgelog.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ProgramDay
import dev.happyc0der.forgelog.domain.model.SessionExercise
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.workout.PreviousWorkoutMatcher
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.navigation.StartWorkoutRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class DayChoice(
    val program: WorkoutProgram,
    val day: ProgramDay,
    val exerciseCount: Int,
)

data class PlannedExerciseItem(
    val localId: Long,
    val exercise: Exercise,
    val pointersOverride: String?,
    val targetRestSeconds: Int?,
    val previous: SessionExerciseWithSets? = null,
)

data class StartWorkoutUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val needsDaySelection: Boolean = false,
    val dayChoices: List<DayChoice> = emptyList(),
    val programName: String? = null,
    val dayName: String? = null,
    val roster: List<PlannedExerciseItem> = emptyList(),
    val canConfirm: Boolean = false,
)

sealed interface StartWorkoutEvent {
    data class Message(val value: String) : StartWorkoutEvent
    data class Started(val sessionId: Long) : StartWorkoutEvent
}

private data class StartWorkoutPartial(
    val selectedDayId: Long?,
    val dayChoices: List<DayChoice>,
    val roster: List<PlannedExerciseItem>,
    val programName: String?,
    val dayName: String?,
)

@HiltViewModel
class StartWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val programRepository: ProgramRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
) : ViewModel() {
    private val routeDayId = savedStateHandle.toRoute<StartWorkoutRoute>().programDayId
    private val selectedDayId = MutableStateFlow(routeDayId.takeIf { it > 0L })
    private val roster = MutableStateFlow<List<PlannedExerciseItem>>(emptyList())
    private val programName = MutableStateFlow<String?>(null)
    private val dayName = MutableStateFlow<String?>(null)
    private val programId = MutableStateFlow<Long?>(null)
    private val loadingDay = MutableStateFlow(routeDayId > 0L)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val localIds = AtomicLong(1L)
    private var loadedDayId: Long? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayChoices = programRepository.observePrograms(includeArchived = false)
        .flatMapLatest { programs ->
            if (programs.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(programs.map { program -> programRepository.observeProgramDetail(program.id) }) { details ->
                    details.filterNotNull().flatMap { detail ->
                        detail.days.map { dayDetail ->
                            DayChoice(
                                program = detail.program,
                                day = dayDetail.day,
                                exerciseCount = dayDetail.exercises.size,
                            )
                        }
                    }
                }
            }
        }

    val uiState: StateFlow<StartWorkoutUiState> = combine(
        combine(selectedDayId, dayChoices, roster, programName, dayName) {
                selected, choices, items, program, day ->
            StartWorkoutPartial(
                selectedDayId = selected,
                dayChoices = choices,
                roster = items,
                programName = program,
                dayName = day,
            )
        },
        loadingDay,
        errorMessage,
    ) { partial, loading, error ->
        StartWorkoutUiState(
            isLoading = partial.selectedDayId != null && loading,
            errorMessage = error,
            needsDaySelection = partial.selectedDayId == null && error == null,
            dayChoices = partial.dayChoices,
            programName = partial.programName,
            dayName = partial.dayName,
            roster = partial.roster,
            canConfirm = partial.roster.isNotEmpty() && partial.selectedDayId != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StartWorkoutUiState(isLoading = routeDayId > 0L, needsDaySelection = routeDayId <= 0L),
    )

    private val eventsChannel = Channel<StartWorkoutEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            selectedDayId.collect { dayId ->
                if (dayId != null && dayId != loadedDayId) {
                    loadDay(dayId)
                }
            }
        }
    }

    fun selectDay(dayId: Long) {
        loadedDayId = null
        errorMessage.value = null
        loadingDay.value = true
        selectedDayId.value = dayId
    }

    fun moveExercise(from: Int, to: Int) {
        roster.update { items ->
            items.toMutableList().also { mutable ->
                val item = mutable.removeAt(from)
                mutable.add(to, item)
            }
        }
    }

    fun skipExercise(localId: Long) {
        roster.update { items -> items.filterNot { it.localId == localId } }
    }

    fun addExercise(exerciseId: Long) {
        launchSafely(::reportAsMessage) {
            val exercise = exerciseRepository.getExercise(exerciseId) ?: return@launchSafely
            val history = workoutSessionRepository.getRecentCompletedDetails(excludeSessionId = 0L)
            val item = PlannedExerciseItem(
                localId = localIds.getAndIncrement(),
                exercise = exercise,
                pointersOverride = exercise.defaultPointers,
                targetRestSeconds = null,
                previous = previousFor(
                    exercise = exercise,
                    programId = programId.value,
                    programDayId = selectedDayId.value,
                    history = history,
                    order = roster.value.size,
                ),
            )
            roster.update { it + item }
        }
    }

    fun confirmStart() {
        launchSafely(::reportAsMessage) {
            val dayId = selectedDayId.value
            val items = roster.value
            if (dayId == null || items.isEmpty()) {
                eventsChannel.send(
                    StartWorkoutEvent.Message(application.getString(R.string.workout_need_exercises)),
                )
                return@launchSafely
            }
            val existing = workoutSessionRepository.getInProgressSession()
            if (existing != null) {
                eventsChannel.send(StartWorkoutEvent.Started(existing.id))
                return@launchSafely
            }
            val sessionId = workoutSessionRepository.startSession(
                programId = programId.value,
                programDayId = dayId,
                sessionName = listOfNotNull(programName.value, dayName.value).joinToString(" · "),
                exercises = items.map { item ->
                    SessionStartExercise(
                        exercise = item.exercise,
                        pointersOverride = item.pointersOverride,
                        targetRestSeconds = item.targetRestSeconds,
                    )
                },
            )
            eventsChannel.send(StartWorkoutEvent.Started(sessionId))
        }
    }

    private fun reportAsMessage(throwable: Throwable) {
        viewModelScope.launch {
            eventsChannel.send(
                StartWorkoutEvent.Message(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.state_error_generic),
                ),
            )
        }
    }

    private suspend fun loadDay(dayId: Long) {
        loadingDay.value = true
        val detail = programRepository.getDayDetail(dayId)
        if (detail == null) {
            loadingDay.value = false
            errorMessage.value = application.getString(R.string.workout_day_missing)
            return
        }
        val program = programRepository.getProgram(detail.day.programId)
        programId.value = program?.id
        programName.value = program?.name
        dayName.value = detail.day.name
        val history = workoutSessionRepository.getRecentCompletedDetails(excludeSessionId = 0L)
        roster.value = detail.exercises.mapIndexed { index, item ->
            PlannedExerciseItem(
                localId = localIds.getAndIncrement(),
                exercise = item.exercise,
                pointersOverride = item.programExercise.defaultPointersOverride
                    ?: item.exercise.defaultPointers,
                targetRestSeconds = item.programExercise.targetRestSeconds,
                previous = previousFor(
                    exercise = item.exercise,
                    programId = program?.id,
                    programDayId = dayId,
                    history = history,
                    order = index,
                ),
            )
        }
        loadedDayId = dayId
        loadingDay.value = false
    }

    private fun previousFor(
        exercise: Exercise,
        programId: Long?,
        programDayId: Long?,
        history: List<dev.happyc0der.forgelog.domain.model.SessionDetail>,
        order: Int,
    ): SessionExerciseWithSets? {
        val dummySession = WorkoutSession(
            id = 0L,
            programDayId = programDayId,
            programId = programId,
            sessionName = "",
            startedAt = 0L,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val dummyExercise = SessionExercise(
            id = 0L,
            sessionId = 0L,
            exerciseId = exercise.id,
            displayNameSnapshot = exercise.name,
            exerciseOrder = order,
            startedAt = 0L,
        )
        return PreviousWorkoutMatcher.findPreviousExercise(
            currentSession = dummySession,
            currentExercise = dummyExercise,
            history = history,
        )
    }
}

package dev.happyc0der.forgelog.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseTargets
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.ProgramDay
import dev.happyc0der.forgelog.domain.model.SessionExercise
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SessionStartExercise
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.workout.PreviousPerformance
import dev.happyc0der.forgelog.domain.workout.PreviousWorkoutMatcher
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.common.reportErrors
import dev.happyc0der.forgelog.ui.navigation.StartWorkoutRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

/**
 * One exercise in today's roster.
 *
 * Targets start as whatever the program day specifies and can be changed here for this session
 * only — [dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository.startSession]
 * snapshots them onto the session, and nothing writes back to the program.
 */
data class PlannedExerciseItem(
    val localId: Long,
    val exercise: Exercise,
    val pointersOverride: String?,
    /** The program exercise's notes, read-only here. */
    val planNotes: String? = null,
    override val plannedSets: Int? = null,
    override val targetRepMin: Int? = null,
    override val targetRepMax: Int? = null,
    override val targetWeight: Double? = null,
    override val targetDurationSeconds: Int? = null,
    override val targetRestSeconds: Int? = null,
    val previous: PreviousPerformance? = null,
    /**
     * Bumped when a target is set from outside its field -- a progression suggestion taken -- so
     * the field shows the new value rather than what was last typed into it.
     */
    val targetsRevision: Int = 0,
) : ExerciseTargets

/** Which target a planner edit applies to. */
enum class TargetField {
    PLANNED_SETS,
    REP_MIN,
    REP_MAX,
    WEIGHT,
    DURATION_SECONDS,
    REST_SECONDS,
}

data class StartWorkoutUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isAdHoc: Boolean = false,
    val needsDaySelection: Boolean = false,
    val dayChoices: List<DayChoice> = emptyList(),
    val programName: String? = null,
    val dayName: String? = null,
    val dayNotes: String? = null,
    val roster: List<PlannedExerciseItem> = emptyList(),
    val canConfirm: Boolean = false,
    val weightUnit: ExerciseUnit = ExerciseUnit.LB,
)

sealed interface StartWorkoutEvent {
    data class Message(val value: String) : StartWorkoutEvent
    data class Started(val sessionId: Long) : StartWorkoutEvent

    /**
     * Another session is already running. Raised instead of silently navigating into it: the user
     * just arranged a roster, and throwing that away with no explanation looked like a bug.
     */
    data class AlreadyInProgress(val sessionId: Long, val sessionName: String) : StartWorkoutEvent
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
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<StartWorkoutRoute>()
    private val routeDayId = route.programDayId
    private val isAdHoc = route.adHoc
    private val selectedDayId = MutableStateFlow(routeDayId.takeIf { it > 0L })
    private val roster = MutableStateFlow<List<PlannedExerciseItem>>(emptyList())
    private val programName = MutableStateFlow<String?>(null)
    private val dayName = MutableStateFlow<String?>(null)
    private val dayNotes = MutableStateFlow<String?>(null)
    private val programId = MutableStateFlow<Long?>(null)
    private val weightUnit = settingsRepository.settings
        .map { it.defaultWeightUnit }
        .reportErrors(ExerciseUnit.LB) { reportLoadError(it) }
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
        // The day list is the one arm fed by the database. Unguarded, a query failure completed the
        // whole combine exceptionally, so the screen sat on its initial state forever and the error
        // it was about to show never arrived.
        .reportErrors(emptyList()) { reportLoadError(it) }

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
        weightUnit,
        dayNotes,
    ) { partial, loading, error, unit, notes ->
        StartWorkoutUiState(
            isLoading = partial.selectedDayId != null && loading,
            errorMessage = error,
            isAdHoc = isAdHoc,
            needsDaySelection = !isAdHoc && partial.selectedDayId == null && error == null,
            dayChoices = partial.dayChoices,
            programName = partial.programName,
            dayName = partial.dayName,
            dayNotes = notes,
            roster = partial.roster,
            canConfirm = partial.roster.isNotEmpty() &&
                (isAdHoc || partial.selectedDayId != null),
            weightUnit = unit,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StartWorkoutUiState(
            isLoading = routeDayId > 0L,
            isAdHoc = isAdHoc,
            needsDaySelection = !isAdHoc && routeDayId <= 0L,
        ),
    )

    private val eventsChannel = Channel<StartWorkoutEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            selectedDayId.collect { dayId ->
                if (dayId != null && dayId != loadedDayId) {
                    // Caught per load rather than around the whole collect: a failure on one day
                    // must not kill the collector and leave every later selection doing nothing.
                    try {
                        loadDay(dayId)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        loadingDay.value = false
                        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
                            ?: application.getString(R.string.state_error_generic)
                    }
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

    /**
     * Adjusts one target for today's session. The program template is never touched, which is the
     * whole point of editing here rather than in the day builder.
     */
    fun setTarget(localId: Long, field: TargetField, value: Double?) {
        roster.update { items ->
            items.map { item ->
                if (item.localId != localId) {
                    item
                } else {
                    when (field) {
                        TargetField.PLANNED_SETS -> item.copy(plannedSets = value?.toInt())
                        TargetField.REP_MIN -> item.copy(targetRepMin = value?.toInt())
                        TargetField.REP_MAX -> item.copy(targetRepMax = value?.toInt())
                        TargetField.WEIGHT -> item.copy(targetWeight = value)
                        TargetField.DURATION_SECONDS -> item.copy(targetDurationSeconds = value?.toInt())
                        TargetField.REST_SECONDS -> item.copy(targetRestSeconds = value?.toInt())
                    }
                }
            }
        }
    }

    /** Makes a suggested weight today's target, as if it had been typed in. */
    fun applyProgression(localId: Long, weight: Double) {
        roster.update { items ->
            items.map { item ->
                if (item.localId != localId) {
                    item
                } else {
                    item.copy(targetWeight = weight, targetsRevision = item.targetsRevision + 1)
                }
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
            if (items.isEmpty() || (!isAdHoc && dayId == null)) {
                eventsChannel.send(
                    StartWorkoutEvent.Message(application.getString(R.string.workout_need_exercises)),
                )
                return@launchSafely
            }
            val existing = workoutSessionRepository.getInProgressSession()
            if (existing != null) {
                eventsChannel.send(
                    StartWorkoutEvent.AlreadyInProgress(
                        sessionId = existing.id,
                        sessionName = existing.sessionName.ifBlank {
                            application.getString(R.string.workout_active_title)
                        },
                    ),
                )
                return@launchSafely
            }
            val sessionName = if (isAdHoc) {
                application.getString(R.string.workout_adhoc_name)
            } else {
                listOfNotNull(programName.value, dayName.value).joinToString(" · ")
            }
            val sessionId = workoutSessionRepository.startSession(
                programId = if (isAdHoc) null else programId.value,
                programDayId = if (isAdHoc) null else dayId,
                sessionName = sessionName,
                exercises = items.map { item ->
                    SessionStartExercise(
                        exercise = item.exercise,
                        pointersOverride = item.pointersOverride,
                        plannedSets = item.plannedSets,
                        targetRepMin = item.targetRepMin,
                        targetRepMax = item.targetRepMax,
                        targetWeight = item.targetWeight,
                        targetDurationSeconds = item.targetDurationSeconds,
                        targetRestSeconds = item.targetRestSeconds,
                    )
                },
            )
            eventsChannel.send(StartWorkoutEvent.Started(sessionId))
        }
    }

    /** A failed data source: shown on the screen, since the roster may be unrenderable. */
    private fun reportLoadError(throwable: Throwable) {
        loadingDay.value = false
        errorMessage.value = throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)
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
        dayNotes.value = detail.day.notes?.takeIf { it.isNotBlank() }
        val history = workoutSessionRepository.getRecentCompletedDetails(excludeSessionId = 0L)
        roster.value = detail.exercises.mapIndexed { index, item ->
            PlannedExerciseItem(
                localId = localIds.getAndIncrement(),
                exercise = item.exercise,
                pointersOverride = item.programExercise.defaultPointersOverride
                    ?: item.exercise.defaultPointers,
                planNotes = item.programExercise.notes?.takeIf { it.isNotBlank() },
                // Every target the day builder can set has to arrive here, or the plan silently
                // evaporates on its way into the session.
                plannedSets = item.programExercise.plannedSets,
                targetRepMin = item.programExercise.targetRepMin,
                targetRepMax = item.programExercise.targetRepMax,
                targetWeight = item.programExercise.targetWeight,
                targetDurationSeconds = item.programExercise.targetDurationSeconds,
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
    ): PreviousPerformance? {
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
        return PreviousWorkoutMatcher.findPrevious(
            currentSession = dummySession,
            currentExercise = dummyExercise,
            history = history,
        )
    }
}

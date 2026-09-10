package dev.happyc0der.forgelog.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
import dev.happyc0der.forgelog.domain.settings.AppSettings
import dev.happyc0der.forgelog.domain.settings.SettingsRepository
import dev.happyc0der.forgelog.domain.time.TimeProvider
import dev.happyc0der.forgelog.domain.workout.PreviousPerformance
import dev.happyc0der.forgelog.domain.workout.RestTimer
import dev.happyc0der.forgelog.domain.workout.RestTimerState
import dev.happyc0der.forgelog.domain.workout.PreviousWorkoutMatcher
import dev.happyc0der.forgelog.domain.workout.SessionRest
import dev.happyc0der.forgelog.domain.workout.SetFieldVisibility
import dev.happyc0der.forgelog.domain.workout.SetInputField
import dev.happyc0der.forgelog.domain.workout.SetPrefill
import dev.happyc0der.forgelog.domain.workout.SetTargets
import dev.happyc0der.forgelog.domain.workout.formatElapsed
import dev.happyc0der.forgelog.domain.workout.formatSeconds
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.common.reportErrors
import dev.happyc0der.forgelog.ui.common.ticker
import dev.happyc0der.forgelog.ui.navigation.ActiveWorkoutRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActiveExerciseUi(
    val item: SessionExerciseWithSets,
    val unit: ExerciseUnit,
    val previous: PreviousPerformance?,
    val expanded: Boolean,
    val revealedFields: Set<SetInputField>,
    val notesDraft: String,
)

/** Everything the rest countdown needs to render, already resolved against the clock. */
data class RestTimerUi(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val remainingLabel: String = "0:00",
    val targetSeconds: Int = 0,
    val overrunSeconds: Int = 0,
    val progress: Float = 0f,
) {
    val isOverrun: Boolean get() = overrunSeconds > 0
}

data class ActiveWorkoutUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val detail: SessionDetail? = null,
    val exercises: List<ActiveExerciseUi> = emptyList(),
    val sessionElapsedLabel: String = "0:00",
    val sinceLastSetLabel: String? = null,
    val drafts: Map<String, String> = emptyMap(),
    val restTimer: RestTimerUi = RestTimerUi(),
    val vibrateOnRestEnd: Boolean = true,
    val soundOnRestEnd: Boolean = false,
)

sealed interface ActiveWorkoutEvent {
    data class Message(val value: String) : ActiveWorkoutEvent
    data object Finished : ActiveWorkoutEvent
    data object Abandoned : ActiveWorkoutEvent

    /** Rest reached zero. Raised once per timer, not once per tick. */
    data object RestFinished : ActiveWorkoutEvent
}

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val workoutSessionRepository: WorkoutSessionRepository,
    exerciseRepository: ExerciseRepository,
    private val timeProvider: TimeProvider,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<ActiveWorkoutRoute>().sessionId
    private val drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val revealed = MutableStateFlow<Map<Long, Set<SetInputField>>>(emptyMap())
    private val debounceJobs = mutableMapOf<String, Job>()
    private val loadError = MutableStateFlow<String?>(null)

    /**
     * Serialises set inserts so a double tap cannot produce two sets with the same number.
     *
     * [SetPrefill.nextSet] derives the number from the highest one already present, and reading that
     * from UI state is only correct while no other insert is in flight.
     */
    private var addSetJob: Job? = null

    /**
     * In memory on purpose. The countdown is anchored to the completion time of the set that started
     * it, so the remaining time is recomputed from the database rather than stored — a rest timer
     * survives rotation and backgrounding without a column of its own. Only a deliberate pause or
     * dismiss is transient, and losing either on process death is the right behaviour.
     */
    private val restTimer = MutableStateFlow<RestTimerState?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val previousByExercise = workoutSessionRepository.observeSessionDetail(sessionId)
        .flatMapLatest { detail ->
            flow {
                if (detail == null) {
                    emit(emptyMap())
                } else {
                    val history = workoutSessionRepository.getRecentCompletedDetails(detail.session.id)
                    emit(
                        detail.exercises.associate { item ->
                            item.exercise.id to PreviousWorkoutMatcher.findPrevious(
                                currentSession = detail.session,
                                currentExercise = item.exercise,
                                history = history,
                            )
                        },
                    )
                }
            }
        }
        // A failure to look up history must not take the screen down, or even dominate it: the log
        // is still usable without the "last time" column, so this is a snackbar.
        .reportErrors(emptyMap()) { reportAsMessage(it) }

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        combine(
            workoutSessionRepository.observeSessionDetail(sessionId),
            ticker(timeProvider),
            drafts,
            revealed,
            exerciseRepository.observeExercises(includeArchived = true),
        ) { detail, now, draftMap, revealedMap, exercises ->
            ActiveWorkoutPartial(
                detail = detail,
                now = now,
                drafts = draftMap,
                revealed = revealedMap,
                units = exercises.associate { it.id to it.defaultUnit },
            )
        }.reportErrors(null) { reportLoadError(it) },
        previousByExercise,
        restTimer,
        settingsRepository.settings.reportErrors(AppSettings()) { reportLoadError(it) },
        loadError,
    ) { partial, previous, timer, settings, error ->
        val detail = partial?.detail
        if (partial == null || detail == null) {
            ActiveWorkoutUiState(
                isLoading = false,
                errorMessage = error ?: application.getString(R.string.workout_session_missing),
            )
        } else {
            val expandedId = detail.session.expandedSessionExerciseId
                ?: detail.exercises.firstOrNull()?.exercise?.id
            val lastCompletedAt = SessionRest.lastCompletedAt(
                detail.exercises.asSequence().flatMap { it.sets.asSequence() },
            )
            val sinceLastSetMs = SessionRest.sinceLastSetMs(partial.now, lastCompletedAt)
            ActiveWorkoutUiState(
                isLoading = false,
                // Non-fatal: the log still renders, with the failure shown alongside it.
                errorMessage = error,
                detail = detail,
                exercises = detail.exercises.map { item ->
                    val unit = item.exercise.exerciseId?.let(partial.units::get) ?: ExerciseUnit.LB
                    ActiveExerciseUi(
                        item = item,
                        unit = unit,
                        previous = previous[item.exercise.id],
                        expanded = item.exercise.id == expandedId,
                        revealedFields = partial.revealed[item.exercise.id].orEmpty(),
                        notesDraft = partial.drafts[exerciseNotesKey(item.exercise.id)]
                            ?: item.exercise.exerciseNotes.orEmpty(),
                    )
                },
                sessionElapsedLabel = formatElapsed(partial.now - detail.session.startedAt),
                sinceLastSetLabel = sinceLastSetMs?.let(::formatElapsed),
                drafts = partial.drafts,
                restTimer = timer.toUi(partial.now),
                vibrateOnRestEnd = settings.restTimerVibration,
                soundOnRestEnd = settings.restTimerSound,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveWorkoutUiState(),
    )

    private val eventsChannel = Channel<ActiveWorkoutEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        observeRestCompletion()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRestCompletion() {
        /*
         * Announces the end of rest exactly once per timer.
         *
         * The ticker runs only while a timer is active AND still counting: transformWhile stops it
         * on the tick that reaches zero, so a finished timer is not still waking this coroutine
         * once a second for as long as the screen stays on the back stack. distinctUntilChanged
         * keeps it to one event per timer even across a restart.
         */
        launchSafely(::reportAsMessage) {
            restTimer
                .flatMapLatest { state ->
                    if (state == null || !state.isActive) {
                        flowOf(false)
                    } else {
                        ticker(timeProvider)
                            .map { now -> RestTimer.hasFinished(state, now) }
                            .transformWhile { finished ->
                                emit(finished)
                                !finished
                            }
                    }
                }
                .distinctUntilChanged()
                .collect { finished ->
                    if (finished) eventsChannel.send(ActiveWorkoutEvent.RestFinished)
                }
        }
    }

    fun expand(sessionExerciseId: Long) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.updateExpandedExercise(sessionId, sessionExerciseId)
        }
    }

    fun addSet(sessionExerciseId: Long) {
        val previous = addSetJob
        addSetJob = launchSafely(::reportAsMessage) {
            // Wait for any insert still in flight, then re-read the sets straight from the
            // database. Both halves are needed: uiState is fed by a Room flow that has not
            // necessarily emitted the previous insert yet, so a second tap would otherwise derive
            // the same set number and produce two sets numbered alike.
            previous?.join()
            val item = workoutSessionRepository.getSessionDetail(sessionId)
                ?.exercises
                ?.firstOrNull { it.exercise.id == sessionExerciseId }
                ?: return@launchSafely
            val unit = uiState.value.exercises
                .firstOrNull { it.item.exercise.id == sessionExerciseId }
                ?.unit
                ?: ExerciseUnit.LB
            val next = SetPrefill.nextSet(
                sessionExerciseId = sessionExerciseId,
                existing = item.sets,
                defaultUnit = unit,
                historical = previousByExerciseSnapshot(sessionExerciseId),
                targets = SetTargets(
                    targetRepMin = item.exercise.targetRepMin,
                    targetRepMax = item.exercise.targetRepMax,
                    targetWeight = item.exercise.targetWeight,
                    targetDurationSeconds = item.exercise.targetDurationSeconds,
                    targetRestSeconds = item.exercise.targetRestSeconds,
                ),
            )
            workoutSessionRepository.upsertSetLog(next)
        }
    }

    /** A failed operation the user asked for: shown as a snackbar, the log stays usable. */
    private fun reportAsMessage(throwable: Throwable) {
        viewModelScope.launch {
            eventsChannel.send(ActiveWorkoutEvent.Message(messageFor(throwable)))
        }
    }

    /** A failed data source: shown on the screen, since there may be nothing left to render. */
    private fun reportLoadError(throwable: Throwable) {
        loadError.value = messageFor(throwable)
    }

    private fun messageFor(throwable: Throwable): String =
        throwable.message?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.state_error_generic)

    private fun previousByExerciseSnapshot(sessionExerciseId: Long) =
        uiState.value.exercises
            .firstOrNull { it.item.exercise.id == sessionExerciseId }
            ?.previous
            ?.exercise

    /**
     * How the session felt, recorded while it is still happening.
     *
     * Previously only editable afterwards from history, which is both the wrong moment to ask and
     * the reason the planner's "how did this go last time" line was almost always blank.
     */
    fun onOverallFeeling(feeling: Int?) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.setOverallFeeling(sessionId, feeling)
        }
    }

    fun onOverallNotes(notes: String) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.setOverallNotes(sessionId, notes.trim().ifBlank { null })
        }
    }

    fun deleteSet(setId: Long) {
        launchSafely(::reportAsMessage) {
            // Drop any pending debounced write for this set, or it would resurrect the row.
            drafts.value.keys
                .filter { it.substringBefore(":").toLongOrNull() == setId }
                .forEach { key ->
                    debounceJobs.remove(key)?.cancel()
                    drafts.update { it - key }
                }
            workoutSessionRepository.deleteSetLog(setId)
        }
    }

    /**
     * Starts rest without ticking a set — for a rest taken between exercises, or after a set that
     * was logged earlier. The countdown otherwise only ever began when a set was completed.
     */
    fun startRestTimer(sessionExerciseId: Long) {
        launchSafely(::reportAsMessage) {
            startRestTimer(sessionExerciseId, timeProvider.nowEpochMs())
        }
    }

    fun toggleMoreFields(sessionExerciseId: Long) {
        revealed.update { current ->
            val existing = current[sessionExerciseId].orEmpty()
            val next = if (existing.isEmpty()) SetInputField.entries.toSet() else emptySet()
            current + (sessionExerciseId to next)
        }
    }

    fun onSetType(set: SetLog, type: SetType) {
        persistSet(set.copy(setType = type))
    }

    fun onSetCompleted(set: SetLog, completed: Boolean) {
        launchSafely(::reportAsMessage) {
            if (!completed) {
                workoutSessionRepository.upsertSetLog(
                    set.copy(
                        completed = false,
                        completedAt = null,
                        restAfterSetSeconds = null,
                    ),
                )
                return@launchSafely
            }
            val now = timeProvider.nowEpochMs()
            val otherSets = currentDetail()
                ?.exercises
                ?.asSequence()
                ?.flatMap { it.sets.asSequence() }
                .orEmpty()
            val lastCompletedAt = SessionRest.lastCompletedAt(otherSets, excludeSetId = set.id)
            workoutSessionRepository.upsertSetLog(
                set.copy(
                    completed = true,
                    completedAt = now,
                    restAfterSetSeconds = SessionRest.restAfterSetSeconds(now, lastCompletedAt),
                ),
            )
            startRestTimer(set.sessionExerciseId, now)
        }
    }

    /**
     * Rest begins the moment a set is ticked, using the exercise's planned rest where the program
     * specified one and the user's default otherwise. This is what the planned-rest target is for.
     */
    private suspend fun startRestTimer(sessionExerciseId: Long, anchorEpochMs: Long) {
        val plannedRest = currentDetail()
            ?.exercises
            ?.firstOrNull { it.exercise.id == sessionExerciseId }
            ?.exercise
            ?.targetRestSeconds
        val defaultRest = settingsRepository.settings.first().defaultRestSeconds
        restTimer.value = RestTimer.start(
            targetSeconds = RestTimer.suggestedTarget(plannedRest, defaultRest),
            anchorEpochMs = anchorEpochMs,
        )
    }

    fun pauseRestTimer() {
        restTimer.update { current ->
            current?.let { RestTimer.pause(it, timeProvider.nowEpochMs()) }
        }
    }

    fun resumeRestTimer() {
        restTimer.update { current ->
            current?.let { RestTimer.resume(it, timeProvider.nowEpochMs()) }
        }
    }

    fun adjustRestTimer(deltaSeconds: Int) {
        restTimer.update { current -> current?.let { RestTimer.adjust(it, deltaSeconds) } }
    }

    fun skipRestTimer() {
        restTimer.update { current -> current?.let(RestTimer::dismiss) }
    }

    private fun RestTimerState?.toUi(nowEpochMs: Long): RestTimerUi {
        if (this == null || !isActive) return RestTimerUi()
        val remaining = RestTimer.remainingSeconds(this, nowEpochMs)
        return RestTimerUi(
            isActive = true,
            isPaused = isPaused,
            remainingLabel = formatSeconds(remaining),
            targetSeconds = targetSeconds,
            overrunSeconds = RestTimer.overrunSeconds(this, nowEpochMs),
            progress = if (targetSeconds <= 0) {
                1f
            } else {
                ((targetSeconds - remaining).toFloat() / targetSeconds).coerceIn(0f, 1f)
            },
        )
    }

    fun onSetUnit(set: SetLog, unit: ExerciseUnit) {
        persistSet(set.copy(weightUnit = unit))
    }

    fun onSetRpe(set: SetLog, rpe: Int?) {
        persistSet(set.copy(rpe = rpe))
    }

    fun onSetRir(set: SetLog, rir: Int?) {
        persistSet(set.copy(rir = rir))
    }

    fun onSetText(set: SetLog, field: String, value: String) {
        val key = setFieldKey(set.id, field)
        drafts.update { it + (key to value) }
        debounce(key) {
            val latest = drafts.value[key] ?: value
            val updated = applySetField(setWithDrafts(setFromState(set.id) ?: set), field, latest)
            workoutSessionRepository.upsertSetLog(updated)
            drafts.update { it - key }
        }
    }

    fun onExerciseNotes(sessionExerciseId: Long, value: String) {
        val key = exerciseNotesKey(sessionExerciseId)
        drafts.update { it + (key to value) }
        debounce(key) {
            val detail = currentDetail() ?: return@debounce
            val exercise = detail.exercises.firstOrNull { it.exercise.id == sessionExerciseId }?.exercise
                ?: return@debounce
            workoutSessionRepository.upsertSessionExercise(
                exercise.copy(exerciseNotes = (drafts.value[key] ?: value).trim().ifBlank { null }),
            )
            drafts.update { it - key }
        }
    }

    fun finish() {
        launchSafely(::reportAsMessage) {
            flushDrafts()
            workoutSessionRepository.completeSession(sessionId)
            eventsChannel.send(ActiveWorkoutEvent.Finished)
        }
    }

    fun abandon() {
        launchSafely(::reportAsMessage) {
            flushDrafts()
            workoutSessionRepository.abandonSession(sessionId)
            eventsChannel.send(ActiveWorkoutEvent.Abandoned)
        }
    }

    fun fieldValue(set: SetLog, field: String): String {
        val draft = uiState.value.drafts[setFieldKey(set.id, field)]
        if (draft != null) return draft
        return when (field) {
            FIELD_REPS -> set.reps?.toString().orEmpty()
            FIELD_WEIGHT -> set.weight?.toString().orEmpty()
            FIELD_DURATION -> set.durationSeconds?.toString().orEmpty()
            FIELD_DISTANCE -> set.distanceMeters?.toString().orEmpty()
            FIELD_REST -> set.restAfterSetSeconds?.toString().orEmpty()
            FIELD_NOTES -> set.notes.orEmpty()
            else -> ""
        }
    }

    fun isFieldVisible(unit: ExerciseUnit, revealedFields: Set<SetInputField>, field: SetInputField): Boolean =
        SetFieldVisibility.isVisible(field, unit, revealedFields)

    private fun persistSet(set: SetLog) {
        launchSafely(::reportAsMessage) {
            workoutSessionRepository.upsertSetLog(set)
        }
    }

    private fun debounce(key: String, block: suspend () -> Unit) {
        debounceJobs[key]?.cancel()
        debounceJobs[key] = launchSafely(::reportAsMessage) {
            delay(250)
            block()
        }
    }

    private suspend fun flushDrafts() {
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        val snapshot = drafts.value
        snapshot.forEach { (key, value) ->
            if (key.startsWith("ex:") && key.endsWith(":notes")) {
                val id = key.removePrefix("ex:").removeSuffix(":notes").toLongOrNull() ?: return@forEach
                val exercise = currentDetail()?.exercises?.firstOrNull { it.exercise.id == id }?.exercise
                    ?: return@forEach
                workoutSessionRepository.upsertSessionExercise(
                    exercise.copy(exerciseNotes = value.trim().ifBlank { null }),
                )
            } else {
                val setId = key.substringBefore(":").toLongOrNull() ?: return@forEach
                val field = key.substringAfter(":")
                val set = setFromState(setId) ?: return@forEach
                workoutSessionRepository.upsertSetLog(applySetField(set, field, value))
            }
        }
        drafts.value = emptyMap()
    }

    private fun setWithDrafts(set: SetLog): SetLog {
        var updated = set
        listOf(FIELD_REPS, FIELD_WEIGHT, FIELD_DURATION, FIELD_DISTANCE, FIELD_REST, FIELD_NOTES)
            .forEach { field ->
                val draft = drafts.value[setFieldKey(set.id, field)] ?: return@forEach
                updated = applySetField(updated, field, draft)
            }
        return updated
    }

    private fun applySetField(set: SetLog, field: String, value: String): SetLog {
        val trimmed = value.trim()
        return when (field) {
            FIELD_REPS -> set.copy(reps = trimmed.toIntOrNull())
            FIELD_WEIGHT -> set.copy(weight = trimmed.toDoubleOrNull())
            FIELD_DURATION -> set.copy(durationSeconds = trimmed.toIntOrNull())
            FIELD_DISTANCE -> set.copy(distanceMeters = trimmed.toDoubleOrNull())
            FIELD_REST -> set.copy(restAfterSetSeconds = trimmed.toIntOrNull())
            FIELD_NOTES -> set.copy(notes = trimmed.ifBlank { null })
            else -> set
        }
    }

    private fun setFromState(setId: Long): SetLog? =
        uiState.value.exercises.asSequence().flatMap { it.item.sets.asSequence() }.firstOrNull { it.id == setId }

    private fun currentDetail(): SessionDetail? = uiState.value.detail

    private data class ActiveWorkoutPartial(
        val detail: SessionDetail?,
        val now: Long,
        val drafts: Map<String, String>,
        val revealed: Map<Long, Set<SetInputField>>,
        val units: Map<Long, ExerciseUnit>,
    )

    companion object {
        const val FIELD_REPS = "reps"
        const val FIELD_WEIGHT = "weight"
        const val FIELD_DURATION = "duration"
        const val FIELD_DISTANCE = "distance"
        const val FIELD_REST = "rest"
        const val FIELD_NOTES = "notes"

        fun setFieldKey(setId: Long, field: String): String = "$setId:$field"
        fun exerciseNotesKey(sessionExerciseId: Long): String = "ex:$sessionExerciseId:notes"
    }
}

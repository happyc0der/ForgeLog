package dev.happyc0der.forgelog.ui.workout

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionDetail
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.repository.ExerciseRepository
import dev.happyc0der.forgelog.domain.repository.ProgramRepository
import dev.happyc0der.forgelog.domain.repository.WorkoutSessionRepository
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
import dev.happyc0der.forgelog.domain.workout.SetsLeft
import dev.happyc0der.forgelog.domain.workout.SetTargets
import dev.happyc0der.forgelog.domain.workout.asWeightUnit
import dev.happyc0der.forgelog.domain.workout.formatElapsed
import dev.happyc0der.forgelog.domain.workout.formatSeconds
import dev.happyc0der.forgelog.ui.common.launchSafely
import dev.happyc0der.forgelog.ui.common.reportErrors
import dev.happyc0der.forgelog.ui.common.ticker
import dev.happyc0der.forgelog.ui.navigation.ActiveWorkoutRoute
import dev.happyc0der.forgelog.workout.RestTimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActiveExerciseUi(
    val item: SessionExerciseWithSets,
    val unit: ExerciseUnit,
    /** What its planned weight is in: [unit] for a loaded lift, else the default weight unit. */
    val targetWeightUnit: ExerciseUnit = unit,
    val previous: PreviousPerformance?,
    val expanded: Boolean,
    val revealedFields: Set<SetInputField>,
    /** Fields shown because the plan or the last session used them; see [SetFieldVisibility.inUse]. */
    val fieldsInUse: Set<SetInputField> = emptySet(),
    val notesDraft: String,
    /** The program's notes for this exercise, when the session came from a program day. */
    val planNotes: String? = null,
)

/** The logger's clocks, resolved against the current time. See [ActiveWorkoutViewModel.clock]. */
data class LoggerClockUi(
    val sessionElapsedLabel: String = "0:00",
    val sinceLastSetLabel: String? = null,
    val restTimer: RestTimerUi = RestTimerUi(),
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
    /** The program day's notes -- a warm-up, say -- when the session came from one. */
    val dayNotes: String? = null,
    val exercises: List<ActiveExerciseUi> = emptyList(),
    val sessionElapsedLabel: String = "0:00",
    val sinceLastSetLabel: String? = null,
    val drafts: Map<String, String> = emptyMap(),
    val restTimer: RestTimerUi = RestTimerUi(),
)

sealed interface ActiveWorkoutEvent {
    data class Message(val value: String) : ActiveWorkoutEvent
    data object Finished : ActiveWorkoutEvent
    data object Abandoned : ActiveWorkoutEvent
}

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val workoutSessionRepository: WorkoutSessionRepository,
    exerciseRepository: ExerciseRepository,
    programRepository: ProgramRepository,
    private val timeProvider: TimeProvider,
    private val settingsRepository: SettingsRepository,
    private val restTimerController: RestTimerController,
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

    /** Serialises ticks, for the same reason; see [onSetCompleted]. */
    private var completionJob: Job? = null

    private var finishCheckJob: Job? = null
    private val _finishPrompt = MutableStateFlow<List<SetsLeft>?>(null)

    /**
     * The sets still to do, while Finish is asking whether to finish anyway; null otherwise.
     *
     * Finish ended the workout on a single tap wherever it stood, with no way back to it, while
     * Abandon asked first. It now asks when planned sets are not done, or added sets not ticked.
     * Held here rather than in the screen, so a rotation keeps the question open.
     */
    val finishPrompt: StateFlow<List<SetsLeft>?> = _finishPrompt.asStateFlow()

    /*
     * The rest countdown is not held here. It lives in [RestTimerController], for the app, because
     * this ViewModel is destroyed as soon as the user leaves the logger -- and the countdown, and
     * its alert, went with it. This class starts, adjusts and displays it.
     */

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

    /**
     * The program day this session was started from: its notes, and each exercise's.
     *
     * Read live from the program rather than snapshotted, since they are reference text and not a
     * record of the session: an edit made mid-workout shows up here, which is what anyone editing
     * them would expect. Matched by exercise; if a day lists the same lift twice, its first entry's
     * notes are used.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val planContext = workoutSessionRepository.observeSessionDetail(sessionId)
        .map { it?.session?.programDayId }
        .distinctUntilChanged()
        .flatMapLatest { dayId ->
            if (dayId == null) {
                flowOf(PlanContext())
            } else {
                programRepository.observeDayDetail(dayId).map { day ->
                    PlanContext(
                        dayNotes = day?.day?.notes?.takeIf { it.isNotBlank() },
                        notesByExerciseId = day?.exercises.orEmpty()
                            .mapNotNull { entry ->
                                entry.programExercise.notes?.takeIf { it.isNotBlank() }
                                    ?.let { entry.programExercise.exerciseId to it }
                            }
                            .distinctBy { it.first }
                            .toMap(),
                    )
                }
            }
        }
        // Reference text only: losing it must not cost the log.
        .reportErrors(PlanContext()) { reportAsMessage(it) }

    /*
     * Everything on the logger except its clocks, which tick every second while this changes only
     * when the data does. They used to be one flow, so every tick rebuilt every exercise card as a
     * new object and the whole screen recomposed once a second -- on the test device, a frame of
     * 22 ms (over budget) each second, for nothing but the session clock. Kept apart, the cards are
     * the same objects from one tick to the next, and Compose skips them.
     */
    private val content: Flow<LoggerContent?> = combine(
        combine(
            workoutSessionRepository.observeSessionDetail(sessionId),
            drafts,
            revealed,
            exerciseRepository.observeExercises(includeArchived = true),
            settingsRepository.settings.map { it.defaultWeightUnit }.distinctUntilChanged(),
        ) { detail, draftMap, revealedMap, exercises, defaultWeightUnit ->
            ActiveWorkoutPartial(
                detail = detail,
                drafts = draftMap,
                revealed = revealedMap,
                units = exercises.associate { it.id to it.defaultUnit },
                defaultWeightUnit = defaultWeightUnit,
            )
        }.reportErrors(null) { reportLoadError(it) },
        combine(previousByExercise, planContext, ::Pair),
    ) { partial, context ->
        val (previous, plan) = context
        val detail = partial?.detail ?: return@combine null
        val expandedId = detail.session.expandedSessionExerciseId
            ?: detail.exercises.firstOrNull()?.exercise?.id
        LoggerContent(
            detail = detail,
            dayNotes = plan.dayNotes,
            exercises = detail.exercises.map { item ->
                val unit = item.exercise.exerciseId?.let(partial.units::get) ?: ExerciseUnit.LB
                ActiveExerciseUi(
                    item = item,
                    unit = unit,
                    targetWeightUnit = unit.asWeightUnit(fallback = partial.defaultWeightUnit),
                    previous = previous[item.exercise.id],
                    expanded = item.exercise.id == expandedId,
                    revealedFields = partial.revealed[item.exercise.id].orEmpty(),
                    fieldsInUse = SetFieldVisibility.inUse(
                        targets = item.exercise,
                        previousSets = previous[item.exercise.id]?.completedSets.orEmpty(),
                    ),
                    notesDraft = partial.drafts[exerciseNotesKey(item.exercise.id)]
                        ?: item.exercise.exerciseNotes.orEmpty(),
                    planNotes = item.exercise.exerciseId?.let(plan.notesByExerciseId::get),
                )
            },
            drafts = partial.drafts,
            lastCompletedAt = SessionRest.lastCompletedAt(
                detail.exercises.asSequence().flatMap { it.sets.asSequence() },
            ),
        )
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * The logger without its clocks: what the screen's body draws. It changes only when the data
     * does, so a clock tick recomposes nothing but the clock.
     */
    val contentState: StateFlow<ActiveWorkoutUiState> = combine(content, loadError) { loaded, error ->
        if (loaded == null) {
            ActiveWorkoutUiState(
                isLoading = false,
                errorMessage = error ?: application.getString(R.string.workout_session_missing),
            )
        } else {
            ActiveWorkoutUiState(
                isLoading = false,
                // Non-fatal: the log still renders, with the failure shown alongside it.
                errorMessage = error,
                detail = loaded.detail,
                dayNotes = loaded.dayNotes,
                exercises = loaded.exercises,
                drafts = loaded.drafts,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveWorkoutUiState(),
    )

    /**
     * The clocks alone -- session time, time since the last set, the rest countdown -- for the header
     * and the rest bar, the only parts of the screen that change every second.
     *
     * One state used to carry both, so each tick recomposed the whole logger: on the test device a
     * frame of 34 ms every second, where the budget is 8. Split, a tick recomposes three lines of
     * text and the rest bar.
     */
    val clock: StateFlow<LoggerClockUi> = combine(
        content.map { it?.detail?.session?.startedAt to it?.lastCompletedAt }.distinctUntilChanged(),
        ticker(timeProvider),
        restTimerController.observe(sessionId),
    ) { (startedAt, lastCompletedAt), now, timer ->
        LoggerClockUi(
            sessionElapsedLabel = startedAt?.let { formatElapsed(now - it) } ?: LoggerClockUi().sessionElapsedLabel,
            sinceLastSetLabel = SessionRest.sinceLastSetMs(now, lastCompletedAt)?.let(::formatElapsed),
            restTimer = timer.toUi(now),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LoggerClockUi(),
    )

    /** Both together, for a caller that wants the whole picture in one state. */
    val uiState: StateFlow<ActiveWorkoutUiState> = combine(contentState, clock) { screen, time ->
        screen.copy(
            sessionElapsedLabel = time.sessionElapsedLabel,
            sinceLastSetLabel = time.sinceLastSetLabel,
            restTimer = time.restTimer,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveWorkoutUiState(),
    )

    private val eventsChannel = Channel<ActiveWorkoutEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    init {
        restoreRestTimer()
    }

    /**
     * Picks a running countdown back up after the process was killed.
     *
     * The timer itself is in memory, but everything needed to rebuild it is not: the anchor is the
     * completion time of the last set, which is a column. Without this, putting the phone down for
     * two minutes of rest — the moment Android is most likely to reclaim the app — lost the
     * countdown entirely.
     *
     * Runs once, and only while this session has no countdown already: a live one -- running,
     * paused or skipped, and now surviving the logger being left and reopened -- always wins over
     * a reconstruction.
     */
    private fun restoreRestTimer() {
        launchSafely(::reportAsMessage) {
            val detail = workoutSessionRepository.observeSessionDetail(sessionId)
                .filterNotNull()
                .first()
            if (restTimerController.current(sessionId) != null) return@launchSafely
            val lastCompleted = detail.exercises
                .asSequence()
                .flatMap { logged -> logged.sets.asSequence().map { logged to it } }
                .filter { (_, set) -> set.completed && set.completedAt != null }
                .maxByOrNull { (_, set) -> set.completedAt ?: 0L }
                ?: return@launchSafely
            val (logged, set) = lastCompleted
            val anchor = set.completedAt ?: return@launchSafely
            val target = RestTimer.suggestedTarget(
                plannedRestSeconds = logged.exercise.targetRestSeconds,
                defaultRestSeconds = settingsRepository.settings.first().defaultRestSeconds,
            )
            val restored = RestTimer.restore(anchor, target, timeProvider.nowEpochMs())
                ?: return@launchSafely
            restTimerController.restore(sessionId, restored, startedBySetId = set.id)
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
            val exerciseUnit = contentState.value.exercises
                .firstOrNull { it.item.exercise.id == sessionExerciseId }
                ?.unit
                ?: ExerciseUnit.LB
            // A timed or bodyweight lift's own unit is not a weight unit: its planned weight -- a
            // weighted plank, a farmer's walk -- is in the user's default one.
            val weightUnit = exerciseUnit.asWeightUnit(
                fallback = settingsRepository.settings.first().defaultWeightUnit,
            )
            val next = SetPrefill.nextSet(
                sessionExerciseId = sessionExerciseId,
                existing = item.sets,
                defaultUnit = weightUnit,
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
        contentState.value.exercises
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
            startRestTimer(sessionExerciseId, timeProvider.nowEpochMs(), startedBySetId = null)
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

    /**
     * Ticks or unticks a set, and records the rest that ticking it has just ended.
     *
     * The rest a completion measures is the gap since the *previous* completed set, so it is
     * written onto that set as its rest-after. It used to be written onto the set being ticked,
     * which put every rest one set late: Set 1 never showed a rest, Set 2 carried the rest that
     * came before it, and the rest after the final set was never recorded at all. The set being
     * ticked keeps its own planned rest until the next completion measures the real one.
     *
     * Read from the database rather than from UI state, which may not yet reflect a set completed
     * a moment ago; and with anything still being typed applied, because a tick within the
     * autosave delay otherwise wrote the row without it -- a duration typed and ticked at once
     * was saved, and measured, as the old one.
     */
    fun onSetCompleted(set: SetLog, completed: Boolean) {
        // One at a time, each seeing the last one's writes: two ticks in quick succession each
        // read the session before the other had written, so both measured from the same set.
        // Timed at the tap, not whenever the queued work gets to run.
        val tappedAt = timeProvider.nowEpochMs()
        val previousCompletion = completionJob
        completionJob = launchSafely(::reportAsMessage) {
            previousCompletion?.join()
            val allSets = workoutSessionRepository.getSessionDetail(sessionId)
                ?.exercises
                ?.flatMap { it.sets }
                .orEmpty()
            // Gone from the database means deleted a moment ago; writing it would bring it back.
            val stored = allSets.firstOrNull { it.id == set.id } ?: return@launchSafely
            val current = setWithDrafts(stored)
            if (!completed) {
                // Unticking touches only this set. Its planned rest stays; the rest recorded on the
                // set before it is left as it was, because there is no way to know what it held
                // before this completion overwrote it.
                workoutSessionRepository.upsertSetLog(current.copy(completed = false, completedAt = null))
                // A mis-tapped set must not leave rest running that never began.
                restTimerController.stopIfStartedBy(sessionId, set.id)
                return@launchSafely
            }
            val now = tappedAt
            val previous = SessionRest.previousCompleted(allSets.asSequence(), excludeSetId = set.id)
            workoutSessionRepository.upsertSetLog(current.copy(completed = true, completedAt = now))
            val measured = previous?.let {
                SessionRest.restAfterSetSeconds(
                    nowEpochMs = now,
                    lastCompletedAt = it.completedAt,
                    nextSetDurationSeconds = current.durationSeconds,
                )
            }
            // Null when there is nothing to measure -- the first set, or sets ticked off in a row
            // after the fact -- and then the earlier set keeps the rest it already had.
            if (previous != null && measured != null) {
                workoutSessionRepository.setRestAfter(setLogId = previous.id, seconds = measured)
            }
            startRestTimer(set.sessionExerciseId, now, startedBySetId = set.id)
        }
    }

    /**
     * Rest begins the moment a set is ticked, using the exercise's planned rest where the program
     * specified one and the user's default otherwise. This is what the planned-rest target is for.
     */
    private suspend fun startRestTimer(sessionExerciseId: Long, anchorEpochMs: Long, startedBySetId: Long?) {
        val plannedRest = currentDetail()
            ?.exercises
            ?.firstOrNull { it.exercise.id == sessionExerciseId }
            ?.exercise
            ?.targetRestSeconds
        val defaultRest = settingsRepository.settings.first().defaultRestSeconds
        restTimerController.start(
            sessionId = sessionId,
            targetSeconds = RestTimer.suggestedTarget(plannedRest, defaultRest),
            anchorEpochMs = anchorEpochMs,
            startedBySetId = startedBySetId,
        )
    }

    fun pauseRestTimer() {
        restTimerController.update(sessionId) { RestTimer.pause(it, timeProvider.nowEpochMs()) }
    }

    fun resumeRestTimer() {
        restTimerController.update(sessionId) { RestTimer.resume(it, timeProvider.nowEpochMs()) }
    }

    fun adjustRestTimer(deltaSeconds: Int) {
        restTimerController.update(sessionId) { RestTimer.adjust(it, deltaSeconds) }
    }

    fun skipRestTimer() {
        restTimerController.update(sessionId, RestTimer::dismiss)
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

    /** Finish, asking first if sets are left. */
    fun requestFinish() {
        if (finishCheckJob?.isActive == true) return
        finishCheckJob = launchSafely(::reportAsMessage) {
            // A tick still being written counts: tapping Done on the last set and then Finish
            // straight away must not be told that set is left.
            completionJob?.join()
            addSetJob?.join()
            val detail = workoutSessionRepository.getSessionDetail(sessionId) ?: return@launchSafely
            val left = SetsLeft.of(detail)
            if (left.isEmpty()) finish() else _finishPrompt.value = left
        }
    }

    fun confirmFinish() {
        _finishPrompt.value = null
        finish()
    }

    fun dismissFinishPrompt() {
        _finishPrompt.value = null
    }

    fun finish() {
        launchSafely(::reportAsMessage) {
            flushDrafts()
            workoutSessionRepository.completeSession(sessionId)
            restTimerController.clear(sessionId)
            eventsChannel.send(ActiveWorkoutEvent.Finished)
        }
    }

    fun abandon() {
        launchSafely(::reportAsMessage) {
            flushDrafts()
            workoutSessionRepository.abandonSession(sessionId)
            restTimerController.clear(sessionId)
            eventsChannel.send(ActiveWorkoutEvent.Abandoned)
        }
    }

    fun fieldValue(set: SetLog, field: String): String {
        val draft = contentState.value.drafts[setFieldKey(set.id, field)]
        if (draft != null) return draft
        return when (field) {
            FIELD_REPS -> set.reps?.toString().orEmpty()
            FIELD_WEIGHT -> set.weight?.let(Formatters::plainNumber).orEmpty()
            FIELD_DURATION -> set.durationSeconds?.toString().orEmpty()
            FIELD_DISTANCE -> set.distanceMeters?.let(Formatters::plainNumber).orEmpty()
            FIELD_REST -> set.restAfterSetSeconds?.toString().orEmpty()
            FIELD_NOTES -> set.notes.orEmpty()
            else -> ""
        }
    }

    fun isFieldVisible(
        unit: ExerciseUnit,
        revealedFields: Set<SetInputField>,
        fieldsInUse: Set<SetInputField>,
        field: SetInputField,
    ): Boolean = SetFieldVisibility.isVisible(field, unit, revealedFields, fieldsInUse)

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
        contentState.value.exercises.asSequence().flatMap { it.item.sets.asSequence() }.firstOrNull { it.id == setId }

    private fun currentDetail(): SessionDetail? = contentState.value.detail

    private data class PlanContext(
        val dayNotes: String? = null,
        val notesByExerciseId: Map<Long, String> = emptyMap(),
    )

    private data class LoggerContent(
        val detail: SessionDetail,
        val dayNotes: String?,
        val exercises: List<ActiveExerciseUi>,
        val drafts: Map<String, String>,
        val lastCompletedAt: Long?,
    )

    private data class ActiveWorkoutPartial(
        val detail: SessionDetail?,
        val drafts: Map<String, String>,
        val revealed: Map<Long, Set<SetInputField>>,
        val units: Map<Long, ExerciseUnit>,
        val defaultWeightUnit: ExerciseUnit,
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

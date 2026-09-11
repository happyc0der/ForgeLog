package dev.happyc0der.forgelog.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.workout.PreviousPerformance
import dev.happyc0der.forgelog.domain.workout.Progression
import dev.happyc0der.forgelog.domain.workout.asWeightUnit
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.format.relativeDate
import dev.happyc0der.forgelog.ui.components.ConfirmDialog
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.components.DragHandle
import dev.happyc0der.forgelog.ui.components.ReorderableColumn
import dev.happyc0der.forgelog.ui.input.ClearFocusWhenKeyboardHides
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.util.label
import dev.happyc0der.forgelog.ui.format.currentZone
import dev.happyc0der.forgelog.ui.format.today

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartWorkoutScreen(
    onBack: () -> Unit,
    onPickFromLibrary: () -> Unit,
    onStarted: (Long) -> Unit,
    viewModel: StartWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Its duration and rest fields read "1m 30s" only once they lose focus.
    ClearFocusWhenKeyboardHides()
    var alreadyRunning by remember { mutableStateOf<StartWorkoutEvent.AlreadyInProgress?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StartWorkoutEvent.Message -> snackbarHostState.showSnackbar(event.value)
                is StartWorkoutEvent.Started -> onStarted(event.sessionId)
                is StartWorkoutEvent.AlreadyInProgress -> alreadyRunning = event
            }
        }
    }

    alreadyRunning?.let { running ->
        ConfirmDialog(
            title = stringResource(R.string.workout_in_progress_title),
            message = stringResource(R.string.workout_in_progress_message, running.sessionName),
            confirmLabel = stringResource(R.string.workout_in_progress_resume),
            onConfirm = {
                alreadyRunning = null
                onStarted(running.sessionId)
            },
            onDismiss = { alreadyRunning = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.dayName?.let { day ->
                            listOfNotNull(uiState.programName, day).joinToString(" · ")
                        } ?: stringResource(
                            if (uiState.isAdHoc) {
                                R.string.workout_adhoc_name
                            } else {
                                R.string.workout_start_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            // Hidden while the keyboard is up, where it would float over the target fields being
            // typed into and is not what anyone is reaching for anyway.
            val showFab = !uiState.needsDaySelection && !uiState.isLoading && !WindowInsets.isImeVisible
            AnimatedVisibility(visible = showFab, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(onClick = onPickFromLibrary) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.workout_add_exercise),
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage ?: stringResource(R.string.state_error_generic),
                onRetry = onBack,
                modifier = Modifier.padding(innerPadding),
            )
            uiState.needsDaySelection && uiState.dayChoices.isEmpty() -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.workout_pick_day_empty_title),
                message = stringResource(R.string.workout_pick_day_empty_message),
                modifier = Modifier.padding(innerPadding),
            )
            uiState.needsDaySelection -> DayPickerList(
                choices = uiState.dayChoices,
                onSelect = viewModel::selectDay,
                modifier = Modifier.padding(innerPadding),
            )
            uiState.roster.isEmpty() -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(
                    if (uiState.isAdHoc) {
                        R.string.workout_adhoc_empty_title
                    } else {
                        R.string.workout_roster_empty_title
                    },
                ),
                message = stringResource(
                    if (uiState.isAdHoc) {
                        R.string.workout_adhoc_empty_message
                    } else {
                        R.string.workout_roster_empty_message
                    },
                ),
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TestTags.START_WORKOUT_SCREEN)
                        .padding(innerPadding)
                        // Before verticalScroll: it must shrink the viewport, not the content.
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        // Clearance for the floating action button, which sits over this content.
                        .padding(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.dayNotes?.let { notes -> DayNotesCard(notes = notes, collapsible = false) }
                    ReorderableColumn(
                        items = uiState.roster,
                        key = { it.localId },
                        onMove = viewModel::moveExercise,
                        // Legitimately empty here: the planner's roster is in-memory for this
                        // session only, so there is nothing to persist on drop.
                        onDragEnd = {},
                        moveUpLabel = stringResource(R.string.action_move_up),
                        moveDownLabel = stringResource(R.string.action_move_down),
                    ) { item, dragModifier ->
                        PlannedExerciseCard(
                            item = item,
                            dragModifier = dragModifier,
                            weightUnit = uiState.weightUnit,
                            onTargetChange = { field, value ->
                                viewModel.setTarget(item.localId, field, value)
                            },
                            onUseWeight = { weight -> viewModel.applyProgression(item.localId, weight) },
                            onSkip = { viewModel.skipExercise(item.localId) },
                        )
                    }
                    Button(
                        onClick = viewModel::confirmStart,
                        enabled = uiState.canConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.START_WORKOUT_CONFIRM),
                    ) {
                        Text(text = stringResource(R.string.workout_confirm_start))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPickerList(
    choices: List<DayChoice>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.workout_pick_day_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.workout_pick_day_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        choices.forEach { choice ->
            ListItem(
                headlineContent = { Text(text = choice.day.name) },
                supportingContent = {
                    Text(
                        text = if (choice.exerciseCount == 1) {
                            "${choice.program.name} · " +
                                stringResource(R.string.program_day_exercise_count_one)
                        } else {
                            "${choice.program.name} · " +
                                stringResource(R.string.program_day_exercise_count, choice.exerciseCount)
                        },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(choice.day.id) },
            )
        }
    }
}

@Composable
private fun PlannedExerciseCard(
    item: PlannedExerciseItem,
    dragModifier: Modifier,
    weightUnit: ExerciseUnit,
    onTargetChange: (TargetField, Double?) -> Unit,
    onUseWeight: (Double) -> Unit,
    onSkip: () -> Unit,
) {
    val liftWeightUnit = item.exercise.defaultUnit.asWeightUnit(fallback = weightUnit)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                DragHandle(dragModifier = dragModifier) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.action_drag_handle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${item.exercise.category.label()} · ${item.exercise.defaultUnit.label()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item.pointersOverride?.takeIf { it.isNotBlank() }?.let { pointers ->
                Text(
                    text = pointers,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PlanNotesBlock(notes = item.planNotes)
            TargetSection(
                item = item,
                // The exercise's own unit for a loaded lift, as the logger uses. The planner used
                // the default for every lift, so a kg exercise's target read "@ 100 lb" here.
                weightUnit = liftWeightUnit,
                onTargetChange = onTargetChange,
            )
            Progression.hint(
                targets = item,
                lastSets = item.previous?.exercise?.sets.orEmpty(),
                category = item.exercise.category,
                unit = liftWeightUnit,
            )?.let { hint -> ProgressionHintBlock(hint = hint, onUse = onUseWeight) }
            PreviousSessionPanel(previous = item.previous)
            OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.workout_skip_exercise))
            }
        }
    }
}

/**
 * What happened last time this exercise was trained.
 *
 * Three things this has to get right, all of which it previously got wrong:
 * the date is shown, because "Previous session" alone cannot distinguish last Tuesday from last
 * March; it is labelled as a record rather than a prescription, so a heavier number does not read as
 * an instruction; and only completed sets appear, because a set row that was added and never ticked
 * did not happen.
 */
@Composable
fun PreviousSessionPanel(
    previous: PreviousPerformance?,
) {
    val zone = currentZone()
    val completedSets = previous?.completedSets.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = previous?.let { performance ->
                    // Dated by its start, as History dates it.
                    stringResource(
                        R.string.workout_previous_title_dated,
                        relativeDate(performance.session.startedAt, today(zone), zone),
                    )
                } ?: stringResource(R.string.workout_previous_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            // No sec/min toggle here any more. It was bound to the app-wide duration setting, so
            // switching how last session *read* silently changed how every duration is *typed*;
            // and the lines below are now written as "1m 16s", which needs no unit chosen.
        }
        if (previous == null || completedSets.isEmpty()) {
            Text(
                text = stringResource(R.string.workout_previous_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Text(
            text = stringResource(R.string.workout_previous_disclaimer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        completedSets.forEach { set ->
            Text(
                text = stringResource(
                    R.string.workout_previous_set,
                    set.setNumber,
                    previousSetSummary(set),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            set.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        previous.exercise.exercise.feeling?.let { feeling ->
            Text(
                text = stringResource(R.string.workout_previous_feeling, feeling),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        previous.exercise.exercise.exerciseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun previousSetSummary(set: SetLog): String {
    val parts = buildList {
        add(set.setType.label())
        set.reps?.let { add(stringResource(R.string.workout_reps_short, it)) }
        // Formatters.weight, not "$it": a Double renders 500 as "500.0".
        set.weight?.let { add(Formatters.weight(it, set.weightUnit)) }
        // Durations and rest as "1m 16s". In minutes mode these used to render as decimal minutes,
        // so 76 seconds read "1.27" -- which looks like 1:27 and means 1:16.
        set.durationSeconds?.let { add(Formatters.seconds(it)) }
        set.distanceMeters?.let { add(Formatters.distanceMeters(it)) }
        set.restAfterSetSeconds?.let { rest ->
            add(stringResource(R.string.workout_previous_rest, Formatters.seconds(rest)))
        }
        set.rpe?.let { add("RPE $it") }
        set.rir?.let { add("RIR $it") }
    }
    return parts.joinToString(" · ")
}

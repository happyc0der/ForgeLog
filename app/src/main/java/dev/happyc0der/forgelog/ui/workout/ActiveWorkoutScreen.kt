package dev.happyc0der.forgelog.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.library.HowToUrl
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import dev.happyc0der.forgelog.domain.workout.SetInputField
import dev.happyc0der.forgelog.domain.workout.SetsLeft
import dev.happyc0der.forgelog.ui.components.ConfirmDialog
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.FeelingRow
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.components.NotesField
import dev.happyc0der.forgelog.ui.exercise.EnumDropdown
import dev.happyc0der.forgelog.ui.input.ClearFocusWhenKeyboardHides
import dev.happyc0der.forgelog.ui.input.DurationSecondsField
import dev.happyc0der.forgelog.ui.input.SetEntryTextField
import dev.happyc0der.forgelog.ui.input.rememberDurationInputUnit
import dev.happyc0der.forgelog.ui.input.rememberRestInputUnit
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.util.label
import dev.happyc0der.forgelog.ui.util.openHowToUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onBack: () -> Unit,
    onLeave: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    // Without the clocks, which only the header lines and the rest bar below read: a tick
    // recomposes those and nothing else.
    val uiState by viewModel.contentState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var abandonConfirm by rememberSaveable { mutableStateOf(false) }
    // Held by id so a rotation does not silently close the confirmation.
    var deletingSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val deletingSet = deletingSetId?.let { id ->
        uiState.exercises.asSequence().flatMap { it.item.sets.asSequence() }
            .firstOrNull { it.id == id }
    }
    val context = LocalContext.current
    val howToMissing = stringResource(R.string.exercise_how_to_missing_app)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val (durationUnit, onDurationUnitChange) = rememberDurationInputUnit()
    val (restUnit, onRestUnitChange) = rememberRestInputUnit()
    ClearFocusWhenKeyboardHides()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ActiveWorkoutEvent.Message -> snackbarHostState.showSnackbar(event.value)
                // Finishing and abandoning are not the same outcome: one earned a summary, the
                // other has nothing to show.
                ActiveWorkoutEvent.Finished -> onFinished()
                ActiveWorkoutEvent.Abandoned -> onLeave()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // Two lines at most: a long day name wrapped to three and pushed the clocks down.
                        Text(
                            text = uiState.detail?.session?.sessionName ?: stringResource(R.string.workout_active_title),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        LoggerClockLines(viewModel)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { abandonConfirm = true }) {
                        Text(text = stringResource(R.string.workout_abandon))
                    }
                    TextButton(
                        onClick = viewModel::requestFinish,
                        modifier = Modifier.testTag(TestTags.ACTIVE_WORKOUT_FINISH),
                    ) {
                        Text(text = stringResource(R.string.workout_finish))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            val clock by viewModel.clock.collectAsStateWithLifecycle()
            RestTimerBar(
                state = clock.restTimer,
                onPause = viewModel::pauseRestTimer,
                onResume = viewModel::resumeRestTimer,
                onAdjust = viewModel::adjustRestTimer,
                onSkip = viewModel::skipRestTimer,
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            // Only when there is genuinely nothing to render. A recoverable failure carries an
            // errorMessage alongside a usable log, and belongs in a snackbar rather than replacing
            // a workout in progress with an error page.
            uiState.detail == null -> ErrorState(
                message = uiState.errorMessage ?: stringResource(R.string.workout_session_missing),
                onRetry = onBack,
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TestTags.ACTIVE_WORKOUT_SCREEN)
                        .padding(innerPadding)
                        .imePadding()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.dayNotes?.let { notes -> DayNotesCard(notes = notes, collapsible = true) }
                    uiState.exercises.forEach { exerciseUi ->
                        ExerciseLoggerCard(
                            exerciseUi = exerciseUi,
                            viewModel = viewModel,
                            durationUnit = durationUnit,
                            restUnit = restUnit,
                            onRestUnitChange = onRestUnitChange,
                            onDurationUnitChange = onDurationUnitChange,
                            onDeleteSet = { deletingSetId = it.id },
                            onExpand = { viewModel.expand(exerciseUi.item.exercise.id) },
                            onOpenHowTo = { url ->
                                val normalized = HowToUrl.normalize(url).getOrNull()
                                if (normalized == null || !context.openHowToUrl(normalized)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(howToMissing)
                                    }
                                }
                            },
                        )
                    }
                    SessionFeedbackCard(
                        feeling = uiState.detail?.session?.overallFeeling,
                        notes = uiState.detail?.session?.overallNotes.orEmpty(),
                        onFeelingChange = viewModel::onOverallFeeling,
                        onNotesChange = viewModel::onOverallNotes,
                    )
                }
            }
        }
    }

    deletingSet?.let { set ->
        ConfirmDialog(
            title = stringResource(R.string.workout_delete_set_title),
            message = stringResource(R.string.workout_delete_set_message, set.setNumber),
            confirmLabel = stringResource(R.string.workout_delete_set),
            onConfirm = {
                viewModel.deleteSet(set.id)
                deletingSetId = null
            },
            onDismiss = { deletingSetId = null },
        )
    }

    val finishPrompt by viewModel.finishPrompt.collectAsStateWithLifecycle()
    finishPrompt?.let { left ->
        ConfirmDialog(
            title = stringResource(R.string.workout_finish_title),
            message = finishPromptMessage(left),
            confirmLabel = stringResource(R.string.workout_finish),
            dismissLabel = stringResource(R.string.workout_finish_keep_going),
            onConfirm = viewModel::confirmFinish,
            onDismiss = viewModel::dismissFinishPrompt,
            confirmTestTag = TestTags.ACTIVE_WORKOUT_FINISH_CONFIRM,
        )
    }

    if (abandonConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.workout_abandon_title),
            message = stringResource(R.string.workout_abandon_message),
            confirmLabel = stringResource(R.string.workout_abandon),
            onConfirm = {
                abandonConfirm = false
                viewModel.abandon()
            },
            onDismiss = { abandonConfirm = false },
        )
    }
}

/**
 * "3 sets aren't done yet:", a line per exercise, and -- when some were added and not ticked --
 * what happens to those. At most [FINISH_PROMPT_MAX_LINES] exercises are named, so an untouched
 * nine-lift day still fits.
 */
@Composable
private fun finishPromptMessage(left: List<SetsLeft>): String {
    val total = left.sumOf { it.count }
    val lines = left.take(FINISH_PROMPT_MAX_LINES).map { exercise ->
        stringResource(
            R.string.workout_finish_left_line,
            exercise.exerciseName,
            pluralStringResource(R.plurals.workout_set_count, exercise.count, exercise.count),
        )
    }
    val more = left.size - lines.size
    return buildString {
        append(pluralStringResource(R.plurals.workout_finish_sets_left, total, total))
        lines.forEach { append("\n").append(it) }
        if (more > 0) append("\n").append(pluralStringResource(R.plurals.workout_finish_more_exercises, more, more))
        if (left.any { it.unticked > 0 }) {
            append("\n\n").append(stringResource(R.string.workout_finish_left_note))
        }
    }
}

private const val FINISH_PROMPT_MAX_LINES = 4

/** Session time and time since the last set: the header's two ticking lines, and all that ticks. */
@Composable
private fun LoggerClockLines(viewModel: ActiveWorkoutViewModel) {
    val clock by viewModel.clock.collectAsStateWithLifecycle()
    Text(
        text = stringResource(R.string.workout_session_timer, clock.sessionElapsedLabel),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
    )
    Text(
        text = clock.sinceLastSetLabel?.let { label ->
            stringResource(R.string.workout_since_last_set, label)
        } ?: stringResource(R.string.workout_since_last_set_none),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ExerciseLoggerCard(
    exerciseUi: ActiveExerciseUi,
    viewModel: ActiveWorkoutViewModel,
    durationUnit: DurationInputUnit,
    onDurationUnitChange: (DurationInputUnit) -> Unit,
    restUnit: DurationInputUnit,
    onRestUnitChange: (DurationInputUnit) -> Unit,
    onDeleteSet: (SetLog) -> Unit,
    onExpand: () -> Unit,
    onOpenHowTo: (String) -> Unit,
) {
    val exercise = exerciseUi.item.exercise
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!exerciseUi.expanded) Modifier.clickable(onClick = onExpand) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (exerciseUi.expanded) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = exercise.displayNameSnapshot,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (exerciseUi.expanded) {
                    exercise.howToUrlSnapshot?.let { url ->
                        IconButton(onClick = { onOpenHowTo(url) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.action_open_how_to),
                            )
                        }
                    }
                }
            }
            targetSummary(exercise, exerciseUi.targetWeightUnit)?.let { targets ->
                Text(
                    text = stringResource(R.string.workout_exercise_target, targets),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            exerciseUi.progression?.let { hint -> ProgressionHintBlock(hint = hint) }
            if (!exerciseUi.expanded) {
                val planned = exercise.plannedSets
                val done = exerciseUi.item.sets.count { it.completed }
                Text(
                    // This counts sets, not exercises. It used to borrow the day builder's
                    // "%1$d exercises" string, so an untouched lift read "0 exercises".
                    // Against the plan when there is one -- "2 of 3 sets done" -- since what is
                    // left is the thing worth knowing between lifts.
                    text = if (planned != null && planned > 0) {
                        pluralStringResource(R.plurals.workout_sets_done_of_planned, planned, done, planned)
                    } else {
                        pluralStringResource(
                            R.plurals.workout_set_count,
                            exerciseUi.item.sets.size,
                            exerciseUi.item.sets.size,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                exercise.pointersSnapshot?.takeIf { it.isNotBlank() }?.let { pointers ->
                    Text(
                        text = stringResource(R.string.workout_pointers),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(text = pointers, style = MaterialTheme.typography.bodyMedium)
                }
                PlanNotesBlock(notes = exerciseUi.planNotes)
                PreviousSessionPanel(previous = exerciseUi.previous)
                SetEntryTextField(
                    value = exerciseUi.notesDraft,
                    onValueChange = { viewModel.onExerciseNotes(exercise.id, it) },
                    label = stringResource(R.string.workout_exercise_notes),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Text,
                    singleLine = false,
                    minLines = 2,
                )
                val addSetRow = remember { BringIntoViewRequester() }
                val newSetHeader = remember { BringIntoViewRequester() }
                val lastSetId = exerciseUi.item.sets.lastOrNull()?.id
                exerciseUi.item.sets.forEach { set ->
                    key(set.id) {
                        SetRow(
                            headerRequester = newSetHeader.takeIf { set.id == lastSetId },
                            set = set,
                            unit = exerciseUi.unit,
                            revealed = exerciseUi.revealedFields,
                            inUse = exerciseUi.fieldsInUse,
                            viewModel = viewModel,
                            durationUnit = durationUnit,
                            restUnit = restUnit,
                            onRestUnitChange = onRestUnitChange,
                            onDurationUnitChange = onDurationUnitChange,
                            onDeleteSet = onDeleteSet,
                        )
                    }
                }
                val moreRevealed = exerciseUi.revealedFields.isNotEmpty()
                TextButton(onClick = { viewModel.toggleMoreFields(exercise.id) }) {
                    Text(
                        text = stringResource(
                            if (moreRevealed) R.string.workout_hide_fields else R.string.workout_more_fields,
                        ),
                    )
                }
                /*
                 * A new set pushes this row down by a whole set's height, so it used to fall off the
                 * bottom of the screen on every add, and each set meant scrolling to find it again.
                 * When a set is added, the row is brought back into view -- which, in portrait,
                 * shows the new set above it too. Then the new set's own header is: in landscape a
                 * set is taller than the screen, and the button alone left the set just added, and
                 * its Done, scrolled off the top. Where both cannot fit, the set wins. Not when a
                 * set is deleted, nor when the card is opened.
                 */
                val setCount = exerciseUi.item.sets.size
                var shownSetCount by remember { mutableIntStateOf(setCount) }
                LaunchedEffect(setCount) {
                    if (setCount > shownSetCount) {
                        // After the new row has been laid out, so there is a position to go to.
                        withFrameNanos { }
                        addSetRow.bringIntoView()
                        newSetHeader.bringIntoView()
                    }
                    shownSetCount = setCount
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(addSetRow),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { viewModel.addSet(exercise.id) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TestTags.ACTIVE_WORKOUT_ADD_SET),
                    ) {
                        Text(text = stringResource(R.string.workout_add_set))
                    }
                    // Rest that is not triggered by ticking a set: between exercises, or after a
                    // set logged a while ago.
                    OutlinedButton(onClick = { viewModel.startRestTimer(exercise.id) }) {
                        Text(text = stringResource(R.string.workout_start_rest))
                    }
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    /** Set on the newest set only, so adding one can bring its header into view. */
    headerRequester: BringIntoViewRequester?,
    set: SetLog,
    unit: ExerciseUnit,
    revealed: Set<SetInputField>,
    inUse: Set<SetInputField>,
    viewModel: ActiveWorkoutViewModel,
    durationUnit: DurationInputUnit,
    onDurationUnitChange: (DurationInputUnit) -> Unit,
    restUnit: DurationInputUnit,
    onRestUnitChange: (DurationInputUnit) -> Unit,
    onDeleteSet: (SetLog) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(headerRequester?.let { Modifier.bringIntoViewRequester(it) } ?: Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.workout_set_number, set.setNumber),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            /*
             * The checkbox and its label are one target. Only the 48 dp box used to respond, so a
             * tap on the word "Done" -- the bigger, more obvious thing to hit between sets --
             * silently did nothing, and the set, its rest and the timer were never started.
             */
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = set.completed,
                        role = Role.Checkbox,
                        onValueChange = { viewModel.onSetCompleted(set, it) },
                    )
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = set.completed, onCheckedChange = null)
                Text(
                    text = stringResource(R.string.workout_set_completed),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            IconButton(onClick = { onDeleteSet(set) }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.workout_delete_set),
                )
            }
        }
        EnumDropdown(
            label = stringResource(R.string.workout_set_type),
            selected = set.setType,
            options = SetType.entries,
            optionLabel = { it.label() },
            onSelected = { viewModel.onSetType(set, it) },
        )
        if (viewModel.isFieldVisible(unit, revealed, inUse, SetInputField.REPS)) {
            SetEntryTextField(
                value = viewModel.fieldValue(set, ActiveWorkoutViewModel.FIELD_REPS),
                onValueChange = { viewModel.onSetText(set, ActiveWorkoutViewModel.FIELD_REPS, it) },
                label = stringResource(R.string.workout_field_reps),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
            )
        }
        if (viewModel.isFieldVisible(unit, revealed, inUse, SetInputField.WEIGHT)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                SetEntryTextField(
                    value = viewModel.fieldValue(set, ActiveWorkoutViewModel.FIELD_WEIGHT),
                    onValueChange = { viewModel.onSetText(set, ActiveWorkoutViewModel.FIELD_WEIGHT, it) },
                    label = stringResource(R.string.workout_field_weight),
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Decimal,
                )
                EnumDropdown(
                    // This set's unit. It borrowed the exercise form's "Default unit".
                    label = stringResource(R.string.session_detail_field_unit),
                    selected = set.weightUnit,
                    options = listOf(ExerciseUnit.LB, ExerciseUnit.KG),
                    optionLabel = { it.label() },
                    onSelected = { viewModel.onSetUnit(set, it) },
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        if (viewModel.isFieldVisible(unit, revealed, inUse, SetInputField.DURATION)) {
            DurationSecondsField(
                secondsText = viewModel.fieldValue(set, ActiveWorkoutViewModel.FIELD_DURATION),
                onSecondsTextChange = {
                    viewModel.onSetText(set, ActiveWorkoutViewModel.FIELD_DURATION, it)
                },
                label = stringResource(R.string.workout_field_duration),
                unit = durationUnit,
                onUnitChange = onDurationUnitChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (viewModel.isFieldVisible(unit, revealed, inUse, SetInputField.DISTANCE)) {
            SetEntryTextField(
                value = viewModel.fieldValue(set, ActiveWorkoutViewModel.FIELD_DISTANCE),
                onValueChange = { viewModel.onSetText(set, ActiveWorkoutViewModel.FIELD_DISTANCE, it) },
                label = stringResource(R.string.workout_field_distance),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
            )
        }
        DurationSecondsField(
            secondsText = viewModel.fieldValue(set, ActiveWorkoutViewModel.FIELD_REST),
            onSecondsTextChange = {
                viewModel.onSetText(set, ActiveWorkoutViewModel.FIELD_REST, it)
            },
            label = stringResource(R.string.workout_field_rest),
            unit = restUnit,
            onUnitChange = onRestUnitChange,
            modifier = Modifier.fillMaxWidth(),
        )
        if (revealed.isNotEmpty()) {
            NullableIntDropdown(
                label = stringResource(R.string.workout_field_rpe),
                selected = set.rpe,
                options = listOf(null) + (1..10).toList(),
                onSelected = { viewModel.onSetRpe(set, it) },
            )
            NullableIntDropdown(
                label = stringResource(R.string.workout_field_rir),
                selected = set.rir,
                options = listOf(null) + (0..10).toList(),
                onSelected = { viewModel.onSetRir(set, it) },
            )
            SetEntryTextField(
                value = viewModel.fieldValue(set, ActiveWorkoutViewModel.FIELD_NOTES),
                onValueChange = { viewModel.onSetText(set, ActiveWorkoutViewModel.FIELD_NOTES, it) },
                label = stringResource(R.string.workout_field_notes),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Text,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun NullableIntDropdown(
    label: String,
    selected: Int?,
    options: List<Int?>,
    onSelected: (Int?) -> Unit,
) {
    EnumDropdown(
        label = label,
        selected = selected,
        options = options,
        optionLabel = { it?.toString() ?: "—" },
        onSelected = onSelected,
    )
}

/**
 * How the whole session went, recorded while it is happening rather than remembered afterwards
 * from the history screen.
 */
@Composable
private fun SessionFeedbackCard(
    feeling: Int?,
    notes: String,
    onFeelingChange: (Int?) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.workout_session_feedback),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.session_detail_feeling),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FeelingRow(feeling = feeling, onChange = onFeelingChange)
            NotesField(
                label = stringResource(R.string.session_detail_overall_notes),
                initial = notes,
                onCommit = onNotesChange,
            )
        }
    }
}

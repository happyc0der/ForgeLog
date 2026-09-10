package dev.happyc0der.forgelog.ui.programs

import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.domain.library.HowToUrl
import dev.happyc0der.forgelog.domain.model.ProgramExercise
import dev.happyc0der.forgelog.domain.model.ProgramExerciseDetail
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import dev.happyc0der.forgelog.ui.components.ConfirmDialog
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.components.DragHandle
import dev.happyc0der.forgelog.ui.components.ReorderableColumn
import dev.happyc0der.forgelog.ui.exercise.ExerciseForm
import dev.happyc0der.forgelog.ui.exercise.ExerciseFormState
import dev.happyc0der.forgelog.ui.exercise.ExerciseFormStateSaver
import dev.happyc0der.forgelog.ui.input.DurationSecondsField
import dev.happyc0der.forgelog.ui.input.rememberDurationInputUnit
import dev.happyc0der.forgelog.ui.util.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDayBuilderScreen(
    onBack: () -> Unit,
    onPickFromLibrary: () -> Unit,
    onDayDuplicated: (Long) -> Unit,
    onStartDay: (Long) -> Unit,
    viewModel: ProgramDayBuilderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var addChoiceExpanded by rememberSaveable { mutableStateOf(false) }
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<ProgramExerciseDetail?>(null) }
    val nameRequired = stringResource(R.string.exercise_name_required)
    val urlInvalid = stringResource(R.string.exercise_how_to_invalid)
    val (durationUnit, onDurationUnitChange) = rememberDurationInputUnit()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProgramDayBuilderEvent.Message -> snackbarHostState.showSnackbar(event.value)
                is ProgramDayBuilderEvent.Duplicated -> onDayDuplicated(event.dayId)
            }
        }
    }

    val detail = uiState.detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = detail?.day?.name ?: stringResource(R.string.day_builder_title))
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
                    if (detail != null && detail.exercises.isNotEmpty()) {
                        TextButton(onClick = { onStartDay(detail.day.id) }) {
                            Text(text = stringResource(R.string.workout_start_this_day))
                        }
                    }
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                        )
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.action_duplicate)) },
                            onClick = {
                                showAddMenu = false
                                viewModel.duplicateDay()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { addChoiceExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.day_builder_add_exercise),
                )
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
            detail == null -> ErrorState(
                message = stringResource(R.string.program_day_missing),
                onRetry = onBack,
                modifier = Modifier.padding(innerPadding),
            )
            detail.exercises.isEmpty() -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.day_builder_empty_title),
                message = stringResource(R.string.day_builder_empty_message),
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                            .testTag(TestTags.PROGRAM_DAY_BUILDER_SCREEN)
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DayNotesField(
                        initial = detail.day.notes.orEmpty(),
                        onCommit = viewModel::setDayNotes,
                    )
                    ReorderableColumn(
                        items = uiState.exercises,
                        key = { it.programExercise.id },
                        onMove = viewModel::moveExercise,
                        onDragEnd = viewModel::persistExerciseOrder,
                        moveUpLabel = stringResource(R.string.action_move_up),
                        moveDownLabel = stringResource(R.string.action_move_down),
                    ) { item, dragModifier ->
                        ProgramExerciseCard(
                            item = item,
                            dragModifier = dragModifier,
                            durationUnit = durationUnit,
                            onDurationUnitChange = onDurationUnitChange,
                            onSave = viewModel::saveProgramExercise,
                            onRemove = { pendingRemove = item },
                        )
                    }
                }
            }
        }
    }

    if (addChoiceExpanded) {
        ModalBottomSheet(
            onDismissRequest = { addChoiceExpanded = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.day_builder_add_exercise),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        addChoiceExpanded = false
                        onPickFromLibrary()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.action_add_from_library))
                }
                OutlinedButton(
                    onClick = {
                        addChoiceExpanded = false
                        showCreateSheet = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.action_add_new_exercise))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showCreateSheet) {
        var form by rememberSaveable(stateSaver = ExerciseFormStateSaver) { mutableStateOf(ExerciseFormState()) }
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.exercise_editor_create_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExerciseForm(
                    state = form,
                    onNameChange = { form = form.copy(name = it, nameError = null) },
                    onCategoryChange = { form = form.copy(category = it) },
                    onUnitChange = { form = form.copy(defaultUnit = it) },
                    onHowToUrlChange = { form = form.copy(howToUrl = it, howToUrlError = null) },
                    onPointersChange = { form = form.copy(defaultPointers = it) },
                )
                Button(
                    onClick = {
                        if (form.name.isBlank()) {
                            form = form.copy(nameError = nameRequired)
                            return@Button
                        }
                        val urlResult = HowToUrl.normalize(form.howToUrl)
                        if (urlResult.isFailure) {
                            form = form.copy(howToUrlError = urlInvalid)
                            return@Button
                        }
                        if (viewModel.createExerciseAndAdd(form)) {
                            showCreateSheet = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        }
    }

    pendingRemove?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.day_builder_remove_title),
            message = stringResource(R.string.day_builder_remove_message, item.exercise.name),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                viewModel.removeProgramExercise(item.programExercise.id)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null },
        )
    }
}

@Composable
private fun ProgramExerciseCard(
    item: ProgramExerciseDetail,
    dragModifier: Modifier,
    durationUnit: DurationInputUnit,
    onDurationUnitChange: (DurationInputUnit) -> Unit,
    onSave: (ProgramExercise) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by rememberSaveable(item.programExercise.id) { mutableStateOf(false) }
    var plannedSets by rememberSaveable(item.programExercise.id) {
        mutableStateOf(item.programExercise.plannedSets?.toString().orEmpty())
    }
    var repMin by rememberSaveable(item.programExercise.id) {
        mutableStateOf(item.programExercise.targetRepMin?.toString().orEmpty())
    }
    var repMax by rememberSaveable(item.programExercise.id) {
        mutableStateOf(item.programExercise.targetRepMax?.toString().orEmpty())
    }
    var weight by rememberSaveable(item.programExercise.id) {
        mutableStateOf(item.programExercise.targetWeight?.toString().orEmpty())
    }
    var duration by rememberSaveable(item.programExercise.id) {
        mutableStateOf(item.programExercise.targetDurationSeconds?.toString().orEmpty())
    }
    var rest by rememberSaveable(item.programExercise.id) {
        mutableStateOf(item.programExercise.targetRestSeconds?.toString().orEmpty())
    }
    var pointers by rememberSaveable(item.programExercise.id) {
        mutableStateOf(item.programExercise.defaultPointersOverride.orEmpty())
    }
    var notes by rememberSaveable(item.programExercise.id) {
        mutableStateOf(item.programExercise.notes.orEmpty())
    }
    var error by rememberSaveable(item.programExercise.id) { mutableStateOf<String?>(null) }
    val numberInvalid = stringResource(R.string.program_exercise_number_invalid)
    val rangeInvalid = stringResource(R.string.program_exercise_rep_range_invalid)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        text = "${item.exercise.category.label()} • ${item.exercise.defaultUnit.label()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(text = if (expanded) stringResource(R.string.action_done) else stringResource(R.string.action_edit))
                }
                OutlinedButton(onClick = onRemove) {
                    Text(text = stringResource(R.string.action_remove))
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                NumberField(R.string.program_exercise_planned_sets, plannedSets) { plannedSets = it; error = null }
                NumberField(R.string.program_exercise_rep_min, repMin) { repMin = it; error = null }
                NumberField(R.string.program_exercise_rep_max, repMax) { repMax = it; error = null }
                NumberField(R.string.program_exercise_target_weight, weight, decimal = true) { weight = it; error = null }
                DurationSecondsField(
                    secondsText = duration,
                    onSecondsTextChange = { duration = it; error = null },
                    label = stringResource(R.string.program_exercise_target_duration),
                    unit = durationUnit,
                    onUnitChange = onDurationUnitChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                DurationSecondsField(
                    secondsText = rest,
                    onSecondsTextChange = { rest = it; error = null },
                    label = stringResource(R.string.program_exercise_target_rest),
                    unit = durationUnit,
                    onUnitChange = onDurationUnitChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = pointers,
                    onValueChange = { pointers = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.program_exercise_pointers)) },
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.program_exercise_notes)) },
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Button(
                    onClick = {
                        val planned = parseOptionalInt(plannedSets)
                        val min = parseOptionalInt(repMin)
                        val max = parseOptionalInt(repMax)
                        val targetWeight = parseOptionalDouble(weight)
                        val targetDuration = parseOptionalInt(duration)
                        val targetRest = parseOptionalInt(rest)
                        if (listOf(planned, min, max, targetDuration, targetRest).any { it.isFailure } ||
                            targetWeight.isFailure
                        ) {
                            error = numberInvalid
                            return@Button
                        }
                        val minVal = min.getOrNull()
                        val maxVal = max.getOrNull()
                        if (minVal != null && maxVal != null && maxVal < minVal) {
                            error = rangeInvalid
                            return@Button
                        }
                        onSave(
                            item.programExercise.copy(
                                plannedSets = planned.getOrNull(),
                                targetRepMin = minVal,
                                targetRepMax = maxVal,
                                targetWeight = targetWeight.getOrNull(),
                                targetDurationSeconds = targetDuration.getOrNull(),
                                targetRestSeconds = targetRest.getOrNull(),
                                defaultPointersOverride = pointers.trim().ifBlank { null },
                                notes = notes.trim().ifBlank { null },
                            ),
                        )
                        expanded = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    labelRes: Int,
    value: String,
    decimal: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        label = { Text(text = stringResource(labelRes)) },
        singleLine = true,
        // These fields only ever take numbers, and the full keyboard made entering a target weight
        // needlessly fiddly mid-setup.
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
    )
}

/**
 * Notes for the whole day, committed on Save rather than per keystroke so a long note is one write.
 */
@Composable
private fun DayNotesField(
    initial: String,
    onCommit: (String) -> Unit,
) {
    var draft by rememberSaveable(initial) { mutableStateOf(initial) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.program_day_notes)) },
            minLines = 2,
        )
        if (draft != initial) {
            TextButton(onClick = { onCommit(draft) }) {
                Text(text = stringResource(R.string.action_save))
            }
        }
    }
}

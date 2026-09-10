package com.example.forgelog.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.forgelog.R
import com.example.forgelog.domain.library.HowToUrl
import com.example.forgelog.domain.model.ExerciseUnit
import com.example.forgelog.domain.model.SetLog
import com.example.forgelog.domain.model.SetType
import com.example.forgelog.domain.workout.DurationInputUnit
import com.example.forgelog.domain.workout.SetInputField
import com.example.forgelog.ui.components.ConfirmDialog
import com.example.forgelog.ui.components.ErrorState
import com.example.forgelog.ui.components.LoadingState
import com.example.forgelog.ui.exercise.EnumDropdown
import com.example.forgelog.ui.input.DurationSecondsField
import com.example.forgelog.ui.input.SetEntryTextField
import com.example.forgelog.ui.input.rememberDurationInputUnit
import com.example.forgelog.ui.util.label
import com.example.forgelog.ui.util.openHowToUrl
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onBack: () -> Unit,
    onLeave: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var abandonConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val howToMissing = stringResource(R.string.exercise_how_to_missing_app)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val (durationUnit, onDurationUnitChange) = rememberDurationInputUnit()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ActiveWorkoutEvent.Message -> snackbarHostState.showSnackbar(event.value)
                ActiveWorkoutEvent.Finished,
                ActiveWorkoutEvent.Abandoned,
                -> onLeave()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = uiState.detail?.session?.sessionName ?: stringResource(R.string.workout_active_title))
                        Text(
                            text = stringResource(R.string.workout_session_timer, uiState.sessionElapsedLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = uiState.sinceLastSetLabel?.let { label ->
                                stringResource(R.string.workout_since_last_set, label)
                            } ?: stringResource(R.string.workout_since_last_set_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                    TextButton(onClick = viewModel::finish) {
                        Text(text = stringResource(R.string.workout_finish))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.errorMessage != null || uiState.detail == null -> ErrorState(
                message = uiState.errorMessage ?: stringResource(R.string.workout_session_missing),
                onRetry = onBack,
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.exercises.forEach { exerciseUi ->
                        ExerciseLoggerCard(
                            exerciseUi = exerciseUi,
                            viewModel = viewModel,
                            durationUnit = durationUnit,
                            onDurationUnitChange = onDurationUnitChange,
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
                }
            }
        }
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

@Composable
private fun ExerciseLoggerCard(
    exerciseUi: ActiveExerciseUi,
    viewModel: ActiveWorkoutViewModel,
    durationUnit: DurationInputUnit,
    onDurationUnitChange: (DurationInputUnit) -> Unit,
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
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.action_open_how_to),
                            )
                        }
                    }
                }
            }
            if (!exerciseUi.expanded) {
                Text(
                    text = stringResource(
                        R.string.program_day_exercise_count,
                        exerciseUi.item.sets.size,
                    ),
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
                PreviousSessionPanel(
                    previous = exerciseUi.previous,
                    durationUnit = durationUnit,
                    onDurationUnitChange = onDurationUnitChange,
                )
                SetEntryTextField(
                    value = exerciseUi.notesDraft,
                    onValueChange = { viewModel.onExerciseNotes(exercise.id, it) },
                    label = stringResource(R.string.workout_exercise_notes),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Text,
                    singleLine = false,
                    minLines = 2,
                )
                exerciseUi.item.sets.forEach { set ->
                    key(set.id) {
                        SetRow(
                            set = set,
                            unit = exerciseUi.unit,
                            revealed = exerciseUi.revealedFields,
                            viewModel = viewModel,
                            durationUnit = durationUnit,
                            onDurationUnitChange = onDurationUnitChange,
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
                Button(
                    onClick = { viewModel.addSet(exercise.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.workout_add_set))
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    set: SetLog,
    unit: ExerciseUnit,
    revealed: Set<SetInputField>,
    viewModel: ActiveWorkoutViewModel,
    durationUnit: DurationInputUnit,
    onDurationUnitChange: (DurationInputUnit) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.workout_set_number, set.setNumber),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Checkbox(
                checked = set.completed,
                onCheckedChange = { viewModel.onSetCompleted(set, it) },
            )
            Text(text = stringResource(R.string.workout_set_completed))
        }
        EnumDropdown(
            label = stringResource(R.string.workout_set_type),
            selected = set.setType,
            options = SetType.entries,
            optionLabel = { it.label() },
            onSelected = { viewModel.onSetType(set, it) },
        )
        if (viewModel.isFieldVisible(unit, revealed, SetInputField.REPS)) {
            SetEntryTextField(
                value = viewModel.fieldValue(set, ActiveWorkoutViewModel.FIELD_REPS),
                onValueChange = { viewModel.onSetText(set, ActiveWorkoutViewModel.FIELD_REPS, it) },
                label = stringResource(R.string.workout_field_reps),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
            )
        }
        if (viewModel.isFieldVisible(unit, revealed, SetInputField.WEIGHT)) {
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
                    label = stringResource(R.string.exercise_field_unit),
                    selected = set.weightUnit,
                    options = listOf(ExerciseUnit.LB, ExerciseUnit.KG),
                    optionLabel = { it.label() },
                    onSelected = { viewModel.onSetUnit(set, it) },
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        if (viewModel.isFieldVisible(unit, revealed, SetInputField.DURATION)) {
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
        if (viewModel.isFieldVisible(unit, revealed, SetInputField.DISTANCE)) {
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
            unit = durationUnit,
            onUnitChange = onDurationUnitChange,
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

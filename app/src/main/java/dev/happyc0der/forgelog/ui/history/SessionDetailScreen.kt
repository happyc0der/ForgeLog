package dev.happyc0der.forgelog.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SessionExerciseWithSets
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.ui.components.CardHeader
import dev.happyc0der.forgelog.ui.components.ConfirmDialog
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.ForgeCard
import dev.happyc0der.forgelog.ui.components.ForgeHeroCard
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.components.StatGrid
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.util.label
import java.time.LocalDate
import java.time.ZoneId

private val FEELING_RANGE = 1..5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onResumeWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val zone = remember { ZoneId.systemDefault() }
    var editingSet by remember { mutableStateOf<SetLog?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionDetailEvent.Message -> snackbarHostState.showSnackbar(event.value)
                SessionDetailEvent.Deleted -> onBack()
                is SessionDetailEvent.RepeatStarted -> onResumeWorkout(event.sessionId)
            }
        }
    }

    editingSet?.let { set ->
        SetEditorDialog(
            set = set,
            onSave = {
                viewModel.saveSet(it)
                editingSet = null
            },
            onDelete = {
                viewModel.deleteSet(set.id)
                editingSet = null
            },
            onDismiss = { editingSet = null },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            message = stringResource(
                R.string.history_delete_message,
                uiState.detail?.session?.sessionName.orEmpty(),
            ),
            onConfirm = {
                confirmDelete = false
                viewModel.deleteSession()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.detail?.session?.sessionName?.ifBlank { null }
                            ?: stringResource(R.string.session_detail_title),
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
                actions = {
                    if (uiState.detail != null) {
                        TextButton(onClick = viewModel::toggleEditing) {
                            Text(
                                text = stringResource(
                                    if (uiState.isEditing) {
                                        R.string.session_detail_done
                                    } else {
                                        R.string.session_detail_edit
                                    },
                                ),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        val detail = uiState.detail
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage ?: stringResource(R.string.state_error_generic),
                onRetry = onBack,
                modifier = Modifier.padding(innerPadding),
            )
            detail == null -> EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.session_detail_missing),
                message = stringResource(R.string.history_empty_message),
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag(TestTags.SESSION_DETAIL_SCREEN),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") {
                    SessionSummaryCard(
                        uiState = uiState,
                        zone = zone,
                        onRepeat = viewModel::repeatSession,
                        onDelete = { confirmDelete = true },
                    )
                }
                item(key = "feeling") {
                    SessionFeelingCard(
                        feeling = detail.session.overallFeeling,
                        notes = detail.session.overallNotes,
                        isEditing = uiState.isEditing,
                        onFeelingChange = viewModel::setOverallFeeling,
                        onNotesChange = viewModel::setOverallNotes,
                    )
                }
                items(
                    count = detail.exercises.size,
                    key = { index -> detail.exercises[index].exercise.id },
                ) { index ->
                    val logged = detail.exercises[index]
                    ExerciseLogCard(
                        logged = logged,
                        weightUnit = uiState.weightUnit,
                        isEditing = uiState.isEditing,
                        onEditSet = { editingSet = it },
                        onFeelingChange = { viewModel.setExerciseFeeling(logged.exercise.id, it) },
                        onNotesChange = { viewModel.setExerciseNotes(logged.exercise.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryCard(
    uiState: SessionDetailUiState,
    zone: ZoneId,
    onRepeat: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = uiState.summary ?: return
    val session = uiState.detail?.session ?: return
    ForgeHeroCard {
        CardHeader(
            title = Formatters.relativeDate(session.startedAt, LocalDate.now(zone), zone) +
                " · " + Formatters.timeOfDay(session.startedAt, zone),
            trailing = {
                Text(
                    text = stringResource(session.status.historyLabelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            },
        )
        StatGrid(
            stats = listOf(
                stringResource(R.string.home_stat_duration) to
                    Formatters.compactDuration(summary.durationMs),
                stringResource(R.string.home_stat_volume) to
                    summary.loadLb.takeIf { it > 0.0 }
                        ?.let { Formatters.volume(it, uiState.weightUnit) },
                stringResource(R.string.home_stat_sets) to summary.totalSets.toString(),
                stringResource(R.string.home_stat_exercises) to summary.exerciseCount.toString(),
            ),
        )
        Formatters.timedSeconds(summary.volume.totalDurationSeconds)?.let { timed ->
            Text(
                text = stringResource(R.string.home_stat_time) + ": " + timed,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRepeat,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(text = stringResource(R.string.history_repeat))
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SessionFeelingCard(
    feeling: Int?,
    notes: String?,
    isEditing: Boolean,
    onFeelingChange: (Int?) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    // Read-only sessions with nothing recorded have nothing to show, so the card stays out of the way.
    if (!isEditing && feeling == null && notes.isNullOrBlank()) return
    ForgeCard {
        CardHeader(title = stringResource(R.string.session_detail_feeling))
        FeelingRow(feeling = feeling, enabled = isEditing, onChange = onFeelingChange)
        if (isEditing) {
            NotesField(
                label = stringResource(R.string.session_detail_overall_notes),
                initial = notes.orEmpty(),
                onCommit = onNotesChange,
            )
        } else if (!notes.isNullOrBlank()) {
            Text(text = notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FeelingRow(
    feeling: Int?,
    enabled: Boolean,
    onChange: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FEELING_RANGE.forEach { value ->
            FilterChip(
                selected = feeling == value,
                enabled = enabled,
                onClick = { onChange(if (feeling == value) null else value) },
                label = { Text(text = value.toString()) },
            )
        }
        if (feeling == null) {
            Text(
                text = stringResource(R.string.home_value_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Notes are committed on focus loss rather than per keystroke, so a long note is one database write
 * instead of one per character.
 */
@Composable
private fun NotesField(
    label: String,
    initial: String,
    onCommit: (String) -> Unit,
) {
    var draft by rememberSaveable(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(text = label) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = { onCommit(draft) }) {
        Text(text = stringResource(R.string.action_save))
    }
}

@Composable
private fun ExerciseLogCard(
    logged: SessionExerciseWithSets,
    weightUnit: ExerciseUnit,
    isEditing: Boolean,
    onEditSet: (SetLog) -> Unit,
    onFeelingChange: (Int?) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    ForgeCard {
        Text(
            text = logged.exercise.displayNameSnapshot,
            style = MaterialTheme.typography.titleMedium,
        )
        if (logged.sets.isEmpty()) {
            Text(
                text = stringResource(R.string.session_detail_no_sets),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            logged.sets.forEach { set ->
                SetRow(
                    set = set,
                    isEditing = isEditing,
                    onEdit = { onEditSet(set) },
                )
            }
        }
        if (isEditing) {
            CardHeader(title = stringResource(R.string.session_detail_feeling))
            FeelingRow(feeling = logged.exercise.feeling, enabled = true, onChange = onFeelingChange)
            NotesField(
                label = stringResource(R.string.session_detail_exercise_notes),
                initial = logged.exercise.exerciseNotes.orEmpty(),
                onCommit = onNotesChange,
            )
        } else {
            logged.exercise.feeling?.let { feeling ->
                Text(
                    text = stringResource(R.string.session_detail_feeling) + " " +
                        stringResource(R.string.home_feeling_value, feeling),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            logged.exercise.exerciseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(text = notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SetRow(
    set: SetLog,
    isEditing: Boolean,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isEditing) 48.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.workout_set_number, set.setNumber) + " · " +
                    setSummary(set),
                style = MaterialTheme.typography.bodyMedium,
                color = if (set.completed) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            set.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isEditing) {
            TextButton(onClick = onEdit) {
                Text(text = stringResource(R.string.session_detail_edit))
            }
        }
    }
}

/** Only the fields that were actually recorded appear, so an unused field never reads as a zero. */
@Composable
private fun setSummary(set: SetLog): String {
    val parts = buildList {
        add(set.setType.label())
        set.reps?.let { add("$it reps") }
        set.weight?.let { add("$it ${set.weightUnit.label()}") }
        set.durationSeconds?.let { add("${it}s") }
        set.distanceMeters?.let { add("${it}m") }
        set.rpe?.let { add("RPE $it") }
        set.rir?.let { add("RIR $it") }
        if (!set.completed) add(stringResource(R.string.session_detail_field_completed) + "?")
    }
    return parts.joinToString(" · ")
}

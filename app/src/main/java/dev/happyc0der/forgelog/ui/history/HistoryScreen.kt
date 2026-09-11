package dev.happyc0der.forgelog.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import dev.happyc0der.forgelog.domain.history.LoggedExercise
import dev.happyc0der.forgelog.domain.model.ProgramDay
import dev.happyc0der.forgelog.domain.model.SessionStatus
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.ui.components.CardHeader
import dev.happyc0der.forgelog.ui.components.ConfirmDialog
import dev.happyc0der.forgelog.ui.components.DateRangePickerDialog
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.ForgeCard
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.components.OptionDropdown
import dev.happyc0der.forgelog.ui.components.StatGrid
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.format.relativeDate
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.theme.forgeLogColors
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit,
    onResumeWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val zone = remember { ZoneId.systemDefault() }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingDelete = pendingDeleteId?.let { id ->
        uiState.rows.firstOrNull { it.summary.sessionId == id }
    }
    var pendingSaveAsDayId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingSaveAsDay = pendingSaveAsDayId?.let { id ->
        uiState.rows.firstOrNull { it.summary.sessionId == id }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.Message -> snackbarHostState.showSnackbar(event.value)
                is HistoryEvent.RepeatStarted -> onResumeWorkout(event.sessionId)
            }
        }
    }

    if (showRangePicker) {
        DateRangePickerDialog(
            zone = zone,
            initialFromEpochMs = uiState.filter.fromEpochMs,
            initialUntilEpochMs = uiState.filter.untilEpochMs,
            onConfirm = { from, until ->
                viewModel.setDateRange(from, until)
                showRangePicker = false
            },
            onDismiss = { showRangePicker = false },
        )
    }

    pendingDelete?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            message = stringResource(R.string.history_delete_message, row.summary.sessionName),
            onConfirm = {
                viewModel.deleteSession(row.summary.sessionId)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }

    pendingSaveAsDay?.let { row ->
        SaveAsProgramDayDialog(
            sessionName = row.summary.sessionName,
            programs = uiState.programs.filterNot { it.isArchived },
            onConfirm = { programId, dayName ->
                viewModel.saveAsProgramDay(row.summary.sessionId, programId, dayName)
                pendingSaveAsDayId = null
            },
            onDismiss = { pendingSaveAsDayId = null },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.nav_history)) },
                actions = {
                    if (uiState.isFilterActive) {
                        TextButton(onClick = viewModel::clearFilters) {
                            Text(text = stringResource(R.string.history_clear_filters))
                        }
                    }
                    IconButton(
                        onClick = { showFilters = !showFilters },
                        modifier = Modifier.testTag(TestTags.HISTORY_FILTERS),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.history_filters),
                            tint = if (uiState.isFilterActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage ?: stringResource(R.string.state_error_generic),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag(TestTags.HISTORY_SCREEN),
            ) {
                OutlinedTextField(
                    value = uiState.filter.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.history_search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(TestTags.HISTORY_SEARCH),
                )
                AnimatedVisibility(visible = showFilters) {
                    FilterPanel(
                        uiState = uiState,
                        onStatusSelected = viewModel::onStatusSelected,
                        onProgramSelected = viewModel::onProgramSelected,
                        onDaySelected = viewModel::onDaySelected,
                        onExerciseSelected = viewModel::onExerciseSelected,
                        onPresetSelected = { preset ->
                            if (preset == DateRangePreset.CUSTOM) {
                                showRangePicker = true
                            } else {
                                viewModel.onPresetSelected(preset)
                            }
                        },
                    )
                }
                if (uiState.rows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        title = stringResource(
                            if (uiState.isFilterActive) {
                                R.string.history_empty_filtered_title
                            } else {
                                R.string.history_empty_title
                            },
                        ),
                        message = stringResource(
                            if (uiState.isFilterActive) {
                                R.string.history_empty_filtered_message
                            } else {
                                R.string.history_empty_message
                            },
                        ),
                    )
                } else {
                    HistoryList(
                        rows = uiState.rows,
                        weightUnit = uiState.weightUnit,
                        today = uiState.today,
                        zone = zone,
                        onOpen = onOpenSession,
                        // A workout still in progress is gone back into, not repeated.
                        onRepeat = { row ->
                            if (row.status == SessionStatus.IN_PROGRESS) {
                                onResumeWorkout(row.summary.sessionId)
                            } else {
                                viewModel.repeatSession(row.summary.sessionId)
                            }
                        },
                        onSaveAsDay = { pendingSaveAsDayId = it.summary.sessionId },
                        onDelete = { pendingDeleteId = it.summary.sessionId },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(
    uiState: HistoryUiState,
    onStatusSelected: (SessionStatus?) -> Unit,
    onProgramSelected: (Long?) -> Unit,
    onDaySelected: (Long?) -> Unit,
    onExerciseSelected: (Long?) -> Unit,
    onPresetSelected: (DateRangePreset) -> Unit,
) {
    val statusLabels = SessionStatus.entries.associateWith { stringResource(it.historyLabelRes()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            // Scrollable: five chips do not fit a phone's width, and the fifth — the custom range
            // — was clipped off the end where nothing could reach it.
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DateRangePreset.entries.forEach { preset ->
                FilterChip(
                    selected = uiState.preset == preset,
                    onClick = { onPresetSelected(preset) },
                    label = {
                        Text(
                            text = customRangeLabel(preset, uiState)
                                ?: stringResource(preset.labelRes()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }
        OptionDropdown(
            label = stringResource(R.string.history_filter_status),
            selected = uiState.filter.status,
            options = SessionStatus.entries,
            optionLabel = { statusLabels.getValue(it) },
            onSelect = onStatusSelected,
        )
        OptionDropdown(
            label = stringResource(R.string.history_filter_program),
            selected = uiState.programs.firstOrNull { it.id == uiState.filter.programId },
            options = uiState.programs,
            optionLabel = WorkoutProgram::name,
            onSelect = { onProgramSelected(it?.id) },
        )
        OptionDropdown(
            label = stringResource(R.string.history_filter_day),
            selected = uiState.days.firstOrNull { it.id == uiState.filter.programDayId },
            options = uiState.days,
            optionLabel = ProgramDay::name,
            onSelect = { onDaySelected(it?.id) },
            // A day only makes sense inside a program, so the picker stays shut until one is chosen.
            enabled = uiState.filter.programId != null,
        )
        OptionDropdown(
            label = stringResource(R.string.history_filter_exercise),
            selected = uiState.loggedExercises.firstOrNull {
                it.exerciseId == uiState.filter.exerciseId
            },
            options = uiState.loggedExercises,
            optionLabel = LoggedExercise::displayName,
            onSelect = { onExerciseSelected(it?.exerciseId) },
        )
    }
}

@Composable
private fun HistoryList(
    rows: List<HistoryRow>,
    weightUnit: dev.happyc0der.forgelog.domain.model.ExerciseUnit,
    today: LocalDate?,
    zone: ZoneId,
    onOpen: (Long) -> Unit,
    onRepeat: (HistoryRow) -> Unit,
    onSaveAsDay: (HistoryRow) -> Unit,
    onDelete: (HistoryRow) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.HISTORY_LIST),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = rows, key = { it.summary.sessionId }) { row ->
            HistoryRowCard(
                row = row,
                weightUnit = weightUnit,
                today = today,
                zone = zone,
                onOpen = onOpen,
                onRepeat = onRepeat,
                onSaveAsDay = onSaveAsDay,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun HistoryRowCard(
    row: HistoryRow,
    weightUnit: dev.happyc0der.forgelog.domain.model.ExerciseUnit,
    today: LocalDate?,
    zone: ZoneId,
    onOpen: (Long) -> Unit,
    onRepeat: (HistoryRow) -> Unit,
    onSaveAsDay: (HistoryRow) -> Unit,
    onDelete: (HistoryRow) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ForgeCard(modifier = Modifier.clickable { onOpen(row.summary.sessionId) }) {
        CardHeader(
            title = buildString {
                append(
                    today?.let { relativeDate(row.startedAt, it, zone) }
                        ?: Formatters.timeOfDay(row.startedAt, zone),
                )
                append(" · ")
                append(Formatters.timeOfDay(row.startedAt, zone))
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusLabel(status = row.status)
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.history_view_session)) },
                                onClick = {
                                    menuOpen = false
                                    onOpen(row.summary.sessionId)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(
                                            if (row.status == SessionStatus.IN_PROGRESS) {
                                                R.string.home_resume_workout
                                            } else {
                                                R.string.history_repeat
                                            },
                                        ),
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onRepeat(row)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.history_save_as_day)) },
                                onClick = {
                                    menuOpen = false
                                    onSaveAsDay(row)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.action_delete)) },
                                onClick = {
                                    menuOpen = false
                                    onDelete(row)
                                },
                            )
                        }
                    }
                }
            },
        )
        Text(
            text = row.summary.sessionName.ifBlank { stringResource(R.string.workout_adhoc_name) },
            style = MaterialTheme.typography.titleMedium,
        )
        StatGrid(
            stats = listOf(
                stringResource(R.string.home_stat_duration) to
                    Formatters.compactDuration(row.summary.durationMs),
                stringResource(R.string.home_stat_volume) to
                    row.summary.loadLb.takeIf { it > 0.0 }
                        ?.let { Formatters.volume(it, weightUnit) },
                stringResource(R.string.home_stat_sets) to row.summary.totalSets.toString(),
                stringResource(R.string.home_stat_exercises) to row.summary.exerciseCount.toString(),
            ),
        )
    }
}

@Composable
private fun StatusLabel(status: SessionStatus) {
    val extended = MaterialTheme.forgeLogColors
    val color = when (status) {
        SessionStatus.COMPLETED -> extended.success
        SessionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
        SessionStatus.ABANDONED -> extended.warning
    }
    Text(
        text = stringResource(status.historyLabelRes()),
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

internal fun SessionStatus.historyLabelRes(): Int = when (this) {
    SessionStatus.COMPLETED -> R.string.history_status_completed
    SessionStatus.IN_PROGRESS -> R.string.history_status_in_progress
    SessionStatus.ABANDONED -> R.string.history_status_abandoned
}

private fun DateRangePreset.labelRes(): Int = when (this) {
    DateRangePreset.ALL_TIME -> R.string.history_range_all
    DateRangePreset.THIS_WEEK -> R.string.history_range_this_week
    DateRangePreset.LAST_WEEK -> R.string.history_range_last_week
    DateRangePreset.LAST_30_DAYS -> R.string.history_range_30_days
    DateRangePreset.CUSTOM -> R.string.history_range_custom
}

/** The custom chip shows the dates it stands for once it has some, rather than staying "Custom…". */
@Composable
private fun customRangeLabel(preset: DateRangePreset, uiState: HistoryUiState): String? {
    if (preset != DateRangePreset.CUSTOM) return null
    val from = uiState.filter.fromEpochMs ?: return null
    val until = uiState.filter.untilEpochMs ?: return null
    val today = uiState.today ?: return null
    val zone = ZoneId.systemDefault()
    return stringResource(
        R.string.history_range_custom_selected,
        relativeDate(from, today, zone),
        // The stored end is exclusive; name the last day actually included.
        relativeDate(until - 1, today, zone),
    )
}

package dev.happyc0der.forgelog.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.ProgramSummary
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.ui.components.ConfirmDialog
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.LoadingState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(
    onOpenProgram: (Long) -> Unit,
    onOpenExerciseLibrary: () -> Unit,
    viewModel: ProgramsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<ProgramEditorTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<WorkoutProgram?>(null) }
    var historyWarning by remember { mutableStateOf<WorkoutProgram?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProgramsEvent.Message -> snackbarHostState.showSnackbar(event.value)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.programs_title)) },
                actions = {
                    IconButton(onClick = onOpenExerciseLibrary) {
                        Icon(
                            imageVector = Icons.Filled.FitnessCenter,
                            contentDescription = stringResource(R.string.action_exercises),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editor = ProgramEditorTarget.Create }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_create_program),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage ?: stringResource(R.string.state_error_generic),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            uiState.programs.isEmpty() -> EmptyState(
                icon = Icons.Outlined.FitnessCenter,
                title = stringResource(R.string.programs_empty_title),
                message = stringResource(R.string.programs_empty_message),
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    FilterChip(
                        selected = uiState.includeArchived,
                        onClick = viewModel::onToggleArchived,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        label = {
                            Text(
                                text = stringResource(
                                    if (uiState.includeArchived) {
                                        R.string.action_hide_archived
                                    } else {
                                        R.string.action_show_archived
                                    },
                                ),
                            )
                        },
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        items(uiState.programs, key = { it.program.id }) { summary ->
                            ProgramRow(
                                summary = summary,
                                onClick = { onOpenProgram(summary.program.id) },
                                onRename = { editor = ProgramEditorTarget.Rename(summary.program) },
                                onDuplicate = { viewModel.duplicate(summary.program.id) },
                                onArchive = {
                                    viewModel.setArchived(
                                        summary.program.id,
                                        !summary.program.isArchived,
                                    )
                                },
                                onDelete = {
                                    scope.launch {
                                        if (viewModel.hasSessionHistory(summary.program.id)) {
                                            historyWarning = summary.program
                                        } else {
                                            pendingDelete = summary.program
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    editor?.let { target ->
        ProgramEditorDialog(
            target = target,
            onDismiss = { editor = null },
            onConfirm = { name, description ->
                when (target) {
                    ProgramEditorTarget.Create -> viewModel.createProgram(name, description)
                    is ProgramEditorTarget.Rename -> viewModel.renameProgram(
                        target.program,
                        name,
                        description,
                    )
                }
                editor = null
            },
        )
    }

    pendingDelete?.let { program ->
        ConfirmDialog(
            title = stringResource(R.string.program_delete_title),
            message = stringResource(R.string.program_delete_message, program.name),
            onConfirm = {
                viewModel.delete(program.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    historyWarning?.let { program ->
        ConfirmDialog(
            title = stringResource(R.string.program_delete_history_title),
            message = stringResource(R.string.program_delete_history_message, program.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.delete(program.id)
                historyWarning = null
            },
            onDismiss = { historyWarning = null },
        )
    }
}

@Composable
private fun ProgramRow(
    summary: ProgramSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val color = runCatching { Color(android.graphics.Color.parseColor(summary.program.color)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)
    ListItem(
        headlineContent = { Text(text = summary.program.name) },
        supportingContent = {
            val days = if (summary.dayCount == 1) {
                stringResource(R.string.programs_day_count_one)
            } else {
                stringResource(R.string.programs_day_count, summary.dayCount)
            }
            val archived = if (summary.program.isArchived) {
                " • ${stringResource(R.string.state_archived)}"
            } else {
                ""
            }
            val description = summary.program.description?.takeIf { it.isNotBlank() }
            Text(text = listOfNotNull(description, days).joinToString(" • ") + archived)
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = color,
            ) {}
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_duplicate)) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    if (summary.program.isArchived) {
                                        R.string.action_unarchive
                                    } else {
                                        R.string.action_archive
                                    },
                                ),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onArchive()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

sealed interface ProgramEditorTarget {
    data object Create : ProgramEditorTarget
    data class Rename(val program: WorkoutProgram) : ProgramEditorTarget
}

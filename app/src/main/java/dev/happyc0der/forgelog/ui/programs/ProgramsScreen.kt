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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.domain.model.ProgramSummary
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.ui.components.ConfirmDialog
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.format.relativeDate
import java.time.ZoneId
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.format.currentZone
import dev.happyc0der.forgelog.ui.format.today

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramsScreen(
    onOpenProgram: (Long) -> Unit,
    onOpenExerciseLibrary: () -> Unit,
    viewModel: ProgramsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = currentZone()
    val snackbarHostState = remember { SnackbarHostState() }
    /*
     * The editor is held by id rather than by value, so a rotation does not close it. That is not
     * merely tidy: it carries a typed name, description and colour, and the fields inside it can
     * only restore themselves if the dialog is still there to restore into. 0 means "creating a
     * new program", which has no id to remember. A pending delete lives in the ViewModel.
     */
    var editorProgramId by rememberSaveable { mutableStateOf<Long?>(null) }
    val deleteRequest by viewModel.pendingDelete.collectAsStateWithLifecycle()

    fun programById(id: Long) = uiState.programs.firstOrNull { it.program.id == id }?.program
    val editor = editorProgramId?.let { id ->
        if (id == 0L) ProgramEditorTarget.Create else programById(id)?.let(ProgramEditorTarget::Rename)
    }
    val deleting = deleteRequest?.let { request -> programById(request.programId)?.let { request to it } }

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
            FloatingActionButton(onClick = { editorProgramId = 0L }) {
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
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TestTags.PROGRAMS_SCREEN)
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
                    if (uiState.programs.isEmpty()) {
                        // Deliberately inside the column, below the chip. When every program was
                        // archived, a full-screen empty state replaced the one control that could
                        // reveal them, leaving the user stuck.
                        EmptyState(
                            icon = Icons.Outlined.FitnessCenter,
                            title = stringResource(
                                if (uiState.includeArchived) {
                                    R.string.programs_empty_title
                                } else {
                                    R.string.programs_empty_visible_title
                                },
                            ),
                            message = stringResource(
                                if (uiState.includeArchived) {
                                    R.string.programs_empty_message
                                } else {
                                    R.string.programs_empty_visible_message
                                },
                            ),
                        )
                        return@Column
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        items(uiState.programs, key = { it.program.id }) { summary ->
                            ProgramRow(
                                summary = summary,
                                zone = zone,
                                onClick = { onOpenProgram(summary.program.id) },
                                onRename = { editorProgramId = summary.program.id },
                                onDuplicate = { viewModel.duplicate(summary.program.id) },
                                onArchive = {
                                    viewModel.setArchived(
                                        summary.program.id,
                                        !summary.program.isArchived,
                                    )
                                },
                                onDelete = { viewModel.requestDelete(summary.program.id) },
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
            onDismiss = { editorProgramId = null },
            onConfirm = { name, description, color ->
                when (target) {
                    ProgramEditorTarget.Create -> viewModel.createProgram(name, description, color)
                    is ProgramEditorTarget.Rename -> viewModel.renameProgram(
                        target.program,
                        name,
                        description,
                        color,
                    )
                }
                editorProgramId = null
            },
        )
    }

    deleting?.let { (request, program) ->
        ConfirmDialog(
            title = stringResource(
                if (request.hasHistory) R.string.program_delete_history_title else R.string.program_delete_title,
            ),
            message = if (request.hasHistory) {
                stringResource(R.string.program_delete_history_message, program.name)
            } else {
                stringResource(R.string.program_delete_message, program.name)
            },
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun ProgramRow(
    summary: ProgramSummary,
    zone: ZoneId,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
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
            val completions = when (summary.completedSessionCount) {
                0 -> null
                1 -> stringResource(R.string.programs_completed_count_one)
                else -> stringResource(R.string.programs_completed_count, summary.completedSessionCount)
            }
            val archived = if (summary.program.isArchived) {
                " • ${stringResource(R.string.state_archived)}"
            } else {
                ""
            }
            val description = summary.program.description?.takeIf { it.isNotBlank() }
            Column {
                // Its own line, cut at two. Run together with the counts, a long description --
                // a program's rules, say -- filled the card and pushed the day count out of sight.
                // The program's own screen shows it in full.
                description?.let {
                    Text(text = it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = listOfNotNull(days, completions).joinToString(" • ") + archived,
                )
                // "Not performed yet" rather than an absent line, so a never-used program is
                // distinguishable from one whose date simply failed to load.
                Text(
                    text = summary.lastPerformedAt?.let { stamp ->
                        stringResource(
                            R.string.programs_last_performed,
                            relativeDate(stamp, today(zone), zone),
                        )
                    } ?: stringResource(R.string.programs_never_performed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

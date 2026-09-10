package dev.happyc0der.forgelog.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.Exercise
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.ui.components.ConfirmDialog
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.util.label
import dev.happyc0der.forgelog.ui.util.openHowToUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    picker: Boolean,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onPicked: (Long) -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Exercise?>(null) }
    val howToMissing = stringResource(R.string.exercise_how_to_missing_app)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExerciseLibraryEvent.Message -> snackbarHostState.showSnackbar(event.value)
                is ExerciseLibraryEvent.OpenHowTo -> {
                    if (!context.openHowToUrl(event.url)) {
                        snackbarHostState.showSnackbar(howToMissing)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (picker) {
                                R.string.exercise_library_pick_title
                            } else {
                                R.string.exercise_library_title
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
            FloatingActionButton(onClick = onCreate) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_create_exercise),
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
                        .padding(innerPadding),
                ) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        label = { Text(text = stringResource(R.string.search_exercises)) },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = uiState.category == null,
                            onClick = { viewModel.onCategorySelected(null) },
                            label = { Text(text = stringResource(R.string.filter_all)) },
                        )
                        ExerciseCategory.entries.forEach { category ->
                            FilterChip(
                                selected = uiState.category == category,
                                onClick = { viewModel.onCategorySelected(category) },
                                label = { Text(text = category.label()) },
                            )
                        }
                        FilterChip(
                            selected = uiState.includeArchived,
                            onClick = viewModel::onToggleArchived,
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
                    }
                    if (uiState.exercises.isEmpty()) {
                        val isSearch = uiState.query.isNotBlank() || uiState.category != null
                        EmptyState(
                            icon = Icons.Outlined.FitnessCenter,
                            title = stringResource(
                                if (isSearch) {
                                    R.string.exercise_library_empty_search_title
                                } else {
                                    R.string.exercise_library_empty_title
                                },
                            ),
                            message = stringResource(
                                if (isSearch) {
                                    R.string.exercise_library_empty_search_message
                                } else {
                                    R.string.exercise_library_empty_message
                                },
                            ),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp),
                        ) {
                            items(uiState.exercises, key = { it.id }) { exercise ->
                                ExerciseRow(
                                    exercise = exercise,
                                    picker = picker,
                                    onClick = {
                                        if (picker) onPicked(exercise.id) else onEdit(exercise.id)
                                    },
                                    onEdit = { onEdit(exercise.id) },
                                    onOpenHowTo = { viewModel.onOpenHowTo(exercise) },
                                    onArchive = { viewModel.archive(exercise, !exercise.isArchived) },
                                    onDelete = { pendingDelete = exercise },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { exercise ->
        ConfirmDialog(
            title = stringResource(R.string.exercise_delete_title),
            message = stringResource(R.string.exercise_delete_message, exercise.name),
            onConfirm = {
                viewModel.delete(exercise)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    picker: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onOpenHowTo: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(text = exercise.name) },
        supportingContent = {
            val archived = if (exercise.isArchived) {
                " • ${stringResource(R.string.state_archived)}"
            } else {
                ""
            }
            Text(text = "${exercise.category.label()} • ${exercise.defaultUnit.label()}$archived")
        },
        leadingContent = if (exercise.howToUrl != null) {
            {
                IconButton(onClick = onOpenHowTo) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.action_open_how_to),
                    )
                }
            }
        } else {
            null
        },
        trailingContent = {
            if (!picker) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_edit)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    if (exercise.isArchived) {
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
        modifier = Modifier.clickable(onClick = onClick),
    )
}

package dev.happyc0der.forgelog.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.domain.model.ProgramDay
import dev.happyc0der.forgelog.domain.model.ProgramDayDetail
import dev.happyc0der.forgelog.ui.components.ConfirmDialog
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.components.DragHandle
import dev.happyc0der.forgelog.ui.components.ReorderableColumn
import dev.happyc0der.forgelog.ui.components.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(
    onBack: () -> Unit,
    onOpenDay: (Long) -> Unit,
    onStartDay: (Long) -> Unit,
    viewModel: ProgramDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    var createDay by rememberSaveable { mutableStateOf(false) }
    var renameDay by remember { mutableStateOf<ProgramDay?>(null) }
    var deleteDay by remember { mutableStateOf<ProgramDay?>(null) }
    val requiredName = stringResource(R.string.program_day_name_required)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProgramDetailEvent.Message -> snackbarHostState.showSnackbar(event.value)
                is ProgramDetailEvent.OpenDay -> onOpenDay(event.dayId)
            }
        }
    }

    val detail = uiState.detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = detail?.program?.name ?: stringResource(R.string.programs_title)) },
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
            if (detail != null) {
                FloatingActionButton(onClick = { createDay = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_create_day),
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
            detail == null -> ErrorState(
                message = stringResource(R.string.program_missing),
                onRetry = onBack,
                modifier = Modifier.padding(innerPadding),
            )
            detail.days.isEmpty() -> EmptyState(
                icon = Icons.Outlined.CalendarMonth,
                title = stringResource(R.string.program_detail_empty_title),
                message = stringResource(R.string.program_detail_empty_message),
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TestTags.PROGRAM_DETAIL_SCREEN)
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .padding(bottom = 88.dp),
                ) {
                    detail.program.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    ReorderableColumn(
                        // uiState.days applies the in-flight drag order, so a row follows the finger
                        // instead of waiting for a round trip through the database.
                        items = uiState.days.mapNotNull { day ->
                            detail.days.firstOrNull { it.day.id == day.id }
                        },
                        key = { it.day.id },
                        onMove = viewModel::moveDay,
                        onDragEnd = viewModel::persistDayOrder,
                        scrollState = scrollState,
                        moveUpLabel = stringResource(R.string.action_move_up),
                        moveDownLabel = stringResource(R.string.action_move_down),
                    ) { dayDetail, dragModifier ->
                        ProgramDayRow(
                            dayDetail = dayDetail,
                            dragModifier = dragModifier,
                            onClick = { onOpenDay(dayDetail.day.id) },
                            onStart = { onStartDay(dayDetail.day.id) },
                            onRename = { renameDay = dayDetail.day },
                            onDuplicate = { viewModel.duplicateDay(dayDetail.day.id) },
                            onDelete = { deleteDay = dayDetail.day },
                        )
                    }
                }
            }
        }
    }

    if (createDay) {
        TextInputDialog(
            title = stringResource(R.string.program_day_create_title),
            initialValue = "",
            label = stringResource(R.string.program_day_field_name),
            validator = { value -> if (value.isBlank()) requiredName else null },
            onConfirm = {
                viewModel.createDay(it)
                createDay = false
            },
            onDismiss = { createDay = false },
        )
    }

    renameDay?.let { day ->
        TextInputDialog(
            title = stringResource(R.string.program_day_rename_title),
            initialValue = day.name,
            label = stringResource(R.string.program_day_field_name),
            validator = { value -> if (value.isBlank()) requiredName else null },
            onConfirm = {
                viewModel.renameDay(day, it)
                renameDay = null
            },
            onDismiss = { renameDay = null },
        )
    }

    deleteDay?.let { day ->
        ConfirmDialog(
            title = stringResource(R.string.program_day_delete_title),
            message = stringResource(R.string.program_day_delete_message, day.name),
            onConfirm = {
                viewModel.deleteDay(day.id)
                deleteDay = null
            },
            onDismiss = { deleteDay = null },
        )
    }
}

@Composable
private fun ProgramDayRow(
    dayDetail: ProgramDayDetail,
    dragModifier: Modifier,
    onClick: () -> Unit,
    onStart: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val count = dayDetail.exercises.size
    ListItem(
        headlineContent = { Text(text = dayDetail.day.name) },
        supportingContent = {
            Text(
                text = if (count == 1) {
                    stringResource(R.string.program_day_exercise_count_one)
                } else {
                    stringResource(R.string.program_day_exercise_count, count)
                },
            )
        },
        leadingContent = {
            DragHandle(dragModifier = dragModifier) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.action_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.workout_start_this_day)) },
                    onClick = {
                        menuOpen = false
                        onStart()
                    },
                )
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
                    text = { Text(text = stringResource(R.string.action_delete)) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

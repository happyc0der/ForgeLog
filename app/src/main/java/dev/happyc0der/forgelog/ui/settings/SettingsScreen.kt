package dev.happyc0der.forgelog.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import dev.happyc0der.forgelog.data.backup.UriDocumentHandle
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.settings.AppSettings
import dev.happyc0der.forgelog.domain.workout.DurationInputUnit
import dev.happyc0der.forgelog.ui.components.CardHeader
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.ForgeCard
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.components.OptionDropdown
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.util.label
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmImport by rememberSaveable { mutableStateOf(false) }
    // Storage Access Framework: the user picks the destination, so ForgeLog needs no storage
    // permission at all and never writes anywhere the user did not choose.
    //
    // One launcher per MIME type, rather than one whose type is swapped in state. The contract is
    // captured when the launcher is registered, so a single shared launcher would offer to save the
    // CSV as application/json depending on recomposition timing.
    val createJsonDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        viewModel.onExportDestinationChosen(uri?.let(::UriDocumentHandle))
    }
    val createCsvDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        viewModel.onExportDestinationChosen(uri?.let(::UriDocumentHandle))
    }
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        viewModel.onImportSourceChosen(uri?.let(::UriDocumentHandle))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbarHostState.showSnackbar(event.value)
                is SettingsEvent.PickExportDestination -> when (event.mimeType) {
                    "text/csv" -> createCsvDocument.launch(event.suggestedName)
                    else -> createJsonDocument.launch(event.suggestedName)
                }
                SettingsEvent.PickImportSource ->
                    openDocument.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(text = stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(text = stringResource(R.string.settings_import_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmImport = false
                        viewModel.beginImport()
                    },
                ) {
                    Text(text = stringResource(R.string.settings_import_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmDelete) {
        DeleteEverythingDialog(
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAllData()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(text = stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage ?: stringResource(R.string.state_error_generic),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag(TestTags.SETTINGS_SCREEN),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.isWorking) {
                    item(key = "working") {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_working),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item(key = "units") { UnitsCard(uiState.settings, viewModel) }
                item(key = "rest") { RestCard(uiState.settings, viewModel) }
                item(key = "week") { WeekCard(uiState.settings, viewModel) }
                item(key = "backup") {
                    BackupCard(
                        uiState = uiState,
                        onExportJson = viewModel::beginJsonExport,
                        onImport = { confirmImport = true },
                        onExportCsv = viewModel::beginCsvExport,
                        onCsvRange = viewModel::setCsvRange,
                    )
                }
                if (uiState.debugToolsAvailable) {
                    item(key = "debug") {
                        ForgeCard {
                            CardHeader(title = stringResource(R.string.settings_section_debug))
                            ActionRow(
                                label = stringResource(R.string.settings_seed),
                                detail = stringResource(R.string.settings_seed_detail),
                                enabled = !uiState.isWorking,
                                testTag = "settings_seed",
                                onClick = viewModel::seedSampleData,
                            )
                        }
                    }
                }
                item(key = "danger") { DangerCard(onDelete = { confirmDelete = true }) }
                item(key = "about") { AboutCard(uiState) }
            }
        }
    }
}

@Composable
private fun UnitsCard(settings: AppSettings, viewModel: SettingsViewModel) {
    ForgeCard {
        CardHeader(title = stringResource(R.string.settings_section_units))
        val weightUnits = listOf(ExerciseUnit.LB, ExerciseUnit.KG)
        val weightLabels = weightUnits.associateWith { it.label() }
        OptionDropdown(
            label = stringResource(R.string.settings_default_weight_unit),
            selected = settings.defaultWeightUnit,
            options = weightUnits,
            optionLabel = { weightLabels.getValue(it) },
            onSelect = { viewModel.setDefaultWeightUnit(it ?: ExerciseUnit.LB) },
            anyLabel = weightLabels.getValue(ExerciseUnit.LB),
        )
        val durationLabels = DurationInputUnit.entries.associateWith { it.name.lowercase(Locale.US) }
        OptionDropdown(
            label = stringResource(R.string.settings_duration_unit),
            selected = settings.durationInputUnit,
            options = DurationInputUnit.entries,
            optionLabel = { durationLabels.getValue(it) },
            onSelect = { viewModel.setDurationInputUnit(it ?: DurationInputUnit.SECONDS) },
            anyLabel = durationLabels.getValue(DurationInputUnit.SECONDS),
        )
    }
}

@Composable
private fun RestCard(settings: AppSettings, viewModel: SettingsViewModel) {
    ForgeCard {
        CardHeader(title = stringResource(R.string.settings_section_rest))
        Text(
            text = stringResource(R.string.settings_default_rest) + ": " +
                stringResource(R.string.settings_default_rest_value, settings.defaultRestSeconds),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 90, 120, 180, 240).forEach { seconds ->
                FilterChip(
                    selected = settings.defaultRestSeconds == seconds,
                    onClick = { viewModel.setDefaultRestSeconds(seconds) },
                    label = { Text(text = "${seconds}s") },
                )
            }
        }
        SwitchRow(
            label = stringResource(R.string.settings_rest_vibration),
            checked = settings.restTimerVibration,
            onCheckedChange = viewModel::setRestTimerVibration,
        )
        SwitchRow(
            label = stringResource(R.string.settings_rest_sound),
            checked = settings.restTimerSound,
            onCheckedChange = viewModel::setRestTimerSound,
        )
    }
}

@Composable
private fun WeekCard(settings: AppSettings, viewModel: SettingsViewModel) {
    ForgeCard {
        CardHeader(title = stringResource(R.string.settings_section_week))
        val dayLabels = DayOfWeek.entries.associateWith {
            it.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
        OptionDropdown(
            label = stringResource(R.string.settings_week_start),
            selected = settings.weekStartDay,
            options = DayOfWeek.entries,
            optionLabel = { dayLabels.getValue(it) },
            onSelect = { viewModel.setWeekStartDay(it ?: DayOfWeek.MONDAY) },
            anyLabel = dayLabels.getValue(DayOfWeek.MONDAY),
        )
        SwitchRow(
            label = stringResource(R.string.settings_include_warmup),
            checked = settings.includeWarmupInVolume,
            onCheckedChange = viewModel::setIncludeWarmupInVolume,
        )
        Text(
            text = stringResource(R.string.settings_include_warmup_detail),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackupCard(
    uiState: SettingsUiState,
    onExportJson: () -> Unit,
    onImport: () -> Unit,
    onExportCsv: () -> Unit,
    onCsvRange: (CsvRange) -> Unit,
) {
    ForgeCard {
        CardHeader(title = stringResource(R.string.settings_section_backup))
        ActionRow(
            label = stringResource(R.string.settings_export_json),
            detail = stringResource(R.string.settings_export_json_detail),
            enabled = !uiState.isWorking,
            testTag = TestTags.SETTINGS_EXPORT_JSON,
            onClick = onExportJson,
        )
        ActionRow(
            label = stringResource(R.string.settings_import_json),
            detail = stringResource(R.string.settings_import_json_detail),
            enabled = !uiState.isWorking,
            testTag = TestTags.SETTINGS_IMPORT_JSON,
            onClick = onImport,
        )
        val rangeLabels = CsvRange.entries.associateWith { stringResource(it.labelRes()) }
        OptionDropdown(
            label = stringResource(R.string.settings_csv_range),
            selected = uiState.csvRange,
            options = CsvRange.entries,
            optionLabel = { rangeLabels.getValue(it) },
            onSelect = { onCsvRange(it ?: CsvRange.ALL_TIME) },
            anyLabel = rangeLabels.getValue(CsvRange.ALL_TIME),
        )
        ActionRow(
            label = stringResource(R.string.settings_export_csv),
            detail = stringResource(R.string.settings_export_csv_detail),
            enabled = !uiState.isWorking,
            testTag = TestTags.SETTINGS_EXPORT_CSV,
            onClick = onExportCsv,
        )
    }
}

@Composable
private fun DangerCard(onDelete: () -> Unit) {
    ForgeCard {
        CardHeader(title = stringResource(R.string.settings_section_danger))
        ActionRow(
            label = stringResource(R.string.settings_delete_all),
            detail = stringResource(R.string.settings_delete_all_detail),
            enabled = true,
            testTag = TestTags.SETTINGS_DELETE_ALL,
            destructive = true,
            onClick = onDelete,
        )
    }
}

@Composable
private fun AboutCard(uiState: SettingsUiState) {
    ForgeCard {
        CardHeader(title = stringResource(R.string.settings_section_about))
        Text(
            text = stringResource(R.string.settings_privacy_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_version, uiState.appVersion),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_database_version, uiState.databaseVersion),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(
    label: String,
    detail: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(testTag),
        ) {
            Text(
                text = label,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Deleting everything asks the user to type the word, not just tap twice.
 *
 * The data has no copy unless they exported one, so a mis-tap must not be enough.
 */
@Composable
private fun DeleteEverythingDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val keyword = stringResource(R.string.settings_delete_all_keyword)
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_delete_all_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.settings_delete_all_message))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.settings_delete_all_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == keyword) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun CsvRange.labelRes(): Int = when (this) {
    CsvRange.ALL_TIME -> R.string.settings_csv_range_all
    CsvRange.THIS_WEEK -> R.string.settings_csv_range_week
    CsvRange.LAST_4_WEEKS -> R.string.settings_csv_range_4_weeks
    CsvRange.LAST_YEAR -> R.string.settings_csv_range_year
}

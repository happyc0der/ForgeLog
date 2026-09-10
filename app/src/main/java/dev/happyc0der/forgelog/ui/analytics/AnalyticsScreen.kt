package dev.happyc0der.forgelog.ui.analytics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import dev.happyc0der.forgelog.domain.analytics.ExerciseRecords
import dev.happyc0der.forgelog.domain.analytics.PeriodComparison
import dev.happyc0der.forgelog.domain.analytics.PeriodMetrics
import dev.happyc0der.forgelog.domain.history.LoggedExercise
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.ui.components.BarChart
import dev.happyc0der.forgelog.ui.components.BarDatum
import dev.happyc0der.forgelog.domain.time.WeekBoundary
import dev.happyc0der.forgelog.ui.components.CardHeader
import dev.happyc0der.forgelog.ui.components.DateRangePickerDialog
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.ForgeCard
import dev.happyc0der.forgelog.ui.components.LineChart
import dev.happyc0der.forgelog.ui.components.LinePoint
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.components.OptionDropdown
import dev.happyc0der.forgelog.ui.components.ProportionBars
import dev.happyc0der.forgelog.ui.components.StatGrid
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.format.relativeDate
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.util.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    var showFormula by rememberSaveable { mutableStateOf(false) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }

    if (showRangePicker) {
        DateRangePickerDialog(
            zone = zone,
            initialFromEpochMs = uiState.currentRange?.start,
            initialUntilEpochMs = uiState.currentRange?.endExclusive,
            onConfirm = { from, until ->
                viewModel.setCustomRange(from, until)
                showRangePicker = false
            },
            onDismiss = { showRangePicker = false },
        )
    }

    if (showFormula) {
        AlertDialog(
            onDismissRequest = { showFormula = false },
            title = { Text(text = stringResource(R.string.analytics_estimated_1rm)) },
            text = { Text(text = stringResource(R.string.analytics_1rm_formula)) },
            confirmButton = {
                TextButton(onClick = { showFormula = false }) {
                    Text(text = stringResource(R.string.action_close))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(text = stringResource(R.string.nav_analytics)) }) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(innerPadding))
            uiState.errorMessage != null -> ErrorState(
                message = uiState.errorMessage ?: stringResource(R.string.state_error_generic),
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            !uiState.hasAnyData -> EmptyState(
                icon = Icons.Outlined.Insights,
                title = stringResource(R.string.analytics_empty_title),
                message = stringResource(R.string.analytics_empty_message),
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag(TestTags.ANALYTICS_SCREEN),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "comparison") {
                    ComparisonCard(
                        comparison = uiState.comparison,
                        offset = uiState.comparisonOffset,
                        weightUnit = uiState.weightUnit,
                        isCustomRange = uiState.isCustomRange,
                        currentRange = uiState.currentRange,
                        zone = zone,
                        onOffsetChange = viewModel::setComparisonOffset,
                        onPickRange = { showRangePicker = true },
                    )
                }
                item(key = "volume_by_day") {
                    VolumeByDayCard(uiState = uiState, zone = zone)
                }
                item(key = "categories") {
                    ForgeCard {
                        CardHeader(title = stringResource(R.string.analytics_sets_by_category))
                        ProportionBars(
                            entries = uiState.setsByCategory.map { (category, count) ->
                                BarDatum(label = category.label(), value = count.toDouble())
                            },
                        )
                    }
                }
                item(key = "progress") {
                    ExerciseProgressCard(
                        uiState = uiState,
                        zone = zone,
                        onSelectExercise = viewModel::selectExercise,
                        onSelectWindow = viewModel::setTrendWindow,
                        onExplainFormula = { showFormula = true },
                    )
                }
                if (uiState.records.isNotEmpty()) {
                    item(key = "records") {
                        RecordsCard(records = uiState.records, weightUnit = uiState.weightUnit)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(
    comparison: PeriodComparison?,
    offset: Int,
    weightUnit: ExerciseUnit,
    isCustomRange: Boolean,
    currentRange: WeekBoundary.Range?,
    zone: ZoneId,
    onOffsetChange: (Int) -> Unit,
    onPickRange: () -> Unit,
) {
    ForgeCard {
        CardHeader(
            title = if (isCustomRange && currentRange != null) {
                stringResource(
                    R.string.analytics_compare_custom_title,
                    rangeLabel(currentRange, zone),
                )
            } else {
                stringResource(
                    R.string.analytics_compare_title,
                    if (offset == 1) {
                        stringResource(R.string.analytics_compare_last_week)
                    } else {
                        stringResource(R.string.analytics_compare_weeks_ago, offset)
                    },
                )
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag(TestTags.ANALYTICS_RANGE_PICKER),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1, 2, 4).forEach { weeks ->
                FilterChip(
                    selected = !isCustomRange && offset == weeks,
                    onClick = { onOffsetChange(weeks) },
                    label = {
                        Text(
                            text = if (weeks == 1) {
                                stringResource(R.string.analytics_compare_last_week)
                            } else {
                                stringResource(R.string.analytics_compare_weeks_ago, weeks)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
            FilterChip(
                selected = isCustomRange,
                onClick = onPickRange,
                label = {
                    Text(
                        text = stringResource(R.string.history_range_custom),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
        val current = comparison?.current ?: PeriodMetrics.EMPTY
        StatGrid(
            stats = listOf(
                stringResource(R.string.analytics_metric_sessions) to current.sessionCount.toString(),
                stringResource(R.string.analytics_metric_sets) to current.totalSets.toString(),
                stringResource(R.string.analytics_metric_volume) to
                    current.loadLb.takeIf { it > 0.0 }?.let { Formatters.volume(it, weightUnit) },
                stringResource(R.string.analytics_metric_duration) to
                    Formatters.compactDuration(current.totalDurationMs.takeIf { it > 0L }),
                stringResource(R.string.analytics_metric_avg_session) to
                    Formatters.compactDuration(current.averageSessionDurationMs),
                stringResource(R.string.analytics_metric_avg_rpe) to
                    current.averageRpe?.let { String.format(Locale.US, "%.1f", it) },
            ),
        )
        Formatters.timedSeconds(current.timedSeconds)?.let { timed ->
            Text(
                text = stringResource(R.string.analytics_metric_timed) + ": " + timed,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Change is shown per metric, and says "no baseline" rather than 0% when the earlier window
        // had nothing to compare against.
        comparison?.let { ChangeRow(comparison = it, weightUnit = weightUnit) }
    }
}

@Composable
private fun ChangeRow(comparison: PeriodComparison, weightUnit: ExerciseUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChangeLine(
            label = stringResource(R.string.analytics_metric_volume),
            ratio = comparison.changeRatio { it.loadLb },
            previous = comparison.previous.loadLb
                .takeIf { it > 0.0 }
                ?.let { Formatters.volume(it, weightUnit) },
        )
        ChangeLine(
            label = stringResource(R.string.analytics_metric_sets),
            ratio = comparison.changeRatio { it.totalSets.toDouble() },
            previous = comparison.previous.totalSets.takeIf { it > 0 }?.toString(),
        )
        ChangeLine(
            label = stringResource(R.string.analytics_metric_sessions),
            ratio = comparison.changeRatio { it.sessionCount.toDouble() },
            previous = comparison.previous.sessionCount.takeIf { it > 0 }?.toString(),
        )
    }
}

@Composable
private fun ChangeLine(label: String, ratio: Double?, previous: String?) {
    val percent = ratio?.let { (it * 100).toInt() }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                percent == null -> stringResource(R.string.analytics_change_none)
                percent >= 0 -> stringResource(R.string.analytics_change_up, percent)
                else -> stringResource(R.string.analytics_change_down, percent)
            } + (previous?.let { " (was $it)" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VolumeByDayCard(uiState: AnalyticsUiState, zone: ZoneId) {
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE", Locale.getDefault()) }
    ForgeCard {
        CardHeader(title = stringResource(R.string.analytics_volume_by_day))
        BarChart(
            bars = uiState.volumeByDay.map { day ->
                BarDatum(
                    label = dayFormatter.format(Instant.ofEpochMilli(day.range.start).atZone(zone)),
                    value = day.loadLb,
                )
            },
            valueLabel = { Formatters.volume(it, uiState.weightUnit) },
        )
    }
}

@Composable
private fun ExerciseProgressCard(
    uiState: AnalyticsUiState,
    zone: ZoneId,
    onSelectExercise: (Long?) -> Unit,
    onSelectWindow: (TrendWindow) -> Unit,
    onExplainFormula: () -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    ForgeCard {
        CardHeader(title = stringResource(R.string.analytics_exercise_progress))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendWindow.entries.forEach { window ->
                FilterChip(
                    selected = uiState.trendWindow == window,
                    onClick = { onSelectWindow(window) },
                    label = {
                        Text(
                            text = stringResource(window.labelRes()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }
        OptionDropdown(
            label = stringResource(R.string.history_filter_exercise),
            selected = uiState.exercises.firstOrNull { it.exerciseId == uiState.selectedExerciseId },
            options = uiState.exercises,
            optionLabel = LoggedExercise::displayName,
            onSelect = { onSelectExercise(it?.exerciseId) },
        )
        Text(
            text = stringResource(R.string.analytics_top_set),
            style = MaterialTheme.typography.titleSmall,
        )
        LineChart(
            points = uiState.topSetTrend.map { point ->
                LinePoint(
                    value = point.value,
                    label = dateFormatter.format(Instant.ofEpochMilli(point.epochMs).atZone(zone)),
                )
            },
            valueLabel = { Formatters.volume(it, uiState.weightUnit) },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.analytics_estimated_1rm),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = onExplainFormula) {
                Text(
                    text = stringResource(R.string.analytics_1rm_explain),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        LineChart(
            points = uiState.oneRepMaxTrend.map { point ->
                LinePoint(
                    value = point.value,
                    label = dateFormatter.format(Instant.ofEpochMilli(point.epochMs).atZone(zone)),
                )
            },
            valueLabel = { Formatters.volume(it, uiState.weightUnit) },
        )
        uiState.selectedExerciseRecords?.let { records ->
            RecordRow(records = records, weightUnit = uiState.weightUnit)
        }
    }
}

@Composable
private fun RecordsCard(records: List<ExerciseRecords>, weightUnit: ExerciseUnit) {
    ForgeCard {
        CardHeader(title = stringResource(R.string.analytics_records))
        records.forEach { record ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = record.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                )
                RecordRow(records = record, weightUnit = weightUnit)
            }
        }
    }
}

/** Only the record kinds an exercise can actually hold are shown; the rest are simply absent. */
@Composable
private fun RecordRow(records: ExerciseRecords, weightUnit: ExerciseUnit) {
    val parts = buildList {
        records.heaviestWeight?.weightLb?.let {
            add(stringResource(R.string.analytics_record_heaviest) + " " + Formatters.weight(it, weightUnit))
        }
        records.mostReps?.reps?.let {
            add(stringResource(R.string.analytics_record_reps) + " " + it)
        }
        records.bestSetVolume?.setVolumeLb?.let {
            add(stringResource(R.string.analytics_record_volume) + " " + Formatters.volume(it, weightUnit))
        }
        records.longestDuration?.durationSeconds?.let { seconds ->
            Formatters.timedSeconds(seconds)?.let {
                add(stringResource(R.string.analytics_record_duration) + " " + it)
            }
        }
        records.bestEstimatedOneRepMax?.estimatedOneRepMaxLb?.let {
            add(stringResource(R.string.analytics_record_1rm) + " " + Formatters.weight(it, weightUnit))
        }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun TrendWindow.labelRes(): Int = when (this) {
    TrendWindow.TWELVE_WEEKS -> R.string.analytics_trend_12_weeks
    TrendWindow.SIX_MONTHS -> R.string.analytics_trend_6_months
    TrendWindow.ONE_YEAR -> R.string.analytics_trend_1_year
}

/** A range as the two local dates it covers. The stored end is exclusive, so it names the day before. */
@Composable
private fun rangeLabel(range: WeekBoundary.Range, zone: ZoneId): String {
    val today = LocalDate.now(zone)
    return stringResource(
        R.string.history_range_custom_selected,
        relativeDate(range.start, today, zone),
        relativeDate(range.endExclusive - 1, today, zone),
    )
}

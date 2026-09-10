package dev.happyc0der.forgelog.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.analytics.PrCandidate
import dev.happyc0der.forgelog.domain.analytics.RecordKind
import dev.happyc0der.forgelog.domain.analytics.SessionRecord
import dev.happyc0der.forgelog.domain.home.SessionSummary
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.ui.components.CardHeader
import dev.happyc0der.forgelog.ui.components.EM_DASH
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.ForgeCard
import dev.happyc0der.forgelog.ui.components.ForgeHeroCard
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.components.StatGrid
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.format.relativeDate
import java.time.LocalDate
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.theme.forgeLogColors
import java.time.ZoneId

/**
 * What the workout came to, shown once, straight after finishing.
 *
 * Deliberately not a dialog over the logger: the session is over, and the numbers deserve a page
 * rather than a toast. Abandoning a workout does not come here — there is nothing to celebrate and
 * nothing was completed.
 */
@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    onViewLog: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val summary = uiState.summary

    when {
        uiState.isLoading -> LoadingState(modifier = modifier)
        summary == null -> ErrorState(
            message = uiState.errorMessage ?: stringResource(R.string.state_error_generic),
            onRetry = viewModel::retry,
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag(TestTags.WORKOUT_SUMMARY_SCREEN),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "headline") {
                HeadlineCard(summary = summary, weightUnit = uiState.weightUnit, zone = zone)
            }
            item(key = "records") {
                RecordsCard(records = uiState.records, weightUnit = uiState.weightUnit)
            }
            item(key = "breakdown") {
                BreakdownCard(exercises = uiState.exercises, weightUnit = uiState.weightUnit)
            }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.WORKOUT_SUMMARY_DONE),
                    ) {
                        Text(text = stringResource(R.string.summary_action_done))
                    }
                    TextButton(
                        onClick = { onViewLog(summary.sessionId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.summary_action_view_log))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadlineCard(
    summary: SessionSummary,
    weightUnit: ExerciseUnit,
    zone: ZoneId,
) {
    ForgeHeroCard {
        Text(
            text = stringResource(R.string.summary_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = summary.sessionName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        summary.completedAt?.let { completedAt ->
            Text(
                text = stringResource(
                    R.string.summary_subtitle,
                    relativeDate(completedAt, LocalDate.now(zone), zone),
                    Formatters.timeOfDay(completedAt, zone),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatGrid(
            stats = listOf(
                stringResource(R.string.home_stat_duration) to
                    Formatters.compactDuration(summary.durationMs),
                stringResource(R.string.home_stat_volume) to
                    summary.loadLb.takeIf { it > 0.0 }?.let { Formatters.volume(it, weightUnit) },
                stringResource(R.string.home_stat_sets) to summary.totalSets.toString(),
                stringResource(R.string.home_stat_exercises) to summary.exerciseCount.toString(),
            ),
        )
    }
}

@Composable
private fun RecordsCard(records: List<SessionRecord>, weightUnit: ExerciseUnit) {
    ForgeCard(modifier = Modifier.testTag(TestTags.WORKOUT_SUMMARY_RECORDS)) {
        if (records.isEmpty()) {
            CardHeader(title = stringResource(R.string.summary_records_none_title))
            Text(
                text = stringResource(R.string.summary_records_none_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CardHeader(title = stringResource(R.string.summary_records_title))
            records.forEach { record ->
                RecordRow(record = record, weightUnit = weightUnit)
            }
        }
    }
}

@Composable
private fun RecordRow(record: SessionRecord, weightUnit: ExerciseUnit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            // Decorative: the row's text already says which record this is.
            contentDescription = null,
            tint = MaterialTheme.forgeLogColors.warning,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = record.exerciseName,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(record.kind.labelRes()) + ": " +
                    recordValue(record.kind, record.candidate, weightUnit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = record.previousBest
                    ?.let {
                        stringResource(
                            R.string.summary_record_was,
                            recordValue(record.kind, it, weightUnit),
                        )
                    }
                    ?: stringResource(R.string.summary_record_first),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BreakdownCard(exercises: List<SummaryExerciseUi>, weightUnit: ExerciseUnit) {
    ForgeCard {
        CardHeader(title = stringResource(R.string.summary_breakdown_title))
        exercises.forEach { exercise ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(text = exercise.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (exercise.completedSets == 0) {
                            stringResource(R.string.summary_no_completed_sets)
                        } else {
                            stringResource(R.string.summary_exercise_sets, exercise.completedSets)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = exercise.topSetLabel ?: EM_DASH,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = exercise.volumeLb
                            .takeIf { it > 0.0 }
                            ?.let { Formatters.volume(it, weightUnit) }
                            ?: EM_DASH,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun RecordKind.labelRes(): Int = when (this) {
    RecordKind.HEAVIEST_WEIGHT -> R.string.summary_record_heaviest
    RecordKind.MOST_REPS -> R.string.summary_record_most_reps
    RecordKind.BEST_SET_VOLUME -> R.string.summary_record_set_volume
    RecordKind.LONGEST_DURATION -> R.string.summary_record_duration
    RecordKind.BEST_ESTIMATED_1RM -> R.string.summary_record_one_rep_max
}

@Composable
private fun recordValue(
    kind: RecordKind,
    candidate: PrCandidate,
    weightUnit: ExerciseUnit,
): String = when (kind) {
    RecordKind.HEAVIEST_WEIGHT -> candidate.weightLb?.let { Formatters.load(it, weightUnit) }
    RecordKind.MOST_REPS -> candidate.reps?.let {
        stringResource(R.string.workout_target_summary_reps, it.toString())
    }
    RecordKind.BEST_SET_VOLUME -> candidate.setVolumeLb?.let { Formatters.volume(it, weightUnit) }
    RecordKind.LONGEST_DURATION -> candidate.durationSeconds?.let(Formatters::seconds)
    RecordKind.BEST_ESTIMATED_1RM -> candidate.estimatedOneRepMaxLb?.let {
        Formatters.load(it, weightUnit)
    }
} ?: EM_DASH

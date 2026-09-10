package dev.happyc0der.forgelog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.home.DayPart
import dev.happyc0der.forgelog.domain.home.SessionSummary
import dev.happyc0der.forgelog.domain.home.TrainingTotals
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.WorkoutSession
import dev.happyc0der.forgelog.ui.components.CardHeader
import dev.happyc0der.forgelog.ui.components.ForgeCard
import dev.happyc0der.forgelog.ui.components.ForgeHeroCard
import dev.happyc0der.forgelog.ui.components.StatGrid
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.testing.TestTags
import java.time.LocalDate
import java.time.ZoneId

/** Minimum touch target the design system requires. */
private val ActionHeight = 56.dp

@Composable
internal fun GreetingHeader(
    dayPart: DayPart,
    today: LocalDate?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.HOME_GREETING),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(dayPart.greetingRes()),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (today != null) {
            Text(
                text = Formatters.fullDate(today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DayPart.greetingRes(): Int = when (this) {
    DayPart.MORNING -> R.string.home_greeting_morning
    DayPart.AFTERNOON -> R.string.home_greeting_afternoon
    DayPart.EVENING -> R.string.home_greeting_evening
    DayPart.NIGHT -> R.string.home_greeting_night
}

/**
 * The dominant action when a session is live. It sits above everything else on the screen and the
 * other entry points are suppressed, because starting a second workout is not a thing ForgeLog can
 * do — one session at a time.
 */
@Composable
internal fun ResumeCard(
    session: WorkoutSession,
    elapsedLabel: String,
    onResume: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    ForgeHeroCard(modifier = modifier) {
        CardHeader(title = stringResource(R.string.home_resume_title))
        Text(
            text = session.sessionName.ifBlank { stringResource(R.string.workout_active_title) },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.home_elapsed, elapsedLabel),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Button(
            onClick = { onResume(session.id) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ActionHeight)
                .testTag(TestTags.HOME_RESUME_ACTION),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_resume_workout),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
internal fun StartWorkoutCard(
    hasPrograms: Boolean,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ForgeHeroCard(modifier = modifier) {
        Text(
            text = stringResource(
                if (hasPrograms) R.string.home_title else R.string.home_no_programs_title,
            ),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(
                if (hasPrograms) R.string.home_message else R.string.home_no_programs_message,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasPrograms) {
            Button(
                onClick = onStartWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ActionHeight)
                    .testTag(TestTags.HOME_PRIMARY_ACTION),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_start_workout),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
internal fun LastWorkoutCard(
    summary: SessionSummary?,
    weightUnit: ExerciseUnit,
    today: LocalDate?,
    zone: ZoneId,
    onOpen: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ForgeCard(modifier = modifier.testTag(TestTags.HOME_LAST_WORKOUT)) {
        CardHeader(
            title = stringResource(R.string.home_last_workout_title),
            trailing = {
                if (summary?.completedAt != null && today != null) {
                    Text(
                        text = Formatters.relativeDate(summary.completedAt, today, zone),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            },
        )
        if (summary == null) {
            Text(
                text = stringResource(R.string.home_last_workout_none_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ForgeCard
        }
        Text(
            text = summary.sessionName.ifBlank { stringResource(R.string.workout_adhoc_name) },
            style = MaterialTheme.typography.titleMedium,
        )
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
        val feeling = summary.overallFeeling
        if (feeling != null) {
            Text(
                text = stringResource(R.string.home_stat_feeling) + ": " +
                    stringResource(R.string.home_feeling_value, feeling),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onOpen != null) {
            OutlinedButton(
                onClick = { onOpen(summary.sessionId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(text = stringResource(R.string.history_view_session))
            }
        }
    }
}

@Composable
internal fun WeekSummaryCard(
    totals: TrainingTotals,
    weightUnit: ExerciseUnit,
    modifier: Modifier = Modifier,
) {
    ForgeCard(modifier = modifier.testTag(TestTags.HOME_WEEK_SUMMARY)) {
        CardHeader(title = stringResource(R.string.home_week_title))
        if (totals.sessionCount == 0) {
            Text(
                text = stringResource(R.string.home_week_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ForgeCard
        }
        StatGrid(
            stats = listOf(
                stringResource(R.string.home_stat_sessions) to totals.sessionCount.toString(),
                stringResource(R.string.home_stat_sets) to totals.totalSets.toString(),
                stringResource(R.string.home_stat_volume) to
                    totals.loadLb.takeIf { it > 0.0 }?.let { Formatters.volume(it, weightUnit) },
                stringResource(R.string.home_stat_time) to
                    Formatters.compactDuration(totals.totalDurationMs.takeIf { it > 0L }),
            ),
        )
    }
}

@Composable
internal fun QuickActions(
    showAdHoc: Boolean,
    onStartAdHoc: () -> Unit,
    onCreateProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ForgeCard(modifier = modifier) {
        CardHeader(title = stringResource(R.string.home_quick_actions))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAdHoc) {
                OutlinedButton(
                    onClick = onStartAdHoc,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(TestTags.HOME_ACTION_ADHOC),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FitnessCenter,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.home_action_adhoc))
                }
            }
            OutlinedButton(
                onClick = onCreateProgram,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(TestTags.HOME_ACTION_CREATE_PROGRAM),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.home_action_create_program))
            }
        }
        Spacer(modifier = Modifier.height(0.dp))
    }
}

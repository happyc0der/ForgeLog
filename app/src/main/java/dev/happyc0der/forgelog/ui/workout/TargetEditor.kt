package dev.happyc0der.forgelog.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.ui.format.Formatters

/**
 * Shows what the program asks for today, and lets it be changed for this session only.
 *
 * Every field accepts an empty value meaning "no target", so clearing one is possible rather than
 * being stuck with whatever the program specified. A malformed entry is ignored rather than written
 * as a zero target.
 */
@Composable
internal fun TargetSection(
    item: PlannedExerciseItem,
    weightUnit: ExerciseUnit,
    onTargetChange: (TargetField, Double?) -> Unit,
) {
    var expanded by rememberSaveable(item.localId) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.workout_targets_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(text = stringResource(R.string.workout_targets_edit))
            }
        }
        Text(
            text = targetSummary(item, weightUnit)
                ?: stringResource(R.string.workout_targets_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.workout_targets_session_only),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetField(
                        label = stringResource(R.string.workout_target_sets),
                        value = item.plannedSets?.toString(),
                        onChange = { onTargetChange(TargetField.PLANNED_SETS, it) },
                        modifier = Modifier.weight(1f),
                    )
                    TargetField(
                        label = stringResource(R.string.workout_target_weight),
                        value = item.targetWeight?.toString(),
                        onChange = { onTargetChange(TargetField.WEIGHT, it) },
                        decimal = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetField(
                        label = stringResource(R.string.workout_target_rep_min),
                        value = item.targetRepMin?.toString(),
                        onChange = { onTargetChange(TargetField.REP_MIN, it) },
                        modifier = Modifier.weight(1f),
                    )
                    TargetField(
                        label = stringResource(R.string.workout_target_rep_max),
                        value = item.targetRepMax?.toString(),
                        onChange = { onTargetChange(TargetField.REP_MAX, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetField(
                        label = stringResource(R.string.workout_target_duration),
                        value = item.targetDurationSeconds?.toString(),
                        onChange = { onTargetChange(TargetField.DURATION_SECONDS, it) },
                        modifier = Modifier.weight(1f),
                    )
                    TargetField(
                        label = stringResource(R.string.workout_target_rest),
                        value = item.targetRestSeconds?.toString(),
                        onChange = { onTargetChange(TargetField.REST_SECONDS, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetField(
    label: String,
    value: String?,
    onChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    var draft by rememberSaveable(value) { mutableStateOf(value.orEmpty()) }
    OutlinedTextField(
        value = draft,
        onValueChange = { input ->
            draft = input
            val trimmed = input.trim()
            when {
                // Empty clears the target rather than meaning zero.
                trimmed.isEmpty() -> onChange(null)
                else -> trimmed.toDoubleOrNull()?.let(onChange)
            }
        },
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}

/** Null when nothing is planned, so the caller can say so rather than showing an empty line. */
@Composable
internal fun targetSummary(item: PlannedExerciseItem, weightUnit: ExerciseUnit): String? {
    if (!item.hasTargets) return null
    val parts = buildList {
        item.plannedSets?.let { add(stringResource(R.string.workout_target_summary_sets, it)) }
        val reps = when {
            item.targetRepMin != null && item.targetRepMax != null ->
                "${item.targetRepMin}-${item.targetRepMax}"
            item.targetRepMin != null -> "${item.targetRepMin}+"
            item.targetRepMax != null -> "≤${item.targetRepMax}"
            else -> null
        }
        reps?.let { add(stringResource(R.string.workout_target_summary_reps, it)) }
        item.targetWeight?.let {
            add(stringResource(R.string.workout_target_summary_weight, Formatters.weight(it, weightUnit)))
        }
        item.targetDurationSeconds?.let { add("${it}s") }
        item.targetRestSeconds?.let { add(stringResource(R.string.workout_previous_rest, "${it}s")) }
    }
    return parts.joinToString(" · ")
}

/**
 * The targets snapshotted onto a session exercise, as a single line for the logger.
 *
 * Null when the plan said nothing, so the logger shows no target row at all rather than an empty
 * label — an exercise added mid-session genuinely has no target.
 */
@Composable
internal fun sessionTargetSummary(
    exercise: dev.happyc0der.forgelog.domain.model.SessionExercise,
    weightUnit: ExerciseUnit,
): String? {
    if (!exercise.hasTargets) return null
    val parts = buildList {
        exercise.plannedSets?.let { add(stringResource(R.string.workout_target_summary_sets, it)) }
        val reps = when {
            exercise.targetRepMin != null && exercise.targetRepMax != null ->
                "${exercise.targetRepMin}-${exercise.targetRepMax}"
            exercise.targetRepMin != null -> "${exercise.targetRepMin}+"
            exercise.targetRepMax != null -> "≤${exercise.targetRepMax}"
            else -> null
        }
        reps?.let { add(stringResource(R.string.workout_target_summary_reps, it)) }
        exercise.targetWeight?.let {
            add(stringResource(R.string.workout_target_summary_weight, Formatters.weight(it, weightUnit)))
        }
        exercise.targetDurationSeconds?.let { add("${it}s") }
    }
    return parts.joinToString(" · ")
}

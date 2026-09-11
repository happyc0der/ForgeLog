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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.ExerciseTargets
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.hasTargets
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.input.DurationSecondsField
import dev.happyc0der.forgelog.ui.input.NumericInput
import dev.happyc0der.forgelog.ui.input.bringIntoViewWhenFocused
import dev.happyc0der.forgelog.ui.input.rememberDurationInputUnit
import dev.happyc0der.forgelog.ui.input.rememberRestInputUnit

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
    val (durationUnit, onDurationUnitChange) = rememberDurationInputUnit()
    val (restUnit, onRestUnitChange) = rememberRestInputUnit()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            // The button is 48 dp tall; top-aligned, its label sat a line below the heading.
            verticalAlignment = Alignment.CenterVertically,
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
                        value = item.targetWeight?.let(Formatters::plainNumber),
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
                /*
                 * Duration and rest follow the same sec/min settings as the day builder and the
                 * logger. They were plain seconds here, labelled "(s)", so with rest entered in
                 * minutes everywhere else, typing "3" meant three minutes in the day builder and
                 * three seconds in this very screen.
                 */
                DurationSecondsField(
                    secondsText = item.targetDurationSeconds?.toString().orEmpty(),
                    onSecondsTextChange = { onTargetChange(TargetField.DURATION_SECONDS, it.toDoubleOrNull()) },
                    label = stringResource(R.string.program_exercise_target_duration),
                    unit = durationUnit,
                    onUnitChange = onDurationUnitChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                DurationSecondsField(
                    secondsText = item.targetRestSeconds?.toString().orEmpty(),
                    onSecondsTextChange = { onTargetChange(TargetField.REST_SECONDS, it.toDoubleOrNull()) },
                    label = stringResource(R.string.program_exercise_target_rest),
                    unit = restUnit,
                    onUnitChange = onRestUnitChange,
                    modifier = Modifier.fillMaxWidth(),
                )
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
    /*
     * The draft is the field's own state and is NOT re-keyed on [value].
     *
     * Keying it on [value] fed the field its own output: typing "1" stored 1.0, which came back as
     * "1.0", which changed the key, which reset the draft — so "12" was entered as "1.02". The
     * roster this edits is in-memory and the user is its only writer, so there is nothing to
     * re-sync from. [value] is the initial text and nothing more.
     */
    var draft by rememberSaveable { mutableStateOf(value.orEmpty()) }
    OutlinedTextField(
        value = draft,
        onValueChange = { raw ->
            // A stray "." in an integer target, or "62,5" in a comma locale, used to be ignored
            // silently: the field showed it and the target stayed at the old value.
            val input = NumericInput.accept(raw, decimal) ?: return@OutlinedTextField
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
        modifier = modifier.bringIntoViewWhenFocused(),
    )
}

/**
 * One plan, as a single line — for the planner's roster and for the logger alike.
 *
 * Null when the plan said nothing, so the caller shows no target row at all rather than an empty
 * label: an exercise added mid-session genuinely has no target. Written once over
 * [ExerciseTargets] because the planner and the logger previously had a copy each, and the
 * logger's copy quietly dropped rest.
 */
@Composable
internal fun targetSummary(targets: ExerciseTargets, weightUnit: ExerciseUnit): String? {
    if (!targets.hasTargets) return null
    val parts = buildList {
        targets.plannedSets?.let { add(stringResource(R.string.workout_target_summary_sets, it)) }
        val reps = when {
            // A fixed count, 3 x 5, reads "5 reps" rather than "5-5 reps".
            targets.targetRepMin != null && targets.targetRepMin == targets.targetRepMax ->
                "${targets.targetRepMin}"
            targets.targetRepMin != null && targets.targetRepMax != null ->
                "${targets.targetRepMin}-${targets.targetRepMax}"
            targets.targetRepMin != null -> "${targets.targetRepMin}+"
            targets.targetRepMax != null -> "≤${targets.targetRepMax}"
            else -> null
        }
        reps?.let { add(stringResource(R.string.workout_target_summary_reps, it)) }
        targets.targetWeight?.let {
            add(stringResource(R.string.workout_target_summary_weight, Formatters.weight(it, weightUnit)))
        }
        targets.targetDurationSeconds?.let { add(Formatters.seconds(it)) }
        targets.targetRestSeconds?.let {
            add(stringResource(R.string.workout_previous_rest, Formatters.seconds(it)))
        }
    }
    return parts.joinToString(" · ")
}

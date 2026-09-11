package dev.happyc0der.forgelog.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.domain.workout.asWeightUnit
import dev.happyc0der.forgelog.ui.components.OptionDropdown
import dev.happyc0der.forgelog.ui.input.DurationSecondsField
import dev.happyc0der.forgelog.ui.input.rememberDurationInputUnit
import dev.happyc0der.forgelog.ui.input.rememberRestInputUnit
import dev.happyc0der.forgelog.ui.util.label

/**
 * Post-hoc editor for one logged set.
 *
 * Every field is shown rather than hidden by exercise type: this is the correction path, and the
 * reason someone opens it is usually that the field they need was the one the logger hid. Values
 * are validated before the dialog will close, so a typo cannot silently blank a logged set —
 * an empty field means "not recorded", a malformed one is refused.
 */
@Composable
internal fun SetEditorDialog(
    set: SetLog,
    onSave: (SetLog) -> Unit,
    /** Null for a set being added: there is nothing to delete yet. */
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    isNew: Boolean = false,
) {
    // rememberSaveable throughout: eleven fields of hand-entered corrections, and rotating the
    // phone mid-edit used to discard every one of them. Enums are stored by name, which survives
    // process death where the enum instance would not.
    var reps by rememberSaveable { mutableStateOf(set.reps?.toString().orEmpty()) }
    var weight by rememberSaveable { mutableStateOf(set.weight?.let(Formatters::plainNumber).orEmpty()) }
    // A weight is in lb or kg. Sets logged before that was enforced can hold "Seconds" -- shown as
    // lb everywhere -- and saving here repairs them to what they meant.
    var unitName by rememberSaveable {
        mutableStateOf(set.weightUnit.asWeightUnit(fallback = ExerciseUnit.LB).name)
    }
    var duration by rememberSaveable { mutableStateOf(set.durationSeconds?.toString().orEmpty()) }
    var distance by rememberSaveable { mutableStateOf(set.distanceMeters?.let(Formatters::plainNumber).orEmpty()) }
    var rest by rememberSaveable { mutableStateOf(set.restAfterSetSeconds?.toString().orEmpty()) }
    var rpe by rememberSaveable { mutableStateOf(set.rpe?.toString().orEmpty()) }
    var rir by rememberSaveable { mutableStateOf(set.rir?.toString().orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(set.notes.orEmpty()) }
    var setTypeName by rememberSaveable { mutableStateOf(set.setType.name) }
    var completed by rememberSaveable { mutableStateOf(set.completed) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    val unit = ExerciseUnit.valueOf(unitName)
    val (durationUnit, onDurationUnitChange) = rememberDurationInputUnit()
    val (restUnit, onRestUnitChange) = rememberRestInputUnit()
    val setType = SetType.valueOf(setTypeName)

    fun attemptSave() {
        val parsedReps = reps.optionalInt() ?: return run { error = R.string.session_detail_invalid_number }
        val parsedWeight = weight.optionalDouble() ?: return run { error = R.string.session_detail_invalid_number }
        val parsedDuration = duration.optionalInt() ?: return run { error = R.string.session_detail_invalid_number }
        val parsedDistance = distance.optionalDouble() ?: return run { error = R.string.session_detail_invalid_number }
        val parsedRest = rest.optionalInt() ?: return run { error = R.string.session_detail_invalid_number }
        val parsedRpe = rpe.optionalInt() ?: return run { error = R.string.session_detail_invalid_number }
        val parsedRir = rir.optionalInt() ?: return run { error = R.string.session_detail_invalid_number }

        val rpeValue = parsedRpe.value
        if (rpeValue != null && rpeValue !in RPE_RANGE) {
            error = R.string.session_detail_invalid_rpe
            return
        }
        val rirValue = parsedRir.value
        if (rirValue != null && rirValue !in RIR_RANGE) {
            error = R.string.session_detail_invalid_rir
            return
        }

        onSave(
            set.copy(
                reps = parsedReps.value,
                weight = parsedWeight.value,
                weightUnit = unit,
                durationSeconds = parsedDuration.value,
                distanceMeters = parsedDistance.value,
                restAfterSetSeconds = parsedRest.value,
                rpe = rpeValue,
                rir = rirValue,
                completed = completed,
                notes = notes.trim().ifBlank { null },
                setType = setType,
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isNew) R.string.session_detail_add_set_title else R.string.session_detail_edit_set,
                    set.setNumber,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val setTypeLabels = SetType.entries.associateWith { it.label() }
                OptionDropdown(
                    label = stringResource(R.string.session_detail_field_type),
                    selected = setType,
                    options = SetType.entries,
                    optionLabel = { setTypeLabels.getValue(it) },
                    onSelect = { setTypeName = (it ?: SetType.WORKING).name },
                    anyLabel = setTypeLabels.getValue(SetType.WORKING),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberEntry(
                        label = stringResource(R.string.session_detail_field_reps),
                        value = reps,
                        onValueChange = { reps = it },
                        modifier = Modifier.weight(1f),
                    )
                    NumberEntry(
                        label = stringResource(R.string.session_detail_field_weight),
                        value = weight,
                        onValueChange = { weight = it },
                        decimal = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                val weightUnits = listOf(ExerciseUnit.LB, ExerciseUnit.KG)
                val unitLabels = weightUnits.associateWith { it.label() }
                OptionDropdown(
                    label = stringResource(R.string.session_detail_field_unit),
                    selected = unit,
                    options = weightUnits,
                    optionLabel = { unitLabels.getValue(it) },
                    onSelect = { unitName = (it ?: ExerciseUnit.LB).name },
                    anyLabel = unitLabels.getValue(ExerciseUnit.LB),
                )
                // Duration and rest in the same sec/min units as the logger. They were plain seconds
                // here, so with rest entered in minutes, "3" meant three minutes in the logger and
                // three seconds when correcting the same set afterwards.
                DurationSecondsField(
                    secondsText = duration,
                    onSecondsTextChange = { duration = it },
                    label = stringResource(R.string.session_detail_field_duration),
                    unit = durationUnit,
                    onUnitChange = onDurationUnitChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberEntry(
                    label = stringResource(R.string.session_detail_field_distance),
                    value = distance,
                    onValueChange = { distance = it },
                    decimal = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberEntry(
                        label = stringResource(R.string.session_detail_field_rpe),
                        value = rpe,
                        onValueChange = { rpe = it },
                        modifier = Modifier.weight(1f),
                    )
                    NumberEntry(
                        label = stringResource(R.string.session_detail_field_rir),
                        value = rir,
                        onValueChange = { rir = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                DurationSecondsField(
                    secondsText = rest,
                    onSecondsTextChange = { rest = it },
                    label = stringResource(R.string.session_detail_field_rest),
                    unit = restUnit,
                    onUnitChange = onRestUnitChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(text = stringResource(R.string.session_detail_field_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // One target with its label, as in the logger: a tap on the word did nothing.
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = completed,
                            role = Role.Checkbox,
                            onValueChange = { completed = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = completed, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.session_detail_field_completed),
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                    )
                }
                error?.let { messageRes ->
                    Text(
                        text = stringResource(messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { attemptSave() }) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                onDelete?.let { delete ->
                    TextButton(onClick = delete) {
                        Text(
                            text = stringResource(R.string.session_detail_delete_set),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun NumberEntry(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}

/** Null signals "malformed"; a present box holding null means the field was left empty. */
private fun String.optionalInt(): Parsed<Int>? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return Parsed(null)
    return trimmed.toIntOrNull()?.let { Parsed(it) }
}

private fun String.optionalDouble(): Parsed<Double>? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return Parsed(null)
    return trimmed.toDoubleOrNull()?.let { Parsed(it) }
}

private class Parsed<T>(val value: T?)

private val RPE_RANGE = 1..10
private val RIR_RANGE = 0..10

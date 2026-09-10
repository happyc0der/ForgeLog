package dev.happyc0der.forgelog.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.domain.model.SetLog
import dev.happyc0der.forgelog.domain.model.SetType
import dev.happyc0der.forgelog.ui.components.OptionDropdown
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
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var reps by remember { mutableStateOf(set.reps?.toString().orEmpty()) }
    var weight by remember { mutableStateOf(set.weight?.toString().orEmpty()) }
    var unit by remember { mutableStateOf(set.weightUnit) }
    var duration by remember { mutableStateOf(set.durationSeconds?.toString().orEmpty()) }
    var distance by remember { mutableStateOf(set.distanceMeters?.toString().orEmpty()) }
    var rest by remember { mutableStateOf(set.restAfterSetSeconds?.toString().orEmpty()) }
    var rpe by remember { mutableStateOf(set.rpe?.toString().orEmpty()) }
    var rir by remember { mutableStateOf(set.rir?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(set.notes.orEmpty()) }
    var setType by remember { mutableStateOf(set.setType) }
    var completed by remember { mutableStateOf(set.completed) }
    var error by remember { mutableStateOf<Int?>(null) }

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
        title = { Text(text = stringResource(R.string.session_detail_edit_set, set.setNumber)) },
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
                    onSelect = { setType = it ?: SetType.WORKING },
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
                val unitLabels = ExerciseUnit.entries.associateWith { it.label() }
                OptionDropdown(
                    label = stringResource(R.string.session_detail_field_unit),
                    selected = unit,
                    options = ExerciseUnit.entries,
                    optionLabel = { unitLabels.getValue(it) },
                    onSelect = { unit = it ?: ExerciseUnit.LB },
                    anyLabel = unitLabels.getValue(ExerciseUnit.LB),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberEntry(
                        label = stringResource(R.string.session_detail_field_duration),
                        value = duration,
                        onValueChange = { duration = it },
                        modifier = Modifier.weight(1f),
                    )
                    NumberEntry(
                        label = stringResource(R.string.session_detail_field_distance),
                        value = distance,
                        onValueChange = { distance = it },
                        decimal = true,
                        modifier = Modifier.weight(1f),
                    )
                }
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
                NumberEntry(
                    label = stringResource(R.string.session_detail_field_rest),
                    value = rest,
                    onValueChange = { rest = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(text = stringResource(R.string.session_detail_field_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = completed, onCheckedChange = { completed = it })
                    Text(text = stringResource(R.string.session_detail_field_completed))
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
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.session_detail_delete_set),
                        color = MaterialTheme.colorScheme.error,
                    )
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

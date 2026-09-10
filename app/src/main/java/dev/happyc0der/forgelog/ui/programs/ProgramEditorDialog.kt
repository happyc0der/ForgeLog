package dev.happyc0der.forgelog.ui.programs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.DEFAULT_PROGRAM_COLOR

@Composable
fun ProgramEditorDialog(
    target: ProgramEditorTarget,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, color: String) -> Unit,
) {
    val initialName = (target as? ProgramEditorTarget.Rename)?.program?.name.orEmpty()
    val initialDescription = (target as? ProgramEditorTarget.Rename)?.program?.description.orEmpty()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    val initialColor = (target as? ProgramEditorTarget.Rename)?.program?.color
        ?: DEFAULT_PROGRAM_COLOR
    var color by rememberSaveable { mutableStateOf(initialColor) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    val required = stringResource(R.string.program_name_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (target is ProgramEditorTarget.Create) {
                        R.string.program_create_title
                    } else {
                        R.string.program_rename_title
                    },
                ),
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.program_field_name)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { error -> { Text(text = error) } },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.program_field_description)) },
                    minLines = 2,
                )
                Spacer(modifier = Modifier.height(12.dp))
                // The colour column was stored from the start and never editable, so every program
                // looked identical in the list.
                Text(
                    text = stringResource(R.string.program_field_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PROGRAM_COLORS.forEach { swatch ->
                        val parsed = runCatching {
                            Color(android.graphics.Color.parseColor(swatch))
                        }.getOrDefault(MaterialTheme.colorScheme.primary)
                        val isSelected = swatch.equals(color, ignoreCase = true)
                        val swatchName = stringResource(swatch.colorNameRes())
                        // The dot stays 36dp; the target around it is 48dp. Selection is announced
                        // rather than only drawn, since a border is invisible to a screen reader.
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = { color = swatch },
                                )
                                .semantics { contentDescription = swatchName },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = parsed,
                                border = if (isSelected) {
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground)
                                } else {
                                    null
                                },
                            ) {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = required
                    } else {
                        onConfirm(trimmed, description.trim(), color)
                    }
                },
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Swatches drawn from the app's own palette, rather than a free-form colour picker.
 *
 * A fixed set keeps every program legible against the near-black background, which an arbitrary
 * colour would not.
 */
private val PROGRAM_COLORS = listOf(
    "#A855F7",
    "#C084FC",
    "#7C3AED",
    "#22C55E",
    "#F59E0B",
    "#EF4444",
)

/** A name for each swatch, so the picker is usable without seeing it. */
private fun String.colorNameRes(): Int = when (lowercase()) {
    "#a855f7" -> R.string.program_color_purple
    "#c084fc" -> R.string.program_color_light_purple
    "#7c3aed" -> R.string.program_color_deep_purple
    "#22c55e" -> R.string.program_color_green
    "#f59e0b" -> R.string.program_color_amber
    "#ef4444" -> R.string.program_color_red
    else -> R.string.program_color_other
}

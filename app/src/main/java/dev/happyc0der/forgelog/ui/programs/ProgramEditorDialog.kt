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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { color = swatch },
                            shape = CircleShape,
                            color = parsed,
                            border = if (swatch.equals(color, ignoreCase = true)) {
                                BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground)
                            } else {
                                null
                            },
                        ) {}
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

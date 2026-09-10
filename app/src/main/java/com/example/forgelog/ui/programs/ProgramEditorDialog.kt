package com.example.forgelog.ui.programs

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
import com.example.forgelog.R

@Composable
fun ProgramEditorDialog(
    target: ProgramEditorTarget,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
) {
    val initialName = (target as? ProgramEditorTarget.Rename)?.program?.name.orEmpty()
    val initialDescription = (target as? ProgramEditorTarget.Rename)?.program?.description.orEmpty()
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = required
                    } else {
                        onConfirm(trimmed, description.trim())
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

package dev.happyc0der.forgelog.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.happyc0der.forgelog.R

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    label: String,
    supportingText: String? = null,
    confirmLabel: String = stringResource(R.string.action_save),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    validator: (String) -> String?,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = label) },
                    supportingText = {
                        val text = error ?: supportingText
                        if (text != null) Text(text = text)
                    },
                    isError = error != null,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val validation = validator(value)
                    if (validation != null) {
                        error = validation
                    } else {
                        onConfirm(value.trim())
                    }
                },
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

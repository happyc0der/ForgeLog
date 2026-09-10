package dev.happyc0der.forgelog.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.WorkoutProgram
import dev.happyc0der.forgelog.ui.components.OptionDropdown

/**
 * Turns a logged session into a reusable program day.
 *
 * Confirm stays disabled until a program is chosen and the day has a name — a day with no program
 * has nowhere to live, and an unnamed one is unusable in the builder.
 */
@Composable
internal fun SaveAsProgramDayDialog(
    sessionName: String,
    programs: List<WorkoutProgram>,
    onConfirm: (programId: Long, dayName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedProgram by remember(programs) { mutableStateOf(programs.firstOrNull()) }
    var dayName by remember { mutableStateOf(sessionName.substringAfterLast(" · ").ifBlank { sessionName }) }
    val canConfirm = selectedProgram != null && dayName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.history_save_as_day_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(R.string.history_save_as_day_message))
                if (programs.isEmpty()) {
                    Text(text = stringResource(R.string.home_no_programs_message))
                } else {
                    OptionDropdown(
                        label = stringResource(R.string.history_filter_program),
                        selected = selectedProgram,
                        options = programs,
                        optionLabel = WorkoutProgram::name,
                        onSelect = { selectedProgram = it },
                        anyLabel = stringResource(R.string.history_filter_any),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dayName,
                        onValueChange = { dayName = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.program_day_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val program = selectedProgram ?: return@TextButton
                    onConfirm(program.id, dayName.trim())
                },
                enabled = canConfirm,
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

package dev.happyc0der.forgelog.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.ui.theme.forgeLogColors
import dev.happyc0der.forgelog.domain.model.ExerciseCategory
import dev.happyc0der.forgelog.domain.model.ExerciseUnit
import dev.happyc0der.forgelog.ui.input.bringIntoViewWhenFocused
import dev.happyc0der.forgelog.ui.util.label

data class ExerciseFormState(
    val name: String = "",
    val category: ExerciseCategory = ExerciseCategory.OTHER,
    val defaultUnit: ExerciseUnit = ExerciseUnit.LB,
    val howToUrl: String = "",
    val defaultPointers: String = "",
    val nameError: String? = null,
    /** Set when another exercise already has this name. Advisory only. */
    val duplicateNameWarning: String? = null,
    val howToUrlError: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseForm(
    state: ExerciseFormState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (ExerciseCategory) -> Unit,
    onUnitChange: (ExerciseUnit) -> Unit,
    onHowToUrlChange: (String) -> Unit,
    onPointersChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.exercise_field_name)) },
            isError = state.nameError != null,
            supportingText = {
                // A duplicate name is a warning, not an error: two similar variations can legitimately
                // share a name, and refusing the save would be the app second-guessing the user.
                val message = state.nameError ?: state.duplicateNameWarning
                if (message != null) {
                    Text(
                        text = message,
                        color = if (state.nameError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.forgeLogColors.warning
                        },
                    )
                }
            },
            singleLine = true,
        )
        EnumDropdown(
            label = stringResource(R.string.exercise_field_category),
            selected = state.category,
            options = ExerciseCategory.entries,
            optionLabel = { it.label() },
            onSelected = onCategoryChange,
        )
        EnumDropdown(
            label = stringResource(R.string.exercise_field_unit),
            selected = state.defaultUnit,
            options = ExerciseUnit.entries,
            optionLabel = { it.label() },
            onSelected = onUnitChange,
        )
        OutlinedTextField(
            value = state.howToUrl,
            onValueChange = onHowToUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.exercise_field_how_to)) },
            isError = state.howToUrlError != null,
            supportingText = {
                Text(
                    text = state.howToUrlError ?: stringResource(R.string.exercise_how_to_helper),
                    color = if (state.howToUrlError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.defaultPointers,
            onValueChange = onPointersChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(R.string.exercise_field_pointers)) },
            minLines = 4,
            supportingText = { Text(text = stringResource(R.string.exercise_pointers_helper)) },
        )
        // What to actually put in the pointers box. Without this the field is just an empty note
        // field, which is why it tended to stay empty.
        Text(
            text = stringResource(R.string.exercise_pointers_examples),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EnumDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .bringIntoViewWhenFocused(),
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

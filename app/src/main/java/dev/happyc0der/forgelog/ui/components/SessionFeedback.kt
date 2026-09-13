package dev.happyc0der.forgelog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.model.StoredNumbers

/**
 * How a session or an exercise felt: the scale this control offers.
 *
 * Not its own scale — the same one the backup importer checks on the way back in, so the two cannot
 * drift into a file the app will not restore.
 */
val FEELING_RANGE = StoredNumbers.FEELING_RANGE

/**
 * The feeling picker, shared by the logger and the session detail screen.
 *
 * Tapping the selected value clears it: a feeling recorded by accident has to be removable, and
 * null means "not said" rather than "bad".
 */
@Composable
fun FeelingRow(
    feeling: Int?,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FEELING_RANGE.forEach { value ->
            FilterChip(
                selected = feeling == value,
                enabled = enabled,
                onClick = { onChange(if (feeling == value) null else value) },
                label = { Text(text = value.toString()) },
            )
        }
        if (feeling == null) {
            Text(
                text = stringResource(R.string.home_value_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A notes field committed on demand rather than per keystroke, so a long note is one database write
 * instead of one per character.
 *
 * The draft is keyed on [initial] so that an edit made elsewhere is picked up, but the save button
 * is what writes: keying alone would fight the user's typing if the committed value round-tripped.
 */
@Composable
fun NotesField(
    label: String,
    initial: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(text = label) },
        minLines = 2,
        modifier = modifier.fillMaxWidth(),
    )
    TextButton(
        onClick = { onCommit(draft) },
        enabled = draft != initial,
    ) {
        Text(text = stringResource(R.string.action_save))
    }
}

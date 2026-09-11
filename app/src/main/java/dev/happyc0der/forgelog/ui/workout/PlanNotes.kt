package dev.happyc0der.forgelog.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R

/*
 * Notes written in the day builder, shown where they are needed: while training.
 *
 * Both kinds of note were saved and then never shown anywhere but the builder itself, so a day's
 * warm-up, or "last time: 3x7 at 20 lb, aim for 8", was out of sight at exactly the moment it was
 * meant for.
 */

/** A program exercise's own notes, under its pointers. Nothing at all when there are none. */
@Composable
internal fun PlanNotesBlock(notes: String?) {
    val text = notes?.takeIf { it.isNotBlank() } ?: return
    Text(
        text = stringResource(R.string.workout_plan_notes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

/**
 * The day's notes, above its exercises.
 *
 * [collapsible] cuts them to three lines with a toggle -- right for the logger, where they have
 * already been read and would otherwise push the first lift down a screen for the whole session.
 */
@Composable
internal fun DayNotesCard(notes: String, collapsible: Boolean, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(!collapsible) }
    var overflows by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.program_day_notes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout -> if (!expanded) overflows = layout.hasVisualOverflow },
                modifier = Modifier.padding(top = 4.dp),
            )
            if (collapsible && (overflows || expanded)) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (expanded) R.string.action_show_less else R.string.action_show_more,
                        ),
                    )
                }
            }
        }
    }
}

private const val COLLAPSED_LINES = 3

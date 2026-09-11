package dev.happyc0der.forgelog.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.workout.ProgressionHint
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.theme.forgeLogColors

/**
 * "Ready to progress": why, and the weight to go up to.
 *
 * With [onUse], each suggested weight is a button that makes it today's target -- the planner. The
 * logger shows it as a line of text, since its sets are typed in directly.
 */
@Composable
internal fun ProgressionHintBlock(
    hint: ProgressionHint,
    modifier: Modifier = Modifier,
    onUse: ((Double) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.PROGRESSION_HINT),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.progression_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.forgeLogColors.success,
        )
        val reason = pluralStringResource(
            R.plurals.progression_reason,
            hint.setCount,
            hint.setCount,
            pluralStringResource(R.plurals.reps_count, hint.reps, hint.reps),
            Formatters.weight(hint.fromWeight, hint.unit),
        )
        Text(
            text = if (onUse == null) {
                reason + " " + stringResource(R.string.progression_try, suggestedRange(hint))
            } else {
                reason
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onUse != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hint.options.forEach { weight ->
                    OutlinedButton(
                        onClick = { onUse(weight) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(text = stringResource(R.string.progression_use, Formatters.weight(weight, hint.unit)))
                    }
                }
            }
        }
    }
}

/** "160 lb", or "160–165 lb" for a lower-body lift's two steps. */
private fun suggestedRange(hint: ProgressionHint): String {
    val lightest = hint.options.first()
    val heaviest = hint.options.last()
    return if (hint.options.size == 1) {
        Formatters.weight(lightest, hint.unit)
    } else {
        Formatters.plainNumber(lightest) + "–" + Formatters.weight(heaviest, hint.unit)
    }
}

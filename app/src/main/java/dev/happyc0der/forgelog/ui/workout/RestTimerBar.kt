package dev.happyc0der.forgelog.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.domain.workout.RestTimer
import dev.happyc0der.forgelog.ui.format.Formatters
import dev.happyc0der.forgelog.ui.theme.forgeLogColors

/**
 * Rest countdown.
 *
 * Deliberately a slim bar rather than a dialog: the spec requires rest to count down *without
 * blocking logging*, so the user can still correct the set they just finished while it runs. An
 * overrun keeps counting up instead of vanishing, because "two minutes over" is information.
 */
@Composable
internal fun RestTimerBar(
    state: RestTimerUi,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = state.isActive, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isOverrun) {
                                // "1m 10s" rather than "70s": with three-minute rests, running
                                // two over read as "over by 130s".
                                stringResource(
                                    R.string.rest_timer_overrun,
                                    Formatters.seconds(state.overrunSeconds),
                                )
                            } else {
                                stringResource(R.string.rest_timer_remaining, state.remainingLabel)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.isOverrun) {
                                MaterialTheme.forgeLogColors.warning
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = if (state.isPaused) {
                                stringResource(R.string.rest_timer_paused)
                            } else {
                                stringResource(
                                    R.string.rest_timer_target,
                                    Formatters.seconds(state.targetSeconds),
                                )
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onAdjust(-RestTimer.ADJUST_STEP_SECONDS) }) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.rest_timer_shorten),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { onAdjust(RestTimer.ADJUST_STEP_SECONDS) }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.rest_timer_lengthen),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { if (state.isPaused) onResume() else onPause() }) {
                        Icon(
                            imageVector = if (state.isPaused) {
                                Icons.Filled.PlayArrow
                            } else {
                                Icons.Filled.Pause
                            },
                            contentDescription = stringResource(
                                if (state.isPaused) {
                                    R.string.rest_timer_resume
                                } else {
                                    R.string.rest_timer_pause
                                },
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onSkip) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.rest_timer_skip),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (state.isOverrun) {
                        MaterialTheme.forgeLogColors.warning
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}

package dev.happyc0der.forgelog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.data.local.UnreadableDatabase
import dev.happyc0der.forgelog.ui.components.CardHeader
import dev.happyc0der.forgelog.ui.components.ErrorState
import dev.happyc0der.forgelog.ui.components.ForgeCard
import dev.happyc0der.forgelog.ui.components.LoadingState
import dev.happyc0der.forgelog.ui.testing.TestTags
import dev.happyc0der.forgelog.ui.format.currentZone

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onResumeWorkout: (Long) -> Unit,
    onStartAdHoc: () -> Unit,
    onCreateProgram: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSession: ((Long) -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Display-only: the ViewModel already resolves every timestamp it reasons about.
    val zone = currentZone()

    when {
        uiState.isLoading -> LoadingState(modifier = modifier)
        uiState.errorMessage != null -> ErrorState(
            message = uiState.errorMessage ?: stringResource(R.string.state_error_generic),
            onRetry = viewModel::retry,
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag(TestTags.HOME_SCREEN),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "greeting") {
                GreetingHeader(dayPart = uiState.dayPart, today = uiState.today)
            }

            // Above everything: it is the only sign the user gets that anything was lost, and the
            // empty screen behind it otherwise reads as a fresh install.
            val unreadable = uiState.unreadableDatabase
            if (unreadable != null) {
                item(key = "data-unreadable") {
                    UnreadableDatabaseCard(
                        unreadable = unreadable,
                        onDismiss = viewModel::dismissUnreadableDatabaseNotice,
                    )
                }
            }

            val inProgress = uiState.inProgress
            if (inProgress != null) {
                item(key = "resume") {
                    ResumeCard(
                        session = inProgress,
                        elapsedLabel = uiState.elapsedLabel,
                        onResume = onResumeWorkout,
                    )
                }
            } else {
                item(key = "start") {
                    StartWorkoutCard(
                        hasPrograms = uiState.hasPrograms,
                        onStartWorkout = onStartWorkout,
                    )
                }
            }

            item(key = "last_workout") {
                LastWorkoutCard(
                    summary = uiState.lastWorkout,
                    weightUnit = uiState.weightUnit,
                    today = uiState.today,
                    zone = zone,
                    onOpen = onOpenSession.takeIf { uiState.lastWorkout != null },
                )
            }

            item(key = "week") {
                WeekSummaryCard(totals = uiState.week, weightUnit = uiState.weightUnit)
            }

            item(key = "quick_actions") {
                QuickActions(
                    // One session at a time: offering another start while one runs is a dead end.
                    showAdHoc = inProgress == null,
                    onStartAdHoc = onStartAdHoc,
                    onCreateProgram = onCreateProgram,
                )
            }
        }
    }
}

/**
 * Says that the database could not be read and the app started again empty.
 *
 * Shown until dismissed rather than as a snackbar: it is the only notice the user gets, it points
 * at the one action that recovers anything, and a message about losing training history should not
 * disappear on its own after four seconds.
 */
@Composable
private fun UnreadableDatabaseCard(
    unreadable: UnreadableDatabase,
    onDismiss: () -> Unit,
) {
    ForgeCard {
        CardHeader(title = stringResource(R.string.home_data_unreadable_title))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.home_data_unreadable_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = unreadable.preservedFileName?.let {
                    stringResource(R.string.home_data_unreadable_kept, it)
                } ?: stringResource(R.string.home_data_unreadable_not_kept),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.home_data_unreadable_dismiss))
            }
        }
    }
}

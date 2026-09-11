package dev.happyc0der.forgelog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.ui.components.ErrorState
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

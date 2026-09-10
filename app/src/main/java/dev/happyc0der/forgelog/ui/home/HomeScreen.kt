package dev.happyc0der.forgelog.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.happyc0der.forgelog.R
import dev.happyc0der.forgelog.ui.components.EmptyState
import dev.happyc0der.forgelog.ui.components.LoadingState

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    onResumeWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        uiState.isLoading -> LoadingState(modifier = modifier)
        uiState.inProgress != null -> {
            val session = uiState.inProgress
            EmptyState(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.home_resume_title),
                message = stringResource(
                    R.string.home_resume_message,
                    session?.sessionName.orEmpty(),
                ),
                modifier = modifier,
                action = {
                    Text(
                        text = stringResource(R.string.home_elapsed, uiState.elapsedLabel),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    HomeActionButton(
                        label = stringResource(R.string.home_resume_workout),
                        onClick = { session?.id?.let(onResumeWorkout) },
                    )
                },
            )
        }
        !uiState.hasPrograms -> EmptyState(
            icon = Icons.Outlined.FitnessCenter,
            title = stringResource(R.string.home_title),
            message = stringResource(R.string.home_start_workout_empty),
            modifier = modifier,
        )
        else -> EmptyState(
            icon = Icons.Filled.PlayArrow,
            title = stringResource(R.string.home_title),
            message = stringResource(R.string.home_message),
            modifier = modifier,
            action = {
                HomeActionButton(
                    label = stringResource(R.string.home_start_workout),
                    onClick = onStartWorkout,
                )
            },
        )
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

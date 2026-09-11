package dev.happyc0der.forgelog.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import dev.happyc0der.forgelog.R

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
) {
    val loadingLabel = stringResource(R.string.state_loading)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            // Otherwise the loading state is silent to a screen reader.
            modifier = Modifier.semantics { contentDescription = loadingLabel },
        )
    }
}

/**
 * [actionLabel] says what [onRetry] does. Screens whose only way out is back used to show "Retry"
 * on a button that went back.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = stringResource(R.string.action_retry),
) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(R.string.state_error_title),
        message = message,
        modifier = modifier,
        action = {
            Button(onClick = onRetry) {
                Text(text = actionLabel)
            }
        },
    )
}

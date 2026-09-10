package com.example.forgelog.ui.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.forgelog.R
import com.example.forgelog.ui.components.EmptyState

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.History,
        title = stringResource(R.string.history_title),
        message = stringResource(R.string.history_message),
        modifier = modifier,
    )
}

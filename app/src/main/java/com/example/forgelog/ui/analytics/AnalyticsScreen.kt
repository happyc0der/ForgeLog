package com.example.forgelog.ui.analytics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.forgelog.R
import com.example.forgelog.ui.components.EmptyState

@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.Insights,
        title = stringResource(R.string.analytics_title),
        message = stringResource(R.string.analytics_message),
        modifier = modifier,
    )
}

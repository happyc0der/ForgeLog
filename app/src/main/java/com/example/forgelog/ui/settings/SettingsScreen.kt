package com.example.forgelog.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.forgelog.R
import com.example.forgelog.ui.components.EmptyState

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.Settings,
        title = stringResource(R.string.settings_title),
        message = stringResource(R.string.settings_message),
        modifier = modifier,
    )
}

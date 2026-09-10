package com.example.forgelog.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.forgelog.R
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object ProgramsRoute

@Serializable
data object HistoryRoute

@Serializable
data object AnalyticsRoute

@Serializable
data object SettingsRoute

@Serializable
data class ExerciseLibraryRoute(
    val picker: Boolean = false,
)

@Serializable
data class ExerciseEditorRoute(
    val exerciseId: Long = -1L,
)

@Serializable
data class ProgramDetailRoute(
    val programId: Long,
)

@Serializable
data class ProgramDayBuilderRoute(
    val dayId: Long,
)

@Serializable
data class StartWorkoutRoute(
    val programDayId: Long = -1L,
)

@Serializable
data class ActiveWorkoutRoute(
    val sessionId: Long,
)

enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(
        route = HomeRoute,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    Programs(
        route = ProgramsRoute,
        labelRes = R.string.nav_programs,
        selectedIcon = Icons.Filled.FitnessCenter,
        unselectedIcon = Icons.Outlined.FitnessCenter,
    ),
    History(
        route = HistoryRoute,
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
    ),
    Analytics(
        route = AnalyticsRoute,
        labelRes = R.string.nav_analytics,
        selectedIcon = Icons.Filled.Insights,
        unselectedIcon = Icons.Outlined.Insights,
    ),
    Settings(
        route = SettingsRoute,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}

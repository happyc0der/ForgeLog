package com.example.forgelog.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.forgelog.ui.analytics.AnalyticsScreen
import com.example.forgelog.ui.exercise.ExerciseEditorScreen
import com.example.forgelog.ui.exercise.ExerciseLibraryScreen
import com.example.forgelog.ui.history.HistoryScreen
import com.example.forgelog.ui.home.HomeScreen
import com.example.forgelog.ui.programs.ProgramDayBuilderScreen
import com.example.forgelog.ui.programs.ProgramDayBuilderViewModel
import com.example.forgelog.ui.programs.ProgramDetailScreen
import com.example.forgelog.ui.programs.ProgramsScreen
import com.example.forgelog.ui.settings.SettingsScreen
import com.example.forgelog.ui.workout.ActiveWorkoutScreen
import com.example.forgelog.ui.workout.StartWorkoutScreen
import com.example.forgelog.ui.workout.StartWorkoutViewModel
import com.example.forgelog.ui.workout.rememberWorkoutNotificationStarter
import com.example.forgelog.workout.WorkoutForegroundService

@Composable
fun ForgeLogNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val startWorkoutService = rememberWorkoutNotificationStarter()

    fun openLogger(sessionId: Long, popPlanner: Boolean) {
        startWorkoutService()
        navController.navigate(ActiveWorkoutRoute(sessionId)) {
            launchSingleTop = true
            if (popPlanner) {
                navController.currentDestination?.id?.let { destinationId ->
                    popUpTo(destinationId) { inclusive = true }
                }
            }
        }
    }

    fun leaveLogger() {
        WorkoutForegroundService.stop(context)
        navController.popBackStack(HomeRoute, inclusive = false)
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onStartWorkout = { navController.navigate(StartWorkoutRoute()) },
                onResumeWorkout = { sessionId -> openLogger(sessionId, popPlanner = false) },
            )
        }
        composable<ProgramsRoute> {
            ProgramsScreen(
                onOpenProgram = { programId ->
                    navController.navigate(ProgramDetailRoute(programId))
                },
                onOpenExerciseLibrary = {
                    navController.navigate(ExerciseLibraryRoute())
                },
            )
        }
        composable<ProgramDetailRoute> {
            ProgramDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenDay = { dayId ->
                    navController.navigate(ProgramDayBuilderRoute(dayId))
                },
                onStartDay = { dayId ->
                    navController.navigate(StartWorkoutRoute(programDayId = dayId))
                },
            )
        }
        composable<ProgramDayBuilderRoute> { entry ->
            val viewModel: ProgramDayBuilderViewModel = hiltViewModel()
            val pickedId by entry.savedStateHandle
                .getStateFlow<Long?>(PICKED_EXERCISE_ID, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(pickedId) {
                val id = pickedId ?: return@LaunchedEffect
                viewModel.addExercise(id)
                entry.savedStateHandle.remove<Long>(PICKED_EXERCISE_ID)
            }
            ProgramDayBuilderScreen(
                onBack = { navController.popBackStack() },
                onPickFromLibrary = {
                    navController.navigate(ExerciseLibraryRoute(picker = true))
                },
                onDayDuplicated = { dayId ->
                    navController.popBackStack()
                    navController.navigate(ProgramDayBuilderRoute(dayId))
                },
                onStartDay = { dayId ->
                    navController.navigate(StartWorkoutRoute(programDayId = dayId))
                },
                viewModel = viewModel,
            )
        }
        composable<StartWorkoutRoute> { entry ->
            val viewModel: StartWorkoutViewModel = hiltViewModel()
            val pickedId by entry.savedStateHandle
                .getStateFlow<Long?>(PICKED_EXERCISE_ID, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(pickedId) {
                val id = pickedId ?: return@LaunchedEffect
                viewModel.addExercise(id)
                entry.savedStateHandle.remove<Long>(PICKED_EXERCISE_ID)
            }
            StartWorkoutScreen(
                onBack = { navController.popBackStack() },
                onPickFromLibrary = {
                    navController.navigate(ExerciseLibraryRoute(picker = true))
                },
                onStarted = { sessionId -> openLogger(sessionId, popPlanner = true) },
                viewModel = viewModel,
            )
        }
        composable<ActiveWorkoutRoute> {
            ActiveWorkoutScreen(
                onBack = { navController.popBackStack() },
                onLeave = { leaveLogger() },
            )
        }
        composable<ExerciseLibraryRoute> { entry ->
            val picker = entry.toRoute<ExerciseLibraryRoute>().picker
            ExerciseLibraryScreen(
                picker = picker,
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate(ExerciseEditorRoute()) },
                onEdit = { exerciseId ->
                    navController.navigate(ExerciseEditorRoute(exerciseId))
                },
                onPicked = { exerciseId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(PICKED_EXERCISE_ID, exerciseId)
                    navController.popBackStack()
                },
            )
        }
        composable<ExerciseEditorRoute> {
            ExerciseEditorScreen(
                onBack = { navController.popBackStack() },
                onSaved = { exerciseId ->
                    navController.popBackStack()
                    val libraryRoute = runCatching {
                        navController.currentBackStackEntry?.toRoute<ExerciseLibraryRoute>()
                    }.getOrNull()
                    if (libraryRoute?.picker == true) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(PICKED_EXERCISE_ID, exerciseId)
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen()
        }
        composable<AnalyticsRoute> {
            AnalyticsScreen()
        }
        composable<SettingsRoute> {
            SettingsScreen()
        }
    }
}

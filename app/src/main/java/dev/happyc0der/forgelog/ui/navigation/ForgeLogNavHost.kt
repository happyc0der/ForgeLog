package dev.happyc0der.forgelog.ui.navigation

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
import dev.happyc0der.forgelog.ui.analytics.AnalyticsScreen
import dev.happyc0der.forgelog.ui.exercise.ExerciseEditorScreen
import dev.happyc0der.forgelog.ui.exercise.ExerciseLibraryScreen
import dev.happyc0der.forgelog.ui.history.HistoryScreen
import dev.happyc0der.forgelog.ui.history.SessionDetailScreen
import dev.happyc0der.forgelog.ui.home.HomeScreen
import dev.happyc0der.forgelog.ui.programs.ProgramDayBuilderScreen
import dev.happyc0der.forgelog.ui.programs.ProgramDayBuilderViewModel
import dev.happyc0der.forgelog.ui.programs.ProgramDetailScreen
import dev.happyc0der.forgelog.ui.programs.ProgramsScreen
import dev.happyc0der.forgelog.ui.settings.SettingsScreen
import dev.happyc0der.forgelog.ui.workout.ActiveWorkoutScreen
import dev.happyc0der.forgelog.ui.workout.StartWorkoutScreen
import dev.happyc0der.forgelog.ui.workout.StartWorkoutViewModel
import dev.happyc0der.forgelog.ui.workout.WorkoutSummaryScreen
import dev.happyc0der.forgelog.ui.workout.rememberWorkoutNotificationStarter
import dev.happyc0der.forgelog.workout.WorkoutForegroundService

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

    /**
     * Replaces the logger with the summary rather than stacking on it: the workout is over, and
     * backing out of the summary must not land on a log that can no longer be added to.
     */
    fun openSummary(sessionId: Long) {
        WorkoutForegroundService.stop(context)
        navController.navigate(WorkoutSummaryRoute(sessionId)) {
            launchSingleTop = true
            popUpTo<ActiveWorkoutRoute> { inclusive = true }
        }
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
                onStartAdHoc = { navController.navigate(StartWorkoutRoute(adHoc = true)) },
                onCreateProgram = { navController.navigate(ProgramsRoute) },
                onOpenSession = { sessionId -> navController.navigate(SessionDetailRoute(sessionId)) },
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
        composable<ActiveWorkoutRoute> { entry ->
            val sessionId = entry.toRoute<ActiveWorkoutRoute>().sessionId
            ActiveWorkoutScreen(
                onBack = { navController.popBackStack() },
                onLeave = { leaveLogger() },
                onFinished = { openSummary(sessionId) },
            )
        }
        composable<WorkoutSummaryRoute> {
            WorkoutSummaryScreen(
                onDone = { navController.popBackStack(HomeRoute, inclusive = false) },
                onViewLog = { id ->
                    navController.navigate(SessionDetailRoute(id)) { launchSingleTop = true }
                },
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
            HistoryScreen(
                onOpenSession = { sessionId -> navController.navigate(SessionDetailRoute(sessionId)) },
                onResumeWorkout = { sessionId -> openLogger(sessionId, popPlanner = false) },
            )
        }
        composable<SessionDetailRoute> {
            SessionDetailScreen(
                onBack = { navController.popBackStack() },
                onResumeWorkout = { sessionId -> openLogger(sessionId, popPlanner = false) },
            )
        }
        composable<AnalyticsRoute> {
            AnalyticsScreen()
        }
        composable<SettingsRoute> {
            SettingsScreen()
        }
    }
}

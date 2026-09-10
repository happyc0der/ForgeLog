package dev.happyc0der.forgelog.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.happyc0der.forgelog.workout.WorkoutForegroundService

/**
 * Starts the workout service, asking for the notification permission if it is not already held.
 *
 * The service is started exactly once per call, before the ask. It has to run either way — it is
 * what keeps the session's clock alive when the app is not on screen — and the permission only
 * decides whether its notification is visible. Starting it again from the permission callback, as
 * this used to, meant every first workout on Android 13+ started the service twice.
 *
 * A denial is respected by doing nothing further: Android stops showing the dialog by itself after
 * two refusals, so there is no need to track it here.
 */
@Composable
fun rememberWorkoutNotificationStarter(): () -> Unit {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // Granted or refused, the service is already running. Nothing to do either way.
    }
    return remember(permissionLauncher, context) {
        {
            WorkoutForegroundService.start(context)
            val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            if (needsAsk) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

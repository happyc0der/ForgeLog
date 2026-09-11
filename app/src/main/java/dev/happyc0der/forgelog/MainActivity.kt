package dev.happyc0der.forgelog

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import dev.happyc0der.forgelog.ui.ForgeLogApp
import dev.happyc0der.forgelog.ui.theme.ForgeLogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * Taps on the workout notification while the app is already running, for it to go back to the
     * logger. They otherwise only brought the app forward, on whatever screen was last open.
     * A tap that starts the app needs nothing: a cold start already opens a workout in progress.
     */
    private val openWorkoutRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_WORKOUT) openWorkoutRequests.trySend(Unit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            ForgeLogTheme {
                ForgeLogApp(openWorkoutRequests = openWorkoutRequests.receiveAsFlow())
            }
        }
    }

    companion object {
        /** The workout notification's tap: back to the workout in progress. */
        const val ACTION_OPEN_WORKOUT = "dev.happyc0der.forgelog.action.OPEN_WORKOUT"
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ForgeLogAppPreview() {
    ForgeLogTheme {
        ForgeLogApp()
    }
}

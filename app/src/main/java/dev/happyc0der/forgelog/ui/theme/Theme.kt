package dev.happyc0der.forgelog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val ForgeLogDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = Accent,
    secondary = Accent,
    onSecondary = OnPrimary,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = Accent,
    onTertiary = OnPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextMuted,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceElevated,
    outline = TextMuted.copy(alpha = 0.48f),
    outlineVariant = TextMuted.copy(alpha = 0.24f),
    inverseSurface = TextPrimary,
    inverseOnSurface = Background,
    inversePrimary = Primary,
    error = Error,
    onError = OnError,
    scrim = Color.Black.copy(alpha = 0.72f),
)

@Composable
fun ForgeLogTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalForgeLogColors provides ForgeLogDarkExtendedColors) {
        MaterialTheme(
            colorScheme = ForgeLogDarkColorScheme,
            shapes = ForgeLogShapes,
            typography = Typography,
            content = content,
        )
    }
}

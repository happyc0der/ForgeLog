package dev.happyc0der.forgelog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Colours the design system needs that Material 3 has no slot for.
 *
 * Success and warning are genuinely semantic — "improved" versus "decreased", completed versus
 * abandoned — so they must not be smuggled into [MaterialTheme.colorScheme]'s tertiary slot,
 * where the next theme tweak would silently change their meaning.
 *
 * Access through [MaterialTheme.forgeLogColors].
 */
@Immutable
data class ForgeLogColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val secondaryAccent: Color,
) {
    /** Subtle purple wash for hero surfaces. Keep it low-contrast; text sits on top of it. */
    val purpleGradient: Brush
        get() = Brush.linearGradient(listOf(secondaryAccent.copy(alpha = 0.38f), Color.Transparent))
}

internal val ForgeLogDarkExtendedColors = ForgeLogColors(
    success = Success,
    onSuccess = OnSuccess,
    warning = Warning,
    onWarning = OnWarning,
    secondaryAccent = SecondaryAccent,
)

internal val LocalForgeLogColors = staticCompositionLocalOf { ForgeLogDarkExtendedColors }

val MaterialTheme.forgeLogColors: ForgeLogColors
    @Composable
    @ReadOnlyComposable
    get() = LocalForgeLogColors.current

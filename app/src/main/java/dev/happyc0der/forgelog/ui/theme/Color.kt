package dev.happyc0der.forgelog.ui.theme

import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0B0B0F)
val Surface = Color(0xFF15151D)
val SurfaceElevated = Color(0xFF1E1B2E)
val Primary = Color(0xFFA855F7)
val Accent = Color(0xFFC084FC)

/**
 * Deeper purple used for gradients and filled containers. Deliberately not mapped to the
 * Material [androidx.compose.material3.ColorScheme.secondary] slot: that slot is used for
 * emphasis *text* across the app, and this colour does not carry enough contrast against
 * [Background] to stay readable at body sizes.
 */
val SecondaryAccent = Color(0xFF7C3AED)

val TextPrimary = Color(0xFFF5F3FF)
val TextMuted = Color(0xFFA1A1AA)
val OnPrimary = Color(0xFF1A0A2E)
val Error = Color(0xFFEF4444)
val OnError = Color(0xFF1A0A0A)

val Success = Color(0xFF22C55E)
val OnSuccess = Color(0xFF052E16)
val Warning = Color(0xFFF59E0B)
val OnWarning = Color(0xFF271503)

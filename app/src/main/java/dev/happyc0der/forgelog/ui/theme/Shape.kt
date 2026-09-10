package dev.happyc0der.forgelog.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Cards land on 16dp ([Shapes.medium]) to 20dp ([Shapes.large]) as the design system requires.
 * Read corner radii from `MaterialTheme.shapes` rather than hardcoding a shape at a call site,
 * so the whole app moves together.
 */
val ForgeLogShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

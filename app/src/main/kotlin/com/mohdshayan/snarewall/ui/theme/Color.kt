package com.mohdshayan.snarewall.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Limestone and snare emerald (BLUEPRINT section 7). Six tokens per mode plus three board-only
 * region tints. Emerald is the one accent; Route marks only the thread, the step chip and warnings.
 */

// Light mode
val LightLimestone = Color(0xFFE3E7E1)
val LightFlag = Color(0xFFF3F5F1)
val LightInk = Color(0xFF17211D)
val LightLichen = Color(0xFF4D5B54)
val LightEmerald = Color(0xFF1A7550)
val LightRoute = Color(0xFFA63F14)

// Dark mode
val DarkLimestone = Color(0xFF141C19)
val DarkFlag = Color(0xFF1E2824)
val DarkInk = Color(0xFFE2E8E4)
val DarkLichen = Color(0xFF9DAAA3)
val DarkEmerald = Color(0xFF4DBE8B)
val DarkRoute = Color(0xFFE68E5A)

// Board-only region tints, laid over Flag at 8 percent.
val RegionTintChalk = Color(0xFF9FB0A6)
val RegionTintSalt = Color(0xFF7F9DB0)
val RegionTintFen = Color(0xFF5F8A4E)

/** The named tokens, for places Material 3 does not model (the board canvas, the step chip). */
@Immutable
data class SnareColors(
    val limestone: Color,
    val flag: Color,
    val ink: Color,
    val lichen: Color,
    val emerald: Color,
    val route: Color,
    val onEmerald: Color,
    val isDark: Boolean,
)

val LightSnare = SnareColors(LightLimestone, LightFlag, LightInk, LightLichen, LightEmerald, LightRoute, LightFlag, false)
val DarkSnare = SnareColors(DarkLimestone, DarkFlag, DarkInk, DarkLichen, DarkEmerald, DarkRoute, DarkLimestone, true)

val LocalSnareColors = staticCompositionLocalOf { LightSnare }

fun regionTint(region: String): Color = when (region) {
    "salt" -> RegionTintSalt
    "fen" -> RegionTintFen
    else -> RegionTintChalk
}

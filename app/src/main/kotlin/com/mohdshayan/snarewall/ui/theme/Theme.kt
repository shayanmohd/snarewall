package com.mohdshayan.snarewall.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = LightEmerald,
    onPrimary = LightFlag,
    primaryContainer = LightFlag,
    onPrimaryContainer = LightInk,
    secondary = LightLichen,
    onSecondary = LightFlag,
    background = LightLimestone,
    onBackground = LightInk,
    surface = LightFlag,
    onSurface = LightInk,
    surfaceVariant = LightLimestone,
    onSurfaceVariant = LightLichen,
    surfaceContainerLowest = LightFlag,
    surfaceContainerLow = LightFlag,
    surfaceContainer = LightFlag,
    surfaceContainerHigh = LightFlag,
    surfaceContainerHighest = LightFlag,
    inverseSurface = LightInk,
    inverseOnSurface = LightFlag,
    outline = LightLichen,
    outlineVariant = LightLichen.copy(alpha = 0.35f),
    error = LightRoute,
    onError = LightFlag,
    scrim = LightInk,
)

private val DarkColors = darkColorScheme(
    primary = DarkEmerald,
    onPrimary = DarkLimestone,
    primaryContainer = DarkFlag,
    onPrimaryContainer = DarkInk,
    secondary = DarkLichen,
    onSecondary = DarkLimestone,
    background = DarkLimestone,
    onBackground = DarkInk,
    surface = DarkFlag,
    onSurface = DarkInk,
    surfaceVariant = DarkLimestone,
    onSurfaceVariant = DarkLichen,
    surfaceContainerLowest = DarkFlag,
    surfaceContainerLow = DarkFlag,
    surfaceContainer = DarkFlag,
    surfaceContainerHigh = DarkFlag,
    surfaceContainerHighest = DarkFlag,
    inverseSurface = DarkInk,
    inverseOnSurface = DarkLimestone,
    outline = DarkLichen,
    outlineVariant = DarkLichen.copy(alpha = 0.35f),
    error = DarkRoute,
    onError = DarkLimestone,
    scrim = DarkLimestone,
)

/**
 * The app theme. Dynamic colour is off: emerald is the identity. System bar icons follow the mode,
 * and LocalReducedMotion is provided for every animation to consult.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides rememberReducedMotion(),
        LocalSnareColors provides if (darkTheme) DarkSnare else LightSnare,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

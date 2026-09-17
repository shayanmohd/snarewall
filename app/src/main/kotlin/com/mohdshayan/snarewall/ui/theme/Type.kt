package com.mohdshayan.snarewall.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mohdshayan.snarewall.R

/*
 * Bungee (a blocky signage face like cut stone) for the wordmark, level titles, HUD numbers and the
 * step chip only. Lexend for all UI and reading. Both SIL OFL 1.1, bundled under res/font.
 */
val Bungee = FontFamily(Font(R.font.bungee_regular, FontWeight.Normal))

val Lexend = FontFamily(
    Font(R.font.lexend_regular, FontWeight.Normal),
    Font(R.font.lexend_medium, FontWeight.Medium),
    Font(R.font.lexend_semibold, FontWeight.SemiBold),
)

/** Level title, 28sp. */
val LevelTitleStyle = TextStyle(fontFamily = Bungee, fontSize = 28.sp, lineHeight = 34.sp)

/** HUD numbers, 18sp. */
val HudStyle = TextStyle(fontFamily = Bungee, fontSize = 18.sp, lineHeight = 22.sp)

/** Step chip, 13sp. */
val ChipStyle = TextStyle(fontFamily = Bungee, fontSize = 13.sp, lineHeight = 16.sp)

val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Bungee, fontSize = 36.sp, lineHeight = 42.sp),
    headlineMedium = LevelTitleStyle,
    headlineSmall = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

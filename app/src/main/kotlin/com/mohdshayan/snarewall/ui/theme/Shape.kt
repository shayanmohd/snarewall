package com.mohdshayan.snarewall.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * The whole radius scale, cut stone rather than pebbles:
 *   RadiusSm 2dp   tiles, walls, chips, medal marks
 *   RadiusMd 8dp   buttons, tray slots, rows that take a press
 *   RadiusLg 16dp  sheet tops and dialogs
 */
val RadiusSm = 2.dp
val RadiusMd = 8.dp
val RadiusLg = 16.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusSm),
    small = RoundedCornerShape(RadiusMd),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusLg),
    extraLarge = RoundedCornerShape(RadiusLg),
)

val SheetShape = RoundedCornerShape(topStart = RadiusLg, topEnd = RadiusLg)

package com.mohdshayan.snarewall.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mohdshayan.snarewall.R
import com.mohdshayan.snarewall.game.Scoring
import com.mohdshayan.snarewall.game.TrapKind
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors
import com.mohdshayan.snarewall.ui.theme.RadiusSm

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

@Composable
fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

/** Screen header: back arrow and a title, sitting under the status bar. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        actions()
    }
}

/** A composed empty or error state: a line of what happened and one way forward. */
@Composable
fun StatePanel(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    art: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.Start) {
        art?.let {
            it()
            Spacer(Modifier.height(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(actionLabel, onAction)
                if (secondaryLabel != null && onSecondary != null) QuietButton(secondaryLabel, onSecondary)
            }
        }
    }
}

/** A skeleton block in the shape of the content it stands in for. No shimmer. */
@Composable
fun SkeletonBlock(width: Dp?, height: Dp, modifier: Modifier = Modifier) {
    val m = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(
        m
            .height(height)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f), RoundedCornerShape(RadiusSm)),
    )
}

object Sprites {
    const val SIZE = 16
    const val KEEP = 20
    const val ROCK_CHALK = 21
    const val ROCK_SALT = 22
    const val ROCK_FEN = 23

    fun enemy(ordinal: Int) = ordinal

    /** Atlas index for a trap, or -1 when it is drawn in code (spike plate, deadfall). */
    fun trap(kind: TrapKind) = when (kind) {
        TrapKind.EMBER -> 12
        TrapKind.FROST -> 13
        TrapKind.SNARE -> 14
        TrapKind.OIL -> 15
        TrapKind.PUSHER -> 16
        TrapKind.DART -> 17
        TrapKind.HAMMER -> 18
        TrapKind.GRINDER -> 19
        else -> -1
    }

    fun rock(region: String) = when (region) {
        "salt" -> ROCK_SALT
        "fen" -> ROCK_FEN
        else -> ROCK_CHALK
    }
}

@Composable
fun rememberAtlas(): ImageBitmap = ImageBitmap.imageResource(R.drawable.sprite_atlas)

fun DrawScope.drawSprite(atlas: ImageBitmap, index: Int, left: Float, top: Float, size: Float, filter: ColorFilter, alpha: Float = 1f) {
    drawImage(
        image = atlas,
        srcOffset = IntOffset(index * Sprites.SIZE, 0),
        srcSize = IntSize(Sprites.SIZE, Sprites.SIZE),
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize = IntSize(size.toInt(), size.toInt()),
        alpha = alpha,
        colorFilter = filter,
        filterQuality = FilterQuality.None,
    )
}

/** Spike plate drawn on the sprite grid: three teeth. [path] is reused, never allocated per frame. */
fun DrawScope.drawSpike(path: Path, left: Float, top: Float, size: Float, color: Color) {
    val u = size / 16f
    path.reset()
    for (k in 0 until 3) {
        val x0 = left + (2 + k * 4) * u
        path.moveTo(x0, top + 12 * u)
        path.lineTo(x0 + 2 * u, top + 4 * u)
        path.lineTo(x0 + 4 * u, top + 12 * u)
        path.close()
    }
    drawPath(path, color)
    drawRect(color, androidx.compose.ui.geometry.Offset(left + 2 * u, top + 12 * u), androidx.compose.ui.geometry.Size(12 * u, 2 * u))
}

/** Deadfall drawn on the sprite grid: a hanging stone on a cord. */
fun DrawScope.drawDeadfall(left: Float, top: Float, size: Float, color: Color) {
    val u = size / 16f
    drawRect(color, androidx.compose.ui.geometry.Offset(left + 7 * u, top + 1 * u), androidx.compose.ui.geometry.Size(2 * u, 4 * u))
    drawRect(color, androidx.compose.ui.geometry.Offset(left + 3 * u, top + 5 * u), androidx.compose.ui.geometry.Size(10 * u, 9 * u))
}

/** The icon for a tray slot or a briefing row: a trap, or the wall block when [kind] is null. */
@Composable
fun TrapGlyph(kind: TrapKind?, tint: Color, modifier: Modifier = Modifier) {
    val atlas = rememberAtlas()
    val filter = remember(tint) { ColorFilter.tint(tint) }
    val path = remember { Path() }
    Canvas(modifier) {
        val s = size.minDimension
        val l = (size.width - s) / 2
        val t = (size.height - s) / 2
        when {
            kind == null -> drawRoundRect(tint, androidx.compose.ui.geometry.Offset(l + s * 0.12f, t + s * 0.12f), androidx.compose.ui.geometry.Size(s * 0.76f, s * 0.76f), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            kind == TrapKind.SPIKE -> drawSpike(path, l, t, s, tint)
            kind == TrapKind.DEADFALL -> drawDeadfall(l, t, s, tint)
            else -> drawSprite(atlas, Sprites.trap(kind), l, t, s, filter)
        }
    }
}

@Composable
fun EnemyGlyph(ordinal: Int, tint: Color, modifier: Modifier = Modifier) {
    val atlas = rememberAtlas()
    val filter = remember(tint) { ColorFilter.tint(tint) }
    Canvas(modifier) {
        val s = size.minDimension
        drawSprite(atlas, Sprites.enemy(ordinal), (size.width - s) / 2, (size.height - s) / 2, s, filter)
    }
}

/** Medal as three stone pips: one for bronze, two for silver, three for gold. Shape carries it, not hue. */
@Composable
fun MedalPips(medal: Scoring.Medal, modifier: Modifier = Modifier, pip: Dp = 8.dp) {
    val c = LocalSnareColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (k in 1..3) {
            val filled = medal.ordinal >= k
            Box(
                Modifier
                    .size(pip)
                    .then(
                        if (filled) Modifier.background(c.emerald, RoundedCornerShape(RadiusSm))
                        else Modifier.border(1.dp, c.lichen.copy(alpha = 0.6f), RoundedCornerShape(RadiusSm)),
                    ),
            )
        }
    }
}

fun medalLabel(m: Scoring.Medal) = when (m) {
    Scoring.Medal.GOLD -> "Gold"
    Scoring.Medal.SILVER -> "Silver"
    Scoring.Medal.BRONZE -> "Bronze"
    Scoring.Medal.NONE -> "No medal"
}

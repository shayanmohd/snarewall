package com.mohdshayan.snarewall.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohdshayan.snarewall.game.Grid
import com.mohdshayan.snarewall.game.Placement
import com.mohdshayan.snarewall.game.Sim
import com.mohdshayan.snarewall.game.TrapKind
import com.mohdshayan.snarewall.ui.components.Sprites
import com.mohdshayan.snarewall.ui.components.drawDeadfall
import com.mohdshayan.snarewall.ui.components.drawSpike
import com.mohdshayan.snarewall.ui.components.drawSprite
import com.mohdshayan.snarewall.ui.components.rememberAtlas
import com.mohdshayan.snarewall.ui.theme.LocalReducedMotion
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors
import com.mohdshayan.snarewall.ui.theme.regionTint
import kotlin.math.floor
import kotlin.math.sin

/**
 * The board. Draws straight from the live [Sim] with enemies interpolated between ticks. Every
 * object used while drawing (paths, filters, strokes) is created once in remember, so a frame
 * allocates nothing.
 */
@Composable
fun BoardCanvas(vm: BoardViewModel, sim: Sim, tile: Dp, description: String, modifier: Modifier = Modifier) {
    val c = LocalSnareColors.current
    val reduced = LocalReducedMotion.current
    val atlas = rememberAtlas()
    val inkFilter = remember(c) { ColorFilter.tint(c.ink) }
    val emeraldFilter = remember(c) { ColorFilter.tint(c.emerald) }
    val onEmeraldFilter = remember(c) { ColorFilter.tint(c.onEmerald) }
    val routeFilter = remember(c) { ColorFilter.tint(c.route) }
    val lichenFilter = remember(c) { ColorFilter.tint(c.lichen) }
    val tint = remember(vm.region) { regionTint(vm.region) }
    val scratch = remember { Path() }
    val thread = remember { Path() }
    val ghostThread = remember { Path() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dashEffect = remember(density) {
        with(density) { PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())) }
    }
    val threadStroke = remember(density, dashEffect) {
        with(density) { Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dashEffect) }
    }
    val linkStroke = remember(density) { with(density) { Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round) } }
    val hairline = remember(density) { with(density) { Stroke(width = 1.dp.toPx()) } }
    val selectStroke = remember(density) { with(density) { Stroke(width = 2.dp.toPx()) } }

    // The one first-run moment: the thread draws gate to keep, once per install.
    val drawIn = remember { Animatable(1f) }
    LaunchedEffect(vm.firstRouteDraw, reduced) {
        if (vm.firstRouteDraw) {
            if (!reduced) {
                drawIn.snapTo(0f)
                drawIn.animateTo(1f, tween(700, easing = LinearEasing))
            }
            vm.firstRouteDrawn()
        }
    }

    // A committed wall or trap settles from 0.9 scale in 90 ms.
    val settle = remember { Animatable(1f) }
    LaunchedEffect(vm.settleSerial, reduced) {
        if (vm.settleSerial > 0 && !reduced) {
            settle.snapTo(0.9f)
            settle.animateTo(1f, tween(90))
        } else {
            settle.snapTo(1f)
        }
    }
    // The route before the last change fades out over 150 ms while the new one is drawn.
    val oldRoute = remember { Animatable(0f) }
    LaunchedEffect(vm.routeChangeSerial, reduced) {
        if (vm.routeChangeSerial > 0 && !reduced) {
            oldRoute.snapTo(1f)
            oldRoute.animateTo(0f, tween(150))
        } else {
            oldRoute.snapTo(0f)
        }
    }

    val obstacleSprite = Sprites.rock(vm.region)
    val sizeMod = Modifier.size(tile * Grid.W, tile * Grid.H)

    androidx.compose.foundation.layout.Box(modifier.then(sizeMod)) {
    // Static layer: floor, obstacles, walls, traps, links and the route. It redraws only when the board
    // or the route changes, and renders into a cached offscreen layer, so a busy wave redraws just enemies.
    Canvas(
        Modifier
            .then(sizeMod)
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen },
    ) {
        vm.routeVersion
        val ts = size.width / Grid.W
        val inset = 1.dp.toPx()
        val radius = CornerRadius(2.dp.toPx())
        val ghost = vm.ghostTile
        // Floor, region tint, seams.
        drawRect(c.flag)
        drawRect(tint, alpha = 0.08f)
        for (x in 1 until Grid.W) drawLine(c.lichen.copy(alpha = 0.16f), Offset(x * ts, 0f), Offset(x * ts, size.height), 1f)
        for (y in 1 until Grid.H) drawLine(c.lichen.copy(alpha = 0.16f), Offset(0f, y * ts), Offset(size.width, y * ts), 1f)

        val layout = sim.layout
        // Gates: an open arch on the top row.
        for (g in layout.gates) {
            val l = Grid.x(g) * ts
            val t = Grid.y(g) * ts
            drawRect(c.lichen.copy(alpha = 0.14f), Offset(l, t), Size(ts, ts))
            drawRect(c.lichen, Offset(l + ts * 0.18f, t), Size(ts * 0.12f, ts * 0.8f))
            drawRect(c.lichen, Offset(l + ts * 0.70f, t), Size(ts * 0.12f, ts * 0.8f))
            drawRect(c.lichen, Offset(l + ts * 0.18f, t + ts * 0.12f), Size(ts * 0.64f, ts * 0.10f))
        }
        // Keep.
        run {
            val l = Grid.x(layout.keep) * ts
            val t = Grid.y(layout.keep) * ts
            drawRect(c.ink.copy(alpha = 0.08f), Offset(l, t), Size(ts, ts))
            drawSprite(atlas, Sprites.KEEP, l + ts * 0.1f, t + ts * 0.1f, ts * 0.8f, inkFilter)
        }
        // Obstacles.
        for (i in 0 until Grid.N) if (layout.obstacle[i]) {
            val l = Grid.x(i) * ts
            val t = Grid.y(i) * ts
            drawRect(c.ink.copy(alpha = 0.10f), Offset(l, t), Size(ts, ts))
            drawSprite(atlas, obstacleSprite, l + ts * 0.12f, t + ts * 0.12f, ts * 0.76f, inkFilter, 0.85f)
        }
        // Floor traps.
        for (k in 0 until sim.traps.size) {
            val tr = sim.traps[k]
            if (tr.kind.wallMounted) continue
            val l = Grid.x(tr.tile) * ts
            val t = Grid.y(tr.tile) * ts
            drawRect(c.emerald.copy(alpha = if (tr.kind == TrapKind.OIL) 0.20f else 0.10f), Offset(l, t), Size(ts, ts))
            val pad = ts * 0.14f
            val s = ts - pad * 2
            if (tr.kind == TrapKind.SPIKE) drawSpike(scratch, l + pad, t + pad, s, c.emerald)
            else drawSprite(atlas, Sprites.trap(tr.kind), l + pad, t + pad, s, emeraldFilter)
            if (tr.upgraded) drawUpgradePip(l, t, ts, c.emerald)
        }
        // Walls and wall traps.
        val settleScale = settle.value
        for (i in 0 until Grid.N) if (sim.walls[i]) {
            val l = Grid.x(i) * ts
            val t = Grid.y(i) * ts
            if (i == vm.settleTile && settleScale < 1f) {
                val shrink = ts * (1f - settleScale) / 2
                drawRoundRect(c.emerald, Offset(l + inset + shrink, t + inset + shrink), Size(ts - inset * 2 - shrink * 2, ts - inset * 2 - shrink * 2), radius)
            } else {
                drawRoundRect(c.emerald, Offset(l + inset, t + inset), Size(ts - inset * 2, ts - inset * 2), radius)
            }
            drawRect(c.ink.copy(alpha = 0.16f), Offset(l + inset, t + ts * 0.80f), Size(ts - inset * 2, ts * 0.20f - inset))
            val ti = sim.trapAt[i]
            if (ti >= 0) {
                val tr = sim.traps[ti]
                val pad = ts * 0.2f
                val s = ts - pad * 2
                if (tr.kind == TrapKind.DEADFALL) drawDeadfall(l + pad, t + pad, s, c.onEmerald)
                else drawSprite(atlas, Sprites.trap(tr.kind), l + pad, t + pad, s, onEmeraldFilter)
                drawFacing(scratch, l, t, ts, tr.facing, c.onEmerald)
                if (tr.upgraded) drawUpgradePip(l, t, ts, c.onEmerald)
            }
        }
        // Combo links.
        val links = sim.links
        for (li in links.indices) {
            val link = links[li]
            val ax = (Grid.x(link.a) + 0.5f) * ts
            val ay = (Grid.y(link.a) + 0.5f) * ts
            val bx = (Grid.x(link.b) + 0.5f) * ts
            val by = (Grid.y(link.b) + 0.5f) * ts
            val hot = false
            val col = if (hot) c.route else c.ink
            drawLine(col.copy(alpha = if (hot) 0.95f else 0.55f), Offset(ax, ay), Offset(bx, by), linkStroke.width * (if (hot) 1.6f else 1f), StrokeCap.Round)
            val mx = (ax + bx) / 2
            val my = (ay + by) / 2
            val d = ts * (if (hot) 0.16f else 0.11f)
            scratch.reset()
            scratch.moveTo(mx, my - d); scratch.lineTo(mx + d, my); scratch.lineTo(mx, my + d); scratch.lineTo(mx - d, my); scratch.close()
            drawPath(scratch, col)
        }

        // The route thread.
        val ghostValid = ghost >= 0 && vm.ghostIsWall && (vm.ghostError == null || vm.ghostError == Placement.Error.NEEDS_COIN)
        val fraction = drawIn.value
        val fade = oldRoute.value
        if (fade > 0f && !ghostValid) for (k in 0 until 2) {
            val n = vm.previousCount[k]
            if (n < 2) continue
            buildThread(ghostThread, vm.previousPath[k], n, ts, 1f)
            drawPath(ghostThread, c.route, alpha = 0.45f * fade, style = threadStroke)
        }
        for (k in 0 until 2) {
            val n = vm.committedCount[k]
            if (n < 2) continue
            buildThread(thread, vm.committedPath[k], n, ts, fraction)
            drawPath(thread, c.route, alpha = if (ghostValid) 0.30f else 1f, style = threadStroke)
            if (ghostValid && vm.ghostCount[k] >= 2) {
                buildThread(ghostThread, vm.ghostPath[k], vm.ghostCount[k], ts, 1f)
                drawPath(ghostThread, c.route, style = threadStroke)
            }
        }

    }
    Canvas(
        Modifier
            .then(sizeMod)
            .semantics { contentDescription = description }
            .pointerInput(sim) {
                val ts = size.width.toFloat() / Grid.W
                fun tileAt(o: Offset): Int {
                    val x = floor(o.x / ts).toInt()
                    val y = floor(o.y / ts).toInt()
                    return if (x in 0 until Grid.W && y in 0 until Grid.H) Grid.idx(x, y) else -1
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var t = tileAt(down.position)
                    vm.ghostAt(t)
                    var last = down.position
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            last = ch.position
                            break
                        }
                        if (ch.positionChange() != Offset.Zero) {
                            ch.consume()
                            last = ch.position
                            t = tileAt(last)
                            vm.ghostAt(t)
                        }
                    }
                    vm.release(tileAt(last))
                }
            },
    ) {
        // Reading these state values here redraws the canvas without recomposing the screen.
        vm.frameTick
        vm.routeVersion
        val selected = vm.selectedTile
        val ghost = vm.ghostTile
        val ts = size.width / Grid.W
        val inset = 1.dp.toPx()
        val radius = CornerRadius(2.dp.toPx())

        // Trap flashes and darts.
        for (k in 0 until sim.traps.size) {
            val tr = sim.traps[k]
            if (tr.flash <= 0f) continue
            val l = Grid.x(tr.tile) * ts
            val t = Grid.y(tr.tile) * ts
            if (!tr.kind.wallMounted) {
                drawRect(c.route.copy(alpha = 0.22f), Offset(l, t), Size(ts, ts))
            } else {
                drawRoundRect(c.route, Offset(l + inset, t + inset), Size(ts - inset * 2, ts - inset * 2), radius, style = selectStroke)
                if (tr.kind == TrapKind.DART) {
                    val cx = l + ts / 2
                    val cy = t + ts / 2
                    val reach = ts * 1.6f
                    drawLine(c.route, Offset(cx, cy), Offset(cx + Grid.DX[tr.facing] * reach, cy + Grid.DY[tr.facing] * reach), 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        // Combo links lit by a combo that just fired.
        val hotLinks = sim.links
        for (li in hotLinks.indices) {
            val link = hotLinks[li]
            if (vm.comboFlash[link.a] <= 0f && vm.comboFlash[link.b] <= 0f) continue
            val ax = (Grid.x(link.a) + 0.5f) * ts
            val ay = (Grid.y(link.a) + 0.5f) * ts
            val bx = (Grid.x(link.b) + 0.5f) * ts
            val by = (Grid.y(link.b) + 0.5f) * ts
            drawLine(c.route, Offset(ax, ay), Offset(bx, by), linkStroke.width * 1.6f, StrokeCap.Round)
            val mx = (ax + bx) / 2
            val my = (ay + by) / 2
            val d = ts * 0.16f
            scratch.reset()
            scratch.moveTo(mx, my - d); scratch.lineTo(mx + d, my); scratch.lineTo(mx, my + d); scratch.lineTo(mx - d, my); scratch.close()
            drawPath(scratch, c.route)
        }

        // Enemies.
        val alpha = vm.renderAlpha
        for (e in sim.enemies) {
            if (!e.active) continue
            val ex = (e.prevX + (e.x - e.prevX) * alpha) * ts
            val ey = (e.prevY + (e.y - e.prevY) * alpha) * ts
            val big = e.kind.armoured && e.kind.unpushable
            var s = ts * (if (big) 0.95f else 0.72f)
            var lift = 0f
            var a = 1f
            when (e.move) {
                Sim.MOVE_HOP -> {
                    val arc = sin(e.progress * Math.PI.toFloat())
                    lift = ts * 0.45f * arc
                    s *= 1f + 0.15f * arc
                }
                Sim.MOVE_TUNNEL -> a = 0.35f
                Sim.MOVE_FLY -> lift = ts * 0.18f
            }
            if (e.move == Sim.MOVE_FLY || e.move == Sim.MOVE_HOP) {
                drawOval(c.ink.copy(alpha = 0.14f), Offset(ex - s * 0.35f, ey + s * 0.25f), Size(s * 0.7f, s * 0.22f))
            }
            if (e.move == Sim.MOVE_TUNNEL) {
                drawOval(c.lichen.copy(alpha = 0.35f), Offset(ex - s * 0.4f, ey + s * 0.15f), Size(s * 0.8f, s * 0.3f))
            }
            val filter = if (e.flash > 0f) routeFilter else if (e.move == Sim.MOVE_TUNNEL) lichenFilter else inkFilter
            drawSprite(atlas, Sprites.enemy(e.kind.ordinal), ex - s / 2, ey - s / 2 - lift, s, filter, a)
            if (e.sapTimer > 0f) {
                drawCircle(c.route, radius = ts * 0.08f, center = Offset(ex + s * 0.4f, ey - s * 0.4f - lift))
            }
            if (e.hp < e.maxHp && e.hp > 0f) {
                val w = ts * 0.7f
                val top = ey - s / 2 - lift - ts * 0.12f
                drawRect(c.ink.copy(alpha = 0.22f), Offset(ex - w / 2, top), Size(w, ts * 0.07f))
                drawRect(c.emerald, Offset(ex - w / 2, top), Size(w * (e.hp / e.maxHp), ts * 0.07f))
            }
        }

        // Ghost.
        if (ghost >= 0) {
            val l = Grid.x(ghost) * ts
            val t = Grid.y(ghost) * ts
            val ok = vm.ghostError == null
            if (ok) {
                if (vm.ghostIsWall) drawRoundRect(c.emerald.copy(alpha = 0.45f), Offset(l + inset, t + inset), Size(ts - inset * 2, ts - inset * 2), radius)
                else drawRect(c.emerald.copy(alpha = 0.25f), Offset(l, t), Size(ts, ts))
                drawRoundRect(c.emerald, Offset(l + inset, t + inset), Size(ts - inset * 2, ts - inset * 2), radius, style = selectStroke)
            } else {
                drawRoundRect(c.route, Offset(l + inset, t + inset), Size(ts - inset * 2, ts - inset * 2), radius, style = selectStroke)
                val p = ts * 0.28f
                drawLine(c.route, Offset(l + p, t + p), Offset(l + ts - p, t + ts - p), 3.dp.toPx(), StrokeCap.Round)
                drawLine(c.route, Offset(l + ts - p, t + p), Offset(l + p, t + ts - p), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        // Selection.
        if (selected >= 0) {
            val l = Grid.x(selected) * ts
            val t = Grid.y(selected) * ts
            drawRect(c.ink, Offset(l, t), Size(ts, ts), style = selectStroke)
            drawRect(c.flag, Offset(l + 2.dp.toPx(), t + 2.dp.toPx()), Size(ts - 4.dp.toPx(), ts - 4.dp.toPx()), style = hairline)
        }
    }
    }
}

private fun buildThread(path: Path, tiles: IntArray, count: Int, ts: Float, fraction: Float) {
    path.reset()
    val last = if (fraction >= 1f) count - 1 else maxOf(1, (fraction * (count - 1)).toInt())
    for (k in 0..last) {
        val x = (Grid.x(tiles[k]) + 0.5f) * ts
        val y = (Grid.y(tiles[k]) + 0.5f) * ts
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
}

private fun DrawScope.drawFacing(path: Path, l: Float, t: Float, ts: Float, facing: Int, color: androidx.compose.ui.graphics.Color) {
    val cx = l + ts / 2
    val cy = t + ts / 2
    val r = ts * 0.5f
    val w = ts * 0.12f
    val tipX = cx + Grid.DX[facing] * r
    val tipY = cy + Grid.DY[facing] * r
    val baseX = cx + Grid.DX[facing] * (r - w * 1.3f)
    val baseY = cy + Grid.DY[facing] * (r - w * 1.3f)
    val px = -Grid.DY[facing] * w
    val py = Grid.DX[facing] * w
    path.reset()
    path.moveTo(tipX, tipY)
    path.lineTo(baseX + px, baseY + py)
    path.lineTo(baseX - px, baseY - py)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawUpgradePip(l: Float, t: Float, ts: Float, color: androidx.compose.ui.graphics.Color) {
    val s = ts * 0.12f
    drawRect(color, Offset(l + ts - s * 2f, t + s), Size(s, s))
    drawRect(color, Offset(l + ts - s * 2f, t + s * 2.4f), Size(s, s))
}

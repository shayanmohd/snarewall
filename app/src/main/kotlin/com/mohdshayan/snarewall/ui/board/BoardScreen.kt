package com.mohdshayan.snarewall.ui.board

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.snarewall.game.Grid
import com.mohdshayan.snarewall.game.Phase
import com.mohdshayan.snarewall.game.Placement
import com.mohdshayan.snarewall.game.Sim
import com.mohdshayan.snarewall.game.TrapKind
import com.mohdshayan.snarewall.ui.components.PrimaryButton
import com.mohdshayan.snarewall.ui.components.QuietButton
import com.mohdshayan.snarewall.ui.components.SkeletonBlock
import com.mohdshayan.snarewall.ui.components.StatePanel
import com.mohdshayan.snarewall.ui.components.TrapGlyph
import com.mohdshayan.snarewall.ui.nav.Board
import com.mohdshayan.snarewall.ui.nav.Result
import com.mohdshayan.snarewall.ui.theme.ChipStyle
import com.mohdshayan.snarewall.ui.theme.HudStyle
import com.mohdshayan.snarewall.ui.theme.LocalReducedMotion
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors
import com.mohdshayan.snarewall.ui.theme.RadiusMd
import com.mohdshayan.snarewall.ui.theme.RadiusSm
import com.mohdshayan.snarewall.ui.theme.SheetShape

@Composable
fun BoardScreen(args: Board, onQuit: () -> Unit, onFinished: (Result) -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: BoardViewModel = viewModel(key = "board-${args.levelId}-${args.difficulty}-${args.daily}") { BoardViewModel(app, args) }
    val sim = vm.sim
    val finished = vm.finished
    val onFinishedState by rememberUpdatedState(onFinished)
    LaunchedEffect(finished) { finished?.let { onFinishedState(it) } }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(vm.hapticTick) { if (vm.hapticTick > 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.onStop() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    BackHandler(enabled = sim != null && finished == null) {
        if (vm.pauseSheetOpen) vm.dismissPauseSheet() else vm.openPause()
    }

    // The frame loop. Fixed-step simulation inside vm.frame; this only measures time.
    if (sim != null) {
        LaunchedEffect(sim) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) vm.frame(minOf(now - last, 250_000_000L))
                    last = now
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            vm.loadError != null -> LoadError(vm, onQuit)
            sim == null -> BoardSkeleton()
            else -> BoardLayoutFor(vm, sim, onQuit)
        }
    }

    if (vm.pauseSheetOpen && sim != null) {
        PauseSheet(vm, onQuit)
    }
}

@Composable
private fun LoadError(vm: BoardViewModel, onQuit: () -> Unit) {
    val msg = vm.loadError ?: return
    val restartable = msg.startsWith("This level")
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
    ) {
        StatePanel(
            title = msg,
            body = if (restartable) "The level itself is fine. Restart it from wave 1." else "Reinstalling Snarewall restores the bundled levels.",
            actionLabel = if (restartable) "Restart level" else "Back to home",
            onAction = if (restartable) ({ vm.restart() }) else onQuit,
            secondaryLabel = if (restartable) "Back to home" else null,
            onSecondary = if (restartable) onQuit else null,
        )
    }
}

@Composable
private fun BoardSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBlock(null, 44.dp)
        SkeletonBlock(null, 0.dp, Modifier.weight(1f))
        SkeletonBlock(null, 64.dp)
    }
}

@Composable
private fun BoardLayoutFor(vm: BoardViewModel, sim: Sim, onQuit: () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val wide = maxWidth >= 600.dp
        val description = run {
            val waveText = if (sim.spec.endless) "Wave ${vm.wave}" else "Wave ${vm.wave} of ${sim.totalWaves}"
            "$waveText, ${vm.hearts} hearts, ${vm.coin} coin, route ${vm.routeSteps} steps"
        }
        if (wide) {
            val rail = 320.dp
            val boardW = maxWidth - rail - 24.dp
            val tile = tileSize(boardW, maxHeight - 16.dp - CHIP_STRIP)
            Row(Modifier.fillMaxSize().padding(8.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    BoardArea(vm, sim, tile, description)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.width(rail).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HudStrip(vm, sim, stacked = true)
                    if (vm.selectedTile >= 0) ItemPanel(vm, sim) else TrapTray(vm, sim, vertical = true)
                    Spacer(Modifier.weight(1f))
                    SendWaveButton(vm, Modifier.fillMaxWidth())
                }
            }
        } else {
            val hudH = 52.dp
            val trayH = 76.dp
            val tile = tileSize(maxWidth - 16.dp, maxHeight - hudH - trayH - 16.dp - CHIP_STRIP)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.height(hudH).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { HudStrip(vm, sim) }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    BoardArea(vm, sim, tile, description)
                }
                Box(Modifier.height(trayH).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    if (vm.selectedTile >= 0) ItemPanel(vm, sim) else TrayRow(vm, sim)
                }
            }
        }
    }
}

private val CHIP_STRIP = 28.dp

private fun tileSize(w: Dp, h: Dp): Dp {
    val byW = w / Grid.W
    val byH = h / Grid.H
    return if (byW < byH) byW else byH
}

@Composable
private fun BoardArea(vm: BoardViewModel, sim: Sim, tile: Dp, description: String) {
    val c = LocalSnareColors.current
    val reduced = LocalReducedMotion.current
    Column {
    Box(Modifier.size(tile * Grid.W, tile * Grid.H)) {
        BoardCanvas(vm, sim, tile, description)
        // Coach mark or message, over the top of the board.
        Column(Modifier.align(Alignment.TopCenter).padding(start = 8.dp, end = 8.dp, top = tile + 4.dp)) {
            val coach = vm.coachStep
            if (coach >= 0) CoachMark(coach, onSkip = { vm.dismissCoach() })
            val m = vm.message
            AnimatedVisibility(
                visible = m != null,
                enter = if (reduced) fadeIn(snap()) else fadeIn(tween(150)),
                exit = if (reduced) fadeOut(snap()) else fadeOut(tween(150)),
            ) {
                val last = m
                if (last != null) {
                    Text(
                        last.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (last.warning) c.flag else c.ink,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(if (last.warning) c.route else c.flag, RoundedCornerShape(RadiusMd))
                            .border(1.dp, if (last.warning) c.route else c.lichen.copy(alpha = 0.4f), RoundedCornerShape(RadiusMd))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
        // Step chip under the keep, outside the board so it never covers a tile.
    if (!vm.showStepChip) Spacer(Modifier.height(CHIP_STRIP))
    if (vm.showStepChip) {
            val keepX = Grid.x(sim.layout.keep)
            val ghostActive = vm.ghostTile >= 0 && vm.ghostIsWall
            val err = vm.ghostError
            val text = when {
                ghostActive && err == Placement.Error.SEALS -> "Seals the keep"
                ghostActive && err == null && vm.ghostDelta != 0 -> (if (vm.ghostDelta > 0) "+" else "") + "${vm.ghostDelta} steps"
                ghostActive && err == null -> "+0 steps"
                else -> "${vm.routeSteps} steps"
            }
            val chipW = 150.dp
            val x = (tile * keepX + tile / 2 - chipW / 2).coerceIn(0.dp, tile * Grid.W - chipW)
            Box(
                Modifier
                    .offset(x = x)
                    .width(chipW)
                    .height(CHIP_STRIP),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .background(c.route, RoundedCornerShape(RadiusSm))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (ghostActive && err == Placement.Error.SEALS) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = c.flag, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(text, style = ChipStyle, color = c.flag, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CoachMark(step: Int, onSkip: () -> Unit) {
    val c = LocalSnareColors.current
    val text = when (step) {
        0 -> "Place a wall. Watch the route bend."
        1 -> "Put a spike plate on the route."
        else -> "Send the first wave."
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.flag, RoundedCornerShape(RadiusMd))
            .border(1.dp, c.emerald, RoundedCornerShape(RadiusMd))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${step + 1} of 3", style = MaterialTheme.typography.labelSmall, color = c.lichen)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.ink, modifier = Modifier.weight(1f))
        IconButton(onClick = onSkip, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Skip the tutorial", tint = c.lichen, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HudStrip(vm: BoardViewModel, sim: Sim, stacked: Boolean = false) {
    val c = LocalSnareColors.current
    val waveLabel = if (sim.spec.endless) "Wave ${vm.wave}" else "Wave ${vm.wave} of ${sim.totalWaves}"
    Column {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = c.route, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("${vm.hearts}", style = HudStyle, color = c.ink, modifier = Modifier.semantics { contentDescription = "${vm.hearts} hearts" })
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(14.dp).background(c.emerald, RoundedCornerShape(RadiusSm)))
        Spacer(Modifier.width(5.dp))
        Text("${vm.coin}", style = HudStyle, color = c.ink, modifier = Modifier.semantics { contentDescription = "${vm.coin} coin" })
        Spacer(Modifier.width(14.dp))
        if (stacked) {
            Spacer(Modifier.weight(1f))
        } else {
            Text(
                waveLabel,
                style = MaterialTheme.typography.labelMedium,
                color = c.lichen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        IconButton(onClick = { vm.undo() }, enabled = vm.undoAvailable, modifier = Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo last placement", tint = if (vm.undoAvailable) c.ink else c.lichen.copy(alpha = 0.5f))
        }
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = "Change speed") { vm.cycleSpeed() }
                .semantics { contentDescription = "Speed ${vm.speed}x" }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.FastForward, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            Text("${vm.speed}x", style = HudStyle, color = c.ink)
        }
        IconButton(onClick = { vm.togglePause() }, modifier = Modifier.size(44.dp)) {
            if (vm.paused) Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume", tint = c.emerald)
            else Icon(Icons.Rounded.Pause, contentDescription = "Pause", tint = c.ink)
        }
    }
    if (stacked) Text(waveLabel, style = MaterialTheme.typography.labelMedium, color = c.lichen)
    }
}

@Composable
private fun TrayRow(vm: BoardViewModel, sim: Sim) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (vm.leftHanded) {
            SendWaveButton(vm)
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f)) { TrapTray(vm, sim, vertical = false) }
        if (!vm.leftHanded) {
            Spacer(Modifier.width(8.dp))
            SendWaveButton(vm)
        }
    }
}

@Composable
private fun SendWaveButton(vm: BoardViewModel, modifier: Modifier = Modifier) {
    val label = when (vm.phase) {
        Phase.WAVE -> if (vm.paused) "Paused" else "Wave running"
        else -> "Send wave"
    }
    PrimaryButton(label, onClick = { vm.sendWave() }, enabled = vm.phase == Phase.BUILD, modifier = modifier.widthIn(min = 104.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrapTray(vm: BoardViewModel, sim: Sim, vertical: Boolean) {
    val slots: List<TrapKind?> = listOf<TrapKind?>(null) + sim.spec.roster
    if (vertical) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slots.forEach { TraySlot(vm, sim, it, labelled = true) }
        }
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slots.forEach { TraySlot(vm, sim, it, labelled = false) }
        }
    }
}

@Composable
private fun TraySlot(vm: BoardViewModel, sim: Sim, kind: TrapKind?, labelled: Boolean) {
    val c = LocalSnareColors.current
    val selected = vm.tool == kind
    val cost = if (kind == null) Sim.WALL_COST else sim.trapCost(kind)
    val affordable = vm.coin >= cost
    val name = kind?.label ?: "Wall"
    Column(
        Modifier
            .width(if (labelled) 96.dp else 56.dp)
            .background(if (selected) c.flag else c.limestone, RoundedCornerShape(RadiusMd))
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.emerald else c.lichen.copy(alpha = 0.35f)),
                RoundedCornerShape(RadiusMd),
            )
            .clickable(role = Role.Button, onClickLabel = "Select $name") { vm.selectTool(kind) }
            .semantics(mergeDescendants = true) {
                contentDescription = "$name, $cost coin"
                this.selected = selected
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrapGlyph(kind, if (affordable) c.emerald else c.lichen.copy(alpha = 0.6f), Modifier.size(30.dp))
        if (labelled) {
            Text(name, style = MaterialTheme.typography.labelSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("$cost", style = ChipStyle, color = if (affordable) c.ink else c.lichen)
    }
}

@Composable
private fun ItemPanel(vm: BoardViewModel, sim: Sim) {
    val c = LocalSnareColors.current
    val tile = vm.selectedTile
    val ti = sim.trapAt.getOrElse(tile) { -1 }
    val trap = if (ti >= 0) sim.traps[ti] else null
    val isWall = sim.walls.getOrElse(tile) { false }
    val content = vm.content
    val title = when {
        trap != null && trap.upgraded -> "${trap.kind.label}, upgraded"
        trap != null -> trap.kind.label
        else -> "Wall"
    }
    val refund = (trap?.spent ?: 0) / 2 + if (isWall) Sim.WALL_COST / 2 else 0
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.flag, RoundedCornerShape(RadiusMd))
            .border(1.dp, c.lichen.copy(alpha = 0.35f), RoundedCornerShape(RadiusMd))
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val note = trap?.let { content?.trap(it.kind)?.note } ?: "Mount a wall trap on it, or sell it."
                Text(note, style = MaterialTheme.typography.labelSmall, color = c.lichen, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trap != null && !trap.upgraded) {
                SmallAction("Upgrade ${sim.trapCost(trap.kind)}") { vm.upgradeSelected() }
            }
            if (trap != null && trap.kind.wallMounted) {
                SmallAction("Turn") { vm.rotateSelected() }
            }
            SmallAction(if (refund > 0) "Sell +$refund" else "Sell") { vm.sellSelected() }
            IconButton(onClick = { vm.selectedTile = -1 }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = c.lichen)
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    val c = LocalSnareColors.current
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.emerald, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseSheet(vm: BoardViewModel, onQuit: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val c = LocalSnareColors.current
    ModalBottomSheet(
        onDismissRequest = { vm.dismissPauseSheet() },
        sheetState = state,
        shape = SheetShape,
        containerColor = c.flag,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Paused", style = MaterialTheme.typography.titleLarge, color = c.ink)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Resume", onClick = { vm.closePause() }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SheetRow("Undo last placement") {
                vm.dismissPauseSheet()
                vm.undo()
            }
            HorizontalDivider(color = c.lichen.copy(alpha = 0.2f))
            SheetRow(if (vm.args.daily) "Restart today's map" else "Restart level") { vm.restart() }
            HorizontalDivider(color = c.lichen.copy(alpha = 0.2f))
            SheetRow("Quit to home") {
                vm.closePause()
                onQuit()
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (vm.phase == Phase.WAVE) "Close this sheet to build while paused. Quitting keeps your run from the start of this wave."
                else "Quitting keeps your run as it stands before the next wave.",
                style = MaterialTheme.typography.bodySmall,
                color = c.lichen,
            )
        }
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

package com.mohdshayan.snarewall.ui.levels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.snarewall.game.Difficulty
import com.mohdshayan.snarewall.game.EnemyKind
import com.mohdshayan.snarewall.game.GameContent
import com.mohdshayan.snarewall.game.LevelDef
import com.mohdshayan.snarewall.game.ProgressRules
import com.mohdshayan.snarewall.game.RunSave
import com.mohdshayan.snarewall.game.Scoring
import com.mohdshayan.snarewall.game.TrapKind
import com.mohdshayan.snarewall.ui.components.EnemyGlyph
import com.mohdshayan.snarewall.ui.components.MedalPips
import com.mohdshayan.snarewall.ui.components.PrimaryButton
import com.mohdshayan.snarewall.ui.components.QuietButton
import com.mohdshayan.snarewall.ui.components.ScreenHeader
import com.mohdshayan.snarewall.ui.components.SkeletonBlock
import com.mohdshayan.snarewall.ui.components.StatePanel
import com.mohdshayan.snarewall.ui.components.TrapGlyph
import com.mohdshayan.snarewall.ui.components.medalLabel
import com.mohdshayan.snarewall.ui.nav.Board
import com.mohdshayan.snarewall.ui.theme.HudStyle
import com.mohdshayan.snarewall.ui.theme.LevelTitleStyle
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors
import com.mohdshayan.snarewall.ui.theme.RadiusMd
import com.mohdshayan.snarewall.ui.theme.SheetShape

@Composable
fun LevelsScreen(onBack: () -> Unit, onStart: (Board) -> Unit, vm: LevelsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var briefing by rememberSaveable { mutableStateOf(-1) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Levels", onBack)
        when (val s = state) {
            LevelsState.Loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(null, 48.dp)
                repeat(3) {
                    SkeletonBlock(140.dp, 22.dp)
                    SkeletonBlock(null, 88.dp)
                }
            }
            LevelsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                StatePanel("Levels could not be read.", "Reinstalling Snarewall restores the bundled levels.", actionLabel = "Back to home", onAction = onBack)
            }
            is LevelsState.Ready -> {
                LevelsBody(s, onDifficulty = vm::setDifficulty, onOpen = { briefing = it })
                val lv = s.content.level(briefing)
                if (lv != null) {
                    BriefingSheet(
                        level = lv,
                        content = s.content,
                        difficulty = s.difficulty,
                        row = s.rows.firstOrNull { it.levelId == lv.id && it.difficulty == s.difficulty },
                        resume = s.resume,
                        onDismiss = { briefing = -1 },
                        onStart = { fresh ->
                            briefing = -1
                            onStart(Board(lv.id, s.difficulty, false, fresh))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelsBody(s: LevelsState.Ready, onDifficulty: (String) -> Unit, onOpen: (Int) -> Unit) {
    val c = LocalSnareColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        DifficultyControl(s.difficulty, onDifficulty)
        Text(
            when (s.difficulty) {
                "warden" -> "Warden: enemies have less health. Medals are kept per difficulty."
                "iron" -> "Iron: enemies have far more health. Medals are kept per difficulty."
                else -> "Standard: the game as balanced. Medals are kept per difficulty."
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.lichen,
            modifier = Modifier.padding(top = 8.dp),
        )
        s.content.regions.forEach { region ->
            Spacer(Modifier.height(24.dp))
            Text(region.name, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(10.dp))
            val levels = s.content.levels.filter { it.region == region.id }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { lv ->
                    val row = s.rows.firstOrNull { it.levelId == lv.id && it.difficulty == s.difficulty }
                    val medal = Scoring.Medal.entries.firstOrNull { it.id == row?.medal } ?: Scoring.Medal.NONE
                    val open = ProgressRules.unlocked(lv.id, s.rows)
                    LevelTile(lv, medal, open, Modifier.weight(1f)) { onOpen(lv.id) }
                }
            }
            val locked = levels.firstOrNull { !ProgressRules.unlocked(it.id, s.rows) }
            if (locked != null) {
                Text(
                    "Clear level ${locked.id - 1} first",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.lichen,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DifficultyControl(selected: String, onSelect: (String) -> Unit) {
    val c = LocalSnareColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, c.lichen.copy(alpha = 0.4f), RoundedCornerShape(RadiusMd))
            .padding(3.dp),
    ) {
        Difficulty.entries.forEach { d ->
            val on = d.id == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(if (on) c.emerald else c.limestone, RoundedCornerShape(RadiusMd - 2.dp))
                    .clickable(role = Role.Tab) { onSelect(d.id) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(d.label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onEmerald else c.ink)
            }
        }
    }
}

@Composable
private fun LevelTile(lv: LevelDef, medal: Scoring.Medal, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalSnareColors.current
    Column(
        modifier
            .aspectRatio(0.72f)
            .background(if (open) c.flag else c.limestone, RoundedCornerShape(RadiusMd))
            .border(1.dp, c.lichen.copy(alpha = if (open) 0.3f else 0.2f), RoundedCornerShape(RadiusMd))
            .clickable(enabled = open, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = if (open) "Level ${lv.id}, ${lv.name}, ${medalLabel(medal)}" else "Level ${lv.id}, locked. Clear level ${lv.id - 1} first"
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${lv.id}", style = HudStyle, color = if (open) c.ink else c.lichen.copy(alpha = 0.7f))
        Text(
            if (open) lv.name else "Locked",
            style = MaterialTheme.typography.labelSmall,
            color = c.lichen,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        MedalPips(medal, pip = 7.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefingSheet(
    level: LevelDef,
    content: GameContent,
    difficulty: String,
    row: com.mohdshayan.snarewall.game.LevelProgressRow?,
    resume: RunSave?,
    onDismiss: () -> Unit,
    onStart: (fresh: Boolean) -> Unit,
) {
    val c = LocalSnareColors.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val par = level.par[difficulty]
    val regionName = content.regions.firstOrNull { it.id == level.region }?.name ?: ""
    val resumeHere = resume != null && resume.dailyKey == null && resume.levelId == level.id && resume.difficulty == difficulty
    val resumeElsewhere = resume != null && !resumeHere
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, shape = SheetShape, containerColor = c.flag) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("$regionName, level ${level.id}", style = MaterialTheme.typography.labelMedium, color = c.lichen)
            Text(level.name, style = LevelTitleStyle, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "${level.waves.size} waves, ${level.startCoin} coin to start, 20 hearts.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.ink,
            )
            if (par != null) {
                Text("Par: silver ${par.silver}, gold ${par.gold}.", style = MaterialTheme.typography.bodyMedium, color = c.lichen)
            }
            if (row != null && row.clears > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    val m = Scoring.Medal.entries.firstOrNull { it.id == row.medal } ?: Scoring.Medal.NONE
                    MedalPips(m)
                    Spacer(Modifier.width(8.dp))
                    Text("Best ${row.bestScore}, ${medalLabel(m).lowercase()}", style = MaterialTheme.typography.bodyMedium, color = c.lichen)
                }
            }
            val newEnemies = level.newEnemies.mapNotNull { EnemyKind.byId(it) }
            if (newEnemies.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("New enemies", style = MaterialTheme.typography.titleMedium, color = c.ink)
                newEnemies.forEach { e ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        EnemyGlyph(e.ordinal, c.ink, Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${e.label}: ${content.enemy(e).note.replaceFirstChar { it.lowercase() }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.ink,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Traps on this level", style = MaterialTheme.typography.titleMedium, color = c.ink)
            level.traps.mapNotNull { TrapKind.byId(it) }.forEach { t ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrapGlyph(t, c.emerald, Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${t.label}, ${content.trap(t).cost} coin. ${content.trap(t).note}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                        modifier = Modifier.widthIn(max = 520.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (resumeHere) {
                PrimaryButton("Continue from wave ${resume!!.wave + 1}", onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                QuietButton("Start level", onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Start level", onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth())
                if (resumeElsewhere) {
                    val where = if (resume!!.dailyKey != null) "today's map" else "level ${resume.levelId}"
                    Text(
                        "Starting replaces your saved run on $where.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.lichen,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

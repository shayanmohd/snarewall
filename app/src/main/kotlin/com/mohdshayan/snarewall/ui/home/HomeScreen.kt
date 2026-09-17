package com.mohdshayan.snarewall.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.data.SaveTransfer
import com.mohdshayan.snarewall.ui.components.PrimaryButton
import com.mohdshayan.snarewall.ui.components.SkeletonBlock
import com.mohdshayan.snarewall.ui.components.StatePanel
import com.mohdshayan.snarewall.ui.nav.Board
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onPlay: (Board) -> Unit,
    onLevels: () -> Unit,
    onDaily: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = LocalSnareColors.current
    val scope = rememberCoroutineScope()
    var importError by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val t = ServiceLocator.saveTransfer
            importError = when (val r = t.read(uri)) {
                is SaveTransfer.ReadResult.Ok -> try {
                    t.apply(r.file)
                    vm.retry()
                    null
                } catch (e: Exception) {
                    "That file is not a Snarewall save."
                }
                SaveTransfer.ReadResult.NewerVersion -> "That save is from a newer version of Snarewall."
                SaveTransfer.ReadResult.NotASave -> "That file is not a Snarewall save."
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            "SNAREWALL",
            style = MaterialTheme.typography.displaySmall,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text("Your walls decide the path.", style = MaterialTheme.typography.bodyLarge, color = c.lichen)
        Spacer(Modifier.height(28.dp))
        MazeVignette(Modifier.fillMaxWidth().widthIn(max = 480.dp).height(132.dp))
        Spacer(Modifier.height(28.dp))
        when (val s = state) {
            HomeState.Loading -> {
                SkeletonBlock(null, 52.dp)
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(160.dp, 16.dp)
                Spacer(Modifier.height(32.dp))
                repeat(3) {
                    SkeletonBlock(null, 22.dp)
                    Spacer(Modifier.height(30.dp))
                }
            }
            HomeState.Error -> {
                StatePanel(
                title = "Your progress could not be read.",
                body = "Import a save file or start fresh.",
                actionLabel = "Import save",
                onAction = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                secondaryLabel = "Start fresh",
                onSecondary = { vm.startFresh {} },
                modifier = Modifier.padding(horizontal = 0.dp),
            )
                importError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.route, modifier = Modifier.padding(top = 12.dp)) }
            }
            is HomeState.Ready -> {
                PrimaryButton(s.primaryLabel, onClick = { onPlay(s.primary) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    s.primaryNote ?: if (s.firstLaunch) "The first level teaches the walls in under a minute." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.lichen,
                )
                Spacer(Modifier.height(28.dp))
                HomeRow("Levels", if (s.firstLaunch) "15 levels in 3 regions" else "${s.cleared} of 15 cleared, ${s.medals} ${if (s.medals == 1) "medal" else "medals"}", onLevels)
                HorizontalDivider(color = c.lichen.copy(alpha = 0.22f))
                HomeRow("Daily map", "A new board every day", onDaily)
                HorizontalDivider(color = c.lichen.copy(alpha = 0.22f))
                HomeRow("Settings", "Sound, theme, save file", onSettings)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeRow(title: String, note: String, onClick: () -> Unit) {
    val c = LocalSnareColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(note, style = MaterialTheme.typography.bodySmall, color = c.lichen)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.lichen)
    }
}

/** A still strip of maze: walls, a gate, the keep and the dashed route bending through the gaps. */
@Composable
private fun MazeVignette(modifier: Modifier) {
    val c = LocalSnareColors.current
    val path = remember { Path() }
    Canvas(modifier) {
        val cols = 12
        val rows = 4
        val ts = minOf(size.width / cols, size.height / rows)
        val w = ts * cols
        val left = 0f
        drawRoundRect(c.flag, Offset(left, 0f), Size(w, ts * rows), CornerRadius(2.dp.toPx()))
        val walls = listOf(2 to 0, 2 to 1, 2 to 2, 5 to 1, 5 to 2, 5 to 3, 8 to 0, 8 to 1, 8 to 2, 10 to 2, 10 to 3)
        for ((x, y) in walls) {
            drawRoundRect(c.emerald, Offset(left + x * ts + 1.5f, y * ts + 1.5f), Size(ts - 3f, ts - 3f), CornerRadius(2.dp.toPx()))
        }
        val route = listOf(0 to 0, 0 to 3, 3 to 3, 3 to 0, 6 to 0, 6 to 3, 9 to 3, 9 to 0, 11 to 0, 11 to 3)
        path.reset()
        route.forEachIndexed { i, (x, y) ->
            val px = left + (x + 0.5f) * ts
            val py = (y + 0.5f) * ts
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(
            path, c.route,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
        )
        drawRect(c.ink.copy(alpha = 0.85f), Offset(left + 11 * ts + ts * 0.25f, 3 * ts + ts * 0.25f), Size(ts * 0.5f, ts * 0.5f))
    }
}

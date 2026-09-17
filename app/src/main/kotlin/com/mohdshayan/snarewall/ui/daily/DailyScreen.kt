package com.mohdshayan.snarewall.ui.daily

import android.app.Application
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.game.DailyGenerator
import com.mohdshayan.snarewall.game.DailyMap
import com.mohdshayan.snarewall.game.DailyScoreRow
import com.mohdshayan.snarewall.game.Grid
import com.mohdshayan.snarewall.game.SaveCodec
import com.mohdshayan.snarewall.ui.components.PrimaryButton
import com.mohdshayan.snarewall.ui.components.ScreenHeader
import com.mohdshayan.snarewall.ui.components.SkeletonBlock
import com.mohdshayan.snarewall.ui.components.Sprites
import com.mohdshayan.snarewall.ui.components.StatePanel
import com.mohdshayan.snarewall.ui.components.TrapGlyph
import com.mohdshayan.snarewall.ui.components.drawSprite
import com.mohdshayan.snarewall.ui.components.rememberAtlas
import com.mohdshayan.snarewall.ui.nav.Board
import com.mohdshayan.snarewall.ui.theme.HudStyle
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors
import com.mohdshayan.snarewall.ui.theme.regionTint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface DailyState {
    data object Loading : DailyState
    data object Error : DailyState
    data class Ready(val map: DailyMap, val regionName: String, val today: DailyScoreRow?, val history: List<DailyScoreRow>, val resumeWave: Int?, val otherRun: String?) : DailyState
}

class DailyViewModel(app: Application) : AndroidViewModel(app) {
    private val retry = MutableStateFlow(0)
    // Retry restarts the whole pipeline, because catch ends the flow it guards.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<DailyState> = retry.flatMapLatest { combine(ServiceLocator.progress.daily, ServiceLocator.progress.resume) { rows, resume ->
        val key = DailyGenerator.todayKey()
        val map = DailyGenerator.generate(key)
        val content = ServiceLocator.content.load()
        val save = resume?.let { SaveCodec.decodeRun(it.stateJson) }
        DailyState.Ready(
            map,
            content.regions.firstOrNull { it.id == map.region }?.name ?: "",
            rows.firstOrNull { it.dateKey == key },
            rows.filter { it.dateKey != key },
            save?.takeIf { it.dailyKey == key }?.wave,
            save?.takeIf { it.dailyKey != key }?.let { if (it.dailyKey == null) "level ${it.levelId}" else "an earlier daily map" },
        ) as DailyState
    }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .catch { emit(DailyState.Error) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyState.Loading)

    fun retry() {
        retry.value++
    }
}

@Composable
fun DailyScreen(onBack: () -> Unit, onPlay: (Board) -> Unit, vm: DailyViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = LocalSnareColors.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Daily map", onBack)
        when (val s = state) {
            DailyState.Loading -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(180.dp, 22.dp)
                SkeletonBlock(160.dp, 240.dp)
                SkeletonBlock(null, 52.dp)
            }
            DailyState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                StatePanel("Today's map could not be built.", "The daily board is made on this phone from the date.", actionLabel = "Try again", onAction = vm::retry)
            }
            is DailyState.Ready -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                val date = LocalDate.parse(s.map.dateKey).format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
                Text(date, style = MaterialTheme.typography.titleLarge, color = c.ink)
                Text(
                    "${s.regionName}. The same board, traps and waves for everyone today. Waves grow until the keep falls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.lichen,
                    modifier = Modifier.widthIn(max = 520.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Top) {
                    MiniBoard(s.map, Modifier.width(132.dp).aspectRatio(Grid.W / Grid.H.toFloat()))
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Traps today", style = MaterialTheme.typography.titleSmall, color = c.ink)
                        s.map.roster.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TrapGlyph(t, c.emerald, Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(t.label, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (s.resumeWave != null) {
                    PrimaryButton("Continue from wave ${s.resumeWave + 1}", onClick = { onPlay(Board(0, "standard", true, false, s.map.dateKey)) }, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton("Play today's map", onClick = { onPlay(Board(0, "standard", true, true, s.map.dateKey)) }, modifier = Modifier.fillMaxWidth())
                    if (s.otherRun != null) {
                        Text(
                            "Starting replaces your saved run on ${s.otherRun}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.lichen,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (s.today != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.today.bestScore}", style = HudStyle, color = c.ink)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Best today, ${s.today.bestWave} waves held over ${s.today.runs} ${if (s.today.runs == 1) "run" else "runs"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.lichen,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("History", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Spacer(Modifier.height(8.dp))
                if (s.history.isEmpty()) {
                    Text(
                        "Your daily scores collect here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.lichen,
                    )
                    if (s.today == null && s.resumeWave == null) {
                        Spacer(Modifier.height(12.dp))
                        Text("Play today's map to start the list.", style = MaterialTheme.typography.bodyMedium, color = c.lichen)
                    }
                } else {
                    s.history.forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "${row.dateKey}, ${row.bestWave} waves held, best score ${row.bestScore}"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val d = LocalDate.parse(row.dateKey).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
                            Text(d, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                            Text("${row.bestWave} waves", style = MaterialTheme.typography.bodyMedium, color = c.lichen)
                            Spacer(Modifier.width(16.dp))
                            Text("${row.bestScore}", style = HudStyle, color = c.ink, modifier = Modifier.widthIn(min = 64.dp))
                        }
                        HorizontalDivider(color = c.lichen.copy(alpha = 0.2f))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MiniBoard(map: DailyMap, modifier: Modifier) {
    val c = LocalSnareColors.current
    val atlas = rememberAtlas()
    val ink = remember(c) { ColorFilter.tint(c.ink) }
    val layout = map.layout
    Canvas(modifier.semantics { contentDescription = "Today's board" }) {
        val ts = size.width / Grid.W
        drawRect(c.flag)
        drawRect(regionTint(map.region), alpha = 0.08f)
        for (i in 0 until Grid.N) {
            val l = Grid.x(i) * ts
            val t = Grid.y(i) * ts
            when {
                layout.obstacle[i] -> drawSprite(atlas, Sprites.rock(map.region), l, t, ts, ink, 0.85f)
                i == layout.keep -> drawSprite(atlas, Sprites.KEEP, l, t, ts, ink)
                layout.isGate(i) -> drawRoundRect(c.lichen, Offset(l + ts * 0.2f, t), Size(ts * 0.6f, ts * 0.7f), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

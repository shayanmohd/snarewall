package com.mohdshayan.snarewall.ui.result

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.play.core.review.ReviewManagerFactory
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.game.DailyGenerator
import com.mohdshayan.snarewall.game.Scoring
import com.mohdshayan.snarewall.ui.components.MedalPips
import com.mohdshayan.snarewall.ui.components.PrimaryButton
import com.mohdshayan.snarewall.ui.components.QuietButton
import com.mohdshayan.snarewall.ui.components.medalLabel
import com.mohdshayan.snarewall.ui.nav.Board
import com.mohdshayan.snarewall.ui.nav.Result
import com.mohdshayan.snarewall.ui.theme.HudStyle
import com.mohdshayan.snarewall.ui.theme.LevelTitleStyle
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors
import kotlinx.coroutines.flow.first

private const val LEVEL_COUNT = 15

@Composable
fun ResultScreen(args: Result, onPlay: (Board) -> Unit, onHome: () -> Unit, onLevels: () -> Unit) {
    val c = LocalSnareColors.current
    val context = LocalContext.current
    val medal = Scoring.Medal.entries.firstOrNull { it.id == args.medal } ?: Scoring.Medal.NONE
    val day = args.dateKey ?: DailyGenerator.todayKey()

    // Review prompt: once, after the third level clear, never after a loss or on a daily run.
    LaunchedEffect(args) {
        if (args.daily || !args.won) return@LaunchedEffect
        val prefs = ServiceLocator.appPrefs
        val s = prefs.settings.first()
        if (s.levelsClearedTotal >= 3 && !s.reviewPrompted) {
            prefs.setReviewPrompted(true)
            val activity = context as? Activity ?: return@LaunchedEffect
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) manager.launchReviewFlow(activity, task.result)
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
        Spacer(Modifier.height(48.dp))
        val title = when {
            args.daily -> "The keep fell on wave ${args.wave}."
            args.won -> "Cleared level ${args.levelId}"
            else -> "The keep fell on wave ${args.wave}."
        }
        Text(
            if (args.daily) "Daily map, $day" else "Level ${args.levelId}, ${args.difficulty.replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.labelMedium,
            color = c.lichen,
        )
        Text(title, style = if (args.won) LevelTitleStyle else MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(24.dp))
        Text("${args.score}", style = MaterialTheme.typography.displaySmall, color = c.ink)
        Text(
            when {
                args.daily && args.newBest -> "New best for today"
                args.daily -> "Score. Your best today stands."
                args.won && args.newBest -> "Score. A new best on this difficulty."
                args.won -> "Score"
                else -> "Score from kills. Only a clear earns a medal."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (args.newBest) c.emerald else c.lichen,
        )
        if (!args.daily) {
            Spacer(Modifier.height(20.dp))
            Row {
                MedalPips(medal, pip = 12.dp)
                Spacer(Modifier.width(12.dp))
                Text(medalLabel(medal), style = MaterialTheme.typography.titleMedium, color = c.ink)
            }
            Spacer(Modifier.height(6.dp))
            Text("Par: silver ${args.silver}, gold ${args.gold}", style = MaterialTheme.typography.bodyMedium, color = c.lichen)
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = c.lichen.copy(alpha = 0.22f))
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("${args.kills}", "Kills")
            if (!args.daily) Stat("${args.hearts}", "Hearts left")
            Stat(if (args.daily) "${maxOf(0, args.wave - 1)}" else "${args.wave}", if (args.daily) "Waves held" else "Waves")
        }
        HorizontalDivider(color = c.lichen.copy(alpha = 0.22f))
        Spacer(Modifier.height(28.dp))
        Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                args.daily -> {
                    PrimaryButton("Share result", onClick = {
                        val text = "Snarewall daily map $day: held ${maxOf(0, args.wave - 1)} waves, score ${args.score}."
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Share result"))
                    }, modifier = Modifier.fillMaxWidth())
                    QuietButton("Try again", onClick = { onPlay(Board(0, "standard", true, true)) }, modifier = Modifier.fillMaxWidth())
                }
                args.won && args.levelId < LEVEL_COUNT -> {
                    PrimaryButton("Next level", onClick = { onPlay(Board(args.levelId + 1, args.difficulty, false, true)) }, modifier = Modifier.fillMaxWidth())
                    QuietButton("Play again", onClick = { onPlay(Board(args.levelId, args.difficulty, false, true)) }, modifier = Modifier.fillMaxWidth())
                }
                args.won -> {
                    PrimaryButton("Levels", onClick = onLevels, modifier = Modifier.fillMaxWidth())
                    QuietButton("Play again", onClick = { onPlay(Board(args.levelId, args.difficulty, false, true)) }, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    PrimaryButton("Try again", onClick = { onPlay(Board(args.levelId, args.difficulty, false, true)) }, modifier = Modifier.fillMaxWidth())
                    QuietButton("Levels", onClick = onLevels, modifier = Modifier.fillMaxWidth())
                }
            }
            QuietButton("Home", onClick = onHome, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val c = LocalSnareColors.current
    Column {
        Text(value, style = HudStyle, color = c.ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.lichen)
    }
}

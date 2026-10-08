package com.mohdshayan.snarewall.ui.unlock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.snarewall.R
import com.mohdshayan.snarewall.game.EnemyKind
import com.mohdshayan.snarewall.game.TrapKind
import com.mohdshayan.snarewall.ui.components.EnemyGlyph
import com.mohdshayan.snarewall.ui.components.PrimaryButton
import com.mohdshayan.snarewall.ui.components.QuietButton
import com.mohdshayan.snarewall.ui.components.ScreenHeader
import com.mohdshayan.snarewall.ui.components.TrapGlyph
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors

/**
 * The one place the full game is sold and restored. It never opens by itself: Levels, Result, Home,
 * Settings and the Board gate open it. [onLevels] is null when the Board gate draws it; there a
 * purchase turns the gate into the board instead.
 */
@Composable
fun UnlockScreen(onBack: () -> Unit, onLevels: (() -> Unit)?, vm: UnlockViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val c = LocalSnareColors.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val address = stringResource(R.string.support_email)
    val view = UnlockRules.view(state, busy)
    val textWidth = Modifier.widthIn(max = 520.dp)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(UnlockText.HEADER, onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (view == UnlockView.OWNED || view == UnlockView.PRIOR) {
                if (note == UnlockNote.RESTORED) {
                    Text(UnlockText.RESTORED, style = MaterialTheme.typography.bodyMedium, color = c.emerald, modifier = textWidth.semantics { liveRegion = LiveRegionMode.Polite })
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    UnlockText.OWNED_TITLE,
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (view == UnlockView.PRIOR) UnlockText.PRIOR_BODY else UnlockText.OWNED_BODY,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.ink,
                    modifier = textWidth,
                )
                if (onLevels != null) {
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton(UnlockText.GO_TO_LEVELS, onClick = onLevels, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth())
                }
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            Text(UnlockText.TITLE, style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(UnlockText.LEAD, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = textWidth)
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlyphRow(UnlockText.ROW_DIGGERS) { EnemyGlyph(EnemyKind.DIGGER.ordinal, c.ink, it) }
                GlyphRow(UnlockText.ROW_OILSKINS) { EnemyGlyph(EnemyKind.OILSKIN.ordinal, c.ink, it) }
                GlyphRow(UnlockText.ROW_TRAPS) { TrapGlyph(TrapKind.EMBER, c.emerald, it) }
                GlyphRow(UnlockText.ROW_WARLORDS) { EnemyGlyph(EnemyKind.WARLORD.ordinal, c.ink, it) }
            }
            Spacer(Modifier.height(16.dp))
            Text(UnlockText.NOTE, style = MaterialTheme.typography.bodyMedium, color = c.lichen, modifier = textWidth)
            Spacer(Modifier.height(24.dp))
            val price = state.price
            if (view == UnlockView.READY && price != null) {
                Text(UnlockText.priceLine(price), style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = textWidth)
                Spacer(Modifier.height(4.dp))
            }
            Text(UnlockText.OWNERSHIP, style = MaterialTheme.typography.bodyMedium, color = c.lichen, modifier = textWidth)

            // The state's own line, then the last action's result; a result that repeats the state line shows once.
            val stateLine: Pair<String, Color>? = when (view) {
                UnlockView.LOADING -> UnlockText.CHECKING to c.lichen
                UnlockView.NOT_REACHABLE -> UnlockText.NOT_REACHABLE to c.route
                UnlockView.UNAVAILABLE -> UnlockText.UNAVAILABLE to c.route
                UnlockView.PENDING -> UnlockText.PENDING to c.lichen
                else -> null
            }
            val resultLine = UnlockText.note(note, address)?.let { it to if (note == UnlockNote.RESTORED) c.emerald else c.route }
            listOfNotNull(stateLine, resultLine).distinctBy { it.first }.forEach { (text, color) ->
                Spacer(Modifier.height(12.dp))
                // A polite live region, so TalkBack reads a new state or result where it appears.
                Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = textWidth.semantics { liveRegion = LiveRegionMode.Polite })
            }

            Spacer(Modifier.height(20.dp))
            // The buy button and Not now share one width and one height: declining is as easy as buying.
            val button = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (view) {
                    UnlockView.READY -> PrimaryButton(
                        UnlockText.buy(price.orEmpty()),
                        onClick = { activity?.let(vm::buy) },
                        modifier = button,
                        enabled = activity != null,
                        // A long price at a large font size would lose its second line; the price line above shows it.
                        fallback = UnlockText.UNLOCK,
                    )
                    UnlockView.LOADING -> PrimaryButton(UnlockText.UNLOCK, onClick = {}, modifier = button, enabled = false)
                    UnlockView.NOT_REACHABLE, UnlockView.UNAVAILABLE -> PrimaryButton(UnlockText.TRY_AGAIN, onClick = vm::retry, modifier = button)
                    else -> Unit // Pending: no buy button until Google Play confirms the payment.
                }
                QuietButton(UnlockText.NOT_NOW, onClick = onBack, modifier = button)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = vm::restore, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(UnlockText.RESTORE, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(16.dp))
            Text(UnlockText.PROMO, style = MaterialTheme.typography.bodySmall, color = c.lichen, modifier = textWidth)
            Spacer(Modifier.height(8.dp))
            Text(UnlockText.PRIOR_PRINT, style = MaterialTheme.typography.bodySmall, color = c.lichen, modifier = textWidth)
            TextButton(onClick = { if (!emailUs(context, address)) vm.emailMissing() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(UnlockText.EMAIL_US, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GlyphRow(text: String, glyph: @Composable (Modifier) -> Unit) {
    val c = LocalSnareColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        glyph(Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.widthIn(max = 520.dp))
    }
}

/**
 * Opens the email app addressed to [address], for a prior buyer's order number. False when no email
 * app is installed. Needs no permission.
 */
fun emailUs(context: Context, address: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(Intent.EXTRA_SUBJECT, UnlockText.EMAIL_SUBJECT)
            putExtra(Intent.EXTRA_TEXT, UnlockText.EMAIL_BODY)
        },
    )
    true
} catch (e: ActivityNotFoundException) {
    false
}

package com.mohdshayan.snarewall.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.ui.components.ScreenHeader
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors

private data class Credit(val title: String, val use: String, val file: String)

private val credits = listOf(
    Credit("Bungee, by David Jonathan Ross", "Wordmark, level titles and numbers. SIL Open Font License 1.1.", "OFL-Bungee.txt"),
    Credit("Lexend, by Bonnie Shaver-Troup and Thomas Jockin", "All other text. SIL Open Font License 1.1.", "OFL-Lexend.txt"),
    Credit("1-Bit Pack, by Kenney", "Enemy, trap, keep and rock sprites, from kenney.nl. CC0.", "Kenney-1-Bit-Pack-License.txt"),
    Credit("Impact Sounds, by Kenney", "Trap, wall and hit sounds, from kenney.nl. CC0.", "Kenney-Impact-Sounds-License.txt"),
    Credit("Interface Sounds, by Kenney", "Placement, wave and result sounds, from kenney.nl. CC0.", "Kenney-Interface-Sounds-License.txt"),
)

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val c = LocalSnareColors.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Licences", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                "Snarewall bundles these fonts, sprites and sounds. The spike plate, deadfall, walls, gates and route thread are drawn in code.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.lichen,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            credits.forEach { cr ->
                val text = remember(cr.file) {
                    try {
                        ServiceLocator.content.licence(cr.file).trim()
                    } catch (e: Exception) {
                        "The licence text could not be read from the app bundle."
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(cr.title, style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(cr.use, style = MaterialTheme.typography.bodyMedium, color = c.lichen)
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = c.ink, modifier = Modifier.widthIn(max = 560.dp))
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = c.lichen.copy(alpha = 0.2f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

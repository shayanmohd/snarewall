package com.mohdshayan.snarewall.ui.settings

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohdshayan.snarewall.R
import com.mohdshayan.snarewall.data.SaveTransfer
import com.mohdshayan.snarewall.data.prefs.Settings
import com.mohdshayan.snarewall.di.ServiceLocator
import com.mohdshayan.snarewall.game.SaveFile
import com.mohdshayan.snarewall.ui.components.ScreenHeader
import com.mohdshayan.snarewall.ui.components.SkeletonBlock
import com.mohdshayan.snarewall.ui.theme.LocalSnareColors
import com.mohdshayan.snarewall.ui.theme.RadiusMd
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = ServiceLocator.appPrefs
    val settings: StateFlow<Settings?> = prefs.settings.map { it as Settings? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var status by mutableStateOf<Pair<String, Boolean>?>(null)
    var pendingImport by mutableStateOf<SaveFile?>(null)

    fun theme(v: String) = viewModelScope.launch { prefs.setTheme(v) }
    fun haptics(v: Boolean) = viewModelScope.launch { prefs.setHaptics(v) }
    fun volume(v: Float) = viewModelScope.launch {
        prefs.setSfxVolume(v)
        ServiceLocator.sfx.volume = v
    }
    fun leftHanded(v: Boolean) = viewModelScope.launch { prefs.setLeftHandedTray(v) }
    fun stepChip(v: Boolean) = viewModelScope.launch { prefs.setShowStepChip(v) }

    fun export(launch: (Intent) -> Unit) = viewModelScope.launch {
        try {
            launch(ServiceLocator.saveTransfer.exportIntent())
            status = "Save exported" to false
        } catch (e: Exception) {
            status = "The save file could not be written. Free some storage and export again." to true
        }
    }

    fun read(uri: Uri) = viewModelScope.launch {
        status = null
        when (val r = ServiceLocator.saveTransfer.read(uri)) {
            is SaveTransfer.ReadResult.Ok -> pendingImport = r.file
            SaveTransfer.ReadResult.NewerVersion -> status = "That save is from a newer version of Snarewall." to true
            SaveTransfer.ReadResult.NotASave -> status = "That file is not a Snarewall save." to true
        }
    }

    fun confirmImport() = viewModelScope.launch {
        val f = pendingImport ?: return@launch
        pendingImport = null
        try {
            ServiceLocator.saveTransfer.apply(f)
            status = "Save imported" to false
        } catch (e: Exception) {
            status = "That file is not a Snarewall save." to true
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onLicences: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val c = LocalSnareColors.current
    val context = LocalContext.current
    val policyUrl = stringResource(R.string.privacy_policy_url)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.read(uri) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack)
        val settings = s
        if (settings == null) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(5) { SkeletonBlock(null, 28.dp) }
            }
            return@Column
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Section("Game")
            Text("Theme", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (id, label) ->
                    val on = settings.theme == id
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .background(if (on) c.emerald else c.flag, RoundedCornerShape(RadiusMd))
                            .clickable(role = Role.RadioButton) { vm.theme(id) }
                            .semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onEmerald else c.ink) }
                }
            }
            Spacer(Modifier.height(12.dp))
            ToggleRow("Haptics", "A tick on placement and combos", settings.haptics, vm::haptics)
            Divider()
            var vol by remember(settings.sfxVolume) { mutableFloatStateOf(settings.sfxVolume) }
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sound volume", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                    Text(if (vol < 0.01f) "Off" else "${(vol * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = c.lichen)
                }
                Slider(
                    value = vol,
                    onValueChange = { vol = it },
                    onValueChangeFinished = { vm.volume(vol) },
                    modifier = Modifier.semantics { contentDescription = "Sound volume" },
                    colors = SliderDefaults.colors(thumbColor = c.emerald, activeTrackColor = c.emerald, inactiveTrackColor = c.lichen.copy(alpha = 0.3f)),
                )
            }
            Divider()
            ToggleRow("Left-handed tray", "Send wave sits on the left", settings.leftHandedTray, vm::leftHanded)
            Divider()
            ToggleRow("Step chip", "Route length at the keep", settings.showStepChip, vm::stepChip)

            Section("Save file")
            Text(
                "Your progress lives only on this phone. Export it to keep a copy or move it to another device.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.lichen,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            ActionRow("Export save") { vm.export { context.startActivity(it) } }
            Divider()
            ActionRow("Import save") { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            vm.status?.let { (text, warn) ->
                Text(text, style = MaterialTheme.typography.bodyMedium, color = if (warn) c.route else c.emerald, modifier = Modifier.padding(top = 8.dp))
            }

            Section("About")
            ActionRow("Licences") { onLicences() }
            Divider()
            ActionRow("Privacy policy") {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(policyUrl)))
                } catch (e: ActivityNotFoundException) {
                    vm.status = "No browser is installed to open the privacy policy." to true
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "No ads, no account, no internet permission. Nothing you do in Snarewall leaves this phone unless you send a save file.",
                style = MaterialTheme.typography.bodySmall,
                color = c.lichen,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    vm.pendingImport?.let { f ->
        AlertDialog(
            onDismissRequest = { vm.pendingImport = null },
            title = { Text("Import save?") },
            text = {
                val levels = f.levelProgress.size
                val days = f.dailyScores.size
                Text("Importing replaces your progress. The file holds $levels level ${if (levels == 1) "result" else "results"} and $days daily ${if (days == 1) "score" else "scores"}.")
            },
            confirmButton = { TextButton(onClick = { vm.confirmImport() }) { Text("Import save") } },
            dismissButton = { TextButton(onClick = { vm.pendingImport = null }) { Text("Keep current progress") } },
            containerColor = c.flag,
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(28.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = LocalSnareColors.current.emerald, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Divider() = HorizontalDivider(color = LocalSnareColors.current.lichen.copy(alpha = 0.2f))

@Composable
private fun ToggleRow(title: String, note: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalSnareColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Switch) { onChange(!value) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(note, style = MaterialTheme.typography.bodySmall, color = c.lichen)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.emerald,
                checkedThumbColor = c.onEmerald,
                uncheckedTrackColor = c.limestone,
                uncheckedThumbColor = c.lichen,
                uncheckedBorderColor = c.lichen,
            ),
        )
    }
}

@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) { Text(title, style = MaterialTheme.typography.titleMedium, color = LocalSnareColors.current.ink) }
}

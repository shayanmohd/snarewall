package com.mohdshayan.snarewall.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

/** Everything Settings shows, plus the small flags that time the tutorial and the review prompt. */
data class Settings(
    val theme: String = "system",
    val haptics: Boolean = true,
    val sfxVolume: Float = 0.7f,
    val leftHandedTray: Boolean = false,
    val showStepChip: Boolean = true,
    val lastDifficulty: String = "standard",
    val defaultSpeed: Int = 1,
    val tutorialDone: Boolean = false,
    val firstRouteDrawn: Boolean = false,
    val levelsClearedTotal: Int = 0,
    val reviewPrompted: Boolean = false,
    val dailyRuns: Int = 0,
)

class AppPrefs(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val LEFT_HANDED = booleanPreferencesKey("left_handed_tray")
        val STEP_CHIP = booleanPreferencesKey("show_step_chip")
        val LAST_DIFFICULTY = stringPreferencesKey("last_difficulty")
        val DEFAULT_SPEED = intPreferencesKey("default_speed")
        val TUTORIAL_DONE = booleanPreferencesKey("tutorial_done")
        val FIRST_ROUTE_DRAWN = booleanPreferencesKey("first_route_drawn")
        val LEVELS_CLEARED = intPreferencesKey("levels_cleared_total")
        val REVIEW_PROMPTED = booleanPreferencesKey("review_prompted")
        val DAILY_RUNS = intPreferencesKey("daily_runs")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = p[Keys.THEME] ?: "system",
            haptics = p[Keys.HAPTICS] ?: true,
            sfxVolume = p[Keys.SFX_VOLUME] ?: 0.7f,
            leftHandedTray = p[Keys.LEFT_HANDED] ?: false,
            showStepChip = p[Keys.STEP_CHIP] ?: true,
            lastDifficulty = p[Keys.LAST_DIFFICULTY] ?: "standard",
            defaultSpeed = (p[Keys.DEFAULT_SPEED] ?: 1).coerceIn(1, 3),
            tutorialDone = p[Keys.TUTORIAL_DONE] ?: false,
            firstRouteDrawn = p[Keys.FIRST_ROUTE_DRAWN] ?: false,
            levelsClearedTotal = p[Keys.LEVELS_CLEARED] ?: 0,
            reviewPrompted = p[Keys.REVIEW_PROMPTED] ?: false,
            dailyRuns = p[Keys.DAILY_RUNS] ?: 0,
        )
    }

    suspend fun current(): Settings = settings.first()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setTheme(v: String) = edit { it[Keys.THEME] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[Keys.HAPTICS] = v }
    suspend fun setSfxVolume(v: Float) = edit { it[Keys.SFX_VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setLeftHandedTray(v: Boolean) = edit { it[Keys.LEFT_HANDED] = v }
    suspend fun setShowStepChip(v: Boolean) = edit { it[Keys.STEP_CHIP] = v }
    suspend fun setLastDifficulty(v: String) = edit { it[Keys.LAST_DIFFICULTY] = v }
    suspend fun setDefaultSpeed(v: Int) = edit { it[Keys.DEFAULT_SPEED] = v.coerceIn(1, 3) }
    suspend fun setTutorialDone(v: Boolean) = edit { it[Keys.TUTORIAL_DONE] = v }
    suspend fun setFirstRouteDrawn(v: Boolean) = edit { it[Keys.FIRST_ROUTE_DRAWN] = v }
    suspend fun setReviewPrompted(v: Boolean) = edit { it[Keys.REVIEW_PROMPTED] = v }
    suspend fun addLevelClear() = edit { it[Keys.LEVELS_CLEARED] = (it[Keys.LEVELS_CLEARED] ?: 0) + 1 }
    suspend fun addDailyRun() = edit { it[Keys.DAILY_RUNS] = (it[Keys.DAILY_RUNS] ?: 0) + 1 }

    /** The user-facing settings, as strings, for the save file. */
    suspend fun exportMap(): Map<String, String> {
        val s = current()
        return mapOf(
            "theme" to s.theme,
            "haptics" to s.haptics.toString(),
            "sfx_volume" to s.sfxVolume.toString(),
            "left_handed_tray" to s.leftHandedTray.toString(),
            "show_step_chip" to s.showStepChip.toString(),
            "last_difficulty" to s.lastDifficulty,
            "default_speed" to s.defaultSpeed.toString(),
            "tutorial_done" to s.tutorialDone.toString(),
            "levels_cleared_total" to s.levelsClearedTotal.toString(),
        )
    }

    suspend fun importMap(raw: Map<String, String>) = edit { p ->
        val m = com.mohdshayan.snarewall.game.SaveCodec.sanitizeSettings(raw)
        m["theme"]?.takeIf { it in setOf("system", "light", "dark") }?.let { p[Keys.THEME] = it }
        m["haptics"]?.toBooleanStrictOrNull()?.let { p[Keys.HAPTICS] = it }
        m["sfx_volume"]?.toFloatOrNull()?.let { p[Keys.SFX_VOLUME] = it.coerceIn(0f, 1f) }
        m["left_handed_tray"]?.toBooleanStrictOrNull()?.let { p[Keys.LEFT_HANDED] = it }
        m["show_step_chip"]?.toBooleanStrictOrNull()?.let { p[Keys.STEP_CHIP] = it }
        m["last_difficulty"]?.takeIf { it in setOf("warden", "standard", "iron") }?.let { p[Keys.LAST_DIFFICULTY] = it }
        m["default_speed"]?.toIntOrNull()?.let { p[Keys.DEFAULT_SPEED] = it.coerceIn(1, 3) }
        m["tutorial_done"]?.toBooleanStrictOrNull()?.let { p[Keys.TUTORIAL_DONE] = it }
        m["levels_cleared_total"]?.toIntOrNull()?.let { p[Keys.LEVELS_CLEARED] = it.coerceAtLeast(0) }
    }
}

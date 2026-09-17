package com.mohdshayan.snarewall.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LevelProgressRow(
    val levelId: Int,
    val difficulty: String,
    val bestScore: Int,
    val medal: String,
    val bestHearts: Int,
    val clears: Int,
    val firstClearedAt: Long? = null,
    val lastPlayedAt: Long,
)

@Serializable
data class ResumeRow(val levelId: Int, val difficulty: String, val waveIndex: Int, val stateJson: String, val savedAt: Long)

@Serializable
data class DailyScoreRow(val dateKey: String, val bestWave: Int, val bestScore: Int, val runs: Int, val lastPlayedAt: Long)

@Serializable
data class SaveFile(
    val format: String = SaveCodec.FORMAT,
    val formatVersion: Int = SaveCodec.VERSION,
    val exportedAt: Long,
    val levelProgress: List<LevelProgressRow>,
    val resume: ResumeRow? = null,
    val dailyScores: List<DailyScoreRow>,
    val settings: Map<String, String> = emptyMap(),
)

/** Reads and writes the player's save file, and refuses anything that is not a Snarewall save it understands. */
object SaveCodec {
    const val FORMAT = "snarewall-save"
    const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    sealed interface Decoded {
        data class Ok(val file: SaveFile) : Decoded
        data object NotASave : Decoded
        data object NewerVersion : Decoded
    }

    fun encode(file: SaveFile): String = json.encodeToString(SaveFile.serializer(), file)

    fun decode(text: String): Decoded {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } catch (e: StackOverflowError) {
            // Deeply nested brackets in a hostile file.
            null
        } ?: return Decoded.NotASave
        val format = try { root["format"]?.jsonPrimitive?.content } catch (e: Exception) { null }
        if (format != FORMAT) return Decoded.NotASave
        val version = try { root["formatVersion"]?.jsonPrimitive?.int } catch (e: Exception) { null } ?: return Decoded.NotASave
        if (version > VERSION) return Decoded.NewerVersion
        if (version < 1) return Decoded.NotASave
        val file = try {
            json.decodeFromJsonElement(SaveFile.serializer(), root)
        } catch (e: Exception) {
            return Decoded.NotASave
        }
        return if (valid(file)) Decoded.Ok(file) else Decoded.NotASave
    }

    private val dateKey = Regex("""\d{4}-\d{2}-\d{2}""")

    /** A yyyy-MM-dd key that is also a real calendar date, so "2026-13-45" never reaches a date formatter. */
    fun isDateKey(key: String): Boolean = dateKey.matches(key) && try {
        java.time.LocalDate.parse(key)
        true
    } catch (e: java.time.DateTimeException) {
        false
    }

    /** Keeps only settings this version understands, with values in range. Anything else is dropped. */
    fun sanitizeSettings(m: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        m["theme"]?.takeIf { it in setOf("system", "light", "dark") }?.let { out["theme"] = it }
        for (k in listOf("haptics", "left_handed_tray", "show_step_chip", "tutorial_done")) {
            m[k]?.toBooleanStrictOrNull()?.let { out[k] = it.toString() }
        }
        m["sfx_volume"]?.toFloatOrNull()?.takeIf { it.isFinite() }?.let { out["sfx_volume"] = it.coerceIn(0f, 1f).toString() }
        m["last_difficulty"]?.takeIf { d -> Difficulty.entries.any { it.id == d } }?.let { out["last_difficulty"] = it }
        m["default_speed"]?.toIntOrNull()?.let { out["default_speed"] = it.coerceIn(1, 3).toString() }
        m["levels_cleared_total"]?.toIntOrNull()?.let { out["levels_cleared_total"] = it.coerceIn(0, 1_000_000).toString() }
        return out
    }

    private fun valid(f: SaveFile): Boolean {
        val medals = Scoring.Medal.entries.map { it.id }
        val diffs = Difficulty.entries.map { it.id }
        f.levelProgress.forEach {
            if (it.levelId !in 1..LEVEL_COUNT || it.difficulty !in diffs || it.medal !in medals) return false
            if (it.bestScore < 0 || it.clears < 0 || it.bestHearts !in 0..RunSpec.START_HEARTS) return false
        }
        if (f.levelProgress.map { it.levelId to it.difficulty }.toSet().size != f.levelProgress.size) return false
        f.dailyScores.forEach {
            if (!isDateKey(it.dateKey) || it.bestWave < 0 || it.bestScore < 0 || it.runs < 0) return false
        }
        if (f.dailyScores.map { it.dateKey }.toSet().size != f.dailyScores.size) return false
        f.resume?.let { r ->
            val save = decodeRun(r.stateJson) ?: return false
            if (save.levelId != r.levelId || save.difficulty != r.difficulty) return false
            if (save.dailyKey != null && (save.levelId != 0 || !isDateKey(save.dailyKey))) return false
            if (save.dailyKey == null && save.levelId !in 1..LEVEL_COUNT) return false
        }
        return true
    }

    private val runJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeRun(save: RunSave): String = runJson.encodeToString(RunSave.serializer(), save)

    fun decodeRun(text: String): RunSave? = try {
        runJson.decodeFromString(RunSave.serializer(), text)
    } catch (e: Exception) {
        null
    }

    const val LEVEL_COUNT = 15
}

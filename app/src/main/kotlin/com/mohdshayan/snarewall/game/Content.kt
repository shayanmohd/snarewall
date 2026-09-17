package com.mohdshayan.snarewall.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The ten traps. Floor traps sit on open floor; wall traps sit on a wall and act on the tile they face. */
enum class TrapKind(val id: String, val label: String, val wallMounted: Boolean) {
    SPIKE("spike", "Spike plate", false),
    OIL("oil", "Oil slick", false),
    EMBER("ember", "Ember grate", false),
    FROST("frost", "Frost vent", false),
    SNARE("snare", "Snare net", false),
    PUSHER("pusher", "Pusher", true),
    DEADFALL("deadfall", "Deadfall", true),
    DART("dart", "Dart wall", true),
    HAMMER("hammer", "Shatter hammer", true),
    GRINDER("grinder", "Grinder", true);

    companion object {
        fun byId(id: String): TrapKind? = entries.firstOrNull { it.id == id }
    }
}

/** The twelve enemies. Traits live here in code; numbers live in tables/enemies.json. */
enum class EnemyKind(val id: String, val label: String) {
    RAIDER("raider", "Raider"),
    RUNNER("runner", "Runner"),
    BRUTE("brute", "Brute"),
    SHIELDBEARER("shieldbearer", "Shieldbearer"),
    SWARMLING("swarmling", "Swarmling"),
    DIGGER("digger", "Digger"),
    JUMPER("jumper", "Jumper"),
    FLYER("flyer", "Flyer"),
    OILSKIN("oilskin", "Oilskin"),
    FROSTBORN("frostborn", "Frostborn"),
    SAPPER("sapper", "Sapper"),
    WARLORD("warlord", "Warlord");

    val armoured get() = this == BRUTE || this == WARLORD
    val flying get() = this == FLYER
    val fireproof get() = this == OILSKIN
    val unslowable get() = this == FROSTBORN
    val unpushable get() = this == WARLORD

    companion object {
        fun byId(id: String): EnemyKind? = entries.firstOrNull { it.id == id }
    }
}

enum class Difficulty(val id: String, val label: String, val hpMult: Float) {
    WARDEN("warden", "Warden", 0.8f),
    STANDARD("standard", "Standard", 1.0f),
    IRON("iron", "Iron", 1.3f);

    companion object {
        fun byId(id: String): Difficulty = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

enum class ComboKind(val id: String) { PUSH_DEADFALL("push_deadfall"), OIL_EMBER("oil_ember"), FROST_HAMMER("frost_hammer"), SNARE_SPIKE("snare_spike") }

@Serializable
data class TrapStats(
    val id: String,
    val cost: Int,
    val period: Float,
    val damage: Float,
    val upgradedDamage: Float,
    val effect: Float = 0f,
    val upgradedEffect: Float = 0f,
    val note: String,
)

@Serializable
data class EnemyStats(
    val id: String,
    val hp: Float,
    val speed: Float,
    val bounty: Int,
    val hearts: Int,
    val note: String,
)

@Serializable
data class ComboDef(val id: String, val first: String, val second: String, val name: String, val note: String)

@Serializable
data class WaveGroup(val enemy: String, val count: Int, val gap: Float, val delay: Float = 0f, val gate: Int = -1)

@Serializable
data class WaveDef(val hp: Float = 1f, val groups: List<WaveGroup>)

@Serializable
data class Par(val silver: Int, val gold: Int)

@Serializable
data class LevelDef(
    val id: Int,
    val region: String,
    val name: String,
    /** Twelve rows of eight characters: '.' floor, '#' obstacle, 'G' gate (top row), 'K' keep (bottom row). */
    val map: List<String>,
    val startCoin: Int,
    val traps: List<String>,
    val newEnemies: List<String>,
    val waves: List<WaveDef>,
    val par: Map<String, Par>,
)

@Serializable
data class DailyTable(
    val startCoin: Int,
    val waveBonus: Int,
    val hpBase: Float,
    val hpGrowth: Float,
    val unlocks: Map<String, Int>,
)

@Serializable
data class RegionDef(val id: String, val name: String)

/** Everything the game reads from assets, parsed and cross-checked. Plain Kotlin, no Android. */
class GameContent(
    val traps: Map<TrapKind, TrapStats>,
    val enemies: Map<EnemyKind, EnemyStats>,
    val combos: Map<ComboKind, ComboDef>,
    val regions: List<RegionDef>,
    val levels: List<LevelDef>,
    val daily: DailyTable,
) {
    fun trap(k: TrapKind) = traps.getValue(k)
    fun enemy(k: EnemyKind) = enemies.getValue(k)
    fun level(id: Int) = levels.firstOrNull { it.id == id }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(
            trapsJson: String,
            enemiesJson: String,
            combosJson: String,
            regionsJson: String,
            dailyJson: String,
            levelJsons: List<String>,
        ): GameContent {
            val traps = json.decodeFromString<List<TrapStats>>(trapsJson)
                .associateBy { TrapKind.byId(it.id) ?: error("Unknown trap ${it.id}") }
            val enemies = json.decodeFromString<List<EnemyStats>>(enemiesJson)
                .associateBy { EnemyKind.byId(it.id) ?: error("Unknown enemy ${it.id}") }
            val combos = json.decodeFromString<List<ComboDef>>(combosJson)
                .associateBy { c -> ComboKind.entries.firstOrNull { it.id == c.id } ?: error("Unknown combo ${c.id}") }
            val regions = json.decodeFromString<List<RegionDef>>(regionsJson)
            val daily = json.decodeFromString<DailyTable>(dailyJson)
            val levels = levelJsons.map { json.decodeFromString<LevelDef>(it) }.sortedBy { it.id }
            TrapKind.entries.forEach { require(it in traps) { "Missing trap stats ${it.id}" } }
            EnemyKind.entries.forEach { require(it in enemies) { "Missing enemy stats ${it.id}" } }
            ComboKind.entries.forEach { require(it in combos) { "Missing combo ${it.id}" } }
            levels.forEach { lv ->
                BoardLayout.fromMap(lv.map)
                require(regions.any { it.id == lv.region }) { "Level ${lv.id} region ${lv.region}" }
                lv.traps.forEach { requireNotNull(TrapKind.byId(it)) { "Level ${lv.id} trap $it" } }
                lv.newEnemies.forEach { requireNotNull(EnemyKind.byId(it)) { "Level ${lv.id} enemy $it" } }
                lv.waves.forEach { w -> w.groups.forEach { g -> requireNotNull(EnemyKind.byId(g.enemy)) { "Level ${lv.id} wave enemy ${g.enemy}" } } }
                Difficulty.entries.forEach { d -> require(d.id in lv.par) { "Level ${lv.id} par ${d.id}" } }
            }
            return GameContent(traps, enemies, combos, regions, levels, daily)
        }
    }
}

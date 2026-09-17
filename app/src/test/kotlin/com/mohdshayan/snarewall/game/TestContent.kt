package com.mohdshayan.snarewall.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Loads the real bundled content from app/src/main/assets, so tests exercise what ships. */
object TestContent {
    val assets = File("src/main/assets")
    val referenceDir = File("src/test/resources/reference")

    val content: GameContent by lazy { load() }

    fun load(): GameContent {
        val t = File(assets, "tables")
        val levels = File(assets, "levels").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.map { it.readText() }
        return GameContent.parse(
            File(t, "traps.json").readText(),
            File(t, "enemies.json").readText(),
            File(t, "combos.json").readText(),
            File(t, "regions.json").readText(),
            File(t, "daily.json").readText(),
            levels,
        )
    }

    /** A tiny content set for trait tests that do not depend on level balance. */
    fun sim(
        map: List<String>,
        waves: List<WaveDef>,
        roster: List<TrapKind> = TrapKind.entries,
        coin: Int = 500,
        difficulty: Difficulty = Difficulty.STANDARD,
    ): Sim {
        val spec = RunSpec(
            levelId = 99, dailyKey = null, layout = BoardLayout.fromMap(map), roster = roster,
            startCoin = coin, difficulty = difficulty, totalWaves = waves.size,
            waveAt = { waves[it] }, waveBonus = { 0 },
        )
        return Sim(spec, content)
    }

    val OPEN_MAP = listOf(
        "...G....",
        "........",
        "........",
        "........",
        "........",
        "........",
        "........",
        "........",
        "........",
        "........",
        "........",
        "...K....",
    )

    fun wave(enemy: EnemyKind, count: Int = 1, gap: Float = 1f, hp: Float = 1f) =
        WaveDef(hp, listOf(WaveGroup(enemy.id, count, gap)))

    /** Runs the current wave to its end, or fails after [maxTicks]. */
    fun runWave(sim: Sim, maxTicks: Int = 60 * 600) {
        var n = 0
        while (sim.phase == Phase.WAVE) {
            sim.step()
            n++
            check(n < maxTicks) { "Wave did not end" }
        }
    }
}

@Serializable
data class RefAction(val wave: Int, val op: String, val tile: Int, val kind: String? = null)

@Serializable
data class Reference(val levelId: Int, val difficulty: String, val actions: List<RefAction>)

object ReferenceIo {
    val json = Json { prettyPrint = false; encodeDefaults = false }

    fun file(levelId: Int, d: Difficulty) = File(TestContent.referenceDir, "level%02d-%s.json".format(levelId, d.id))

    fun read(levelId: Int, d: Difficulty): Reference = json.decodeFromString(file(levelId, d).readText())

    /** Replays a reference script. Every action must succeed exactly when it was recorded. */
    fun replay(sim: Sim, ref: Reference) {
        val byWave = ref.actions.groupBy { it.wave }
        while (sim.phase == Phase.BUILD) {
            byWave[sim.waveIndex]?.forEach { a ->
                val err = when (a.op) {
                    "wall" -> sim.placeWall(a.tile)
                    "trap" -> sim.placeTrap(TrapKind.byId(a.kind!!)!!, a.tile)
                    "upgrade" -> sim.upgrade(a.tile)
                    else -> error("op ${a.op}")
                }
                check(err == null) { "Level ${ref.levelId} ${ref.difficulty} wave ${a.wave} ${a.op} ${a.tile} ${a.kind}: $err" }
            }
            sim.sendWave()
            TestContent.runWave(sim)
        }
    }
}

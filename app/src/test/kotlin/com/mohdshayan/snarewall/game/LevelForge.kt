package com.mohdshayan.snarewall.game

import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Authoring tool, not a check. The maps, rosters and enemy line-ups below are written by hand;
 * this tool balances wave strength against the reference bot and writes the level JSON, the par
 * scores and the reference solutions that LevelSolvabilityTest replays.
 *
 * It only runs when the file app/forge.on exists, so a normal build never rewrites content.
 */
class LevelForge {

    private data class Spec(
        val id: Int, val region: String, val name: String, val coin: Int, val waves: Int,
        val traps: List<TrapKind>, val newEnemies: List<EnemyKind>, val pool: List<EnemyKind>,
        val warlords: Int, val map: List<String>,
    )

    private val T = TrapKind.entries

    private val specs = listOf(
        Spec(1, "chalk", "Barrow Gate", 40, 6, listOf(TrapKind.SPIKE), listOf(EnemyKind.RAIDER), listOf(EnemyKind.RAIDER), 0, listOf(
            "...G....", "........", ".#......", "........", "......#.", "........",
            "........", "........", "..#.....", "........", "........", "....K...")),
        Spec(2, "chalk", "Flint Cut", 42, 7, listOf(TrapKind.SPIKE, TrapKind.SNARE), listOf(EnemyKind.RUNNER), listOf(EnemyKind.RAIDER, EnemyKind.RUNNER), 0, listOf(
            "....G...", "........", "........", "...##...", "........", "........",
            ".#....#.", "........", "........", "....#...", "........", "...K....")),
        Spec(3, "chalk", "Two Barrows", 45, 7, listOf(TrapKind.SPIKE, TrapKind.SNARE, TrapKind.PUSHER, TrapKind.DEADFALL), listOf(EnemyKind.SWARMLING), listOf(EnemyKind.RAIDER, EnemyKind.RUNNER, EnemyKind.SWARMLING), 0, listOf(
            ".G....G.", "........", "...#....", "........", "........", ".#...#..",
            "........", "........", "....#...", "........", "........", "..K.....")),
        Spec(4, "chalk", "Sheep Walk", 48, 8, listOf(TrapKind.SPIKE, TrapKind.SNARE, TrapKind.FROST, TrapKind.PUSHER, TrapKind.DEADFALL, TrapKind.GRINDER), listOf(EnemyKind.BRUTE), listOf(EnemyKind.RAIDER, EnemyKind.RUNNER, EnemyKind.SWARMLING, EnemyKind.BRUTE), 0, listOf(
            "..G.....", "........", ".....#..", ".#......", "........", "........",
            "...##...", "........", "........", ".#....#.", "........", ".....K..")),
        Spec(5, "chalk", "White Horse", 50, 9, listOf(TrapKind.SPIKE, TrapKind.SNARE, TrapKind.FROST, TrapKind.PUSHER, TrapKind.DEADFALL, TrapKind.GRINDER, TrapKind.DART), listOf(EnemyKind.SHIELDBEARER, EnemyKind.WARLORD), listOf(EnemyKind.RAIDER, EnemyKind.RUNNER, EnemyKind.SWARMLING, EnemyKind.BRUTE, EnemyKind.SHIELDBEARER), 1, listOf(
            ".G....G.", "........", "..#..#..", "........", "........", "...#....",
            "........", "........", ".#....#.", "........", "........", "...K....")),
        Spec(6, "salt", "Brine Shaft", 50, 8, listOf(TrapKind.SPIKE, TrapKind.OIL, TrapKind.EMBER, TrapKind.PUSHER, TrapKind.DEADFALL, TrapKind.DART), listOf(EnemyKind.DIGGER), listOf(EnemyKind.RAIDER, EnemyKind.RUNNER, EnemyKind.BRUTE, EnemyKind.DIGGER), 0, listOf(
            "...G....", "........", ".#.#....", "........", "........", ".....#.#",
            "........", "........", ".#.#....", "........", "........", "....K...")),
        Spec(7, "salt", "Pillar Hall", 52, 9, listOf(TrapKind.SPIKE, TrapKind.OIL, TrapKind.EMBER, TrapKind.FROST, TrapKind.HAMMER, TrapKind.DART, TrapKind.GRINDER), listOf(EnemyKind.JUMPER), listOf(EnemyKind.RAIDER, EnemyKind.SWARMLING, EnemyKind.DIGGER, EnemyKind.JUMPER), 0, listOf(
            "....G...", "........", "..#..#..", "........", "..#..#..", "........",
            "..#..#..", "........", "..#..#..", "........", "........", "...K....")),
        Spec(8, "salt", "Lamp Gallery", 55, 9, listOf(TrapKind.SPIKE, TrapKind.FROST, TrapKind.PUSHER, TrapKind.DEADFALL, TrapKind.HAMMER, TrapKind.DART), listOf(EnemyKind.FLYER), listOf(EnemyKind.RAIDER, EnemyKind.RUNNER, EnemyKind.BRUTE, EnemyKind.JUMPER, EnemyKind.FLYER), 0, listOf(
            "..G.....", "........", "......#.", "........", ".#......", "........",
            "......#.", "........", ".#......", "........", "........", "......K.")),
        Spec(9, "salt", "Salt Seam", 55, 10, listOf(TrapKind.SPIKE, TrapKind.SNARE, TrapKind.OIL, TrapKind.EMBER, TrapKind.PUSHER, TrapKind.DEADFALL, TrapKind.DART, TrapKind.GRINDER), listOf(EnemyKind.SAPPER), listOf(EnemyKind.RAIDER, EnemyKind.SWARMLING, EnemyKind.SHIELDBEARER, EnemyKind.DIGGER, EnemyKind.SAPPER), 0, listOf(
            ".G....G.", "........", "........", ".##.....", "........", "........",
            ".....##.", "........", "........", "..#.....", "........", "....K...")),
        Spec(10, "salt", "Deep Crossing", 60, 10, T, emptyList(), listOf(EnemyKind.RUNNER, EnemyKind.BRUTE, EnemyKind.DIGGER, EnemyKind.JUMPER, EnemyKind.FLYER, EnemyKind.SAPPER), 1, listOf(
            "..G..G..", "........", ".#....#.", "........", "...##...", "........",
            ".#....#.", "........", "...##...", "........", "........", "...K....")),
        Spec(11, "fen", "Reed Bank", 60, 10, T, listOf(EnemyKind.OILSKIN), listOf(EnemyKind.RAIDER, EnemyKind.RUNNER, EnemyKind.SWARMLING, EnemyKind.FLYER, EnemyKind.OILSKIN), 0, listOf(
            "...G....", "........", "##......", "........", ".....##.", "........",
            "........", "##......", "........", ".....##.", "........", "....K...")),
        Spec(12, "fen", "Peat Road", 60, 11, T, listOf(EnemyKind.FROSTBORN), listOf(EnemyKind.RAIDER, EnemyKind.BRUTE, EnemyKind.JUMPER, EnemyKind.OILSKIN, EnemyKind.FROSTBORN), 0, listOf(
            "....G...", "........", "...#....", "........", "......#.", "........",
            ".#......", "........", "........", "...#.#..", "........", "..K.....")),
        Spec(13, "fen", "Eel Weir", 62, 11, T, emptyList(), listOf(EnemyKind.RUNNER, EnemyKind.SWARMLING, EnemyKind.SHIELDBEARER, EnemyKind.DIGGER, EnemyKind.FLYER, EnemyKind.FROSTBORN), 0, listOf(
            "G......G", "........", "...##...", "........", "........", "#......#",
            "........", "........", "...##...", "........", "........", "...K....")),
        Spec(14, "fen", "Mist Causeway", 65, 12, T, emptyList(), listOf(EnemyKind.BRUTE, EnemyKind.SHIELDBEARER, EnemyKind.JUMPER, EnemyKind.OILSKIN, EnemyKind.SAPPER, EnemyKind.FLYER), 0, listOf(
            "..G.....", "........", ".#.#.#..", "........", "........", "..#.#.#.",
            "........", "........", ".#.#.#..", "........", "........", ".....K..")),
        Spec(15, "fen", "Drowned Keep", 70, 12, T, emptyList(), EnemyKind.entries.filter { it != EnemyKind.WARLORD }, 2, listOf(
            ".G....G.", "........", "..#..#..", "........", "#......#", "........",
            "...##...", "........", "#......#", "........", "........", "...K....")),
    )

    private fun gapFor(k: EnemyKind) = when (k) {
        EnemyKind.SWARMLING -> 0.35f
        EnemyKind.RUNNER -> 0.6f
        EnemyKind.BRUTE -> 1.6f
        else -> 0.9f
    }

    private fun waves(spec: Spec, hpScale: Float): List<WaveDef> = List(spec.waves) { i ->
        val rng = Rng(Rng.seedOf("forge:${spec.id}:$i"))
        val count = 5 + i + spec.id / 2
        val kinds: List<EnemyKind> = when {
            i == 0 -> listOf(EnemyKind.RAIDER)
            i == 1 && spec.newEnemies.any { it != EnemyKind.WARLORD } -> listOf(EnemyKind.RAIDER, spec.newEnemies.first { it != EnemyKind.WARLORD })
            else -> List(minOf(3, 1 + i / 3)) { spec.pool[rng.nextInt(spec.pool.size)] }.distinct()
        }
        val groups = ArrayList<WaveGroup>()
        var delay = 0f
        kinds.forEachIndexed { k, kind ->
            val share = if (k == kinds.lastIndex) count - kinds.size.let { n -> (count / n) * (n - 1) } else count / kinds.size
            // Flyers ignore the maze, so a full share of them is a dart check rather than a maze test.
            val c = maxOf(1, when (kind) {
                EnemyKind.SWARMLING -> share * 2
                EnemyKind.BRUTE -> maxOf(1, share / 2)
                EnemyKind.FLYER -> (share * 3 + 4) / 5
                else -> share
            })
            groups += WaveGroup(kind.id, c, gapFor(kind), delay)
            delay += c * gapFor(kind) + 1.2f
        }
        if (i == spec.waves - 1 && spec.warlords > 0) groups += WaveGroup(EnemyKind.WARLORD.id, spec.warlords, 6f, delay)
        // Level 1 is the tutorial: its first waves ramp in, so the coach marks' one wall and one spike hold wave 1.
        val ramp = if (spec.id == 1) minOf(1f, 0.3f + 0.35f * i) else 1f
        WaveDef(((hpScale * (1f + 0.1f * i) * ramp) * 100).toInt() / 100f, groups)
    }

    private fun level(spec: Spec, hpScale: Float, par: Map<String, Par>) = LevelDef(
        id = spec.id, region = spec.region, name = spec.name, map = spec.map, startCoin = spec.coin,
        traps = spec.traps.map { it.id }, newEnemies = spec.newEnemies.map { it.id },
        waves = waves(spec, hpScale), par = par,
    )

    private class Outcome(val sim: Sim, val actions: List<RefAction>)

    private fun best(lv: LevelDef, d: Difficulty, content: GameContent): Outcome? {
        var best: Outcome? = null
        for (thick in listOf(false, true)) for (share in listOf(0.35f, 0.5f, 0.65f)) {
            val sim = Sim(RunSpec.forLevel(lv, d), content)
            val bot = Bot(sim, thick, share)
            bot.playToEnd()
            if (sim.phase == Phase.WON && sim.stranded == 0 && (best == null || sim.score() > best.sim.score())) {
                best = Outcome(sim, bot.actions.toList())
            }
        }
        return best
    }

    /** True when every thin serpentine clears with at least [hearts] left: the plain maze a player builds after the early levels. */
    private fun thinClears(lv: LevelDef, d: Difficulty, content: GameContent, hearts: Int = 1) = listOf(0.35f, 0.5f, 0.65f).all { share ->
        val sim = Sim(RunSpec.forLevel(lv, d), content)
        Bot(sim, thick = false, wallShare = share).playToEnd()
        sim.phase == Phase.WON && sim.hearts >= hearts
    }

    @Test
    fun forge() {
        assumeTrue(File("forge.on").exists())
        val base = TestContent.content
        val levelsDir = File(TestContent.assets, "levels").apply { mkdirs() }
        TestContent.referenceDir.mkdirs()
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val dummyPar = Difficulty.entries.associate { it.id to Par(0, 0) }
        val contentFor = { lv: LevelDef -> GameContent(base.traps, base.enemies, base.combos, base.regions, listOf(lv), base.daily) }
        for (spec in specs) {
            val target = 17 - (spec.id * 0.6f).toInt()
            var lo = 0.2f
            var hi = 6f
            repeat(14) {
                val mid = (lo + hi) / 2
                val lv = level(spec, mid, dummyPar)
                val o = best(lv, Difficulty.IRON, contentFor(lv))
                // Tuned against the best build on Iron, and also against thin mazes on Standard, so a level
                // whose best build is a thick maze (jumpers) does not wall off the ordinary player.
                if (o != null && o.sim.hearts >= target && thinClears(lv, Difficulty.STANDARD, contentFor(lv)) &&
                    thinClears(lv, Difficulty.WARDEN, contentFor(lv), hearts = 5)) lo = mid else hi = mid
            }
            // Level 1 is the tutorial: a gentler ramp than the curve, but not free.
            var scale = if (spec.id == 1) lo * 0.7f else lo
            var result: Map<Difficulty, Outcome>? = null
            while (result == null) {
                val lv = level(spec, scale, dummyPar)
                val outs = Difficulty.entries.associateWith { best(lv, it, contentFor(lv)) }
                val empty = Sim(RunSpec.forLevel(lv, Difficulty.WARDEN), contentFor(lv))
                while (empty.phase == Phase.BUILD) { empty.sendWave(); TestContent.runWave(empty) }
                check(empty.phase == Phase.LOST) { "Level ${spec.id} is won with nothing placed" }
                if (outs.values.all { it != null }) result = outs.mapValues { it.value!! } else scale *= 0.95f
            }
            val rawPar = result.mapKeys { it.key.id }.mapValues { (_, o) ->
                val s = o.sim.score()
                Par(silver = (s * 0.75f).toInt() / 10 * 10, gold = (s * 0.95f).toInt() / 10 * 10)
            }
            // The easier setting never asks for a higher score than the harder one.
            val std = rawPar.getValue(Difficulty.STANDARD.id)
            val par = rawPar.mapValues { (id, p) ->
                if (id == Difficulty.WARDEN.id) Par(minOf(p.silver, std.silver), minOf(p.gold, std.gold)) else p
            }
            val lv = level(spec, scale, par)
            File(levelsDir, "level%02d.json".format(spec.id)).writeText(json.encodeToString(LevelDef.serializer(), lv) + "\n")
            result.forEach { (d, o) ->
                ReferenceIo.file(spec.id, d).writeText(ReferenceIo.json.encodeToString(Reference.serializer(), Reference(spec.id, d.id, o.actions)) + "\n")
            }
            println("level ${spec.id} scale $scale " + result.entries.joinToString { "${it.key.id}: hearts ${it.value.sim.hearts} score ${it.value.sim.score()}" })
        }
    }

    @Test
    fun dailyProbe() {
        assumeTrue(File("forge.on").exists())
        val content = TestContent.content
        val start = java.time.LocalDate.of(2026, 1, 1)
        val reached = (0 until 60).map { i ->
            val daily = DailyGenerator.generate(start.plusDays(i * 17L).toString())
            val sim = Sim(DailyGenerator.spec(daily, content), content)
            Bot(sim, thick = false).playToEnd(maxWaves = 60)
            sim.waveIndex
        }
        println("daily reached " + reached.sorted())
    }
}

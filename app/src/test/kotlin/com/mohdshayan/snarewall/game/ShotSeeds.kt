package com.mohdshayan.snarewall.game

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Authoring tool for store screenshots, not a check. Writes Snarewall save files whose runs are
 * reached by the real Sim through the public placement API, so every board in a screenshot is a
 * state the shipped game produces. Runs only when app/shots.on exists.
 */
class ShotSeeds {

    private val content = TestContent.content
    private val out = File("build/shot-seeds")

    private fun progress(): List<LevelProgressRow> {
        val medals = listOf("gold", "gold", "silver", "gold", "bronze", "silver", "silver", "gold", "bronze", "silver")
        val day = 86_400_000L
        val base = 1_788_900_000_000L - 12 * day
        return medals.mapIndexed { i, m ->
            val lv = content.level(i + 1)!!
            val par = lv.par.getValue("standard")
            val score = when (m) {
                "gold" -> par.gold + 37 + i * 11
                "silver" -> par.silver + (par.gold - par.silver) / 3 + 13
                else -> par.silver - 91 - i * 7
            }
            LevelProgressRow(i + 1, "standard", score, m, if (m == "bronze") 6 + i % 3 else 14 + i % 5, 1 + (i % 3), base + i * day / 2, base + i * day / 2 + 3_600_000L)
        } + LevelProgressRow(1, "iron", content.level(1)!!.par.getValue("iron").silver + 42, "silver", 12, 1, base + 6 * day, base + 6 * day)
    }

    private fun daily() = listOf(
        DailyScoreRow("2026-09-16", 19, 3265, 2, 1_789_550_000_000L),
        DailyScoreRow("2026-09-15", 14, 2170, 1, 1_789_460_000_000L),
        DailyScoreRow("2026-09-13", 23, 4120, 3, 1_789_290_000_000L),
        DailyScoreRow("2026-09-12", 11, 1745, 1, 1_789_200_000_000L),
        DailyScoreRow("2026-09-10", 17, 2890, 2, 1_789_030_000_000L),
    )

    private fun apply(sim: Sim, a: RefAction) = when (a.op) {
        "wall" -> sim.placeWall(a.tile)
        "trap" -> sim.placeTrap(TrapKind.byId(a.kind!!)!!, a.tile)
        else -> sim.upgrade(a.tile)
    }

    /** Replays the reference up to the start of [wave], placing that wave's actions when [withBuild]. */
    private fun reach(levelId: Int, wave: Int, withBuild: Boolean): Sim {
        val lv = content.level(levelId)!!
        val ref = ReferenceIo.read(levelId, Difficulty.STANDARD)
        val sim = Sim(RunSpec.forLevel(lv, Difficulty.STANDARD), content)
        val byWave = ref.actions.groupBy { it.wave }
        while (sim.waveIndex < wave) {
            byWave[sim.waveIndex]?.forEach { apply(sim, it) }
            sim.sendWave()
            TestContent.runWave(sim)
            check(sim.phase == Phase.BUILD)
        }
        if (withBuild) byWave[sim.waveIndex]?.forEach { apply(sim, it) }
        return sim
    }

    private fun onRoute(sim: Sim): BooleanArray {
        val mark = BooleanArray(Grid.N)
        val astar = AStar(sim.layout)
        val path = IntArray(Grid.N)
        for (g in sim.layout.gates) {
            val n = astar.path(sim.walls, g, -1, path)
            for (k in 0 until n) mark[path[k]] = true
        }
        return mark
    }

    /** Adds a pusher and a deadfall whose combo is live on the route, building walls and turning traps as a player would. */
    private fun addPushDeadfall(sim: Sim): Boolean {
        val route = onRoute(sim)
        for (a in 0 until Grid.N) for (b in 0 until Grid.N) {
            if (a == b) continue
            val trial = copy(sim)
            val moves = ArrayList<Pair<String, Int>>()
            if (!trial.walls[a]) { if (trial.placeWall(a) != null) continue; moves += "wall" to a }
            if (!trial.walls[b]) { if (trial.placeWall(b) != null) continue; moves += "wall" to b }
            if (trial.trapAt[a] >= 0 || trial.trapAt[b] >= 0) continue
            if (trial.placeTrap(TrapKind.DEADFALL, b) != null) continue
            if (trial.placeTrap(TrapKind.PUSHER, a) != null) continue
            moves += "deadfall" to b
            moves += "pusher" to a
            var found = false
            outer@ for (rd in 0 until 4) {
                for (rp in 0 until 4) {
                    val p = trial.traps.first { it.kind == TrapKind.PUSHER }
                    if (trial.links.any { it.kind == ComboKind.PUSH_DEADFALL } && p.target >= 0 && Grid.y(p.target) in 4..9 && onRoute(trial)[p.target] && route[p.target]) {
                        found = true
                        break@outer
                    }
                    trial.rotate(a); moves += "rotate" to a
                }
                trial.rotate(b); moves += "rotate" to b
            }
            if (!found) continue
            for ((op, t) in moves) {
                val ok = when (op) {
                    "wall" -> sim.placeWall(t) == null
                    "deadfall" -> sim.placeTrap(TrapKind.DEADFALL, t) == null
                    "pusher" -> sim.placeTrap(TrapKind.PUSHER, t) == null
                    else -> sim.rotate(t)
                }
                check(ok) { "$op $t" }
            }
            return sim.links.any { it.kind == ComboKind.PUSH_DEADFALL }
        }
        return false
    }

    private var linked = 0

    /** Oil slicks on route tiles next to an ember grate. */
    private fun addOilEmber(sim: Sim): Boolean {
        val route = onRoute(sim)
        for (e in 0 until Grid.N) {
            if (!route[e] || Grid.y(e) !in 4..9 || sim.trapError(TrapKind.EMBER, e) != null) continue
            val oils = (0..3).map { Grid.step(e, it) }.filter { it >= 0 && route[it] && sim.trapError(TrapKind.OIL, it) == null }
            if (oils.size >= 2) {
                check(sim.placeTrap(TrapKind.EMBER, e) == null)
                oils.forEach { check(sim.placeTrap(TrapKind.OIL, it) == null) }
                return true
            }
        }
        return false
    }

    private fun copy(sim: Sim): Sim {
        val fresh = Sim(sim.spec, content)
        fresh.restore(sim.snapshot())
        return fresh
    }

    private fun write(name: String, resume: RunSave?) {
        out.mkdirs()
        val file = SaveFile(
            exportedAt = 1_789_640_000_000L,
            levelProgress = progress(),
            resume = resume?.let { ResumeRow(it.levelId, it.difficulty, it.wave, SaveCodec.encodeRun(it), 1_789_640_000_000L) },
            dailyScores = daily(),
            settings = mapOf("tutorial_done" to "true", "default_speed" to "1", "levels_cleared_total" to "10", "theme" to "system"),
        )
        check(SaveCodec.decode(SaveCodec.encode(file)) is SaveCodec.Decoded.Ok)
        File(out, "$name.json").writeText(SaveCodec.encode(file))
        println("$name: ${resume?.let { "level ${it.levelId} wave ${it.wave + 1} walls ${it.walls.size} traps ${it.traps.size} coin ${it.coin} hearts ${it.hearts}" }}")
    }

    @Test
    fun seeds() {
        assumeTrue(File("shots.on").exists())
        // 1: Salt Mine, a maze under way with coin left for more walls.
        var best: Triple<Sim, Int, Int>? = null
        // Prefixes: the reference run, then a light thin maze played wave by wave, both through the real Sim.
        val prefixes = listOf(2, 1, 3, 4).map { { reach(7, it, withBuild = false) } } + listOf(1, 2, 3).map { w ->
            {
                val sim = Sim(RunSpec.forLevel(content.level(7)!!, Difficulty.STANDARD), content)
                val bot = Bot(sim, thick = false, wallShare = 0.2f)
                while (sim.waveIndex < w) { bot.playBuildPhase(); sim.sendWave(); TestContent.runWave(sim); check(sim.phase == Phase.BUILD) }
                sim
            }
        }
        for (make in prefixes) {
        if (best != null) break
        val prefix = make()
        for (g1 in -1 until Grid.W) for (b in 0 until Grid.W) for (c in b + 1 until Grid.W) {
            val trial = copy(prefix)
            var ok = true
            if (g1 >= 0) for (x in 0 until Grid.W) if (x != g1) {
                val t = Grid.idx(x, 1)
                if (!trial.walls[t] && !trial.layout.obstacle[t] && trial.placeWall(t) != null) ok = false
            }
            for (x in 0 until Grid.W) if (x != b && x != c) {
                val t = Grid.idx(x, 3)
                if (!trial.walls[t] && !trial.layout.obstacle[t] && trial.placeWall(t) != null) ok = false
            }
            if (!ok) continue
            val base = trial.field.dist[trial.layout.gates[0]]
            val astar = AStar(trial.layout)
            val path = IntArray(Grid.N)
            for (t in 0 until Grid.N) {
                if (trial.wallError(t) != null || Grid.y(t) !in 1..9) continue
                val d = astar.path(trial.walls, trial.layout.gates[0], t, path) - 1 - base
                // A clear detour: prefer +8, then the nearest to it.
                if (d in 3..12 && (best == null || kotlin.math.abs(d - 8) < kotlin.math.abs(best.third - 8))) best = Triple(trial, t, d)
            }
        }
        }
        // The reference maze itself, when the row template does not fit it.
        for (w1 in 1..6) {
            if (best != null) break
            val trial = reach(7, w1, withBuild = false)
            val base = trial.field.dist[trial.layout.gates[0]]
            val astar = AStar(trial.layout)
            val path = IntArray(Grid.N)
            for (t in 0 until Grid.N) {
                if (trial.wallError(t) != null || Grid.y(t) !in 1..9) continue
                val d = astar.path(trial.walls, trial.layout.gates[0], t, path) - 1 - base
                if (d in 2..12 && (best == null || kotlin.math.abs(d - 8) < kotlin.math.abs(best.third - 8))) best = Triple(trial, t, d)
            }
            println("seed 1 fallback wave ${w1 + 1}: coin ${trial.coin} walls ${trial.walls.count { it }} best ${best?.third}")
        }
        val (s1, ghost, delta) = best!!
        println("ghost ${Grid.x(ghost)},${Grid.y(ghost)} +$delta")
        for (y in 0 until 12) println("row " + (0 until 8).joinToString("") { x -> val i = Grid.idx(x, y); when { i == ghost -> "g"; s1.layout.obstacle[i] -> "#"; s1.walls[i] -> "W"; else -> "." } })
        write("seed-1-maze", s1.snapshot())
        // 2: pusher into deadfall, live link on the route.
        val s2 = listOf(8 to 3, 8 to 2, 8 to 1, 4 to 3, 3 to 3).firstNotNullOf { (id, w) ->
            val sim = reach(id, w, withBuild = false)
            if (addPushDeadfall(sim)) sim.also { println("combo on level $id wave ${w + 1}") } else null
        }
        write("seed-2-combo", s2.snapshot())
        // 3: diggers and jumpers against a maze. Played, not edited: a thin serpentine bot plays the level
        // on Standard through the real Sim, wave by wave, and the save is its own build-phase snapshot at the
        // start of the first wave that sends both diggers and jumpers. The level is looked up rather than
        // named, so the caption stays true whatever the level line-ups are.
        val (lvD, w3) = content.levels.firstNotNullOf { lv ->
            lv.waves.indices.firstOrNull { i -> lv.waves[i].groups.map { it.enemy }.let { "digger" in it && "jumper" in it } }
                ?.let { lv to it }
        }
        val thin = Sim(RunSpec.forLevel(lvD, Difficulty.STANDARD), content)
        val player = Bot(thin, thick = false, wallShare = 0.5f)
        while (thin.waveIndex < w3) {
            player.playBuildPhase()
            check(thin.sendWave())
            TestContent.runWave(thin)
            check(thin.phase == Phase.BUILD) { "the thin maze lost level ${lvD.id} before wave ${w3 + 1}" }
        }
        player.playBuildPhase()
        val s3 = thin.snapshot()
        Sim(RunSpec.forLevel(lvD, Difficulty.STANDARD), content).restore(s3)
        write("seed-3-diggers", s3)
        println("level ${lvD.id} wave ${w3 + 1}: ${lvD.waves[w3].groups.map { "${it.count} ${it.enemy} delay ${it.delay}" }}")
        // 5: Fen Causeway, oil and ember.
        val s5 = reach(11, 5, withBuild = false)
        println("s5 coin ${s5.coin}")
        check(addOilEmber(s5)) { "no oil ember found" }
        write("seed-5-fire", s5.snapshot())
        println("level 11 wave 6: ${content.level(11)!!.waves[5].groups.map { "${it.count} ${it.enemy}" }}")
        write("seed-4-daily", null)
        // Performance check: the busiest level on its largest wave.
        val busiest = content.levels.maxBy { l -> l.waves.maxOf { w -> w.groups.sumOf { it.count } } }
        val big = busiest.waves.indices.maxBy { i -> busiest.waves[i].groups.sumOf { it.count } }
        write("seed-perf", reach(busiest.id, big, withBuild = true).snapshot())
        println("perf: level ${busiest.id} wave ${big + 1} enemies ${busiest.waves[big].groups.sumOf { it.count }}")
    }
}

package com.mohdshayan.snarewall.game

/**
 * A plain, deterministic player used to author reference solutions and to prove daily maps are
 * playable. It builds a serpentine of walls, then buys traps in a fixed cycle near the keep first.
 * It never uses a rule the player cannot: every move goes through the public Sim API.
 */
class Bot(private val sim: Sim, private val thick: Boolean, private val wallShare: Float = 0.5f) {

    val actions = ArrayList<RefAction>()
    private var purchases = 0

    private val cycle = listOf(
        TrapKind.DART, TrapKind.GRINDER, TrapKind.SPIKE, TrapKind.HAMMER, TrapKind.FROST,
        TrapKind.EMBER, TrapKind.OIL, TrapKind.DEADFALL, TrapKind.SPIKE, TrapKind.SNARE, TrapKind.PUSHER,
    ).filter { it in sim.spec.roster }

    private fun templateTiles(): List<Int> {
        val keepX = Grid.x(sim.layout.keep)
        val first = if (keepX < 4) 7 else 0
        val rows: List<IntArray> = if (thick) listOf(intArrayOf(10, 9), intArrayOf(7, 6), intArrayOf(3, 2))
        else listOf(intArrayOf(10), intArrayOf(8), intArrayOf(6), intArrayOf(4), intArrayOf(2))
        val out = ArrayList<Int>()
        rows.forEachIndexed { i, ys ->
            val gap = if (i % 2 == 0) first else 7 - first
            for (y in ys) for (x in 0 until Grid.W) if (x != gap) out += Grid.idx(x, y)
        }
        return out
    }

    fun playBuildPhase() {
        if (sim.phase != Phase.BUILD) return
        val wallBudget = maxOf(1, (sim.coin * wallShare).toInt())
        var spent = 0
        for (t in templateTiles()) {
            if (spent >= wallBudget) break
            if (sim.walls[t] || sim.layout.obstacle[t]) continue
            if (sim.placeWall(t) == null) {
                actions += RefAction(sim.waveIndex, "wall", t)
                spent++
            }
        }
        var guard = 0
        while (cycle.isNotEmpty() && guard++ < 40) {
            val kind = cycle[purchases % cycle.size]
            if (purchases > 0 && purchases % 5 == 4 && tryUpgrade()) {
                purchases++
                continue
            }
            if (sim.coin < sim.trapCost(kind)) break
            val tile = bestTile(kind)
            if (tile < 0) {
                if (!tryUpgrade()) break
                purchases++
                continue
            }
            check(sim.placeTrap(kind, tile) == null)
            actions += RefAction(sim.waveIndex, "trap", tile, kind.id)
            purchases++
        }
    }

    private fun tryUpgrade(): Boolean {
        val order = sim.traps.sortedWith(compareBy({ it.upgraded }, { -sim.trapCost(it.kind) }, { it.tile }))
        for (t in order) {
            if (t.upgraded || t.kind == TrapKind.OIL || t.kind == TrapKind.PUSHER) continue
            if (sim.upgradeError(t.tile) == null) {
                sim.upgrade(t.tile)
                actions += RefAction(sim.waveIndex, "upgrade", t.tile)
                return true
            }
        }
        return false
    }

    /** Tiles on the walked route from every gate, nearest the keep first. */
    private fun onRoute(): BooleanArray {
        val mark = BooleanArray(Grid.N)
        for (g in sim.layout.gates) {
            var cur = g
            while (cur != sim.layout.keep && sim.field.dist[cur] > 0) {
                mark[cur] = true
                var nxt = -1
                for (d in intArrayOf(2, 1, 3, 0)) {
                    val n = Grid.step(cur, d)
                    if (n >= 0 && sim.field.dist[n] == sim.field.dist[cur] - 1 && !sim.walls[n] && !sim.layout.obstacle[n]) { nxt = n; break }
                }
                if (nxt < 0) break
                cur = nxt
            }
        }
        return mark
    }

    private fun routeTiles(): List<Int> {
        val mark = onRoute()
        return (0 until Grid.N).filter { mark[it] && !sim.walls[it] && sim.layout.buildable(it) }
            .sortedWith(compareBy({ sim.field.dist[it] }, { it }))
    }

    private fun bestTile(kind: TrapKind): Int {
        if (kind.wallMounted) {
            val walls = (0 until Grid.N).filter { sim.walls[it] && sim.trapAt[it] < 0 && sim.trapError(kind, it) == null }
            if (walls.isEmpty()) return -1
            return when (kind) {
                TrapKind.DART -> walls.maxWithOrNull(compareBy<Int> { coverage(it) }.thenBy { -it }) ?: -1
                else -> walls.minWithOrNull(compareBy<Int> { faceDist(kind, it) }.thenBy { it }) ?: -1
            }
        }
        val route = routeTiles().filter { sim.trapError(kind, it) == null }
        if (route.isEmpty()) return -1
        val partner = when (kind) {
            TrapKind.FROST -> sim.traps.filter { it.kind == TrapKind.HAMMER }.map { it.target }
            TrapKind.OIL -> sim.traps.filter { it.kind == TrapKind.EMBER }.map { it.tile }
            TrapKind.SNARE -> sim.traps.filter { it.kind == TrapKind.SPIKE }.map { it.tile }
            else -> emptyList()
        }
        if (partner.isNotEmpty()) {
            route.firstOrNull { r -> partner.any { p -> Grid.manhattan(p, r) <= (if (kind == TrapKind.FROST) 0 else 1) } }?.let { return it }
            route.firstOrNull { r -> partner.any { p -> Grid.manhattan(p, r) == 1 } }?.let { return it }
        }
        return route.first()
    }

    private fun faceDist(kind: TrapKind, tile: Int): Int {
        val d = sim.autoFacing(kind, tile)
        if (d < 0) return 9999
        val n = Grid.step(tile, d)
        val dist = sim.field.dist[n]
        return if (dist < 0) 9999 else if (onRoute()[n]) dist else dist + 500
    }

    private val flightCells get() = sim.flightLine

    private fun coverage(tile: Int): Int {
        var best = 0
        for (d in 0..3) {
            val first = Grid.step(tile, d)
            if (first < 0 || sim.walls[first] || sim.layout.obstacle[first]) continue
            var c = 0
            var cur = tile
            for (k in 0 until Sim.DART_RANGE) {
                cur = Grid.step(cur, d)
                if (cur < 0) break
                if (!sim.walls[cur] && !sim.layout.obstacle[cur] && sim.field.dist[cur] >= 0) c++
                if (flightCells[cur]) c += 3
            }
            best = maxOf(best, c)
        }
        return best
    }

    /** Plays a whole run. Returns the finished sim. */
    fun playToEnd(maxWaves: Int = Int.MAX_VALUE): Sim {
        while (sim.phase == Phase.BUILD && sim.waveIndex < maxWaves) {
            playBuildPhase()
            sim.sendWave()
            TestContent.runWave(sim)
        }
        return sim
    }
}

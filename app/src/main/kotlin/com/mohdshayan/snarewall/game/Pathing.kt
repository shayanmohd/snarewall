package com.mohdshayan.snarewall.game

/**
 * Breadth-first distance from the keep over walkable tiles. Ground enemies walk it downhill.
 * All buffers are allocated once; compute() allocates nothing.
 */
class DistanceField(private val layout: BoardLayout) {
    val dist = IntArray(Grid.N)
    private val queue = IntArray(Grid.N)

    /** [walls] marks player walls; [extraBlocked] is one more tile treated as a wall (a ghost), or -1. */
    fun compute(walls: BooleanArray, extraBlocked: Int = -1) {
        dist.fill(-1)
        var head = 0
        var tail = 0
        dist[layout.keep] = 0
        queue[tail++] = layout.keep
        while (head < tail) {
            val cur = queue[head++]
            for (d in 0..3) {
                val n = Grid.step(cur, d)
                if (n < 0 || dist[n] >= 0) continue
                if (layout.obstacle[n] || walls[n] || n == extraBlocked) continue
                dist[n] = dist[cur] + 1
                queue[tail++] = n
            }
        }
    }
}

/**
 * The route a ground enemy actually walks: downhill on the distance field, trying directions in
 * [WALK_ORDER] exactly as [Sim] does, so the drawn thread and the enemies never disagree on a tie.
 */
object Route {
    /** Down, right, left, up. Sim uses this same array. */
    val WALK_ORDER = intArrayOf(2, 1, 3, 0)

    /** Writes the tiles from [from] to the keep into [out] and returns the count, or 0 when [from] cannot reach it. */
    fun follow(layout: BoardLayout, walls: BooleanArray, field: DistanceField, from: Int, extraBlocked: Int, out: IntArray): Int {
        if (from < 0 || field.dist[from] < 0) return 0
        var cur = from
        var n = 0
        out[n++] = cur
        while (field.dist[cur] > 0) {
            var next = -1
            for (d in WALK_ORDER) {
                val s = Grid.step(cur, d)
                if (s >= 0 && field.dist[s] == field.dist[cur] - 1 && !layout.obstacle[s] && !walls[s] && s != extraBlocked) {
                    next = s
                    break
                }
            }
            if (next < 0) return 0
            cur = next
            out[n++] = cur
        }
        return n
    }
}

/**
 * A* from one tile to the keep, the independent check on route length. It is written independently of
 * [DistanceField] so a test can check that both agree on length and reachability.
 */
class AStar(private val layout: BoardLayout) {
    private val g = IntArray(Grid.N)
    private val came = IntArray(Grid.N)
    private val open = BooleanArray(Grid.N)
    private val closed = BooleanArray(Grid.N)

    /** Writes the path (start first, keep last) into [out] and returns its tile count, or 0 when sealed. */
    fun path(walls: BooleanArray, from: Int, extraBlocked: Int, out: IntArray): Int {
        val goal = layout.keep
        g.fill(Int.MAX_VALUE)
        came.fill(-1)
        open.fill(false)
        closed.fill(false)
        g[from] = 0
        open[from] = true
        while (true) {
            var best = -1
            var bestF = Int.MAX_VALUE
            var bestH = Int.MAX_VALUE
            for (i in 0 until Grid.N) {
                if (!open[i]) continue
                val h = Grid.manhattan(i, goal)
                val f = g[i] + h
                if (f < bestF || (f == bestF && h < bestH)) {
                    best = i; bestF = f; bestH = h
                }
            }
            if (best < 0) return 0
            if (best == goal) break
            open[best] = false
            closed[best] = true
            for (d in 0..3) {
                val n = Grid.step(best, d)
                if (n < 0 || closed[n]) continue
                if (layout.obstacle[n] || walls[n] || n == extraBlocked) continue
                val ng = g[best] + 1
                if (ng < g[n]) {
                    g[n] = ng
                    came[n] = best
                    open[n] = true
                }
            }
        }
        var count = 0
        var cur = goal
        while (cur != -1) {
            out[count++] = cur
            cur = came[cur]
        }
        out.reverse(0, count)
        return count
    }
}

/** Legality of a wall on [tile]: buildable, free, not under an enemy, and leaves every gate and enemy a route. */
object Placement {
    enum class Error(val message: String) {
        NOT_FLOOR("Walls go on open floor"),
        OCCUPIED("Something is already there"),
        ENEMY_ON_TILE("An enemy is standing there"),
        SEALS("Seals the keep"),
        NEEDS_WALL("Mount it on a wall"),
        NO_FACE("No open floor to face"),
        NOT_IN_ROSTER("Not on this map"),
        GAME_OVER("The level is over"),
        NEEDS_COIN("Needs coin"),
    }

    /**
     * Checks a wall on [tile] against [field], which is recomputed with the tile blocked.
     * [occupied] lists tiles enemies stand on or walk into; [occupiedCount] of them are valid.
     */
    fun checkWall(
        layout: BoardLayout,
        walls: BooleanArray,
        trapAt: IntArray,
        field: DistanceField,
        tile: Int,
        occupied: IntArray,
        occupiedCount: Int,
    ): Error? {
        if (tile < 0 || tile >= Grid.N || !layout.buildable(tile)) return Error.NOT_FLOOR
        if (walls[tile] || trapAt[tile] >= 0) return Error.OCCUPIED
        for (k in 0 until occupiedCount) if (occupied[k] == tile) return Error.ENEMY_ON_TILE
        field.compute(walls, tile)
        var sealed = false
        for (gate in layout.gates) if (field.dist[gate] < 0) sealed = true
        for (k in 0 until occupiedCount) if (field.dist[occupied[k]] < 0) sealed = true
        field.compute(walls)
        return if (sealed) Error.SEALS else null
    }
}

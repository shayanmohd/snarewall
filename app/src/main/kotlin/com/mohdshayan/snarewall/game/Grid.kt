package com.mohdshayan.snarewall.game

/** The fixed 8 by 12 board. Tiles are indexed row first: index = y * W + x. */
object Grid {
    const val W = 8
    const val H = 12
    const val N = W * H

    /** Directions: 0 up, 1 right, 2 down, 3 left. */
    val DX = intArrayOf(0, 1, 0, -1)
    val DY = intArrayOf(-1, 0, 1, 0)

    fun idx(x: Int, y: Int) = y * W + x
    fun x(i: Int) = i % W
    fun y(i: Int) = i / W
    fun opposite(d: Int) = (d + 2) % 4

    /** The neighbour of [i] in direction [d], or -1 off the board. */
    fun step(i: Int, d: Int): Int {
        val nx = x(i) + DX[d]
        val ny = y(i) + DY[d]
        return if (nx in 0 until W && ny in 0 until H) idx(nx, ny) else -1
    }

    fun manhattan(a: Int, b: Int) = kotlin.math.abs(x(a) - x(b)) + kotlin.math.abs(y(a) - y(b))

    /** Direction from [a] to the orthogonal neighbour [b], or -1 when they are not neighbours. */
    fun dirTo(a: Int, b: Int): Int {
        for (d in 0..3) if (step(a, d) == b) return d
        return -1
    }
}

/** The static part of a board: obstacles, gates and the keep. Walls are dynamic and live in [Sim]. */
class BoardLayout(val obstacle: BooleanArray, val gates: IntArray, val keep: Int) {

    fun isGate(i: Int) = gates.contains(i)

    /** Floor the player may build on: not an obstacle, gate or keep. */
    fun buildable(i: Int) = !obstacle[i] && i != keep && !isGate(i)

    companion object {
        fun fromMap(rows: List<String>): BoardLayout {
            require(rows.size == Grid.H) { "Map needs ${Grid.H} rows" }
            val obstacle = BooleanArray(Grid.N)
            val gates = ArrayList<Int>()
            var keep = -1
            rows.forEachIndexed { y, row ->
                require(row.length == Grid.W) { "Row $y needs ${Grid.W} tiles" }
                row.forEachIndexed { x, c ->
                    val i = Grid.idx(x, y)
                    when (c) {
                        '.' -> Unit
                        '#' -> obstacle[i] = true
                        'G' -> { require(y == 0) { "Gates sit on the top row" }; gates += i }
                        'K' -> { require(y == Grid.H - 1 && keep == -1) { "One keep on the bottom row" }; keep = i }
                        else -> error("Bad map tile '$c'")
                    }
                }
            }
            require(gates.size in 1..2) { "One or two gates" }
            require(keep >= 0) { "Map needs a keep" }
            val layout = BoardLayout(obstacle, gates.toIntArray(), keep)
            val field = DistanceField(layout)
            field.compute(BooleanArray(Grid.N))
            layout.gates.forEach { require(field.dist[it] >= 0) { "Gate cannot reach keep" } }
            return layout
        }
    }
}

package com.mohdshayan.snarewall.game

import kotlin.math.pow

/** Today's endless map: the same board, roster and waves for everyone on the same UTC date. */
class DailyMap(
    val dateKey: String,
    val map: List<String>,
    val roster: List<TrapKind>,
    val region: String,
) {
    val layout: BoardLayout = BoardLayout.fromMap(map)
}

object DailyGenerator {

    val REGIONS = listOf("chalk", "salt", "fen")

    /** The daily key for a moment: its UTC calendar date, whatever the phone's time zone or daylight saving. */
    fun dateKey(instant: java.time.Instant): String = instant.atOffset(java.time.ZoneOffset.UTC).toLocalDate().toString()

    fun todayKey(): String = dateKey(java.time.Instant.now())

    fun generate(dateKey: String): DailyMap {
        require(SaveCodec.isDateKey(dateKey)) { "Date key must be a yyyy-MM-dd date" }
        val rng = Rng(Rng.seedOf("daily:$dateKey"))
        val region = REGIONS[rng.nextInt(REGIONS.size)]
        val twoGates = rng.nextInt(3) == 0
        val gatesX = if (twoGates) listOf(1 + rng.nextInt(2), 5 + rng.nextInt(2)) else listOf(2 + rng.nextInt(4))
        val keepX = 2 + rng.nextInt(4)
        var rows: List<String>? = null
        for (attempt in 0 until 60) {
            val grid = Array(Grid.H) { CharArray(Grid.W) { '.' } }
            gatesX.forEach { grid[0][it] = 'G' }
            grid[Grid.H - 1][keepX] = 'K'
            val count = 4 + rng.nextInt(5)
            var placed = 0
            while (placed < count) {
                val x = rng.nextInt(Grid.W)
                val y = 2 + rng.nextInt(Grid.H - 4)
                if (grid[y][x] == '.') {
                    grid[y][x] = '#'; placed++
                }
            }
            val candidate = grid.map { String(it) }
            if (allFloorConnected(candidate)) {
                rows = candidate; break
            }
        }
        val map = rows ?: Array(Grid.H) { y ->
            CharArray(Grid.W) { x ->
                when {
                    y == 0 && x in gatesX -> 'G'
                    y == Grid.H - 1 && x == keepX -> 'K'
                    else -> '.'
                }
            }
        }.map { String(it) }
        val optional = mutableListOf(
            TrapKind.OIL, TrapKind.EMBER, TrapKind.FROST, TrapKind.SNARE,
            TrapKind.PUSHER, TrapKind.DEADFALL, TrapKind.HAMMER, TrapKind.GRINDER,
        )
        val roster = mutableListOf(TrapKind.SPIKE, TrapKind.DART)
        repeat(4) { roster += optional.removeAt(rng.nextInt(optional.size)) }
        return DailyMap(dateKey, map, roster.sortedBy { it.ordinal }, region)
    }

    /** Every open tile can reach the keep, so the generated map has no dead pockets. */
    fun allFloorConnected(rows: List<String>): Boolean {
        val layout = try {
            BoardLayout.fromMap(rows)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val field = DistanceField(layout)
        field.compute(BooleanArray(Grid.N))
        for (i in 0 until Grid.N) if (!layout.obstacle[i] && field.dist[i] < 0) return false
        return true
    }

    /** Wave [n] (0 based) of the daily map. Enemies unlock by wave number; every tenth wave brings a warlord. */
    fun wave(dateKey: String, n: Int, table: DailyTable): WaveDef {
        val rng = Rng(Rng.seedOf("daily:$dateKey:wave:$n"))
        val number = n + 1
        val unlocked = EnemyKind.entries.filter { k ->
            k != EnemyKind.WARLORD && (table.unlocks[k.id] ?: Int.MAX_VALUE) <= number
        }
        val groups = ArrayList<WaveGroup>()
        val kinds = if (unlocked.size <= 2) unlocked else List(minOf(3, 1 + number / 4)) { unlocked[rng.nextInt(unlocked.size)] }.distinct()
        val total = 5 + (number * 1.2f).toInt()
        var delay = 0f
        kinds.forEachIndexed { i, k ->
            val share = if (i == kinds.lastIndex) total - groups.sumOf { it.count } else maxOf(1, total / kinds.size)
            val count = if (k == EnemyKind.SWARMLING) share * 2 else share
            val gap = when (k) {
                EnemyKind.SWARMLING -> 0.35f
                EnemyKind.BRUTE -> 1.6f
                else -> 0.9f
            }
            if (count > 0) groups += WaveGroup(k.id, count, gap, delay)
            delay += count * gap + 1.5f
        }
        if (number % 10 == 0) groups += WaveGroup(EnemyKind.WARLORD.id, number / 10, 3f, delay)
        val hp = table.hpBase * (1f + table.hpGrowth).pow(n)
        return WaveDef(hp, groups)
    }

    fun spec(daily: DailyMap, content: GameContent) = RunSpec(
        levelId = 0,
        dailyKey = daily.dateKey,
        layout = daily.layout,
        roster = daily.roster,
        startCoin = content.daily.startCoin,
        difficulty = Difficulty.STANDARD,
        totalWaves = -1,
        waveAt = { wave(daily.dateKey, it, content.daily) },
        waveBonus = { content.daily.waveBonus + it / 2 },
    )
}

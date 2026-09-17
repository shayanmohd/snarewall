package com.mohdshayan.snarewall.game

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

enum class Phase { BUILD, WAVE, WON, LOST }

/** One run's fixed inputs: a handmade level at a difficulty, or the daily map. */
class RunSpec(
    val levelId: Int,
    val dailyKey: String?,
    val layout: BoardLayout,
    val roster: List<TrapKind>,
    val startCoin: Int,
    val difficulty: Difficulty,
    /** Number of waves, or -1 for the endless daily map. */
    val totalWaves: Int,
    val waveAt: (Int) -> WaveDef,
    val waveBonus: (Int) -> Int,
) {
    val endless get() = totalWaves < 0

    companion object {
        const val START_HEARTS = 20

        fun forLevel(level: LevelDef, difficulty: Difficulty) = RunSpec(
            levelId = level.id,
            dailyKey = null,
            layout = BoardLayout.fromMap(level.map),
            roster = level.traps.map { TrapKind.byId(it)!! },
            startCoin = level.startCoin,
            difficulty = difficulty,
            totalWaves = level.waves.size,
            waveAt = { level.waves[it] },
            waveBonus = { 4 + it },
        )
    }
}

/** The state saved at a wave boundary. There are no live enemies at a boundary, so this is complete. */
@Serializable
data class RunSave(
    val levelId: Int,
    val difficulty: String,
    val dailyKey: String? = null,
    val wave: Int,
    val hearts: Int,
    val coin: Int,
    val walls: List<Int>,
    val traps: List<SavedTrap>,
    val kills: Int,
    val rngState: Long,
)

@Serializable
data class SavedTrap(val tile: Int, val kind: String, val facing: Int, val upgrade: Int)

/** A live enemy. Instances are pooled; nothing is allocated while a wave runs. */
class Enemy {
    var active = false
    var serial = 0
    var kind = EnemyKind.RAIDER
    var hp = 0f
    var maxHp = 0f
    var speed = 0f
    var bounty = 0
    var hearts = 0
    var tile = 0
    var next = 0
    var progress = 0f
    var segLen = 1f
    var move = Sim.MOVE_WALK
    var x = 0f
    var y = 0f
    var prevX = 0f
    var prevY = 0f
    var root = 0f
    var rootGrace = 0f
    var chill = 0f
    var oiled = 0f
    var burn = 0f
    var burnDps = 0f
    var hopCooldown = 0f
    var dug = false
    var sapped = false
    var sapTimer = 0f
    var sapTile = -1
    var flash = 0f
    var dir = 2
    var shoves = 0

    /** The floor tile the enemy is standing on for trap purposes, or -1 when it is in the air or underground. */
    val onTile: Int
        get() = when (move) {
            Sim.MOVE_WALK -> if (progress < 0.5f) tile else next
            else -> -1
        }
}

/**
 * The whole game rule set, stepped at a fixed 60 ticks per second. Speed changes only how many
 * steps the UI runs per frame, so 1x and 3x reach identical states. No Android imports.
 */
class Sim(val spec: RunSpec, val content: GameContent) {

    val layout = spec.layout
    var phase = Phase.BUILD
        private set
    var waveIndex = 0
        private set
    var hearts = RunSpec.START_HEARTS
        private set
    var coin = spec.startCoin
        private set
    var kills = 0
        private set
    var tick = 0L
        private set
    var waveTime = 0f
        private set
    val rng = Rng(Rng.seedOf("${spec.levelId}:${spec.dailyKey}:${spec.difficulty.id}"))

    val walls = BooleanArray(Grid.N)
    val traps = ArrayList<Trap>()
    val trapAt = IntArray(Grid.N) { -1 }
    var links: List<ComboLink> = emptyList()
        private set

    val field = DistanceField(layout)
    private val scratchField = DistanceField(layout)
    val enemies = Array(MAX_ENEMIES) { Enemy() }
    var alive = 0
        private set

    /** Enemies that could not move because no route existed. The placement rule keeps this at zero. */
    var stranded = 0
        private set
    var leaked = 0
        private set

    private var serialCounter = 0
    private var spawnTimes = FloatArray(0)
    private var spawnKinds = IntArray(0)
    private var spawnGates = IntArray(0)
    private var spawnHp = FloatArray(0)
    private var spawnCursor = 0
    private val occupied = IntArray(MAX_ENEMIES * 2)

    private val undo = ArrayList<UndoEntry>()
    val undoCount get() = undo.size

    // Event ring buffer, read by the UI for sound, haptics and flashes.
    private val evType = IntArray(EVENT_CAP)
    private val evA = IntArray(EVENT_CAP)
    private val evB = IntArray(EVENT_CAP)
    private var evHead = 0
    private var evCount = 0

    /** Cells a flyer crosses on its straight line from a gate to the keep. */
    val flightLine = BooleanArray(Grid.N)

    init {
        field.compute(walls)
        val kx = Grid.x(layout.keep) + 0.5f
        val ky = Grid.y(layout.keep) + 0.5f
        for (g in layout.gates) {
            val gx = Grid.x(g) + 0.5f
            val gy = Grid.y(g) + 0.5f
            for (s in 0..200) {
                val t = s / 200f
                val x = (gx + (kx - gx) * t).toInt().coerceIn(0, Grid.W - 1)
                val y = (gy + (ky - gy) * t).toInt().coerceIn(0, Grid.H - 1)
                flightLine[Grid.idx(x, y)] = true
            }
        }
    }

    val totalWaves get() = spec.totalWaves
    val waveNumber get() = waveIndex + 1

    // ------------------------------------------------------------------ placement

    private fun gatherOccupied(): Int {
        var n = 0
        for (e in enemies) {
            if (!e.active || e.move == MOVE_FLY) continue
            occupied[n++] = e.tile
            occupied[n++] = e.next
        }
        return n
    }

    fun wallError(tile: Int): Placement.Error? {
        if (phase == Phase.WON || phase == Phase.LOST) return Placement.Error.GAME_OVER
        val err = Placement.checkWall(layout, walls, trapAt, scratchField, tile, occupied, gatherOccupied())
        if (err != null) return err
        if (coin < WALL_COST) return Placement.Error.NEEDS_COIN
        return null
    }

    fun placeWall(tile: Int): Placement.Error? {
        wallError(tile)?.let { return it }
        walls[tile] = true
        coin -= WALL_COST
        if (phase == Phase.BUILD) undo += UndoEntry(UndoEntry.WALL, tile, WALL_COST)
        onWallsChanged()
        emit(EV_PLACE, tile, -1)
        return null
    }

    fun trapCost(kind: TrapKind) = content.trap(kind).cost

    fun trapError(kind: TrapKind, tile: Int): Placement.Error? {
        if (phase == Phase.WON || phase == Phase.LOST) return Placement.Error.GAME_OVER
        if (kind !in spec.roster) return Placement.Error.NOT_IN_ROSTER
        if (tile < 0 || tile >= Grid.N) return Placement.Error.NOT_FLOOR
        if (trapAt[tile] >= 0) return Placement.Error.OCCUPIED
        if (kind.wallMounted) {
            if (!walls[tile]) return Placement.Error.NEEDS_WALL
            if (autoFacing(kind, tile) < 0) return Placement.Error.NO_FACE
        } else {
            if (!layout.buildable(tile)) return Placement.Error.NOT_FLOOR
            if (walls[tile]) return Placement.Error.OCCUPIED
        }
        if (coin < trapCost(kind)) return Placement.Error.NEEDS_COIN
        return null
    }

    fun placeTrap(kind: TrapKind, tile: Int): Placement.Error? {
        trapError(kind, tile)?.let { return it }
        val facing = if (kind.wallMounted) autoFacing(kind, tile) else 2
        val t = Trap(kind, tile, facing)
        val cost = trapCost(kind)
        t.spent = cost
        coin -= cost
        traps += t
        if (phase == Phase.BUILD) undo += UndoEntry(UndoEntry.TRAP, tile, cost)
        onTrapsChanged()
        emit(EV_PLACE, tile, kind.ordinal)
        return null
    }

    fun upgradeError(tile: Int): Placement.Error? {
        if (phase == Phase.WON || phase == Phase.LOST) return Placement.Error.GAME_OVER
        val i = trapAt.getOrElse(tile) { -1 }
        if (i < 0) return Placement.Error.OCCUPIED
        val t = traps[i]
        if (t.upgraded) return Placement.Error.OCCUPIED
        if (coin < trapCost(t.kind)) return Placement.Error.NEEDS_COIN
        return null
    }

    fun upgrade(tile: Int): Placement.Error? {
        upgradeError(tile)?.let { return it }
        val t = traps[trapAt[tile]]
        val cost = trapCost(t.kind)
        t.upgraded = true
        t.spent += cost
        coin -= cost
        if (phase == Phase.BUILD) undo += UndoEntry(UndoEntry.UPGRADE, tile, cost)
        emit(EV_UPGRADE, tile, t.kind.ordinal)
        return null
    }

    /** Turns a wall trap to its next direction that faces open floor. */
    fun rotate(tile: Int): Boolean {
        val i = trapAt.getOrElse(tile) { -1 }
        if (i < 0 || phase == Phase.WON || phase == Phase.LOST) return false
        val t = traps[i]
        if (!t.kind.wallMounted) return false
        for (k in 1..3) {
            val d = (t.facing + k) % 4
            if (walkable(Grid.step(tile, d))) {
                t.facing = d
                onTrapsChanged()
                return true
            }
        }
        return false
    }

    /** Sells what stands on [tile] for half of what was spent. A wall sells with its trap. Returns coin back, or -1. */
    fun sell(tile: Int): Int {
        if (phase == Phase.WON || phase == Phase.LOST || tile !in 0 until Grid.N) return -1
        var refund = 0
        val i = trapAt[tile]
        if (i >= 0) {
            refund += traps[i].spent / 2
            traps.removeAt(i)
            onTrapsChanged()
            if (!walls[tile]) {
                coin += refund
                purgeUndo(tile)
                emit(EV_SELL, tile, -1)
                return refund
            }
        }
        if (!walls[tile]) return -1
        walls[tile] = false
        refund += WALL_COST / 2
        coin += refund
        purgeUndo(tile)
        onWallsChanged()
        emit(EV_SELL, tile, -1)
        return refund
    }

    private fun purgeUndo(tile: Int) {
        undo.removeAll { it.tile == tile }
    }

    /** Takes back the last placement made in this build phase, for its full cost. Placements made while a wave runs sell for half instead. */
    fun undoLast(): Boolean {
        if (phase == Phase.WON || phase == Phase.LOST) return false
        while (undo.isNotEmpty()) {
            val u = undo.removeAt(undo.size - 1)
            when (u.type) {
                UndoEntry.WALL -> {
                    if (!walls[u.tile] || trapAt[u.tile] >= 0) continue
                    walls[u.tile] = false
                    coin += u.cost
                    onWallsChanged()
                }
                UndoEntry.TRAP -> {
                    val i = trapAt[u.tile]
                    if (i < 0) continue
                    val t = traps[i]
                    if (t.upgraded) continue
                    traps.removeAt(i)
                    coin += u.cost
                    onTrapsChanged()
                }
                UndoEntry.UPGRADE -> {
                    val i = trapAt[u.tile]
                    if (i < 0 || !traps[i].upgraded) continue
                    traps[i].upgraded = false
                    traps[i].spent -= u.cost
                    coin += u.cost
                }
            }
            emit(EV_UNDO, u.tile, -1)
            return true
        }
        return false
    }

    private fun walkable(i: Int) = i >= 0 && !layout.obstacle[i] && !walls[i]

    /** Picks the direction a wall trap faces, preferring a live combo, then the tile nearest the keep. */
    fun autoFacing(kind: TrapKind, tile: Int): Int {
        var fallback = -1
        var fallbackDist = Int.MAX_VALUE
        for (d in 0..3) {
            val n = Grid.step(tile, d)
            if (!walkable(n)) continue
            val combo = when (kind) {
                TrapKind.PUSHER -> {
                    val shove = Grid.step(n, d)
                    traps.any { it.kind == TrapKind.DEADFALL && it.target == shove && shove >= 0 }
                }
                TrapKind.DEADFALL -> traps.any { it.kind == TrapKind.PUSHER && it.shoveTile == n }
                TrapKind.HAMMER -> traps.any { it.kind == TrapKind.FROST && Grid.manhattan(it.tile, n) <= 1 }
                else -> false
            }
            if (combo) return d
            val score = if (kind == TrapKind.DART) -lineCoverage(tile, d) * 1000 + field.dist[n].let { if (it < 0) 999 else it }
            else field.dist[n].let { if (it < 0) 999 else it }
            if (score < fallbackDist) {
                fallbackDist = score
                fallback = d
            }
        }
        return fallback
    }

    private fun lineCoverage(tile: Int, d: Int): Int {
        var c = 0
        var cur = tile
        for (k in 0 until DART_RANGE) {
            cur = Grid.step(cur, d)
            if (cur < 0) break
            if (walkable(cur) && field.dist[cur] >= 0) c++
            if (flightLine[cur]) c += 3
        }
        return c
    }

    private fun onWallsChanged() {
        field.compute(walls)
        // A wall trap whose wall is gone falls with it.
        for (k in traps.size - 1 downTo 0) {
            val t = traps[k]
            if (t.kind.wallMounted && !walls[t.tile]) traps.removeAt(k)
        }
        // A wall trap facing a tile that is now walled turns to open floor, if any.
        for (k in 0 until traps.size) {
            val t = traps[k]
            if (t.kind.wallMounted && !walkable(t.target)) {
                val d = autoFacing(t.kind, t.tile)
                if (d >= 0) t.facing = d
            }
        }
        onTrapsChanged()
    }

    private fun onTrapsChanged() {
        trapAt.fill(-1)
        for (k in 0 until traps.size) trapAt[traps[k].tile] = k
        links = ComboResolver.resolve(traps)
    }

    // ------------------------------------------------------------------ waves

    fun sendWave(): Boolean {
        if (phase != Phase.BUILD) return false
        val wave = spec.waveAt(waveIndex)
        val times = ArrayList<Float>()
        val kinds = ArrayList<Int>()
        val gates = ArrayList<Int>()
        var spawnIdx = 0
        wave.groups.forEach { g ->
            val kind = EnemyKind.byId(g.enemy)!!
            for (i in 0 until g.count) {
                times += g.delay + i * g.gap
                kinds += kind.ordinal
                val gate = if (g.gate in layout.gates.indices) g.gate else spawnIdx % layout.gates.size
                gates += gate
                spawnIdx++
            }
        }
        val order = times.indices.sortedWith(compareBy({ times[it] }, { it }))
        spawnTimes = FloatArray(order.size) { times[order[it]] }
        spawnKinds = IntArray(order.size) { kinds[order[it]] }
        spawnGates = IntArray(order.size) { gates[order[it]] }
        spawnHp = FloatArray(order.size) { wave.hp }
        spawnCursor = 0
        waveTime = 0f
        undo.clear()
        phase = Phase.WAVE
        emit(EV_WAVE_START, waveIndex, -1)
        return true
    }

    val spawnsLeft get() = spawnTimes.size - spawnCursor

    fun step() {
        if (phase != Phase.WAVE) return
        tick++
        waveTime += DT
        while (spawnCursor < spawnTimes.size && spawnTimes[spawnCursor] <= waveTime) {
            spawn(EnemyKind.entries[spawnKinds[spawnCursor]], layout.gates[spawnGates[spawnCursor]], spawnHp[spawnCursor])
            spawnCursor++
        }
        for (e in enemies) {
            if (e.active) updateEnemy(e)
            if (phase != Phase.WAVE) return
        }
        for (k in 0 until traps.size) {
            val t = traps[k]
            if (t.flash > 0f) t.flash -= DT
            updateTrap(t)
            // A pusher can shove an enemy into the keep, and that leak can end the run.
            if (phase != Phase.WAVE) return
        }
        for (e in enemies) {
            if (e.active && e.hp <= 0f) kill(e)
        }
        if (phase == Phase.WAVE && spawnCursor >= spawnTimes.size && alive == 0) waveCleared()
    }

    private fun waveCleared() {
        coin += spec.waveBonus(waveIndex)
        emit(EV_WAVE_CLEAR, waveIndex, -1)
        if (!spec.endless && waveIndex + 1 >= spec.totalWaves) {
            waveIndex++
            phase = Phase.WON
            emit(EV_WON, -1, -1)
        } else {
            waveIndex++
            phase = Phase.BUILD
        }
        // Undo is for the build phase just gone; anything placed during the wave has already worked.
        undo.clear()
    }

    private fun spawn(kind: EnemyKind, gate: Int, hpScale: Float) {
        val e = enemies.firstOrNull { !it.active } ?: return
        val stats = content.enemy(kind)
        e.active = true
        e.serial = serialCounter++
        e.kind = kind
        e.maxHp = stats.hp * spec.difficulty.hpMult * hpScale
        e.hp = e.maxHp
        e.speed = stats.speed
        e.bounty = stats.bounty
        e.hearts = stats.hearts
        e.tile = gate
        e.next = gate
        e.progress = 0f
        e.segLen = 1f
        e.move = if (kind.flying) MOVE_FLY else MOVE_WALK
        e.x = Grid.x(gate) + 0.5f
        e.y = Grid.y(gate) + 0.5f
        e.prevX = e.x
        e.prevY = e.y
        e.root = 0f; e.rootGrace = 0f; e.chill = 0f; e.oiled = 0f; e.burn = 0f; e.burnDps = 0f
        e.hopCooldown = 0f; e.dug = false; e.sapped = false; e.sapTimer = 0f; e.sapTile = -1
        e.flash = 0f; e.dir = 2; e.shoves = 0
        alive++
    }

    private fun updateEnemy(e: Enemy) {
        e.prevX = e.x
        e.prevY = e.y
        if (e.flash > 0f) e.flash -= DT
        if (e.hopCooldown > 0f) e.hopCooldown -= DT
        if (e.rootGrace > 0f) e.rootGrace -= DT
        if (e.chill > 0f) e.chill -= DT
        if (e.oiled > 0f) e.oiled -= DT
        if (e.burn > 0f) {
            e.burn -= DT
            damage(e, e.burnDps * DT, SRC_FIRE)
        }
        if (e.hp <= 0f) return

        if (e.move == MOVE_FLY) {
            val kx = Grid.x(layout.keep) + 0.5f
            val ky = Grid.y(layout.keep) + 0.5f
            val dx = kx - e.x
            val dy = ky - e.y
            val len = sqrt(dx * dx + dy * dy)
            val stepLen = e.speed * DT
            if (len <= stepLen) {
                leak(e)
                return
            }
            e.x += dx / len * stepLen
            e.y += dy / len * stepLen
            e.dir = if (abs(dx) > abs(dy)) (if (dx > 0) 1 else 3) else (if (dy > 0) 2 else 0)
            return
        }

        if (e.root > 0f) {
            e.root -= DT
            return
        }
        if (e.sapTimer > 0f) {
            e.sapTimer -= DT
            if (e.sapTimer <= 0f) {
                if (e.sapTile >= 0 && walls[e.sapTile]) {
                    walls[e.sapTile] = false
                    onWallsChanged()
                    purgeUndo(e.sapTile)
                    emit(EV_WALL_BROKEN, e.sapTile, -1)
                }
                e.sapTile = -1
            }
            return
        }
        if (e.move == MOVE_WALK && e.tile == e.next) {
            decide(e)
            if (e.tile == e.next || !e.active) return
        }

        var slow = 1f
        if (!e.kind.unslowable && e.move == MOVE_WALK) {
            if (e.chill > 0f) slow = minOf(slow, CHILL_SLOW)
            val on = e.onTile
            if (on >= 0) {
                val ti = trapAt[on]
                if (ti >= 0 && traps[ti].kind == TrapKind.OIL) {
                    val oil = content.trap(TrapKind.OIL)
                    slow = minOf(slow, if (traps[ti].upgraded) oil.upgradedEffect else oil.effect)
                }
            }
        }
        if (e.move == MOVE_WALK) {
            val on = e.onTile
            if (on >= 0) {
                val ti = trapAt[on]
                if (ti >= 0 && traps[ti].kind == TrapKind.OIL) e.oiled = OILED_SECONDS
            }
        }
        val segSpeed = if (e.move == MOVE_TUNNEL) e.speed * TUNNEL_SPEED else e.speed
        e.progress += segSpeed * slow * DT / e.segLen
        if (e.progress >= 1f) {
            e.tile = e.next
            e.progress = 0f
            e.segLen = 1f
            e.move = MOVE_WALK
            if (e.tile == layout.keep) {
                e.x = Grid.x(e.tile) + 0.5f
                e.y = Grid.y(e.tile) + 0.5f
                leak(e)
                return
            }
        }
        val fx = Grid.x(e.tile) + 0.5f
        val fy = Grid.y(e.tile) + 0.5f
        val nx = Grid.x(e.next) + 0.5f
        val ny = Grid.y(e.next) + 0.5f
        e.x = fx + (nx - fx) * e.progress
        e.y = fy + (ny - fy) * e.progress
    }

    /** Chooses the next tile for a ground enemy standing on a tile centre. */
    private fun decide(e: Enemy) {
        val here = e.tile
        if (here == layout.keep) {
            leak(e); return
        }
        if (e.kind == EnemyKind.SAPPER && !e.sapped) {
            for (d in SAP_ORDER) {
                val n = Grid.step(here, d)
                if (n >= 0 && walls[n]) {
                    e.sapped = true
                    e.sapTile = n
                    e.sapTimer = SAP_SECONDS
                    e.dir = d
                    emit(EV_SAP_START, n, -1)
                    return
                }
            }
        }
        val dh = field.dist[here]
        if (e.kind == EnemyKind.DIGGER && !e.dug && dh > 0) {
            var bestGain = 0
            var bestTile = -1
            var bestLen = 0
            for (d in 0..3) {
                var cur = Grid.step(here, d)
                var n = 0
                while (cur >= 0 && walls[cur]) {
                    n++; cur = Grid.step(cur, d)
                }
                if (n == 0 || cur < 0 || !walkable(cur) || field.dist[cur] < 0) continue
                val gain = dh - field.dist[cur] - (n + 1)
                if (gain >= DIG_MIN_GAIN && gain > bestGain) {
                    bestGain = gain; bestTile = cur; bestLen = n + 1
                }
            }
            if (bestTile >= 0) {
                e.dug = true
                e.next = bestTile
                e.segLen = bestLen.toFloat()
                e.move = MOVE_TUNNEL
                e.progress = 0f
                e.dir = dirBetween(here, bestTile)
                emit(EV_TUNNEL, here, bestTile)
                return
            }
        }
        if (e.kind == EnemyKind.JUMPER && e.hopCooldown <= 0f && dh > 0) {
            var bestGain = 0
            var bestTile = -1
            for (d in 0..3) {
                val w = Grid.step(here, d)
                if (w < 0 || !walls[w]) continue
                val b = Grid.step(w, d)
                if (b < 0 || !walkable(b) || field.dist[b] < 0) continue
                val gain = dh - field.dist[b] - 2
                if (gain >= HOP_MIN_GAIN && gain > bestGain) {
                    bestGain = gain; bestTile = b
                }
            }
            if (bestTile >= 0) {
                e.hopCooldown = HOP_COOLDOWN
                e.next = bestTile
                e.segLen = 2f
                e.move = MOVE_HOP
                e.progress = 0f
                e.dir = dirBetween(here, bestTile)
                emit(EV_HOP, here, bestTile)
                return
            }
        }
        if (dh > 0) {
            for (d in WALK_ORDER) {
                val n = Grid.step(here, d)
                if (n >= 0 && field.dist[n] == dh - 1 && walkable(n)) {
                    e.next = n
                    e.dir = d
                    return
                }
            }
        }
        // No route. The placement rule makes this unreachable; count it and remove the enemy so a wave always ends.
        stranded++
        leak(e)
    }

    private fun dirBetween(a: Int, b: Int): Int {
        val dx = Grid.x(b) - Grid.x(a)
        val dy = Grid.y(b) - Grid.y(a)
        return if (abs(dx) > abs(dy)) (if (dx > 0) 1 else 3) else (if (dy > 0) 2 else 0)
    }

    private fun leak(e: Enemy) {
        if (!e.active) return
        e.active = false
        alive--
        leaked++
        hearts -= e.hearts
        emit(EV_LEAK, layout.keep, e.kind.ordinal)
        if (hearts <= 0) {
            hearts = 0
            phase = Phase.LOST
            emit(EV_LOST, -1, -1)
        }
    }

    private fun kill(e: Enemy) {
        e.active = false
        alive--
        kills++
        coin += e.bounty
        emit(EV_KILL, floor(e.x).toInt() + floor(e.y).toInt() * Grid.W, e.kind.ordinal)
    }

    private fun damage(e: Enemy, amount: Float, source: Int) {
        if (!e.active || e.hp <= 0f) return
        if (source == SRC_FIRE && e.kind.fireproof) return
        var a = amount
        if (source == SRC_FIRE && e.oiled > 0f) a *= OILED_FIRE
        if (e.kind.armoured && source != SRC_GRIND) a *= ARMOUR
        e.hp -= a
        e.flash = HIT_FLASH
    }

    // ------------------------------------------------------------------ traps

    private fun anyOn(tile: Int): Boolean {
        if (tile < 0) return false
        for (e in enemies) if (e.active && e.hp > 0f && e.onTile == tile) return true
        return false
    }

    private fun updateTrap(t: Trap) {
        if (t.cooldown > 0f) t.cooldown -= DT
        if (t.cooldown > 0f) return
        val stats = content.trap(t.kind)
        val dmg = if (t.upgraded) stats.upgradedDamage else stats.damage
        val effect = if (t.upgraded) stats.upgradedEffect else stats.effect
        when (t.kind) {
            TrapKind.OIL -> Unit
            TrapKind.SPIKE -> if (anyOn(t.tile)) {
                hitTile(t.tile, dmg, SRC_PLAIN, 1f)
                fire(t, stats.period)
            }
            TrapKind.EMBER -> {
                var any = anyOn(t.tile)
                for (k in 0 until t.linkedCount) if (anyOn(t.linkedTiles[k])) any = true
                if (any) {
                    burnTile(t.tile, dmg, effect)
                    var combo = false
                    for (k in 0 until t.linkedCount) {
                        if (anyOn(t.linkedTiles[k])) combo = true
                        burnTile(t.linkedTiles[k], dmg, effect)
                    }
                    fire(t, stats.period)
                    if (combo) emit(EV_COMBO, t.tile, ComboKind.OIL_EMBER.ordinal)
                }
            }
            TrapKind.FROST -> if (anyOn(t.tile)) {
                for (e in enemies) if (e.active && e.onTile == t.tile && !e.kind.unslowable) e.chill = effect
                fire(t, stats.period)
            }
            TrapKind.SNARE -> {
                var caught = false
                var combo = false
                for (e in enemies) {
                    if (!e.active || e.move != MOVE_WALK || e.root > 0f || e.rootGrace > 0f) continue
                    val on = e.onTile
                    var hit = on == t.tile
                    if (!hit) for (k in 0 until t.linkedCount) if (t.linkedTiles[k] == on) { hit = true; combo = true }
                    if (hit) {
                        e.root = effect
                        e.rootGrace = effect + ROOT_GRACE
                        caught = true
                    }
                }
                if (caught) {
                    fire(t, stats.period)
                    if (combo) emit(EV_COMBO, t.tile, ComboKind.SNARE_SPIKE.ordinal)
                }
            }
            TrapKind.PUSHER -> {
                val tgt = t.target
                if (tgt < 0 || !anyOn(tgt)) return
                var victim: Enemy? = null
                for (e in enemies) if (e.active && e.hp > 0f && e.onTile == tgt && !e.kind.unpushable && e.shoves < MAX_SHOVES) {
                    if (victim == null || e.serial < victim.serial) victim = e
                }
                val shove = t.shoveTile
                if (victim != null && walkable(shove) && field.dist[shove] >= 0) {
                    victim.shoves++
                    victim.tile = shove
                    victim.next = shove
                    victim.progress = 0f
                    victim.segLen = 1f
                    victim.sapTimer = 0f
                    victim.sapTile = -1
                    victim.x = Grid.x(shove) + 0.5f
                    victim.y = Grid.y(shove) + 0.5f
                    fire(t, stats.period)
                    emit(EV_SHOVE, tgt, shove)
                    if (shove == layout.keep) {
                        leak(victim)
                        return
                    }
                    val dfall = t.linkedDeadfall
                    if (dfall != null) {
                        val dStats = content.trap(TrapKind.DEADFALL)
                        val dd = if (dfall.upgraded) dStats.upgradedDamage else dStats.damage
                        hitTile(shove, dd * 2f, SRC_PLAIN, 1f)
                        fire(dfall, dStats.period)
                        emit(EV_COMBO, dfall.tile, ComboKind.PUSH_DEADFALL.ordinal)
                    }
                } else {
                    hitTile(tgt, dmg, SRC_PLAIN, 1f)
                    fire(t, stats.period)
                }
            }
            TrapKind.DEADFALL, TrapKind.GRINDER -> {
                val tgt = t.target
                if (anyOn(tgt)) {
                    hitTile(tgt, dmg, if (t.kind == TrapKind.GRINDER) SRC_GRIND else SRC_PLAIN, 1f)
                    fire(t, stats.period)
                }
            }
            TrapKind.HAMMER -> {
                val tgt = t.target
                if (anyOn(tgt)) {
                    var shattered = false
                    for (e in enemies) if (e.active && e.onTile == tgt) {
                        val chilled = e.chill > 0f
                        if (chilled) shattered = true
                        damage(e, if (chilled) dmg * SHATTER else dmg, SRC_PLAIN)
                    }
                    fire(t, stats.period)
                    if (shattered) emit(EV_COMBO, t.tile, ComboKind.FROST_HAMMER.ordinal)
                }
            }
            TrapKind.DART -> {
                val victim = dartTarget(t) ?: return
                val towardDart = Grid.opposite(t.facing)
                if (victim.kind == EnemyKind.SHIELDBEARER && victim.dir == towardDart) {
                    emit(EV_BLOCK, t.tile, -1)
                } else {
                    damage(victim, dmg, SRC_PLAIN)
                }
                fire(t, stats.period)
                emit(EV_DART, t.tile, t.facing)
            }
        }
    }

    /** The nearest enemy in the dart wall's line, ground or air. Darts fly over walls. */
    private fun dartTarget(t: Trap): Enemy? {
        val tx = Grid.x(t.tile)
        val ty = Grid.y(t.tile)
        var best: Enemy? = null
        var bestD = Float.MAX_VALUE
        for (e in enemies) {
            if (!e.active || e.hp <= 0f || e.move == MOVE_TUNNEL) continue
            val ex = floor(e.x).toInt()
            val ey = floor(e.y).toInt()
            val along = when (t.facing) {
                0 -> if (ex == tx && ey < ty) ty - e.y + 0.5f else -1f
                2 -> if (ex == tx && ey > ty) e.y - ty - 0.5f else -1f
                1 -> if (ey == ty && ex > tx) e.x - tx - 0.5f else -1f
                else -> if (ey == ty && ex < tx) tx - e.x + 0.5f else -1f
            }
            if (along < 0f || along > DART_RANGE) continue
            if (along < bestD) {
                bestD = along; best = e
            }
        }
        return best
    }

    private fun fire(t: Trap, period: Float) {
        t.cooldown = period
        t.flash = TRAP_FLASH
        emit(EV_TRAP, t.tile, t.kind.ordinal)
    }

    private fun hitTile(tile: Int, dmg: Float, source: Int, mult: Float) {
        for (e in enemies) if (e.active && e.onTile == tile) damage(e, dmg * mult, source)
    }

    private fun burnTile(tile: Int, dmg: Float, burnSeconds: Float) {
        for (e in enemies) if (e.active && e.onTile == tile) {
            damage(e, dmg, SRC_FIRE)
            if (!e.kind.fireproof) {
                e.burn = burnSeconds
                e.burnDps = dmg * BURN_FRACTION
            }
        }
    }

    // ------------------------------------------------------------------ score, save, events

    /** Kills plus hearts plus unspent coin on a clear; kills alone otherwise. Daily adds waves held. */
    fun score(): Int = if (spec.endless) Scoring.daily(kills, waveIndex) else Scoring.level(kills, hearts, coin, phase == Phase.WON)

    fun snapshot(): RunSave = RunSave(
        levelId = spec.levelId,
        difficulty = spec.difficulty.id,
        dailyKey = spec.dailyKey,
        wave = waveIndex,
        hearts = hearts,
        coin = coin,
        walls = (0 until Grid.N).filter { walls[it] },
        traps = traps.map { SavedTrap(it.tile, it.kind.id, it.facing, if (it.upgraded) 1 else 0) },
        kills = kills,
        rngState = rng.state,
    )

    /** Restores a wave-boundary save onto this fresh run. Throws when the save does not fit the board. */
    fun restore(save: RunSave) {
        require(phase == Phase.BUILD && tick == 0L) { "Restore onto a fresh run" }
        require(save.levelId == spec.levelId && save.difficulty == spec.difficulty.id && save.dailyKey == spec.dailyKey) { "Save is for another run" }
        require(save.hearts in 1..RunSpec.START_HEARTS && save.coin in 0..MAX_COIN && save.kills in 0..MAX_KILLS) { "Bad counters" }
        require(save.wave >= 0 && (if (spec.endless) save.wave < MAX_DAILY_WAVES else save.wave < spec.totalWaves)) { "Bad wave" }
        save.walls.forEach { require(it in 0 until Grid.N && layout.buildable(it)) { "Bad wall" }; walls[it] = true }
        field.compute(walls)
        layout.gates.forEach { require(field.dist[it] >= 0) { "Saved maze seals the keep" } }
        save.traps.forEach { st ->
            val kind = requireNotNull(TrapKind.byId(st.kind)) { "Bad trap" }
            require(st.tile in 0 until Grid.N && trapAt[st.tile] < 0 && st.facing in 0..3) { "Bad trap tile" }
            if (kind.wallMounted) require(walls[st.tile]) else require(layout.buildable(st.tile) && !walls[st.tile])
            val t = Trap(kind, st.tile, st.facing, st.upgrade > 0)
            t.spent = trapCost(kind) * (if (t.upgraded) 2 else 1)
            traps += t
            trapAt[st.tile] = traps.size - 1
        }
        onTrapsChanged()
        waveIndex = save.wave
        hearts = save.hearts
        coin = save.coin
        kills = save.kills
        rng.state = save.rngState
    }

    private fun emit(type: Int, a: Int, b: Int) {
        val slot = (evHead + evCount) % EVENT_CAP
        evType[slot] = type
        evA[slot] = a
        evB[slot] = b
        if (evCount < EVENT_CAP) evCount++ else evHead = (evHead + 1) % EVENT_CAP
    }

    /** Pops the oldest event into [out] (type, a, b). Returns false when there are none. */
    fun pollEvent(out: IntArray): Boolean {
        if (evCount == 0) return false
        out[0] = evType[evHead]
        out[1] = evA[evHead]
        out[2] = evB[evHead]
        evHead = (evHead + 1) % EVENT_CAP
        evCount--
        return true
    }

    fun clearEvents() {
        evCount = 0
    }

    private class UndoEntry(val type: Int, val tile: Int, val cost: Int) {
        companion object {
            const val WALL = 0
            const val TRAP = 1
            const val UPGRADE = 2
        }
    }

    companion object {
        const val TICKS_PER_SECOND = 60
        const val DT = 1f / TICKS_PER_SECOND
        const val MAX_ENEMIES = 160
        const val WALL_COST = 1
        const val DART_RANGE = 8
        const val EVENT_CAP = 256
        /** Bounds for a restored save. Real play stays far below them; a hand-edited file cannot overflow or exhaust memory. */
        const val MAX_COIN = 1_000_000
        const val MAX_KILLS = 10_000_000
        const val MAX_DAILY_WAVES = 1_000

        const val MOVE_WALK = 0
        const val MOVE_HOP = 1
        const val MOVE_TUNNEL = 2
        const val MOVE_FLY = 3

        const val SRC_PLAIN = 0
        const val SRC_FIRE = 1
        const val SRC_GRIND = 2

        const val ARMOUR = 0.5f
        const val CHILL_SLOW = 0.5f
        const val OILED_FIRE = 1.5f
        const val OILED_SECONDS = 2f
        const val BURN_FRACTION = 0.25f
        const val SHATTER = 3f
        const val ROOT_GRACE = 1f
        const val SAP_SECONDS = 1.5f
        const val TUNNEL_SPEED = 0.7f
        const val DIG_MIN_GAIN = 6
        const val HOP_MIN_GAIN = 2
        const val HOP_COOLDOWN = 5f
        /** A pusher may shove one enemy only this often, so a pusher facing back up the route cannot hold it forever. */
        const val MAX_SHOVES = 3
        const val HIT_FLASH = 0.12f
        const val TRAP_FLASH = 0.15f

        private val SAP_ORDER = intArrayOf(2, 1, 3, 0)
        private val WALK_ORDER = Route.WALK_ORDER

        const val EV_PLACE = 1
        const val EV_SELL = 2
        const val EV_UPGRADE = 3
        const val EV_UNDO = 4
        const val EV_WAVE_START = 5
        const val EV_WAVE_CLEAR = 6
        const val EV_WON = 7
        const val EV_LOST = 8
        const val EV_KILL = 9
        const val EV_LEAK = 10
        const val EV_TRAP = 11
        const val EV_COMBO = 12
        const val EV_SHOVE = 13
        const val EV_DART = 14
        const val EV_BLOCK = 15
        const val EV_HOP = 16
        const val EV_TUNNEL = 17
        const val EV_SAP_START = 18
        const val EV_WALL_BROKEN = 19
    }
}

object Scoring {
    const val PER_KILL = 10
    const val PER_HEART = 50

    fun level(kills: Int, hearts: Int, coin: Int, cleared: Boolean) =
        kills * PER_KILL + if (cleared) hearts * PER_HEART + coin else 0

    fun daily(kills: Int, wavesHeld: Int) = kills * PER_KILL + wavesHeld * 25

    enum class Medal(val id: String) { NONE("none"), BRONZE("bronze"), SILVER("silver"), GOLD("gold") }

    fun medal(cleared: Boolean, score: Int, par: Par): Medal = when {
        !cleared -> Medal.NONE
        score >= par.gold -> Medal.GOLD
        score >= par.silver -> Medal.SILVER
        else -> Medal.BRONZE
    }

    fun better(a: Medal, b: Medal) = if (a.ordinal >= b.ordinal) a else b
}

/** Fixed-step clock: turns frame time and a speed into whole simulation ticks, capped per frame. */
class StepClock {
    private var accumulator = 0.0

    /** Returns how many ticks to run for a frame of [frameNanos] at [speed]. Drops time past [maxTicks]. */
    fun advance(frameNanos: Long, speed: Int, maxTicks: Int = MAX_TICKS_PER_FRAME): Int {
        if (frameNanos <= 0L || speed <= 0) return 0
        accumulator += frameNanos / 1_000_000_000.0 * speed * Sim.TICKS_PER_SECOND
        var ticks = accumulator.toInt()
        accumulator -= ticks
        if (ticks > maxTicks) {
            ticks = maxTicks
            accumulator = 0.0
        }
        return ticks
    }

    /** Where the render sits between the last tick and the next, 0 to 1. */
    val alpha: Float get() = accumulator.toFloat().coerceIn(0f, 1f)

    fun reset() {
        accumulator = 0.0
    }

    companion object {
        const val MAX_TICKS_PER_FRAME = 8
    }
}

package com.mohdshayan.snarewall.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathingTest {

    private val content = TestContent.content

    /** After every legal wall, the distance field and A* agree on reachability and length for every gate. */
    @Test
    fun distanceFieldAndAStarAgreeUnderFuzzedPlacements() {
        val maps = content.levels.map { it.map } + listOf(TestContent.OPEN_MAP)
        val path = IntArray(Grid.N)
        var legal = 0
        var sealsRefused = 0
        for ((m, map) in maps.withIndex()) {
            repeat(12) { run ->
                val rng = Rng(1000L * m + run)
                val sim = TestContent.sim(map, listOf(TestContent.wave(EnemyKind.RAIDER)), coin = 1000)
                val astar = AStar(sim.layout)
                repeat(160) {
                    val tile = rng.nextInt(Grid.N)
                    val err = sim.wallError(tile)
                    if (err == Placement.Error.SEALS) {
                        sealsRefused++
                        // A refused wall really would seal a gate.
                        val sealed = sim.layout.gates.any { g -> astar.path(sim.walls, g, tile, path) == 0 }
                        assertTrue("Refused wall at $tile does not seal", sealed)
                    }
                    if (sim.placeWall(tile) == null) {
                        legal++
                        for (g in sim.layout.gates) {
                            val n = astar.path(sim.walls, g, -1, path)
                            assertTrue("Gate $g sealed after legal wall", n > 0)
                            assertEquals("Lengths differ on map $m", sim.field.dist[g], n - 1)
                            assertEquals(g, path[0])
                            assertEquals(sim.layout.keep, path[n - 1])
                            for (k in 1 until n) {
                                assertEquals(1, Grid.manhattan(path[k - 1], path[k]))
                                assertTrue(!sim.walls[path[k]] && !sim.layout.obstacle[path[k]])
                            }
                        }
                        // Every open tile: field reachability matches A* reachability.
                        if (legal % 7 == 0) for (t in 0 until Grid.N) {
                            if (sim.walls[t] || sim.layout.obstacle[t]) continue
                            val n = astar.path(sim.walls, t, -1, path)
                            assertEquals(sim.field.dist[t] >= 0, n > 0)
                            if (n > 0) assertEquals(sim.field.dist[t], n - 1)
                        }
                    }
                }
            }
        }
        assertTrue(legal > 1000)
        assertTrue(sealsRefused > 50)
    }

    @Test
    fun wallRulesRefuseGatesKeepObstaclesAndOccupiedTiles() {
        val map = TestContent.OPEN_MAP.toMutableList().also { it[5] = "..#....." }
        val sim = TestContent.sim(map, listOf(TestContent.wave(EnemyKind.RAIDER)), coin = 3)
        assertEquals(Placement.Error.NOT_FLOOR, sim.wallError(Grid.idx(3, 0)))
        assertEquals(Placement.Error.NOT_FLOOR, sim.wallError(Grid.idx(3, 11)))
        assertEquals(Placement.Error.NOT_FLOOR, sim.wallError(Grid.idx(2, 5)))
        assertNull(sim.placeWall(Grid.idx(0, 3)))
        assertEquals(Placement.Error.OCCUPIED, sim.wallError(Grid.idx(0, 3)))
        assertNull(sim.placeWall(Grid.idx(1, 3)))
        assertNull(sim.placeWall(Grid.idx(2, 3)))
        assertEquals(0, sim.coin)
        assertEquals(Placement.Error.NEEDS_COIN, sim.wallError(Grid.idx(4, 3)))
    }

    @Test
    fun aWallThatWouldCutTheLastGapIsRefusedAndTheRouteIsKept() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER)), coin = 100)
        for (x in 0 until 7) assertNull(sim.placeWall(Grid.idx(x, 5)))
        val before = sim.field.dist[sim.layout.gates[0]]
        assertEquals(Placement.Error.SEALS, sim.placeWall(Grid.idx(7, 5)))
        assertEquals(before, sim.field.dist[sim.layout.gates[0]])
        assertTrue(!sim.walls[Grid.idx(7, 5)])
    }

    @Test
    fun aWallUnderAWalkingEnemyIsRefusedAndEnemiesRerouteNextStep() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER, hp = 50f)), coin = 100)
        sim.sendWave()
        repeat(90) { sim.step() }
        val e = sim.enemies.first { it.active }
        assertEquals(Placement.Error.ENEMY_ON_TILE, sim.wallError(e.next))
        // Block the straight line ahead; the enemy walks around rather than through.
        val ahead = Grid.step(Grid.step(e.next, 2), 2)
        assertNull(sim.placeWall(ahead))
        TestContent.runWave(sim)
        assertEquals(0, sim.stranded)
        assertEquals(19, sim.hearts)
    }

    @Test
    fun undoRefundsInFullAndSellRefundsHalf() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER), TestContent.wave(EnemyKind.RAIDER)), coin = 100)
        val wall = Grid.idx(2, 4)
        assertNull(sim.placeWall(wall))
        assertNull(sim.placeTrap(TrapKind.DEADFALL, wall))
        assertNull(sim.upgrade(wall))
        assertEquals(100 - 1 - 10 - 10, sim.coin)
        assertTrue(sim.undoLast())
        assertTrue(sim.undoLast())
        assertTrue(sim.undoLast())
        assertEquals(100, sim.coin)
        assertTrue(!sim.undoLast())

        assertNull(sim.placeTrap(TrapKind.SPIKE, Grid.idx(3, 5)))
        sim.sendWave()
        assertTrue("Undo does not reach past a sent wave", !sim.undoLast())
        val spike = sim.trapCost(TrapKind.SPIKE)
        assertEquals(spike / 2, sim.sell(Grid.idx(3, 5)))
        assertEquals(100 - spike + spike / 2, sim.coin)
    }

    @Test
    fun wallTrapsNeedAWallAndTrapsMustBeOnTheRoster() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER)), roster = listOf(TrapKind.SPIKE, TrapKind.PUSHER), coin = 100)
        assertEquals(Placement.Error.NEEDS_WALL, sim.trapError(TrapKind.PUSHER, Grid.idx(2, 2)))
        assertEquals(Placement.Error.NOT_IN_ROSTER, sim.trapError(TrapKind.GRINDER, Grid.idx(2, 2)))
        assertNull(sim.placeWall(Grid.idx(2, 2)))
        assertNotNull(sim.trapError(TrapKind.SPIKE, Grid.idx(2, 2)))
        assertNull(sim.placeTrap(TrapKind.PUSHER, Grid.idx(2, 2)))
        // Selling the wall takes its trap with it.
        sim.sell(Grid.idx(2, 2))
        assertEquals(0, sim.traps.size)
    }
}

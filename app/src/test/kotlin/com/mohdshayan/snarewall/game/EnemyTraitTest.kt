package com.mohdshayan.snarewall.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnemyTraitTest {

    /** A long serpentine: walls on rows 3, 6, 9 with alternating gaps. */
    private fun serpentine(sim: Sim, thickRows: Boolean = false) {
        val rows = if (thickRows) listOf(3 to 7, 4 to 7, 7 to 0, 8 to 0) else listOf(3 to 7, 6 to 0, 9 to 7)
        for ((y, gap) in rows) for (x in 0 until Grid.W) if (x != gap) check(sim.placeWall(Grid.idx(x, y)) == null)
    }

    private fun track(sim: Sim, kind: EnemyKind, onTick: (Enemy) -> Unit = {}) {
        var n = 0
        while (sim.phase == Phase.WAVE && n++ < 60 * 300) {
            sim.step()
            sim.enemies.firstOrNull { it.active && it.kind == kind }?.let(onTick)
        }
    }

    @Test
    fun diggerTunnelsOnceUnderALongDetourButWalksAShortOne() {
        val long = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.DIGGER, hp = 100f)))
        serpentine(long)
        long.sendWave()
        var tunnels = 0
        var sawUnderground = false
        val out = IntArray(3)
        while (long.phase == Phase.WAVE) {
            long.step()
            while (long.pollEvent(out)) if (out[0] == Sim.EV_TUNNEL) tunnels++
            if (long.enemies.any { it.active && it.move == Sim.MOVE_TUNNEL }) sawUnderground = true
        }
        assertEquals(1, tunnels)
        assertTrue(sawUnderground)
        assertEquals(19, long.hearts)

        // One short wall with a two step detour: no tunnel.
        val short = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.DIGGER, hp = 100f)))
        assertNull(short.placeWall(Grid.idx(3, 4)))
        short.sendWave()
        var shortTunnels = 0
        while (short.phase == Phase.WAVE) {
            short.step()
            while (short.pollEvent(out)) if (out[0] == Sim.EV_TUNNEL) shortTunnels++
        }
        assertEquals(0, shortTunnels)
        assertEquals(0, long.stranded + short.stranded)
    }

    @Test
    fun jumperHopsASingleWallButNotADoubleOne() {
        val out = IntArray(3)
        val thin = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.JUMPER, hp = 100f)))
        serpentine(thin)
        thin.sendWave()
        var hops = 0
        while (thin.phase == Phase.WAVE) {
            thin.step()
            while (thin.pollEvent(out)) if (out[0] == Sim.EV_HOP) {
                hops++
                assertEquals("A hop crosses exactly one wall", 2, Grid.manhattan(out[1], out[2]))
                val mid = Grid.idx((Grid.x(out[1]) + Grid.x(out[2])) / 2, (Grid.y(out[1]) + Grid.y(out[2])) / 2)
                assertTrue(thin.walls[mid])
            }
        }
        assertTrue(hops >= 1)

        val thick = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.JUMPER, hp = 100f)))
        serpentine(thick, thickRows = true)
        thick.sendWave()
        var thickHops = 0
        while (thick.phase == Phase.WAVE) {
            thick.step()
            while (thick.pollEvent(out)) if (out[0] == Sim.EV_HOP) thickHops++
        }
        assertEquals(0, thickHops)
        assertEquals(0, thin.stranded + thick.stranded)
    }

    @Test
    fun flyerIgnoresWallsAndOnlyDartsHitIt() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.FLYER, hp = 1f)))
        serpentine(sim)
        // Floor traps straight under the flight line do nothing.
        for (y in 1..10) if (!sim.walls[Grid.idx(3, y)]) sim.placeTrap(TrapKind.SPIKE, Grid.idx(3, y))
        sim.sendWave()
        track(sim, EnemyKind.FLYER) { e -> assertTrue(e.x in 3f..4f) }
        assertEquals("Flyer crossed three wall rows untouched", 19, sim.hearts)

        // A dart wall standing in the flight line fires down the column the flyer crosses.
        val darts = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.FLYER, hp = 1f)))
        assertNull(darts.placeWall(Grid.idx(3, 2)))
        assertNull(darts.placeTrap(TrapKind.DART, Grid.idx(3, 2)))
        assertEquals(2, darts.traps[0].facing)
        darts.sendWave()
        TestContent.runWave(darts)
        assertEquals(20, darts.hearts)
        assertEquals(1, darts.kills)
    }

    @Test
    fun sapperKnocksDownTheFirstWallItTouchesOnlyOnce() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.SAPPER, hp = 100f)))
        serpentine(sim)
        val wallsBefore = sim.walls.count { it }
        sim.sendWave()
        val out = IntArray(3)
        var broken = 0
        while (sim.phase == Phase.WAVE) {
            sim.step()
            while (sim.pollEvent(out)) if (out[0] == Sim.EV_WALL_BROKEN) broken++
        }
        assertEquals(1, broken)
        assertEquals(wallsBefore - 1, sim.walls.count { it })
        assertEquals(0, sim.stranded)
    }

    @Test
    fun shieldbearerBlocksDartsFromTheFrontOnly() {
        // Enemy walks down column 3; a dart wall below it faces up, into its face.
        val front = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.SHIELDBEARER, hp = 1f)))
        assertNull(front.placeWall(Grid.idx(3, 10)))
        // Face up: the wall's only sensible face toward the column is up, and keep route bends round it.
        assertNull(front.placeTrap(TrapKind.DART, Grid.idx(3, 10)))
        front.traps[0].facing = 0
        front.sendWave()
        val out = IntArray(3)
        var blocks = 0
        while (front.phase == Phase.WAVE) {
            front.step()
            while (front.pollEvent(out)) if (out[0] == Sim.EV_BLOCK) blocks++
        }
        assertTrue(blocks > 0)

        val raider = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER, hp = 1f)))
        assertNull(raider.placeWall(Grid.idx(3, 10)))
        assertNull(raider.placeTrap(TrapKind.DART, Grid.idx(3, 10)))
        raider.traps[0].facing = 0
        raider.sendWave()
        TestContent.runWave(raider)
        assertEquals(1, raider.kills)
    }

    @Test
    fun oilskinIsFireproofFrostbornUnslowableBruteArmouredWarlordUnpushable() {
        val content = TestContent.content
        val oil = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.OILSKIN, hp = 1f)))
        for (y in 1..10) oil.placeTrap(TrapKind.EMBER, Grid.idx(3, y))
        oil.sendWave()
        track(oil, EnemyKind.OILSKIN) { e -> assertEquals(e.maxHp, e.hp, 0.001f) }
        assertEquals(19, oil.hearts)

        val frost = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.FROSTBORN, hp = 100f), TestContent.wave(EnemyKind.RAIDER, hp = 100f)))
        for (y in 1..10) frost.placeTrap(TrapKind.FROST, Grid.idx(3, y))
        frost.sendWave()
        track(frost, EnemyKind.FROSTBORN) { e -> assertEquals(0f, e.chill, 0f) }
        val frostTicks = frost.tick
        frost.sendWave()
        TestContent.runWave(frost)
        assertTrue("A chilled raider takes longer than the frostborn", frost.tick - frostTicks > frostTicks)

        val brute = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.BRUTE, hp = 100f)))
        brute.placeTrap(TrapKind.SPIKE, Grid.idx(3, 5))
        brute.sendWave()
        var minHp = Float.MAX_VALUE
        track(brute, EnemyKind.BRUTE) { e -> minHp = minOf(minHp, e.hp) }
        val spike = content.trap(TrapKind.SPIKE).damage
        val lost = content.enemy(EnemyKind.BRUTE).hp * 100f - minHp
        val hits = lost / (spike * Sim.ARMOUR)
        assertTrue("Armour halves spike damage", lost > 0f && kotlin.math.abs(hits - kotlin.math.round(hits)) < 0.02f)

        val lord = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.WARLORD, hp = 100f)))
        lord.placeWall(Grid.idx(2, 5))
        lord.placeTrap(TrapKind.PUSHER, Grid.idx(2, 5))
        lord.traps[0].facing = 1
        lord.sendWave()
        val out = IntArray(3)
        var shoves = 0
        while (lord.phase == Phase.WAVE) {
            lord.step()
            while (lord.pollEvent(out)) if (out[0] == Sim.EV_SHOVE) shoves++
        }
        assertEquals(0, shoves)
    }
}

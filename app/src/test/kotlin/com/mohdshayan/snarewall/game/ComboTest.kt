package com.mohdshayan.snarewall.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboTest {

    private fun combos(sim: Sim): Set<ComboKind> {
        val out = IntArray(3)
        val seen = HashSet<ComboKind>()
        while (sim.phase == Phase.WAVE) {
            sim.step()
            while (sim.pollEvent(out)) if (out[0] == Sim.EV_COMBO) seen += ComboKind.entries[out[2]]
        }
        return seen
    }

    @Test
    fun pusherIntoDeadfallLinksAndThrowsForDoubleDamage() {
        // Raider walks down column 3. Pusher on (2,5) faces right at (3,5) and throws onto (4,5).
        // Deadfall on (5,5) faces left at (4,5).
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER, hp = 1f)))
        assertNull(sim.placeWall(Grid.idx(5, 5)))
        assertNull(sim.placeWall(Grid.idx(2, 5)))
        assertNull(sim.placeTrap(TrapKind.DEADFALL, Grid.idx(5, 5)))
        sim.traps.first { it.kind == TrapKind.DEADFALL }.facing = 3
        assertNull(sim.placeTrap(TrapKind.PUSHER, Grid.idx(2, 5)))
        val pusher = sim.traps.first { it.kind == TrapKind.PUSHER }
        assertEquals("Auto facing picks the combo", 1, pusher.facing)
        assertTrue(sim.links.any { it.kind == ComboKind.PUSH_DEADFALL })
        sim.sendWave()
        val seen = combos(sim)
        assertTrue(ComboKind.PUSH_DEADFALL in seen)
        assertEquals(1, sim.kills)
    }

    @Test
    fun oilCarriesEmberFire() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER, hp = 0.25f)))
        assertNull(sim.placeTrap(TrapKind.EMBER, Grid.idx(2, 5)))
        assertNull(sim.placeTrap(TrapKind.OIL, Grid.idx(3, 5)))
        assertTrue(sim.links.any { it.kind == ComboKind.OIL_EMBER })
        sim.sendWave()
        assertTrue(ComboKind.OIL_EMBER in combos(sim))
        assertEquals("Fire reached the raider on the oil", 1, sim.kills)
    }

    @Test
    fun frostSetsUpTheShatterHammerForTriple() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER, hp = 1.6f)))
        assertNull(sim.placeTrap(TrapKind.FROST, Grid.idx(3, 5)))
        assertNull(sim.placeWall(Grid.idx(2, 6)))
        assertNull(sim.placeTrap(TrapKind.HAMMER, Grid.idx(2, 6)))
        sim.traps.first { it.kind == TrapKind.HAMMER }.facing = 1
        sim.sendWave()
        assertTrue(ComboKind.FROST_HAMMER in combos(sim))
    }

    @Test
    fun snareNextToSpikesHoldsEnemiesOnTheSpikes() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER, hp = 0.7f)))
        assertNull(sim.placeTrap(TrapKind.SPIKE, Grid.idx(3, 5)))
        assertNull(sim.placeTrap(TrapKind.SNARE, Grid.idx(2, 5)))
        assertTrue(sim.links.any { it.kind == ComboKind.SNARE_SPIKE })
        sim.sendWave()
        assertTrue(ComboKind.SNARE_SPIKE in combos(sim))
        assertEquals("Held on spikes, the raider dies", 1, sim.kills)

        val control = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER, hp = 0.7f)))
        assertNull(control.placeTrap(TrapKind.SPIKE, Grid.idx(3, 5)))
        control.sendWave()
        TestContent.runWave(control)
        assertEquals("Spikes alone do not", 0, control.kills)
    }

    @Test
    fun linksDisappearWhenATrapIsSold() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.RAIDER)))
        sim.placeTrap(TrapKind.EMBER, Grid.idx(2, 5))
        sim.placeTrap(TrapKind.OIL, Grid.idx(3, 5))
        assertEquals(1, sim.links.size)
        sim.sell(Grid.idx(3, 5))
        assertEquals(0, sim.links.size)
    }

    /** A pusher that throws enemies back up the route must not hold a wave open forever. */
    @Test
    fun aPusherFacingBackUpTheRouteCannotTrapAnEnemyForever() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, listOf(TestContent.wave(EnemyKind.BRUTE, count = 3, hp = 50f)))
        // Brutes walk down column 3. The pusher under the lane throws them back up, against their walk.
        assertNull(sim.placeWall(Grid.idx(2, 6)))
        assertNull(sim.placeTrap(TrapKind.PUSHER, Grid.idx(2, 6)))
        sim.traps[0].facing = 1
        assertNull(sim.placeWall(Grid.idx(4, 5)))
        sim.sendWave()
        TestContent.runWave(sim, maxTicks = 60 * 240)
        assertEquals(Phase.WON, sim.phase)
        assertEquals(0, sim.stranded)
        assertTrue(sim.enemies.none { it.active })
    }
}

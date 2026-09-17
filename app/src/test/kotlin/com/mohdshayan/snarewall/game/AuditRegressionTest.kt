package com.mohdshayan.snarewall.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Defects found in the pre-release audit, each pinned so it cannot come back. */
class AuditRegressionTest {

    private val content = TestContent.content

    private fun pusherIntoKeep(waves: Int): Sim {
        val sim = TestContent.sim(TestContent.OPEN_MAP, List(waves) { TestContent.wave(EnemyKind.RAIDER, count = 20, gap = 4f) })
        for (t in listOf(Grid.idx(2, 11), Grid.idx(4, 11), Grid.idx(3, 9))) assertEquals(null, sim.placeWall(t))
        assertEquals(null, sim.placeTrap(TrapKind.PUSHER, Grid.idx(3, 9)))
        return sim
    }

    private fun drainWonLost(sim: Sim): Pair<Boolean, Boolean> {
        val ev = IntArray(3)
        var won = false
        var lost = false
        while (sim.pollEvent(ev)) {
            if (ev[0] == Sim.EV_WON) won = true
            if (ev[0] == Sim.EV_LOST) lost = true
        }
        return won to lost
    }

    @Test
    fun aPusherShovingTheLastEnemyIntoTheKeepNeverTurnsALossIntoAClear() {
        for (waves in listOf(1, 2)) {
            val sim = pusherIntoKeep(waves)
            sim.sendWave()
            var won = false
            var lost = false
            var n = 0
            while (sim.phase == Phase.WAVE && n++ < 60 * 600) {
                sim.step()
                val (w, l) = drainWonLost(sim)
                won = won || w
                lost = lost || l
            }
            assertEquals("waves $waves", 0, sim.hearts)
            assertEquals("waves $waves", Phase.LOST, sim.phase)
            assertTrue(lost)
            assertFalse("waves $waves fired a win", won)
        }
    }

    @Test
    fun undoNeverRefundsFullPriceForATrapThatAlreadyWorkedThroughAWave() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, List(2) { TestContent.wave(EnemyKind.RAIDER, count = 3) }, coin = 100)
        assertTrue(sim.sendWave())
        repeat(10) { sim.step() }
        assertEquals(null, sim.placeTrap(TrapKind.SPIKE, Grid.idx(3, 5)))
        assertEquals(100 - sim.trapCost(TrapKind.SPIKE), sim.coin)
        assertEquals("a placement during a wave is not undoable", 0, sim.undoCount)
        assertFalse(sim.undoLast())
        TestContent.runWave(sim)
        assertEquals(Phase.BUILD, sim.phase)
        assertFalse(sim.undoLast())
        assertEquals(1, sim.traps.size)

        // A build-phase placement undoes in its own build phase, but not after the wave it fought in.
        val coin = sim.coin
        assertEquals(null, sim.placeTrap(TrapKind.SPIKE, Grid.idx(3, 6)))
        assertEquals(1, sim.undoCount)
        assertTrue(sim.undoLast())
        assertEquals(coin, sim.coin)
    }

    @Test
    fun undoIsClearedWhenAWaveEnds() {
        val sim = TestContent.sim(TestContent.OPEN_MAP, List(3) { TestContent.wave(EnemyKind.RAIDER, count = 2) }, coin = 100)
        assertEquals(null, sim.placeTrap(TrapKind.SPIKE, Grid.idx(3, 5)))
        sim.sendWave()
        TestContent.runWave(sim)
        assertEquals(Phase.BUILD, sim.phase)
        assertEquals(0, sim.undoCount)
    }

    @Test
    fun dailyWavesGiveEveryPickedKindItsShareAndAtMostOneWarlord() {
        val table = content.daily
        for (key in listOf("2026-09-17", "2026-09-18", "2026-10-02")) for (n in 0 until 60) {
            val number = n + 1
            val w = DailyGenerator.wave(key, n, table)
            val plain = w.groups.filter { it.enemy != EnemyKind.WARLORD.id }
            val undoubled = plain.sumOf { if (it.enemy == EnemyKind.SWARMLING.id) it.count / 2 else it.count }
            assertEquals("$key wave $number", 5 + (number * 1.2f).toInt(), undoubled)
            plain.forEach { assertTrue("$key wave $number ${it.enemy}", it.count >= 1) }
            val warlords = w.groups.filter { it.enemy == EnemyKind.WARLORD.id }.sumOf { it.count }
            assertEquals("$key wave $number", if (number % 10 == 0) 1 else 0, warlords)
        }
        // The case the audit found: wave 12 on 2026-09-17 had 18 swarmlings and a single brute.
        val w12 = DailyGenerator.wave("2026-09-17", 11, table)
        assertTrue(w12.groups.none { it.count == 1 && it.enemy != EnemyKind.WARLORD.id })
    }

    @Test
    fun aSaveWithAByteOrderMarkStillImports() {
        val text = SaveCodec.encode(SaveFile(exportedAt = 0L, levelProgress = emptyList(), dailyScores = emptyList()))
        assertTrue(SaveCodec.decode("﻿" + text) is SaveCodec.Decoded.Ok)
        assertTrue(SaveCodec.decode("﻿" + text.replace("\n", "\r\n")) is SaveCodec.Decoded.Ok)
    }
}

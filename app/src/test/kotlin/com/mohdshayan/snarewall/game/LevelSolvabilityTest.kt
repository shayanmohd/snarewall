package com.mohdshayan.snarewall.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelSolvabilityTest {

    private val content = TestContent.content

    /** Every level at every difficulty is beaten by its committed reference maze, and the gold par is reachable. */
    @Test
    fun everyLevelAtEveryDifficultyIsBeatableByItsReferenceMaze() {
        assertEquals(15, content.levels.size)
        for (lv in content.levels) for (d in Difficulty.entries) {
            val sim = Sim(RunSpec.forLevel(lv, d), content)
            ReferenceIo.replay(sim, ReferenceIo.read(lv.id, d))
            assertEquals("Level ${lv.id} ${d.id}", Phase.WON, sim.phase)
            assertTrue(sim.hearts > 0)
            assertEquals(0, sim.stranded)
            val par = lv.par.getValue(d.id)
            assertTrue("Level ${lv.id} ${d.id} gold ${par.gold} above reference ${sim.score()}", sim.score() >= par.gold)
            assertTrue(par.silver in 1 until par.gold)
            assertEquals(Scoring.Medal.GOLD, Scoring.medal(true, sim.score(), par))
        }
    }

    /** Placing nothing loses on every level, even on the easiest difficulty. */
    @Test
    fun placingNothingLosesEveryLevel() {
        for (lv in content.levels) for (d in Difficulty.entries) {
            val sim = Sim(RunSpec.forLevel(lv, d), content)
            while (sim.phase == Phase.BUILD) {
                sim.sendWave()
                TestContent.runWave(sim)
            }
            assertEquals("Level ${lv.id} ${d.id} won with an empty board", Phase.LOST, sim.phase)
            assertEquals(0, sim.kills)
        }
    }

    @Test
    fun medalsFollowParAndOnlyAClearEarnsOne() {
        val par = Par(silver = 400, gold = 600)
        assertEquals(Scoring.Medal.NONE, Scoring.medal(false, 9999, par))
        assertEquals(Scoring.Medal.BRONZE, Scoring.medal(true, 399, par))
        assertEquals(Scoring.Medal.SILVER, Scoring.medal(true, 400, par))
        assertEquals(Scoring.Medal.GOLD, Scoring.medal(true, 600, par))
        assertEquals(Scoring.Medal.GOLD, Scoring.better(Scoring.Medal.GOLD, Scoring.Medal.SILVER))
        assertEquals(10 * 12 + 50 * 7 + 33, Scoring.level(12, 7, 33, true))
        assertEquals(120, Scoring.level(12, 7, 33, false))
    }
}

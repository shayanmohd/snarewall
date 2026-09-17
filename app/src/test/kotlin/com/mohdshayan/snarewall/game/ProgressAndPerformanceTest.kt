package com.mohdshayan.snarewall.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressAndPerformanceTest {

    private val par = Par(silver = 800, gold = 1200)

    @Test
    fun aWorseRunOrALossNeverTakesAMedalOrBestAway() {
        val gold = ProgressRules.level(null, 4, "iron", won = true, score = 1300, hearts = 17, par = par, now = 10L)
        assertEquals(Scoring.Medal.GOLD, gold.medal)
        assertTrue(gold.newBest && gold.firstClear)
        val bronze = ProgressRules.level(gold.row, 4, "iron", won = true, score = 500, hearts = 3, par = par, now = 20L)
        assertEquals("gold", bronze.row.medal)
        assertEquals(1300, bronze.row.bestScore)
        assertEquals(17, bronze.row.bestHearts)
        assertEquals(2, bronze.row.clears)
        assertFalse(bronze.newBest)
        assertEquals(10L, bronze.row.firstClearedAt)
        val loss = ProgressRules.level(bronze.row, 4, "iron", won = false, score = 9999, hearts = 0, par = par, now = 30L)
        assertEquals("gold", loss.row.medal)
        assertEquals(1300, loss.row.bestScore)
        assertEquals(2, loss.row.clears)
        assertFalse(loss.newBest)
        assertEquals(30L, loss.row.lastPlayedAt)
    }

    @Test
    fun aLossFirstThenAClearCountsAsTheFirstClear() {
        val loss = ProgressRules.level(null, 2, "standard", won = false, score = 90, hearts = 0, par = par, now = 1L)
        assertEquals("none", loss.row.medal)
        assertNull(loss.row.firstClearedAt)
        assertFalse(ProgressRules.unlocked(3, listOf(loss.row)))
        val win = ProgressRules.level(loss.row, 2, "standard", won = true, score = 900, hearts = 12, par = par, now = 2L)
        assertTrue(win.firstClear && win.newBest)
        assertEquals("silver", win.row.medal)
        assertEquals(2L, win.row.firstClearedAt)
        assertTrue(ProgressRules.unlocked(3, listOf(win.row)))
        assertFalse(ProgressRules.unlocked(4, listOf(win.row)))
        assertEquals(1, ProgressRules.nextLevel(listOf(win.row), 15))
    }

    @Test
    fun dailyKeepsTheBestAndCountsRuns() {
        val a = ProgressRules.daily(null, "2026-09-17", wavesHeld = 12, score = 900, now = 1L)
        assertTrue(a.newBest)
        val b = ProgressRules.daily(a.row, "2026-09-17", wavesHeld = 9, score = 700, now = 2L)
        assertFalse(b.newBest)
        assertEquals(12, b.row.bestWave)
        assertEquals(900, b.row.bestScore)
        assertEquals(2, b.row.runs)
        val c = ProgressRules.daily(b.row, "2026-09-17", wavesHeld = 23, score = 4120, now = 3L)
        assertTrue(c.newBest)
        assertEquals(23, c.row.bestWave)
        assertEquals(3, c.row.runs)
    }

    /** The largest wave on the busiest level steps without allocating: no garbage, no GC hitches at 3x. */
    @Test
    fun steppingTheBusiestWaveAllocatesAlmostNothing() {
        val content = TestContent.content
        val lv = content.levels.maxBy { l -> l.waves.maxOf { w -> w.groups.sumOf { it.count } } }
        val ref = ReferenceIo.read(lv.id, Difficulty.WARDEN)
        val sim = Sim(RunSpec.forLevel(lv, Difficulty.WARDEN), content)
        val byWave = ref.actions.groupBy { it.wave }
        val biggest = lv.waves.indices.maxBy { i -> lv.waves[i].groups.sumOf { it.count } }
        while (sim.waveIndex < biggest && sim.phase == Phase.BUILD) {
            byWave[sim.waveIndex]?.forEach { a ->
                when (a.op) {
                    "wall" -> sim.placeWall(a.tile)
                    "trap" -> sim.placeTrap(TrapKind.byId(a.kind!!)!!, a.tile)
                    else -> sim.upgrade(a.tile)
                }
            }
            sim.sendWave()
            TestContent.runWave(sim)
        }
        byWave[sim.waveIndex]?.forEach { a ->
            when (a.op) {
                "wall" -> sim.placeWall(a.tile)
                "trap" -> sim.placeTrap(TrapKind.byId(a.kind!!)!!, a.tile)
                else -> sim.upgrade(a.tile)
            }
        }
        assertTrue(sim.sendWave())
        val out = IntArray(3)
        repeat(120) { sim.step(); while (sim.pollEvent(out)) Unit }
        // Unit tests compile against android.jar, so the JVM's allocation counter is reached by reflection.
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val counter = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        val id = Thread.currentThread().id
        val allocated = { counter.invoke(bean, id) as Long }
        allocated()
        val before = allocated()
        var steps = 0
        var peakAlive = 0
        while (sim.phase == Phase.WAVE && steps < 3000) {
            sim.step()
            while (sim.pollEvent(out)) Unit
            peakAlive = maxOf(peakAlive, sim.alive)
            steps++
        }
        val bytes = allocated() - before
        assertTrue("Wave too small to measure: $peakAlive alive", peakAlive >= 5)
        // Sappers breaking a wall rebuild the combo list, a rare event; everything else allocates nothing.
        assertTrue("Allocated $bytes bytes over $steps steps", bytes < 64_000)
    }
}

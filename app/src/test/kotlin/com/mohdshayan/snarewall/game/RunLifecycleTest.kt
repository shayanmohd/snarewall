package com.mohdshayan.snarewall.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Win, lose, restart, undo, speed and resume can never leave a run with no way forward. */
class RunLifecycleTest {

    private val content = TestContent.content

    private fun fingerprint(sim: Sim) = listOf(
        sim.phase, sim.waveIndex, sim.hearts, sim.coin, sim.kills, sim.tick, sim.score(),
        sim.walls.indices.filter { sim.walls[it] }, sim.traps.map { "${it.kind}${it.tile}${it.facing}${it.upgraded}" },
    ).toString()

    @Test
    fun oneXAndThreeXReachTheSameStateBecauseSpeedOnlyChangesTicksPerFrame() {
        val lv = content.levels.first { it.id == 4 }
        val results = listOf(1, 2, 3).map { speed ->
            val sim = Sim(RunSpec.forLevel(lv, Difficulty.STANDARD), content)
            ReferenceIo.read(4, Difficulty.STANDARD).let { ref ->
                val clock = StepClock()
                val byWave = ref.actions.groupBy { it.wave }
                var frame = 0
                while (sim.phase == Phase.BUILD) {
                    byWave[sim.waveIndex]?.forEach { a ->
                        when (a.op) {
                            "wall" -> sim.placeWall(a.tile)
                            "trap" -> sim.placeTrap(TrapKind.byId(a.kind!!)!!, a.tile)
                            else -> sim.upgrade(a.tile)
                        }
                    }
                    sim.sendWave()
                    while (sim.phase == Phase.WAVE) {
                        // Uneven frame pacing: 16 ms, 17 ms, and an occasional 50 ms hitch.
                        val nanos = when (frame++ % 7) { 3 -> 50_000_000L; 5 -> 17_000_000L; else -> 16_666_667L }
                        repeat(clock.advance(nanos, speed)) { sim.step() }
                    }
                }
            }
            fingerprint(sim)
        }
        assertEquals(results[0], results[1])
        assertEquals(results[0], results[2])
    }

    @Test
    fun stepClockCapsTicksAfterAStallAndKeepsFractions() {
        val clock = StepClock()
        assertEquals(StepClock.MAX_TICKS_PER_FRAME, clock.advance(2_000_000_000L, 3))
        assertEquals("The stall is dropped, not replayed", 0, clock.advance(1_000_000L, 1))
        var total = 0
        repeat(60) { total += clock.advance(16_666_667L, 1) }
        assertTrue(total in 59..60)
        total = 0
        repeat(60) { total += clock.advance(16_666_667L, 3) }
        assertTrue(total in 179..180)
    }

    @Test
    fun resumingAWaveBoundarySaveReplaysIdentically() {
        for (id in listOf(3, 9, 14)) {
            val lv = content.level(id)!!
            val ref = ReferenceIo.read(id, Difficulty.IRON)
            val straight = Sim(RunSpec.forLevel(lv, Difficulty.IRON), content)
            ReferenceIo.replay(straight, ref)

            // Play to the start of the middle wave, save, then "kill the process" and resume in a fresh Sim.
            val first = Sim(RunSpec.forLevel(lv, Difficulty.IRON), content)
            val cut = lv.waves.size / 2
            val byWave = ref.actions.groupBy { it.wave }
            while (first.waveIndex < cut) {
                byWave[first.waveIndex]?.forEach { apply(first, it) }
                first.sendWave()
                TestContent.runWave(first)
            }
            val json = SaveCodec.encodeRun(first.snapshot())
            val resumed = Sim(RunSpec.forLevel(lv, Difficulty.IRON), content)
            resumed.restore(SaveCodec.decodeRun(json)!!)
            assertEquals(first.hearts, resumed.hearts)
            assertEquals(first.coin, resumed.coin)
            assertEquals(first.links, resumed.links)
            while (resumed.phase == Phase.BUILD) {
                byWave[resumed.waveIndex]?.forEach { apply(resumed, it) }
                resumed.sendWave()
                TestContent.runWave(resumed)
            }
            assertEquals(Phase.WON, resumed.phase)
            assertEquals(straight.score(), resumed.score())
            assertEquals(straight.hearts, resumed.hearts)
        }
    }

    private fun apply(sim: Sim, a: RefAction) {
        val err = when (a.op) {
            "wall" -> sim.placeWall(a.tile)
            "trap" -> sim.placeTrap(TrapKind.byId(a.kind!!)!!, a.tile)
            else -> sim.upgrade(a.tile)
        }
        check(err == null) { "$a $err" }
    }

    @Test
    fun corruptOrMismatchedSavesAreRefusedSoTheLevelRestartsCleanly() {
        val lv = content.level(2)!!
        val good = Sim(RunSpec.forLevel(lv, Difficulty.STANDARD), content)
        good.placeWall(Grid.idx(1, 5))
        val save = good.snapshot()
        val bad = listOf(
            save.copy(levelId = 3),
            save.copy(difficulty = "iron"),
            save.copy(hearts = 0),
            save.copy(wave = lv.waves.size),
            save.copy(walls = listOf(Grid.idx(4, 0))),
            save.copy(walls = (0 until Grid.W).map { Grid.idx(it, 5) }),
            save.copy(traps = listOf(SavedTrap(Grid.idx(2, 2), "grinder", 0, 0))),
        )
        for (b in bad) {
            val fresh = Sim(RunSpec.forLevel(lv, Difficulty.STANDARD), content)
            try {
                fresh.restore(b)
                fail("Accepted bad save $b")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        assertEquals(null, SaveCodec.decodeRun("{\"levelId\": 2, \"wave\": "))
    }

    /** Random play: placements, sells, undos, rotations and waves. Every wave ends, and a finished run refuses moves. */
    @Test
    fun fuzzedPlayAlwaysHasAWayForward() {
        val out = IntArray(3)
        for (lv in content.levels) for (seed in 0 until 3) {
            val rng = Rng(lv.id * 97L + seed)
            val d = Difficulty.entries[seed]
            val sim = Sim(RunSpec.forLevel(lv, d), content)
            var guard = 0
            while (sim.phase == Phase.BUILD || sim.phase == Phase.WAVE) {
                check(guard++ < 400)
                repeat(rng.nextInt(12)) { randomMove(sim, rng) }
                if (sim.phase == Phase.BUILD) assertTrue(sim.sendWave())
                var ticks = 0
                while (sim.phase == Phase.WAVE) {
                    sim.step()
                    if (rng.nextInt(90) == 0) randomMove(sim, rng)
                    ticks++
                    assertTrue("Wave ${sim.waveIndex} of level ${lv.id} never ended", ticks < 60 * 900)
                    while (sim.pollEvent(out)) Unit
                }
                assertEquals(0, sim.stranded)
                assertTrue(sim.coin >= 0)
                for (g in sim.layout.gates) assertTrue(sim.field.dist[g] >= 0)
            }
            assertTrue(sim.phase == Phase.WON || sim.phase == Phase.LOST)
            // A finished run accepts nothing, so the only way forward is the result screen.
            assertNotNull(sim.wallError(Grid.idx(0, 5)))
            assertFalse(sim.sendWave())
            assertFalse(sim.undoLast())
            assertEquals(-1, sim.sell(Grid.idx(0, 5)))
            val ticks = sim.tick
            sim.step()
            assertEquals(ticks, sim.tick)
            // Restart is a fresh Sim from the same spec, and it can be played.
            val restart = Sim(RunSpec.forLevel(lv, d), content)
            assertEquals(Phase.BUILD, restart.phase)
            assertTrue(restart.sendWave())
        }
    }

    private fun randomMove(sim: Sim, rng: Rng) {
        val tile = rng.nextInt(Grid.N)
        when (rng.nextInt(6)) {
            0, 1 -> sim.placeWall(tile)
            2 -> sim.placeTrap(sim.spec.roster[rng.nextInt(sim.spec.roster.size)], tile)
            3 -> sim.undoLast()
            4 -> sim.sell(tile)
            else -> if (rng.nextInt(2) == 0) sim.upgrade(tile) else sim.rotate(tile)
        }
    }
}

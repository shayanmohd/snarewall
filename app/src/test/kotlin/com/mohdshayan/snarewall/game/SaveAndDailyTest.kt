package com.mohdshayan.snarewall.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SaveAndDailyTest {

    private val content = TestContent.content

    private fun sample() = SaveFile(
        exportedAt = 1_789_000_000_000L,
        levelProgress = listOf(
            LevelProgressRow(1, "standard", 1320, "gold", 18, 3, 1_788_000_000_000L, 1_788_500_000_000L),
            LevelProgressRow(7, "iron", 1640, "bronze", 6, 1, 1_788_100_000_000L, 1_788_600_000_000L),
        ),
        resume = ResumeRow(8, "warden", 3, SaveCodec.encodeRun(
            RunSave(8, "warden", null, 3, 17, 22, listOf(12, 13), listOf(SavedTrap(12, "dart", 2, 1)), 41, 99L),
        ), 1_788_700_000_000L),
        dailyScores = listOf(DailyScoreRow("2026-09-16", 23, 4120, 2, 1_788_800_000_000L)),
        settings = mapOf("theme" to "dark", "sfx_volume" to "0.6"),
    )

    @Test
    fun saveFileRoundTrips() {
        val f = sample()
        val decoded = SaveCodec.decode(SaveCodec.encode(f))
        assertEquals(SaveCodec.Decoded.Ok(f), decoded)
    }

    @Test
    fun foreignNewerAndDamagedFilesAreRefused() {
        assertEquals(SaveCodec.Decoded.NotASave, SaveCodec.decode("not json at all"))
        assertEquals(SaveCodec.Decoded.NotASave, SaveCodec.decode("""{"format": "portwarden-save", "formatVersion": 1}"""))
        assertEquals(SaveCodec.Decoded.NotASave, SaveCodec.decode("[1, 2, 3]"))
        val text = SaveCodec.encode(sample())
        assertEquals(SaveCodec.Decoded.NewerVersion, SaveCodec.decode(text.replace("\"formatVersion\": 1", "\"formatVersion\": 2")))
        assertEquals(SaveCodec.Decoded.NotASave, SaveCodec.decode(text.replace("\"gold\"", "\"platinum\"")))
        assertEquals(SaveCodec.Decoded.NotASave, SaveCodec.decode(text.replace("\"levelId\": 7", "\"levelId\": 16")))
        assertEquals(SaveCodec.Decoded.NotASave, SaveCodec.decode(text.replace("2026-09-16", "yesterday")))
        assertEquals(SaveCodec.Decoded.NotASave, SaveCodec.decode(text.substring(0, text.length / 2)))
        val dup = sample().let { it.copy(levelProgress = it.levelProgress + it.levelProgress.first()) }
        assertEquals(SaveCodec.Decoded.NotASave, SaveCodec.decode(SaveCodec.encode(dup)))
        val badResume = sample().copy(resume = ResumeRow(8, "warden", 3, "{broken", 0L))
        assertEquals(SaveCodec.Decoded.NotASave, SaveCodec.decode(SaveCodec.encode(badResume)))
    }

    @Test
    fun dailyMapIsTheSameForADateAndDiffersAcrossDates() {
        val a = DailyGenerator.generate("2026-09-17")
        val b = DailyGenerator.generate("2026-09-17")
        assertEquals(a.map, b.map)
        assertEquals(a.roster, b.roster)
        assertEquals(DailyGenerator.wave("2026-09-17", 22, content.daily), DailyGenerator.wave("2026-09-17", 22, content.daily))
        val maps = (1..28).map { DailyGenerator.generate("2026-02-%02d".format(it)).map }.toSet()
        assertTrue("Dates give different boards", maps.size > 20)
        assertNotEquals(DailyGenerator.wave("2026-09-17", 5, content.daily), DailyGenerator.wave("2026-09-18", 5, content.daily))
    }

    /** A thousand dates: every map is connected, has six traps with darts for flyers, and is playable by the bot. */
    @Test
    fun aThousandDailyMapsAreSolvable() {
        val start = java.time.LocalDate.of(2026, 1, 1)
        var botWaves = 0
        for (i in 0 until 1000) {
            val key = start.plusDays(i.toLong()).toString()
            val daily = DailyGenerator.generate(key)
            assertTrue(key, DailyGenerator.allFloorConnected(daily.map))
            assertEquals(6, daily.roster.size)
            assertTrue(TrapKind.DART in daily.roster && TrapKind.SPIKE in daily.roster)
            if (i % 10 == 0) {
                val sim = Sim(DailyGenerator.spec(daily, content), content)
                Bot(sim, thick = false).playToEnd(maxWaves = 8)
                assertTrue("$key fell before wave 8", sim.phase == Phase.BUILD && sim.waveIndex == 8)
                assertEquals(0, sim.stranded)
                botWaves += sim.waveIndex

                val empty = Sim(DailyGenerator.spec(daily, content), content)
                while (empty.phase == Phase.BUILD) {
                    empty.sendWave(); TestContent.runWave(empty)
                }
                assertEquals("$key empty board must fall", Phase.LOST, empty.phase)
                assertTrue(empty.waveIndex < 6)
            }
        }
        assertEquals(800, botWaves)
    }

    @Test
    fun contentMatchesWhatTheListingPromises() {
        assertEquals(15, content.levels.size)
        assertEquals((1..15).toList(), content.levels.map { it.id })
        assertEquals(3, content.regions.size)
        assertEquals(listOf("Chalk Downs", "Salt Mine", "Fen Causeway"), content.regions.map { it.name })
        content.regions.forEach { r -> assertEquals(5, content.levels.count { it.region == r.id }) }
        assertEquals(10, TrapKind.entries.size)
        assertEquals(12, EnemyKind.entries.size)
        assertEquals(4, content.combos.size)
        assertEquals(3, Difficulty.entries.size)
        content.levels.forEach { lv ->
            assertTrue("Level ${lv.id} waves", lv.waves.size in 6..12)
            lv.traps.forEach { assertTrue(TrapKind.byId(it) != null) }
        }
        // Each region closes with a warlord.
        listOf(5, 10, 15).forEach { id -> assertTrue(content.level(id)!!.waves.last().groups.any { it.enemy == "warlord" }) }
        // Every trap and every enemy is actually used by the levels.
        TrapKind.entries.forEach { k -> assertTrue(k.id, content.levels.any { k.id in it.traps }) }
        EnemyKind.entries.forEach { k -> assertTrue(k.id, content.levels.any { lv -> lv.waves.any { w -> w.groups.any { it.enemy == k.id } } }) }
        assertEquals(content.levels.size, content.levels.map { it.name }.toSet().size)
    }

    @Test
    fun noEmOrEnDashAnywhereInShippedText() {
        val roots = listOf(File("src/main"), File("../store"), File("../docs"), File("../README.md"))
        val bad = ArrayList<String>()
        roots.filter { it.exists() }.forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml", "json", "md", "html", "txt") }.forEach { f ->
                if (f.name.startsWith("OFL-")) return@forEach
                val text = f.readText()
                if (text.contains('\u2014') || text.contains('\u2013')) bad += f.path
            }
        }
        assertEquals(emptyList<String>(), bad)
    }
}

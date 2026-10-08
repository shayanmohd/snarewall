package com.mohdshayan.snarewall.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one lock: starting or continuing a level 6 to 15 without owning the full game. */
class AccessTest {

    @Test
    fun freeTierIsExactlyChalkDowns() {
        (1..5).forEach { assertTrue("level $it free", Access.levelOpen(it, owned = false)) }
        (6..15).forEach { assertFalse("level $it in the full game", Access.levelOpen(it, owned = false)) }
        (1..15).forEach { assertTrue("level $it owned", Access.levelOpen(it, owned = true)) }
    }

    @Test
    fun theBoundaryMatchesTheContent() {
        // A content edit cannot move the boundary into the middle of a region.
        val content = TestContent.content
        (1..Access.FREE_LAST_LEVEL).forEach { assertEquals("chalk", content.level(it)!!.region) }
        assertNotEquals("chalk", content.level(Access.FREE_LAST_LEVEL + 1)!!.region)
        assertTrue(content.level(Access.FREE_LAST_LEVEL)!!.waves.last().groups.any { it.enemy == "warlord" })
    }

    @Test
    fun dailyIsAlwaysOpen() {
        assertTrue(Access.canStart(0, daily = true, owned = false))
        assertFalse(Access.canStart(6, daily = false, owned = false))
    }

    @Test
    fun aStartedRunSurvivesARefund() {
        assertTrue(Access.boardOpen(latched = true, levelId = 8, daily = false, owned = false))
        assertFalse(Access.boardOpen(latched = false, levelId = 8, daily = false, owned = false))
        assertTrue(Access.boardOpen(latched = false, levelId = 8, daily = false, owned = true))
    }

    @Test
    fun homeNeverOffersALockedLevel() {
        assertNull(Access.nextPlayable(6, owned = false))
        assertEquals(6, Access.nextPlayable(6, owned = true))
        assertEquals(3, Access.nextPlayable(3, owned = false))
        assertNull(Access.nextPlayable(null, owned = true))
    }

    @Test
    fun noPriceIsHardCoded() {
        // The only price the app shows is Google Play's own formatted price.
        val price = Regex("""(USD|INR|₹|\$)\s?\d|2\.99""")
        val roots = listOf(File("src/main/kotlin"), File("src/main/res"), File("src/debug"), File("src/release"))
        val bad = roots.filter { it.exists() }.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "xml") && price.containsMatchIn(it.readText()) }
                .map { it.path }
                .toList()
        }
        assertEquals(emptyList<String>(), bad)
    }
}

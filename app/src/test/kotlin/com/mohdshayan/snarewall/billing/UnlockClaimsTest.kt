package com.mohdshayan.snarewall.billing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Release-only promises the unit tests cannot run (they build the debug variant), pinned from the sources. */
class UnlockClaimsTest {

    @Test
    fun theReleaseBuildHasNoDebugUnlock() {
        val code = File("src/release/kotlin/com/mohdshayan/snarewall/billing/EntitlementOverride.kt").readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .map { it.substringBefore("//").trim() }
            .filter { it.isNotEmpty() }
        assertEquals(
            listOf("package com.mohdshayan.snarewall.billing", "object EntitlementOverride {", "fun forced(): Boolean? = null", "}"),
            code,
        )
        // Only the debug source set reads the screenshot property.
        val readers = File("src/main").walkTopDown().filter { it.isFile && "debug.snarewall" in it.readText() }.map { it.path }.toList()
        assertEquals(emptyList<String>(), readers)
    }

    /** A Kotlin source under src/main with line comments and blank lines dropped. */
    private fun code(path: String): List<String> =
        File("src/main/kotlin/com/mohdshayan/snarewall/$path").readLines()
            .map { it.substringBefore("//").trim() }
            .filter { it.isNotEmpty() }

    @Test
    fun thePriorBuyerRuleIsWiredInWhereD4PutsIt() {
        // App.onCreate: settle straight after super.onCreate(), before anything can open Room or DataStore.
        val app = code("App.kt")
        val start = app.indexOf("super.onCreate()")
        assertTrue(start >= 0)
        assertEquals(
            listOf("LegacyOwner.settle(this)", "ServiceLocator.init(this)", "ServiceLocator.unlock"),
            app.subList(start + 1, start + 4),
        )
        // The repository gets the latch; without it every 1.0.0 buyer would see the free tier.
        assertTrue(
            "legacyOwner = { EntitlementOverride.forced() ?: LegacyOwner.isOwner(ctx()) }," in code("di/ServiceLocator.kt"),
        )
        // The answer is on disk before Room can create the database file it looks for.
        val owner = code("billing/LegacyOwner.kt").joinToString(" ")
        assertTrue(".commit()" in owner)
        assertFalse(".apply()" in owner)
        assertTrue("context.getDatabasePath(AppDatabase.NAME).exists()" in owner)
        assertEquals("snarewall.db", com.mohdshayan.snarewall.data.db.AppDatabase.NAME)
        // Every resume asks Google Play again.
        assertTrue(
            "override fun onResume() { super.onResume() ServiceLocator.unlock.refresh() }" in code("MainActivity.kt").joinToString(" "),
        )
    }

    @Test
    fun onlyThePlayCopyStaysOffBackupsAndTransfers() {
        val exclude = """<exclude domain="file" path="datastore/entitlement.preferences_pb" />"""
        // Android 11 and older: that one exclude and no include, so everything else is still backed up,
        // the prior-buyer latch with the database.
        val backup = File("src/main/res/xml/backup_rules.xml").readText()
        assertEquals(1, Regex("<exclude ").findAll(backup).count())
        assertTrue(exclude in backup)
        assertFalse("<include" in backup)
        // Android 12 and later: the same one exclude in cloud backup and in device transfer, nothing else.
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertEquals(exclude, rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>").trim())
        assertEquals(exclude, rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>").trim())
        assertFalse("<include" in rules)
    }
}

package com.mohdshayan.snarewall.billing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The prior-buyer rule decides, for good, whether someone who paid for Snarewall keeps the full game.
 * T0 is any install time, P a cutoff (2026-10-23 00:00 UTC, an example). Snarewall 1.0.0 leaves one
 * data file, databases/snarewall.db, so the "either data file" case of the Beatwheel plan becomes
 * "the database alone".
 */
class PriorBuyerPolicyTest {

    private val t0 = 1_789_000_000_000L
    private val p = 1_792_713_600_000L
    private val hour = 3_600_000L
    private val day = 86_400_000L

    private val undecided = PriorBuyerRecord(decided = false, owner = false)
    private val decidedFree = PriorBuyerRecord(decided = true, owner = false)
    private val owner = PriorBuyerRecord(decided = true, owner = true)

    private fun facts(database: Boolean, first: Long, last: Long) = InstallFacts(database, first, last)

    private fun next(record: PriorBuyerRecord, facts: InstallFacts, paidUntil: Long) =
        PriorBuyerPolicy.next(record, facts, paidUntil)

    @Test
    fun openedOnePointZeroThenUpdatedIsAPriorBuyer() =
        assertEquals(owner, next(undecided, facts(true, t0, t0 + 3 * day), paidUntil = 0L))

    @Test
    fun neverOpenedOnePointZeroThenUpdatedIsAPriorBuyer() =
        assertEquals(owner, next(undecided, facts(false, t0, t0 + 2 * day), paidUntil = 0L))

    @Test
    fun theDatabaseAloneCounts() =
        // A 1.0.0 database restored onto a fresh install: no update gap, still a buyer.
        assertEquals(owner, next(undecided, facts(true, t0, t0), paidUntil = 0L))

    @Test
    fun freshInstallIsNotAPriorBuyer() =
        assertEquals(decidedFree, next(undecided, facts(false, t0, t0), paidUntil = 0L))

    @Test
    fun updateGapBoundary() {
        assertEquals(decidedFree, next(undecided, facts(false, t0, t0 + 59_999), paidUntil = 0L))
        assertEquals(owner, next(undecided, facts(false, t0, t0 + 60_000), paidUntil = 0L))
    }

    @Test
    fun decisionIsNeverRecomputed() =
        // The second start of a fresh 1.1.0 install, after Room created snarewall.db.
        assertEquals(decidedFree, next(decidedFree, facts(true, t0, t0 + 3 * day), paidUntil = 0L))

    @Test
    fun latchHolds() {
        for (database in listOf(false, true)) {
            for (first in listOf(t0, p - 1, p, p + day)) {
                for (gap in listOf(0L, 3 * day)) {
                    for (paidUntil in listOf(0L, p)) {
                        assertEquals(owner, next(owner, facts(database, first, first + gap), paidUntil))
                    }
                }
            }
        }
    }

    @Test
    fun cutoffUpgradesWindowBuyers() =
        assertEquals(owner, next(decidedFree, facts(false, p - 1, p - 1), paidUntil = p))

    @Test
    fun cutoffBoundary() {
        assertEquals(decidedFree, next(decidedFree, facts(false, p, p), paidUntil = p))
        assertEquals(decidedFree, next(decidedFree, facts(false, p + 1, p + 1), paidUntil = p))
    }

    @Test
    fun cutoffIgnoresDataFilesAndUpdateGap() =
        assertEquals(decidedFree, next(undecided, facts(true, p + hour, p + 3 * day), paidUntil = p))

    @Test
    fun cutoffDecidesUndecidedInstalls() =
        assertEquals(owner, next(undecided, facts(false, p - day, p - day), paidUntil = p))

    @Test
    fun paidUntilIsPinnedForThisRelease() =
        // 1.1.0 ships 0. 1.1.1 sets the moment the store pages showed the app as free, rounded up to the
        // next full hour UTC: pin that value here and assert PAID_UNTIL_MS % 3_600_000L == 0L.
        assertEquals(0L, PriorBuyerPolicy.PAID_UNTIL_MS)
}

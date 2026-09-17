package com.mohdshayan.snarewall.game

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The listing sells combos. That promise is empty if one pair of traps is simply the answer, so this
 * sweeps every two trap build against the full mixed roster, and against every other pair, on every
 * level and difficulty.
 *
 * Each run goes through the public Sim API with the same bot, board, waves and seeds; the only thing
 * that differs between the mixed build and a pair is which traps the shop offers. A pair is the
 * answer on a level when it comes out best of all the two trap builds there and no worse than
 * playing the whole roster, and no pair may be the answer on more than about a third of the levels
 * it can be built on.
 */
class BuildDominanceTest {

    /** About a third, the share agreed for the rebalance. */
    private val share = 1f / 3f

    /** A pair that fits on only a level or two is not a pattern, so it is not judged. */
    private val minCells = 9

    @Test
    fun noTwoTrapBuildIsTheAnswerOnMoreThanAThirdOfLevels() {
        val m = matrix
        val over = m.judged(minCells).filter { m.answerShareOf(it) > share }.sortedByDescending { m.answerShareOf(it) }
        assertTrue(
            "these two trap builds are the answer too often: " + over.joinToString(", ") {
                "%s %d/%d %.2f on %s".format(it, m.answers.getValue(it).size, m.avail.getValue(it),
                    m.answerShareOf(it), m.answers.getValue(it))
            },
            over.isEmpty(),
        )
    }

    /** Several different builds are the answer somewhere, not one. */
    @Test
    fun manyDifferentBuildsAreTheAnswerSomewhere() {
        assertTrue("only ${matrix.answerCount} different pairs are the answer anywhere", matrix.answerCount >= 8)
    }

    /**
     * The audit's dominant pair. Spike plate plus dart wall matched or beat the whole roster on
     * twenty of its thirty three cells, measured exactly this way; these five are cells it took then
     * and must not take again.
     */
    @Test
    fun spikeAndDartLosesWhereItUsedToWin() {
        val cells = matrix.taken.getValue("spike+dart")
        for (cell in listOf("L07-iron", "L08-iron", "L11-iron", "L12-iron", "L14-iron")) {
            assertTrue("spike+dart matches or beats the full roster on $cell again (it takes $cells)", cell !in cells)
        }
    }

    /**
     * Every judged pair has real weaknesses: on at least two levels in five the whole roster simply
     * holds the keep better. Before the rebalance the best pairs fell behind on barely a third of
     * their cells, which is what made two traps a general answer.
     */
    @Test
    fun everyTwoTrapBuildFallsBehindTheWholeRosterOftenEnough() {
        val m = matrix
        val never = m.judged(minCells).filter { m.lossShareOf(it) < 0.4f }.sortedBy { m.lossShareOf(it) }
        assertTrue("these pairs almost never fall behind the whole roster: " +
            never.joinToString(", ") { "%s %.2f".format(it, m.lossShareOf(it)) }, never.isEmpty())
    }

    companion object {
        /** One sweep for the whole class: the careful styles only, which is what a build can afford. */
        private val matrix: Sweep.Matrix by lazy {
            Sweep.matrix(Sweep.cells(TestContent.content, styles = Sweep.FAST_STYLES))
        }
    }
}

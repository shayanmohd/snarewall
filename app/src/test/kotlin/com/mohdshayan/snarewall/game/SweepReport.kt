package com.mohdshayan.snarewall.game

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Measuring tool, not a check. It writes the balance numbers this release was tuned against to
 * app/build/sweep-*.txt so they can be read before and after a change.
 *
 * It only runs when the file app/sweep.on exists, so a normal build never pays for it.
 */
class SweepReport {

    private val content = TestContent.content

    private fun on() = assumeTrue(File("sweep.on").exists())

    private fun write(name: String, text: String) {
        val out = File("build", name).apply { parentFile.mkdirs() }
        out.writeText(text)
        println("wrote ${out.absolutePath}")
    }

    /** Goal (a): the two trap matrix against the full mixed roster, on every level and difficulty. */
    @Test
    fun pairMatrix() {
        on()
        val cells = Sweep.cells(content)
        val m = Sweep.matrix(cells)
        val sb = StringBuilder()
        sb.appendLine("cells ${m.cells}, pairs ${m.avail.size}, pairs beating the mixed build somewhere ${m.winners}")
        sb.appendLine("pairs that are the answer somewhere ${m.answerCount}")
        sb.appendLine("top answer ${m.byAnswers.first()} share %.2f".format(m.answerShareOf(m.byAnswers.first())))
        sb.appendLine("answers above a third of their own cells: " +
            m.judged(9).filter { m.answerShareOf(it) > 1f / 3f }.sortedByDescending { m.answerShareOf(it) }
                .joinToString(", ") { "%s %.2f".format(it, m.answerShareOf(it)) })
        sb.appendLine("top matcher ${m.topPair} share %.2f".format(m.topShare))
        sb.appendLine("matchers above a third of their own cells: " +
            m.judged(9).filter { m.shareOf(it) > 1f / 3f }.sortedByDescending { m.shareOf(it) }
                .joinToString(", ") { "%s %.2f".format(it, m.shareOf(it)) })
        sb.appendLine()
        sb.appendLine("weakest pair by loss share: " + m.judged(9).minByOrNull { m.lossShareOf(it) }
            ?.let { "%s loses on %.2f".format(it, m.lossShareOf(it)) })
        sb.appendLine()
        sb.appendLine("pair                 answers/avail share  matches beats loses  cells where it is the answer")
        for (key in m.byAnswers) {
            sb.appendLine("%-20s %3d/%-3d       %.2f   %.2f    %.2f  %.2f   %s".format(
                key, m.answers.getValue(key).size, m.avail.getValue(key), m.answerShareOf(key), m.shareOf(key),
                m.beatShareOf(key), m.lossShareOf(key), m.answers.getValue(key).joinToString(" ")))
        }
        sb.appendLine()
        sb.appendLine("pair                 takes/avail share  beats  loses  cells it takes from the mixed build")
        for (key in m.ranked) {
            sb.appendLine("%-20s %3d/%-3d     %.2f   %.2f   %.2f   %s".format(
                key, m.taken.getValue(key).size, m.avail.getValue(key), m.shareOf(key), m.beatShareOf(key),
                m.lossShareOf(key), m.taken.getValue(key).joinToString(" ")))
        }
        sb.appendLine()
        sb.appendLine("per cell: mixed roster against the best pair there")
        for (c in cells) {
            val bestPair = c.pairs.maxWithOrNull(compareBy({ if (it.second.won) 1 else 0 }, { it.second.hearts }, { it.second.score }))!!
            sb.appendLine("%-14s mixed %-16s best pair %-20s %s".format(c.label, c.mixed, bestPair.first, bestPair.second))
        }
        sb.appendLine()
        sb.appendLine("spike+dart per cell")
        for (c in cells) {
            val sd = c.pairs.firstOrNull { it.first == "spike+dart" } ?: continue
            sb.appendLine("%-14s mixed %-16s spike+dart %s".format(c.label, c.mixed, sd.second))
        }
        write("sweep-pairs.txt", sb.toString())
        println(sb.toString().lineSequence().take(20).joinToString("\n"))
    }

    /** The same matrix on the careful styles the always on regression test can afford. */
    @Test
    fun pairMatrixFast() {
        on()
        val m = Sweep.matrix(Sweep.cells(content, styles = Sweep.FAST_STYLES))
        val sb = StringBuilder()
        sb.appendLine("fast styles: cells ${m.cells}, pairs that are the answer somewhere ${m.answerCount}")
        sb.appendLine("top answer ${m.byAnswers.first()} share %.2f".format(m.answerShareOf(m.byAnswers.first())))
        sb.appendLine("top matcher ${m.topPair} share %.2f".format(m.topShare))
        sb.appendLine("spike+dart takes ${m.taken.getValue("spike+dart").size} of ${m.avail.getValue("spike+dart")}: " +
            m.taken.getValue("spike+dart"))
        sb.appendLine("lowest loss share " + m.judged(9).minByOrNull { m.lossShareOf(it) }
            ?.let { "%s %.2f".format(it, m.lossShareOf(it)) })
        sb.appendLine()
        sb.appendLine("pair                 answers/avail share  matches beats loses")
        for (key in m.byAnswers) {
            sb.appendLine("%-20s %3d/%-3d       %.2f   %.2f    %.2f  %.2f".format(
                key, m.answers.getValue(key).size, m.avail.getValue(key), m.answerShareOf(key), m.shareOf(key),
                m.beatShareOf(key), m.lossShareOf(key)))
        }
        write("sweep-pairs-fast.txt", sb.toString())
        println(sb.toString().lineSequence().take(6).joinToString("\n"))
    }

    /** Goal (b): does buying an upgrade ever beat buying another trap? */
    @Test
    fun upgradeValue() {
        on()
        val sb = StringBuilder()
        var upWins = 0
        var flatWins = 0
        var same = 0
        val jobs = content.levels.flatMap { lv -> Difficulty.entries.map { lv to it } }
        val rows = jobs.parallelStream().map { (lv, d) ->
            val roster = Sweep.rosterOf(lv)
            val up = Sweep.best(lv, d, content, roster, Sweep.STYLES, upgrades = true)
            val flat = Sweep.best(lv, d, content, roster, Sweep.STYLES, upgrades = false)
            Triple("L%02d-%s".format(lv.id, d.id), up, flat)
        }.toList().sortedBy { it.first }
        for ((label, up, flat) in rows) {
            val verdict = when {
                up.betterThan(flat) -> { upWins++; "upgrading wins" }
                flat.betterThan(up) -> { flatWins++; "more traps wins" }
                else -> { same++; "tie" }
            }
            sb.appendLine("%-14s upgrading %-18s no upgrades %-18s %s".format(label, up, flat, verdict))
        }
        sb.insert(0, "upgrading wins $upWins, more traps wins $flatWins, tie $same\n\n")
        write("sweep-upgrades.txt", sb.toString())
        println(sb.toString().lineSequence().first())
    }

    /** Goal (c) and (d): the curve, the committed reference scores, and where the medals sit. */
    @Test
    fun curveAndMedals() {
        on()
        val sb = StringBuilder()
        sb.appendLine("level   difficulty  refScore  silver  gold  refHearts  botBest")
        for (lv in content.levels) for (d in Difficulty.entries) {
            val sim = Sim(RunSpec.forLevel(lv, d), content)
            ReferenceIo.replay(sim, ReferenceIo.read(lv.id, d))
            val par = lv.par.getValue(d.id)
            val bot = Sweep.best(lv, d, content, Sweep.rosterOf(lv), Sweep.STYLES)
            sb.appendLine("%-7d %-11s %-9d %-7d %-5d %-10d %s".format(
                lv.id, d.id, sim.score(), par.silver, par.gold, sim.hearts, bot))
        }
        sb.appendLine()
        sb.appendLine("levels whose three difficulties are identical in par:")
        for (lv in content.levels) {
            val pars = Difficulty.entries.map { lv.par.getValue(it.id) }
            if (pars.distinct().size == 1) sb.appendLine("  level ${lv.id} all three ${pars[0]}")
            else if (pars[Difficulty.IRON.ordinal] == pars[Difficulty.STANDARD.ordinal]) sb.appendLine("  level ${lv.id} iron equals standard ${pars[0]}")
        }
        sb.appendLine()
        sb.appendLine("wave strength curve (sum of wave hp scale times count):")
        for (lv in content.levels) {
            val load = lv.waves.sumOf { w -> (w.hp * w.groups.sumOf { it.count }).toDouble() }
            sb.appendLine("  level %2d waves %2d load %.1f".format(lv.id, lv.waves.size, load))
        }
        write("sweep-curve.txt", sb.toString())
    }

    /** Goal (c) minor: how far the reference bots hold on the daily map. */
    @Test
    fun dailyReach() {
        on()
        val start = java.time.LocalDate.of(2026, 1, 1)
        val keys = (0 until 40).map { start.plusDays(it * 9L).toString() }
        val rows = keys.parallelStream().map { key ->
            val daily = DailyGenerator.generate(key)
            val reach = listOf(false, true).map { thick ->
                val sim = Sim(DailyGenerator.spec(daily, content), content)
                Bot(sim, thick).playToEnd(maxWaves = 60)
                sim.waveIndex
            }
            "%s thin %2d thick %2d".format(key, reach[0], reach[1])
        }.toList().sorted()
        val nums = rows.map { it.substringAfter("thin ").trim().split(" ")[0].toInt() }
        val thickNums = rows.map { it.substringAfter("thick ").trim().toInt() }
        val sb = StringBuilder()
        sb.appendLine("thin median ${nums.sorted()[nums.size / 2]} min ${nums.min()} max ${nums.max()}")
        sb.appendLine("thick median ${thickNums.sorted()[thickNums.size / 2]} min ${thickNums.min()} max ${thickNums.max()}")
        sb.appendLine("dead at or before wave 30: thin ${nums.count { it <= 30 }} of ${nums.size}, thick ${thickNums.count { it <= 30 }} of ${thickNums.size}")
        sb.appendLine()
        // The board behind the worst run, so a short reach can be told from a bad map.
        val worstKey = rows.minByOrNull { it.substringAfter("thin ").trim().split(" ")[0].toInt() }!!.take(10)
        sb.appendLine("worst map $worstKey")
        DailyGenerator.generate(worstKey).map.forEach { sb.appendLine("  $it") }
        sb.appendLine()
        rows.forEach { sb.appendLine(it) }
        write("sweep-daily.txt", sb.toString())
        println(sb.toString().lineSequence().take(3).joinToString("\n"))
    }
}

package com.mohdshayan.snarewall.game

/**
 * Shared sweep machinery: plays the same bot with a cut down trap roster so a two trap build can be
 * measured against the full mixed roster on the same board, waves and seeds.
 *
 * Nothing here is a rule the player cannot use. Every run goes through the public Sim API.
 */
object Sweep {

    /** thick maze, share of coin spent on walls, bot seed (0 is careful placement, non zero is random). */
    class Style(val thick: Boolean, val share: Float, val seed: Long)

    /** Careful and careless placement, the two the audit measured. The careful six are the forge's own. */
    val STYLES = listOf(
        Style(false, 0.35f, 0L), Style(false, 0.5f, 0L), Style(false, 0.65f, 0L),
        Style(true, 0.35f, 0L), Style(true, 0.5f, 0L), Style(true, 0.65f, 0L),
        Style(false, 0.5f, 7L), Style(true, 0.5f, 11L),
    )

    /** The quick pass used by the always on regression test: careful play only. */
    val FAST_STYLES = listOf(Style(false, 0.35f, 0L), Style(false, 0.65f, 0L), Style(true, 0.5f, 0L))

    class Outcome(val won: Boolean, val hearts: Int, val score: Int) {
        /** A run is better when it clears, then on hearts held, then on score. */
        fun betterThan(o: Outcome?) = when {
            o == null -> true
            won != o.won -> won
            hearts != o.hearts -> hearts > o.hearts
            else -> score > o.score
        }

        override fun toString() = if (won) "clear h$hearts s$score" else "loss h$hearts s$score"
    }

    fun spec(lv: LevelDef, d: Difficulty, roster: List<TrapKind>) = RunSpec(
        levelId = lv.id,
        dailyKey = null,
        layout = BoardLayout.fromMap(lv.map),
        roster = roster,
        startCoin = lv.startCoin,
        difficulty = d,
        totalWaves = lv.waves.size,
        waveAt = { lv.waves[it] },
        waveBonus = { 4 + it },
    )

    fun run(lv: LevelDef, d: Difficulty, content: GameContent, roster: List<TrapKind>, style: Style, upgrades: Boolean = true): Outcome {
        val sim = Sim(spec(lv, d, roster), content)
        Bot(sim, style.thick, style.share, style.seed, upgrades).playToEnd()
        return Outcome(sim.phase == Phase.WON && sim.stranded == 0, sim.hearts, sim.score())
    }

    /** The best this roster reaches over the given placement styles. */
    fun best(
        lv: LevelDef,
        d: Difficulty,
        content: GameContent,
        roster: List<TrapKind>,
        styles: List<Style> = STYLES,
        upgrades: Boolean = true,
    ): Outcome {
        var best: Outcome? = null
        for (s in styles) {
            val o = run(lv, d, content, roster, s, upgrades)
            if (o.betterThan(best)) best = o
        }
        return best!!
    }

    fun rosterOf(lv: LevelDef) = lv.traps.map { TrapKind.byId(it)!! }

    /**
     * Every two trap build the level offers. A level with only two traps in its shop has none: there
     * the pair is the whole roster, so comparing them would only say that a build ties itself.
     */
    fun pairsOf(lv: LevelDef): List<Pair<TrapKind, TrapKind>> {
        val r = rosterOf(lv).sortedBy { it.ordinal }
        if (r.size < 3) return emptyList()
        val out = ArrayList<Pair<TrapKind, TrapKind>>()
        for (i in r.indices) for (j in i + 1 until r.size) out += r[i] to r[j]
        return out
    }

    /** Always in trap order, so the same two traps are one row whatever order a level lists them in. */
    fun name(p: Pair<TrapKind, TrapKind>): String {
        val (a, b) = if (p.first.ordinal <= p.second.ordinal) p else p.second to p.first
        return "${a.id}+${b.id}"
    }

    /**
     * How many level and difficulty cells each two trap build takes from the full mixed roster.
     * A pair takes a cell when it clears with at least as many hearts as the mixed build held there.
     */
    /**
     * A pair takes a cell when it clears and comes out no worse than the whole mixed roster: more
     * hearts, or the same hearts for at least the same score. This is the audit's own reading, that
     * the pair "matches or beats" the reference build, and it is the measure the regression test
     * holds to a third of a pair's cells. Matching counts, because a build that is as good as the
     * whole game while using two traps is exactly the defect: the rest of the game is decoration.
     */
    fun takes(pair: Outcome, mixed: Outcome) =
        pair.won && (pair.hearts > mixed.hearts || (pair.hearts == mixed.hearts && pair.score >= mixed.score))

    /** The narrower reading: the pair holds the keep better, not just as well. */
    fun beats(pair: Outcome, mixed: Outcome) = pair.won && pair.hearts > mixed.hearts

    /** The mixed roster holds the keep better here, so this pair has a real weakness on this level. */
    fun losesTo(pair: Outcome, mixed: Outcome) = !pair.won || mixed.hearts > pair.hearts

    class Matrix(
        val cells: Int,
        val taken: Map<String, List<String>>,
        val beaten: Map<String, List<String>>,
        val lost: Map<String, List<String>>,
        val answers: Map<String, List<String>>,
        val avail: Map<String, Int>,
    ) {
        /** Share of its own cells where this pair is the answer, the best of every build there. */
        fun answerShareOf(pair: String) = (answers[pair]?.size ?: 0).toFloat() / maxOf(1, avail.getValue(pair))
        val byAnswers get() = avail.keys.sortedWith(compareByDescending<String> { answerShareOf(it) }.thenBy { it })
        /** How many different two trap builds are the answer on at least one level. */
        val answerCount get() = answers.count { it.value.isNotEmpty() }

        /** Share of the cells where this pair can even be built that it takes from the mixed build. */
        fun shareOf(pair: String) = (taken[pair]?.size ?: 0).toFloat() / maxOf(1, avail.getValue(pair))
        fun beatShareOf(pair: String) = (beaten[pair]?.size ?: 0).toFloat() / maxOf(1, avail.getValue(pair))
        fun lossShareOf(pair: String) = (lost[pair]?.size ?: 0).toFloat() / maxOf(1, avail.getValue(pair))
        val ranked get() = avail.keys.sortedWith(compareByDescending<String> { shareOf(it) }.thenBy { it })
        val topPair get() = ranked.firstOrNull() ?: "none"
        val topShare get() = if (ranked.isEmpty()) 0f else shareOf(topPair)
        /** Pairs that take at least one cell: how many different builds are a real answer somewhere. */
        val winners get() = taken.count { it.value.isNotEmpty() }
        /** Pairs judged by the regression test: a handful of cells is too few to call a pattern. */
        fun judged(minCells: Int) = avail.filter { it.value >= minCells }.keys
    }

    /** One level and difficulty: what the mixed roster held, and what each pair held. */
    class Cell(val lv: LevelDef, val d: Difficulty, val mixed: Outcome, val pairs: List<Pair<String, Outcome>>) {
        val label get() = "L%02d-%s".format(lv.id, d.id)

        /**
         * The build that is the answer here: the two trap build that comes out best of all of them and
         * is no worse than the whole mixed roster. Null when no pair improves on playing the roster.
         */
        val answer: String?
            get() {
                val top = pairs.maxWithOrNull(
                    compareBy({ if (it.second.won) 1 else 0 }, { it.second.hearts }, { it.second.score }),
                ) ?: return null
                return if (takes(top.second, mixed)) top.first else null
            }
    }

    /** Every cell, measured in parallel. Runs are independent, so the numbers do not depend on the order. */
    fun cells(
        content: GameContent,
        levels: List<LevelDef> = content.levels,
        difficulties: List<Difficulty> = Difficulty.entries,
        styles: List<Style> = STYLES,
    ): List<Cell> {
        val jobs = ArrayList<Pair<LevelDef, Difficulty>>()
        for (lv in levels) {
            if (pairsOf(lv).isEmpty()) continue
            for (d in difficulties) jobs += lv to d
        }
        return jobs.parallelStream().map { (lv, d) ->
            val mixed = best(lv, d, content, rosterOf(lv), styles)
            val rows = pairsOf(lv).map { p -> name(p) to best(lv, d, content, listOf(p.first, p.second), styles) }
            Cell(lv, d, mixed, rows)
        }.toList().sortedWith(compareBy({ it.lv.id }, { it.d.ordinal }))
    }

    fun matrix(cells: List<Cell>): Matrix {
        val taken = LinkedHashMap<String, MutableList<String>>()
        val beaten = LinkedHashMap<String, MutableList<String>>()
        val lost = LinkedHashMap<String, MutableList<String>>()
        val answers = LinkedHashMap<String, MutableList<String>>()
        val avail = LinkedHashMap<String, Int>()
        for (c in cells) {
            for ((key, o) in c.pairs) {
                taken.getOrPut(key) { ArrayList() }
                beaten.getOrPut(key) { ArrayList() }
                lost.getOrPut(key) { ArrayList() }
                answers.getOrPut(key) { ArrayList() }
                avail[key] = (avail[key] ?: 0) + 1
                if (takes(o, c.mixed)) taken.getValue(key) += c.label
                if (beats(o, c.mixed)) beaten.getValue(key) += c.label
                if (losesTo(o, c.mixed)) lost.getValue(key) += c.label
            }
            c.answer?.let { answers.getValue(it) += c.label }
        }
        return Matrix(cells.size, taken, beaten, lost, answers, avail)
    }

    fun matrix(
        content: GameContent,
        levels: List<LevelDef> = content.levels,
        difficulties: List<Difficulty> = Difficulty.entries,
        styles: List<Style> = STYLES,
    ): Matrix = matrix(cells(content, levels, difficulties, styles))
}

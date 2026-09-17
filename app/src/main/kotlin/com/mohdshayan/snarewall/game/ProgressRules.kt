package com.mohdshayan.snarewall.game

/** How a finished run folds into saved progress. Pure, so a test can prove medals never go backwards. */
object ProgressRules {

    class LevelOutcome(val row: LevelProgressRow, val medal: Scoring.Medal, val newBest: Boolean, val firstClear: Boolean)

    fun level(old: LevelProgressRow?, levelId: Int, difficulty: String, won: Boolean, score: Int, hearts: Int, par: Par, now: Long): LevelOutcome {
        val medal = Scoring.medal(won, score, par)
        val oldMedal = Scoring.Medal.entries.firstOrNull { it.id == old?.medal } ?: Scoring.Medal.NONE
        val newBest = won && (old == null || score > old.bestScore || oldMedal == Scoring.Medal.NONE)
        val firstClear = won && (old?.clears ?: 0) == 0
        val row = LevelProgressRow(
            levelId = levelId,
            difficulty = difficulty,
            bestScore = if (won) maxOf(score, old?.bestScore ?: 0) else old?.bestScore ?: 0,
            medal = Scoring.better(oldMedal, medal).id,
            bestHearts = if (won) maxOf(hearts, old?.bestHearts ?: 0) else old?.bestHearts ?: 0,
            clears = (old?.clears ?: 0) + if (won) 1 else 0,
            firstClearedAt = old?.firstClearedAt ?: if (won) now else null,
            lastPlayedAt = now,
        )
        return LevelOutcome(row, medal, newBest, firstClear)
    }

    class DailyOutcome(val row: DailyScoreRow, val newBest: Boolean)

    fun daily(old: DailyScoreRow?, dateKey: String, wavesHeld: Int, score: Int, now: Long): DailyOutcome {
        val newBest = old == null || score > old.bestScore
        return DailyOutcome(
            DailyScoreRow(
                dateKey = dateKey,
                bestWave = maxOf(wavesHeld, old?.bestWave ?: 0),
                bestScore = maxOf(score, old?.bestScore ?: 0),
                runs = (old?.runs ?: 0) + 1,
                lastPlayedAt = now,
            ),
            newBest,
        )
    }

    /** A level opens once the level before it is cleared on any difficulty. */
    fun unlocked(levelId: Int, rows: List<LevelProgressRow>): Boolean =
        levelId <= 1 || rows.any { it.levelId == levelId - 1 && it.clears > 0 }

    /** The level Home offers: the first one never cleared, or null once all are. */
    fun nextLevel(rows: List<LevelProgressRow>, count: Int): Int? =
        (1..count).firstOrNull { id -> rows.none { it.levelId == id && it.clears > 0 } }
}

package com.mohdshayan.snarewall.game

/**
 * Who may start what. [owned] is ServiceLocator.unlock.state's `unlocked`: a prior buyer, or the
 * full_unlock purchase Google Play reports. Pure, so every rule is a JVM test (AccessTest).
 */
object Access {
    /** Chalk Downs is free. The boundary may only ever move outward, never inward. */
    const val FREE_LAST_LEVEL = 5

    fun levelOpen(levelId: Int, owned: Boolean): Boolean = owned || levelId <= FREE_LAST_LEVEL

    /** The daily map is never gated. */
    fun canStart(levelId: Int, daily: Boolean, owned: Boolean): Boolean = daily || levelOpen(levelId, owned)

    /** The Board route latch: once a run was allowed to start, it stays open for that back stack entry. */
    fun boardOpen(latched: Boolean, levelId: Int, daily: Boolean, owned: Boolean): Boolean =
        latched || canStart(levelId, daily, owned)

    /** The level Home may offer, or null when the next one is in the full game. */
    fun nextPlayable(next: Int?, owned: Boolean): Int? = next?.takeIf { levelOpen(it, owned) }
}

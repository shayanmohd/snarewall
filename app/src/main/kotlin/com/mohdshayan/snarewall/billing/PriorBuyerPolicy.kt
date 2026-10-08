package com.mohdshayan.snarewall.billing

/** What the first start of a billing build can see about this install. */
data class InstallFacts(
    val databaseExists: Boolean, // databases/snarewall.db, which 1.0.0 creates on every first launch, read before Room opens
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
)

/** The stored answer: [decided] once the 1.1.0 check ran, [owner] a latch that is never cleared. */
data class PriorBuyerRecord(val decided: Boolean, val owner: Boolean)

/**
 * Who bought Snarewall while it was a paid app. Play Billing cannot see a paid-app purchase, so this
 * reads what the install itself shows. Pure, so every branch is a JVM test (PriorBuyerPolicyTest).
 */
object PriorBuyerPolicy {
    /**
     * 0 in 1.1.0. Set once in 1.1.1 to the moment the US and IN store pages first showed the app as free,
     * rounded up to the next full hour UTC. Never lowered in any later build.
     */
    const val PAID_UNTIL_MS = 0L

    /** A fresh install records equal install and update times; a gap this long means an update from 1.0.0. */
    const val UPDATE_GAP_MS = 60_000L

    fun next(record: PriorBuyerRecord, facts: InstallFacts, paidUntilMs: Long = PAID_UNTIL_MS): PriorBuyerRecord = when {
        record.owner -> record
        // 1.1.1 and later: the cutoff alone decides, on every start, for anyone not already an owner.
        paidUntilMs > 0L -> PriorBuyerRecord(decided = true, owner = facts.firstInstallTime < paidUntilMs)
        record.decided -> record
        // 1.1.0, once: 1.0.0's data, or an install that was already there before this build.
        else -> PriorBuyerRecord(
            decided = true,
            owner = facts.databaseExists || facts.lastUpdateTime - facts.firstInstallTime >= UPDATE_GAP_MS,
        )
    }
}

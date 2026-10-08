package com.mohdshayan.snarewall.billing

/** Release builds never override the unlock: Google Play and the prior-buyer latch decide. Pinned by UnlockClaimsTest. */
object EntitlementOverride {
    fun forced(): Boolean? = null
}

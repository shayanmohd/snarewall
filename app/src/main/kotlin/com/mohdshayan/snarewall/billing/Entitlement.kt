package com.mohdshayan.snarewall.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode

/**
 * The one product this app sells: a non-consumable one-time product in Play Console. It is bought
 * once and never consumed (consuming removes ownership). A product id can never be changed or reused.
 */
const val UNLOCK_PRODUCT_ID = "full_unlock"

/** What the app grants. UNKNOWN lasts only until the local copy is read or Google Play first answers. */
enum class Entitlement { UNKNOWN, LOCKED, PENDING, UNLOCKED }

/** Whether Google Play can sell the unlock on this phone right now. Drives the unlock screen only. */
enum class StoreState { CHECKING, READY, NOT_OFFERED, OFFLINE, UNAVAILABLE }

/** One purchase as Google Play reports it, reduced to what the unlock needs, so the rules test on the JVM. */
data class OwnedPurchase(
    val productIds: List<String>,
    val state: State,
    val acknowledged: Boolean,
    val token: String,
) {
    enum class State { PURCHASED, PENDING, OTHER }
}

/**
 * The unlock rules, kept pure so every branch is a JVM unit test. Grant only PURCHASED, never PENDING,
 * acknowledge PURCHASED within three days (developer.android.com/google/play/billing/integrate).
 */
object EntitlementRules {

    /**
     * A full purchases query finished. When it failed ([queryOk] false: offline, signed out, no Play
     * Store, timeout) the phone keeps what it knows. When it answered, Play's list decides, with one
     * guard: an answer that lacks the unlock relocks a cached unlock only when the same sync's
     * product-details query (made first) also answered OK and no PURCHASED unlock was seen in this
     * process. The guard is an undocumented heuristic against a stale answer, not a guarantee.
     */
    fun afterQuery(
        current: Entitlement,
        queryOk: Boolean,
        owned: List<OwnedPurchase>,
        detailsOk: Boolean,
        purchasedThisRun: Boolean,
        productId: String = UNLOCK_PRODUCT_ID,
    ): Entitlement {
        if (!queryOk) return settle(current)
        val mine = owned.filter { productId in it.productIds }
        return when {
            mine.any { it.state == OwnedPurchase.State.PURCHASED } -> Entitlement.UNLOCKED
            mine.any { it.state == OwnedPurchase.State.PENDING } ->
                if (current == Entitlement.UNLOCKED) Entitlement.UNLOCKED else Entitlement.PENDING
            // UNSPECIFIED_STATE tells nothing either way.
            mine.isNotEmpty() -> settle(current)
            current == Entitlement.UNLOCKED && (!detailsOk || purchasedThisRun) -> Entitlement.UNLOCKED
            else -> Entitlement.LOCKED
        }
    }

    /** A purchase sheet returned [updated], which lists only what changed: it may unlock or mark pending, never relock. */
    fun afterPurchaseUpdate(
        current: Entitlement,
        updated: List<OwnedPurchase>,
        productId: String = UNLOCK_PRODUCT_ID,
    ): Entitlement {
        val mine = updated.filter { productId in it.productIds }
        return when {
            mine.any { it.state == OwnedPurchase.State.PURCHASED } -> Entitlement.UNLOCKED
            mine.any { it.state == OwnedPurchase.State.PENDING } && current != Entitlement.UNLOCKED -> Entitlement.PENDING
            else -> current
        }
    }

    /** PURCHASED and not yet acknowledged. Never PENDING: the three-day window starts at PURCHASED. */
    fun toAcknowledge(owned: List<OwnedPurchase>, productId: String = UNLOCK_PRODUCT_ID): List<OwnedPurchase> =
        owned.filter { productId in it.productIds && it.state == OwnedPurchase.State.PURCHASED && !it.acknowledged }

    /** Google Play could not answer: an unknown state becomes LOCKED, anything else is kept. */
    fun settle(current: Entitlement): Entitlement =
        if (current == Entitlement.UNKNOWN) Entitlement.LOCKED else current

    /** What the unlock screen may say after a connection or product-details answer. */
    fun storeStateFor(responseCode: Int, productFound: Boolean): StoreState = when (responseCode) {
        BillingResponseCode.OK -> if (productFound) StoreState.READY else StoreState.NOT_OFFERED
        // No usable Play Store: missing, too old, blocked by the system or an admin, unsupported country.
        BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED -> StoreState.UNAVAILABLE
        // Play answered but will not sell this product here (inactive, wrong package, propagation delay).
        BillingResponseCode.ITEM_UNAVAILABLE, BillingResponseCode.DEVELOPER_ERROR -> StoreState.NOT_OFFERED
        // SERVICE_UNAVAILABLE, NETWORK_ERROR, ERROR, SERVICE_DISCONNECTED and the rest: try again later.
        else -> StoreState.OFFLINE
    }

    /** Converted apps only: an install older than the moment the app went free was a paid install. */
    fun isPaidEraInstall(firstInstallTimeMs: Long, cutoffMs: Long): Boolean = firstInstallTimeMs in 1 until cutoffMs
}

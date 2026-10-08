package com.mohdshayan.snarewall.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The unlock rules decide whether a buyer keeps what they paid for, so every branch is pinned here. */
class EntitlementTest {

    private fun owned(state: OwnedPurchase.State, acknowledged: Boolean = true, id: String = UNLOCK_PRODUCT_ID) =
        OwnedPurchase(listOf(id), state, acknowledged, token = "t-$state-$id-$acknowledged")

    private fun query(
        current: Entitlement,
        queryOk: Boolean = true,
        owned: List<OwnedPurchase> = emptyList(),
        detailsOk: Boolean = true,
        purchasedThisRun: Boolean = false,
    ) = EntitlementRules.afterQuery(current, queryOk, owned, detailsOk, purchasedThisRun)

    @Test
    fun firstRunWithPlayUnableToAnswerIsLocked() =
        assertEquals(Entitlement.LOCKED, query(Entitlement.UNKNOWN, queryOk = false, detailsOk = false))

    @Test
    fun cachedUnlockSurvivesEveryFailedQuery() =
        assertEquals(Entitlement.UNLOCKED, query(Entitlement.UNLOCKED, queryOk = false, detailsOk = false))

    @Test
    fun purchasedUnlocksEvenWhenDetailsFailed() =
        assertEquals(Entitlement.UNLOCKED, query(Entitlement.LOCKED, owned = listOf(owned(OwnedPurchase.State.PURCHASED)), detailsOk = false))

    @Test
    fun pendingNeverGrants() =
        assertEquals(Entitlement.PENDING, query(Entitlement.LOCKED, owned = listOf(owned(OwnedPurchase.State.PENDING))))

    @Test
    fun pendingNeverDowngradesAnUnlock() =
        assertEquals(Entitlement.UNLOCKED, query(Entitlement.UNLOCKED, owned = listOf(owned(OwnedPurchase.State.PENDING))))

    @Test
    fun absentWhileDetailsFailedKeepsTheCache() =
        assertEquals(Entitlement.UNLOCKED, query(Entitlement.UNLOCKED, detailsOk = false))

    @Test
    fun absentWhileDetailsAnsweredRelocks() =
        // A refund with revoke, a chargeback or an auto-refund drops the purchase from the query.
        assertEquals(Entitlement.LOCKED, query(Entitlement.UNLOCKED, detailsOk = true))

    @Test
    fun absentRightAfterAPurchaseInThisProcessKeepsTheUnlock() =
        assertEquals(Entitlement.UNLOCKED, query(Entitlement.UNLOCKED, purchasedThisRun = true))

    @Test
    fun cancelledPendingBecomesLocked() =
        assertEquals(Entitlement.LOCKED, query(Entitlement.PENDING))

    @Test
    fun unspecifiedStateChangesNothing() =
        assertEquals(Entitlement.UNLOCKED, query(Entitlement.UNLOCKED, owned = listOf(owned(OwnedPurchase.State.OTHER))))

    @Test
    fun anotherProductIsIgnored() =
        assertEquals(Entitlement.LOCKED, query(Entitlement.LOCKED, owned = listOf(owned(OwnedPurchase.State.PURCHASED, id = "other"))))

    @Test
    fun aPurchaseUpdateNeverRelocks() {
        assertEquals(Entitlement.UNLOCKED, EntitlementRules.afterPurchaseUpdate(Entitlement.UNLOCKED, emptyList()))
        assertEquals(
            Entitlement.UNLOCKED,
            EntitlementRules.afterPurchaseUpdate(Entitlement.UNLOCKED, listOf(owned(OwnedPurchase.State.PENDING))),
        )
    }

    @Test
    fun aPurchaseUpdateUnlocksOrMarksPending() {
        assertEquals(
            Entitlement.UNLOCKED,
            EntitlementRules.afterPurchaseUpdate(Entitlement.LOCKED, listOf(owned(OwnedPurchase.State.PURCHASED))),
        )
        assertEquals(
            Entitlement.PENDING,
            EntitlementRules.afterPurchaseUpdate(Entitlement.LOCKED, listOf(owned(OwnedPurchase.State.PENDING))),
        )
    }

    @Test
    fun onlyPurchasedUnacknowledgedPurchasesAreAcknowledged() {
        val list = listOf(
            owned(OwnedPurchase.State.PURCHASED, acknowledged = false),
            owned(OwnedPurchase.State.PURCHASED, acknowledged = true),
            owned(OwnedPurchase.State.PENDING, acknowledged = false),
            owned(OwnedPurchase.State.PURCHASED, acknowledged = false, id = "other"),
        )
        assertEquals(listOf(list[0]), EntitlementRules.toAcknowledge(list))
    }

    @Test
    fun storeStateFollowsTheResponseCode() {
        assertEquals(StoreState.READY, EntitlementRules.storeStateFor(BillingResponseCode.OK, productFound = true))
        assertEquals(StoreState.NOT_OFFERED, EntitlementRules.storeStateFor(BillingResponseCode.OK, productFound = false))
        assertEquals(StoreState.UNAVAILABLE, EntitlementRules.storeStateFor(BillingResponseCode.BILLING_UNAVAILABLE, false))
        assertEquals(StoreState.UNAVAILABLE, EntitlementRules.storeStateFor(BillingResponseCode.FEATURE_NOT_SUPPORTED, false))
        assertEquals(StoreState.NOT_OFFERED, EntitlementRules.storeStateFor(BillingResponseCode.DEVELOPER_ERROR, false))
        assertEquals(StoreState.OFFLINE, EntitlementRules.storeStateFor(BillingResponseCode.NETWORK_ERROR, false))
        assertEquals(StoreState.OFFLINE, EntitlementRules.storeStateFor(BillingResponseCode.SERVICE_UNAVAILABLE, false))
        assertEquals(StoreState.OFFLINE, EntitlementRules.storeStateFor(BillingResponseCode.SERVICE_DISCONNECTED, false))
    }

    @Test
    fun onlyAnInstallFromBeforeTheSwitchIsAPaidInstall() {
        val cutoff = 1_760_000_000_000L
        assertTrue(EntitlementRules.isPaidEraInstall(cutoff - 1, cutoff))
        assertFalse(EntitlementRules.isPaidEraInstall(cutoff, cutoff))
        assertFalse(EntitlementRules.isPaidEraInstall(0L, cutoff))
    }
}

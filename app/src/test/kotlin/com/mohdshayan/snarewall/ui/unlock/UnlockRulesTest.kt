package com.mohdshayan.snarewall.ui.unlock

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.mohdshayan.snarewall.billing.Entitlement
import com.mohdshayan.snarewall.billing.PurchaseOutcome
import com.mohdshayan.snarewall.billing.StoreState
import com.mohdshayan.snarewall.billing.UnlockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The billing core's states, as the Unlock screen and Settings show them (PILOT-DECISIONS D3 and D7). */
class UnlockRulesTest {

    private fun s(
        entitlement: Entitlement = Entitlement.LOCKED,
        store: StoreState = StoreState.CHECKING,
        price: String? = null,
        legacyOwner: Boolean = false,
    ) = UnlockState(entitlement, store, price, legacyOwner)

    @Test
    fun theCoreStatesMapToThePlansScreenStates() {
        assertEquals(UnlockView.LOADING, UnlockRules.view(s(store = StoreState.CHECKING)))
        assertEquals(UnlockView.LOADING, UnlockRules.view(s(Entitlement.UNKNOWN, StoreState.CHECKING)))
        assertEquals(UnlockView.READY, UnlockRules.view(s(store = StoreState.READY, price = "P")))
        assertEquals(UnlockView.NOT_REACHABLE, UnlockRules.view(s(store = StoreState.OFFLINE)))
        assertEquals(UnlockView.UNAVAILABLE, UnlockRules.view(s(store = StoreState.UNAVAILABLE)))
        assertEquals(UnlockView.UNAVAILABLE, UnlockRules.view(s(store = StoreState.NOT_OFFERED)))
        // A product with no offer to buy it at is unavailable, never a button without a price.
        assertEquals(UnlockView.UNAVAILABLE, UnlockRules.view(s(store = StoreState.READY, price = null)))
        assertEquals(UnlockView.PENDING, UnlockRules.view(s(Entitlement.PENDING, StoreState.READY, "P")))
        assertEquals(UnlockView.OWNED, UnlockRules.view(s(Entitlement.UNLOCKED, StoreState.READY, "P")))
        assertEquals(UnlockView.PRIOR, UnlockRules.view(s(Entitlement.LOCKED, StoreState.OFFLINE, legacyOwner = true)))
        assertEquals(UnlockView.PRIOR, UnlockRules.view(s(Entitlement.UNLOCKED, StoreState.READY, "P", legacyOwner = true)))
    }

    @Test
    fun tryAgainShowsLoadingButNeverHidesAPrice() {
        assertEquals(UnlockView.LOADING, UnlockRules.view(s(store = StoreState.OFFLINE), busy = true))
        assertEquals(UnlockView.LOADING, UnlockRules.view(s(store = StoreState.UNAVAILABLE), busy = true))
        assertEquals(UnlockView.READY, UnlockRules.view(s(store = StoreState.READY, price = "P"), busy = true))
    }

    @Test
    fun settingsStatusOrder() {
        assertEquals(FullGameStatus.PRIOR, UnlockRules.status(s(Entitlement.UNLOCKED, legacyOwner = true)))
        assertEquals(FullGameStatus.PLAY, UnlockRules.status(s(Entitlement.UNLOCKED)))
        assertEquals(FullGameStatus.PENDING, UnlockRules.status(s(Entitlement.PENDING)))
        assertEquals(FullGameStatus.FREE, UnlockRules.status(s(Entitlement.LOCKED)))
        assertEquals(FullGameStatus.FREE, UnlockRules.status(s(Entitlement.UNKNOWN)))
    }

    @Test
    fun purchaseSheetResults() {
        assertEquals(UnlockNote.FAILED, UnlockRules.outcomeNote(PurchaseOutcome.FAILED))
        // A cancel shows nothing; unlocked and pending show as the screen's own state.
        listOf(PurchaseOutcome.CANCELLED, PurchaseOutcome.UNLOCKED, PurchaseOutcome.PENDING).forEach {
            assertEquals(UnlockNote.NONE, UnlockRules.outcomeNote(it))
        }
        listOf(BillingResponseCode.OK, BillingResponseCode.USER_CANCELED, BillingResponseCode.ITEM_ALREADY_OWNED).forEach {
            assertEquals(UnlockNote.NONE, UnlockRules.launchNote(it))
        }
        listOf(
            BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.NETWORK_ERROR,
            BillingResponseCode.ERROR, BillingResponseCode.SERVICE_DISCONNECTED,
        ).forEach { assertEquals(UnlockNote.NOT_REACHABLE, UnlockRules.launchNote(it)) }
        listOf(
            BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingResponseCode.ITEM_UNAVAILABLE, BillingResponseCode.DEVELOPER_ERROR,
        ).forEach { assertEquals(UnlockNote.UNAVAILABLE, UnlockRules.launchNote(it)) }
    }

    @Test
    fun restorePurchaseMessages() {
        assertEquals(UnlockNote.RESTORED, UnlockRules.restoreNote(true, s(Entitlement.UNLOCKED, StoreState.READY, "P")))
        assertEquals(UnlockNote.NOT_FOUND, UnlockRules.restoreNote(true, s(Entitlement.LOCKED, StoreState.READY, "P")))
        assertEquals(UnlockNote.NONE, UnlockRules.restoreNote(true, s(Entitlement.PENDING, StoreState.READY, "P")))
        assertEquals(UnlockNote.NOT_REACHABLE, UnlockRules.restoreNote(false, s(store = StoreState.OFFLINE)))
        assertEquals(UnlockNote.UNAVAILABLE, UnlockRules.restoreNote(false, s(store = StoreState.UNAVAILABLE)))
        assertEquals(UnlockNote.UNAVAILABLE, UnlockRules.restoreNote(false, s(store = StoreState.NOT_OFFERED)))
    }

    @Test
    fun theScreenSaysWhoOwnsItAndThatBuyingNeedsAConnection() {
        assertTrue(UnlockText.OWNERSHIP.contains("belongs to the Google account that buys it"))
        assertTrue(UnlockText.OWNERSHIP.contains("Buying needs a connection once."))
        assertEquals("Not now", UnlockText.NOT_NOW)
        assertEquals("Restore purchase", UnlockText.RESTORE)
        assertEquals("Unlock for P", UnlockText.buy("P"))
    }
}

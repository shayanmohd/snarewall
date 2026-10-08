package com.mohdshayan.snarewall.ui.unlock

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.mohdshayan.snarewall.billing.Entitlement
import com.mohdshayan.snarewall.billing.EntitlementRules
import com.mohdshayan.snarewall.billing.PurchaseOutcome
import com.mohdshayan.snarewall.billing.StoreState
import com.mohdshayan.snarewall.billing.UnlockState

/** What the Unlock screen shows. */
enum class UnlockView { LOADING, READY, NOT_REACHABLE, UNAVAILABLE, PENDING, OWNED, PRIOR }

/** The one-line result of the last action: a purchase sheet, Restore purchase or Email us. */
enum class UnlockNote { NONE, FAILED, RESTORED, NOT_FOUND, NOT_REACHABLE, UNAVAILABLE, NO_EMAIL }

/** The status line of the Settings Full game section. */
enum class FullGameStatus { PRIOR, PLAY, PENDING, FREE }

/** How the billing core's answers map to the screen. Pure, so every branch is a JVM test (UnlockRulesTest). */
object UnlockRules {

    /** [busy] is a Try again or Restore purchase still waiting for Google Play. */
    fun view(s: UnlockState, busy: Boolean = false): UnlockView = when {
        s.legacyOwner -> UnlockView.PRIOR
        s.entitlement == Entitlement.UNLOCKED -> UnlockView.OWNED
        s.entitlement == Entitlement.PENDING -> UnlockView.PENDING
        s.store == StoreState.READY && s.price != null -> UnlockView.READY
        busy || s.store == StoreState.CHECKING -> UnlockView.LOADING
        s.store == StoreState.OFFLINE -> UnlockView.NOT_REACHABLE
        // UNAVAILABLE, NOT_OFFERED, or a product Google Play returned with no offer to buy it at.
        else -> UnlockView.UNAVAILABLE
    }

    fun status(s: UnlockState): FullGameStatus = when {
        s.legacyOwner -> FullGameStatus.PRIOR
        s.entitlement == Entitlement.UNLOCKED -> FullGameStatus.PLAY
        s.entitlement == Entitlement.PENDING -> FullGameStatus.PENDING
        else -> FullGameStatus.FREE
    }

    /** A purchase sheet's end. Unlocked and pending show as the screen's state; a cancel shows nothing. */
    fun outcomeNote(outcome: PurchaseOutcome): UnlockNote =
        if (outcome == PurchaseOutcome.FAILED) UnlockNote.FAILED else UnlockNote.NONE

    /** The purchase sheet did not open. ITEM_ALREADY_OWNED re-queries in the repository and ends as an outcome. */
    fun launchNote(responseCode: Int): UnlockNote = when (responseCode) {
        BillingResponseCode.OK, BillingResponseCode.USER_CANCELED, BillingResponseCode.ITEM_ALREADY_OWNED -> UnlockNote.NONE
        else ->
            if (EntitlementRules.storeStateFor(responseCode, productFound = false) == StoreState.OFFLINE) UnlockNote.NOT_REACHABLE
            else UnlockNote.UNAVAILABLE
    }

    /** Restore purchase: [answered] is UnlockRepository.syncNow()'s result, [after] the state it left. */
    fun restoreNote(answered: Boolean, after: UnlockState): UnlockNote = when {
        after.unlocked -> UnlockNote.RESTORED
        // The pending line already says what is happening.
        after.entitlement == Entitlement.PENDING -> UnlockNote.NONE
        answered -> UnlockNote.NOT_FOUND
        after.store == StoreState.UNAVAILABLE || after.store == StoreState.NOT_OFFERED -> UnlockNote.UNAVAILABLE
        else -> UnlockNote.NOT_REACHABLE
    }
}

/**
 * Every line the Unlock screen and the Settings Full game section show. No price is ever written here:
 * the price is only Google Play's own formatted price, passed in.
 */
object UnlockText {
    const val HEADER = "Full game"
    const val TITLE = "Ten more levels"
    const val LEAD = "Chalk Downs and the daily map stay free. The full game opens Salt Mine and Fen Causeway, " +
        "levels 6 to 15, on all three difficulties with their medals."
    const val ROW_DIGGERS = "Diggers tunnel under long detours, jumpers hop thin walls, flyers ignore walls and sappers knock one down"
    const val ROW_OILSKINS = "Oilskins shrug off fire and frostborn cannot be slowed"
    const val ROW_TRAPS = "The oil slick, ember grate and shatter hammer join the campaign, with their two combos"
    const val ROW_WARLORDS = "Four more warlord fights"
    const val NOTE = "Levels still open one at a time as you clear them."
    const val OWNERSHIP = "The full game belongs to the Google account that buys it, so it comes back after a reinstall or " +
        "on a new phone signed in to that account. Buying needs a connection once."
    const val UNLOCK = "Unlock"
    const val TRY_AGAIN = "Try again"
    const val NOT_NOW = "Not now"
    const val RESTORE = "Restore purchase"
    const val EMAIL_US = "Email us"
    const val PROMO = "Have a promo code? Redeem it in the Play Store app, then come back here."
    const val PRIOR_PRINT = "Paid for Snarewall before it was free to start and still see this? Email us your Google Play " +
        "order number (it starts with GPA) and we will send a code."

    const val CHECKING = "Checking the price with Google Play."
    const val RESTORING = "Checking with Google Play."
    const val NOT_REACHABLE = "Google Play is not reachable right now. Check that you are online and signed in to the " +
        "Play Store. Your free levels and the daily map work offline."
    const val UNAVAILABLE = "Purchases are not available on this phone right now. Buying needs the Play Store app, " +
        "signed in to your Google account. Your free levels and the daily map work without it."
    const val PENDING = "Your payment is pending. The full game opens when Google Play confirms it."

    const val OWNED_TITLE = "Full game unlocked"
    const val OWNED_BODY = "Levels 6 to 15 now open one at a time as you clear them."
    const val PRIOR_BODY = "The full game is yours on this phone. Levels 6 to 15 open one at a time as you clear them."
    const val GO_TO_LEVELS = "Go to levels"

    const val FAILED = "The purchase did not go through. You can try again."
    const val RESTORED = "Full game restored."
    const val NOT_FOUND = "No full game purchase found for this Google account. If you bought it on another account, " +
        "switch accounts in the Play Store and tap Restore purchase again."

    const val SEE_FULL_GAME = "See the full game"
    const val STATUS_PRIOR = "The full game is yours on this phone."
    const val STATUS_PLAY = "The full game is yours, bought on Google Play."
    const val STATUS_PENDING = "Payment pending on Google Play."
    const val STATUS_FREE = "Levels 1 to 5 and the daily map are free. Levels 6 to 15 are in the full game."
    const val SETTINGS_PRIOR_PRINT = "Paid for Snarewall before it was free to start? Email us your Google Play order " +
        "number and we will send a code."

    const val EMAIL_SUBJECT = "Snarewall prior purchase"
    const val EMAIL_BODY = "My Google Play order number for Snarewall (it starts with GPA): "

    fun priceLine(price: String) = "One payment of $price. No subscription, no ads, nothing else to buy."
    fun buy(price: String) = "Unlock for $price"
    fun noEmail(address: String) = "No email app is installed. Write to $address."

    fun status(s: FullGameStatus) = when (s) {
        FullGameStatus.PRIOR -> STATUS_PRIOR
        FullGameStatus.PLAY -> STATUS_PLAY
        FullGameStatus.PENDING -> STATUS_PENDING
        FullGameStatus.FREE -> STATUS_FREE
    }

    /** The text of a result line, or null for none. */
    fun note(n: UnlockNote, address: String): String? = when (n) {
        UnlockNote.NONE -> null
        UnlockNote.FAILED -> FAILED
        UnlockNote.RESTORED -> RESTORED
        UnlockNote.NOT_FOUND -> NOT_FOUND
        UnlockNote.NOT_REACHABLE -> NOT_REACHABLE
        UnlockNote.UNAVAILABLE -> UNAVAILABLE
        UnlockNote.NO_EMAIL -> noEmail(address)
    }
}

package com.mohdshayan.snarewall.billing

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.mohdshayan.snarewall.data.prefs.EntitlementStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * What every locked feature and the unlock screen read. [price] is Google Play's own formatted price in
 * the buyer's currency, null until Play offers the product. [legacyOwner] is only ever true in an app
 * that was sold as a paid app before it went free.
 */
data class UnlockState(
    val entitlement: Entitlement = Entitlement.UNKNOWN,
    val store: StoreState = StoreState.CHECKING,
    val price: String? = null,
    val legacyOwner: Boolean = false,
) {
    val unlocked: Boolean get() = legacyOwner || entitlement == Entitlement.UNLOCKED
}

/** How a purchase sheet ended, for the unlock screen's one-line message. */
enum class PurchaseOutcome { UNLOCKED, PENDING, CANCELLED, FAILED }

/**
 * The process's one BillingClient (Play Billing Library 9.1.0, the Java artifact), owned by
 * ServiceLocator and never closed. Purchases go to the Google Play Store app over IPC; the app has no
 * INTERNET permission. Call [refresh] from MainActivity.onResume, read [state], and call
 * [launchPurchase] from a click handler. Every member is used on the main thread: [scope] must run on
 * Dispatchers.Main.immediate.
 */
class UnlockRepository(
    context: Context,
    private val store: EntitlementStore,
    private val scope: CoroutineScope,
    private val productId: String = UNLOCK_PRODUCT_ID,
    legacyOwner: (suspend () -> Boolean)? = null,
) : PurchasesUpdatedListener {

    private val _state = MutableStateFlow(UnlockState())
    val state: StateFlow<UnlockState> = _state.asStateFlow()

    private val _outcomes = MutableSharedFlow<PurchaseOutcome>(extraBufferCapacity = 4)
    val outcomes: SharedFlow<PurchaseOutcome> = _outcomes.asSharedFlow()

    // build() throws without a listener or without enableOneTimeProducts(). enableAutoServiceReconnection
    // (PBL 8+) re-binds on the next call after a disconnect, so nothing reconnects by hand.
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var details: ProductDetails? = null
    private var purchasedThisRun = false
    private var syncJob: Deferred<Boolean>? = null

    // isReady() cannot say whether this client ever connected: in Play Billing 9.1.0 it returns true
    // whenever automatic reconnection is enabled (it returns the builder flag; bytecode read 2026-10-02).
    // So startConnection runs until one setup succeeds, and automatic reconnection takes over after that.
    // The setup runs in [scope], not in the sync that started it: a sync timeout stops waiting but never
    // abandons a setup, and the next sync joins it (startConnection while CONNECTING is DEVELOPER_ERROR).
    private var setupDone = false
    private var setup: Deferred<BillingResult>? = null

    // startConnection threw (a Play Store that refuses the bind). 9.1.0 does not catch that and leaves the
    // client CONNECTING for good, so every later startConnection would answer DEVELOPER_ERROR. Report billing
    // as unavailable for the rest of this process instead.
    private var setupBroken = false

    // launchBillingFlow reports a failed launch twice: as its return value and again through
    // onPurchasesUpdated(code, null) (9.1.0 bytecode). Callers handle the returned code, so the listener drops
    // that one echo. ITEM_ALREADY_OWNED is never recorded: only the listener acts on it.
    private var launchEcho: Int? = null
    private val acknowledging = mutableSetOf<String>()
    private val requeriedAfterNotOwned = mutableSetOf<String>()

    // The phone's copy of Play's last answer, read before any sync can overwrite the state.
    private val cache: Deferred<Boolean> = scope.async { store.unlocked() }

    init {
        scope.launch { applyCache() }
        if (legacyOwner != null) scope.launch { if (legacyOwner()) _state.update { it.copy(legacyOwner = true) } }
    }

    /** Fire and forget: MainActivity.onResume and the unlock screen call this. */
    fun refresh() {
        scope.launch { syncNow() }
    }

    /** Asks Google Play again and waits. False when Play could not answer. Calls during a sync join it. */
    suspend fun syncNow(): Boolean {
        syncJob?.takeIf { it.isActive }?.let { return it.await() }
        val job = scope.async {
            withTimeoutOrNull(SYNC_TIMEOUT_MS) { sync() } ?: run {
                settle(StoreState.OFFLINE)
                false
            }
        }
        syncJob = job
        return job.await()
    }

    /**
     * Opens Google Play's purchase sheet with the Activity hosting the UI (LocalActivity.current).
     * Returns the launch's BillingResponseCode only; the purchase itself arrives in onPurchasesUpdated.
     */
    @MainThread
    fun launchPurchase(activity: Activity): Int {
        val product = details
        if (product == null) {
            refresh()
            return BillingResponseCode.ITEM_UNAVAILABLE
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        // After setProductDetails, which copies the first offer's token itself and would overwrite an
        // earlier call. Never an empty token: setOfferToken("") throws.
        bestOffer(product)?.offerToken?.takeIf { it.isNotEmpty() }?.let { productParams.setOfferToken(it) }
        launchEcho = null
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams.build())).build(),
        )
        val code = result.responseCode
        // ITEM_ALREADY_OWNED comes back through onPurchasesUpdated too, which re-queries Google Play.
        if (code != BillingResponseCode.OK && code != BillingResponseCode.ITEM_ALREADY_OWNED) launchEcho = code
        return code
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        scope.launch {
            val echo = launchEcho
            launchEcho = null
            if (purchases == null && result.responseCode != BillingResponseCode.OK && result.responseCode == echo) {
                return@launch
            }
            when (result.responseCode) {
                BillingResponseCode.OK -> {
                    if (purchases == null) {
                        syncNow()
                        return@launch
                    }
                    val owned = purchases.map { it.toOwned() }
                    if (owned.any { productId in it.productIds && it.state == OwnedPurchase.State.PURCHASED }) {
                        purchasedThisRun = true
                    }
                    val next = EntitlementRules.afterPurchaseUpdate(_state.value.entitlement, owned, productId)
                    setEntitlement(next, persist = next == Entitlement.UNLOCKED)
                    acknowledgeAll(EntitlementRules.toAcknowledge(owned, productId))
                    _outcomes.tryEmit(
                        when (next) {
                            Entitlement.UNLOCKED -> PurchaseOutcome.UNLOCKED
                            Entitlement.PENDING -> PurchaseOutcome.PENDING
                            else -> PurchaseOutcome.FAILED
                        },
                    )
                }
                BillingResponseCode.ITEM_ALREADY_OWNED -> onAlreadyOwned()
                BillingResponseCode.USER_CANCELED -> _outcomes.tryEmit(PurchaseOutcome.CANCELLED)
                else -> _outcomes.tryEmit(PurchaseOutcome.FAILED)
            }
        }
    }

    private suspend fun sync(): Boolean {
        applyCache()
        val setup = connect()
        if (setup.responseCode != BillingResponseCode.OK) {
            settle(EntitlementRules.storeStateFor(setup.responseCode, productFound = false))
            return false
        }
        // Details first: an OK answer is the weak "Play reached its servers" signal the relock guard
        // uses, and ProductDetails is re-read every sync because Google advises against keeping it long.
        val detailsOk = loadProductDetails()
        val (result, purchases) = queryPurchases()
        val queryOk = result.responseCode == BillingResponseCode.OK
        val owned = if (queryOk) purchases.map { it.toOwned() } else emptyList()
        if (owned.any { productId in it.productIds && it.state == OwnedPurchase.State.PURCHASED }) {
            purchasedThisRun = true
        }
        val next = EntitlementRules.afterQuery(_state.value.entitlement, queryOk, owned, detailsOk, purchasedThisRun, productId)
        setEntitlement(next, persist = queryOk)
        acknowledgeAll(EntitlementRules.toAcknowledge(owned, productId))
        return queryOk
    }

    // Play says the unlock is owned but this phone did not know: re-query (which also refreshes Play's
    // cache) and, if it is still missing, let the screen offer a plain retry.
    private suspend fun onAlreadyOwned() {
        syncNow()
        _outcomes.tryEmit(if (_state.value.unlocked) PurchaseOutcome.UNLOCKED else PurchaseOutcome.FAILED)
    }

    // Unacknowledged purchases come back from every queryPurchasesAsync, so each sync retries too.
    private fun acknowledgeAll(purchases: List<OwnedPurchase>) {
        for (purchase in purchases) {
            val token = purchase.token
            if (!acknowledging.add(token)) continue
            scope.launch {
                try {
                    var wait = ACK_FIRST_RETRY_MS
                    repeat(ACK_ATTEMPTS) { attempt ->
                        when (acknowledge(token).responseCode) {
                            BillingResponseCode.OK -> return@launch
                            // Play's purchase cache may be stale: query again now, once per purchase.
                            BillingResponseCode.ITEM_NOT_OWNED -> {
                                if (requeriedAfterNotOwned.add(token)) refresh()
                                return@launch
                            }
                            BillingResponseCode.ERROR,
                            BillingResponseCode.SERVICE_DISCONNECTED,
                            BillingResponseCode.SERVICE_UNAVAILABLE,
                            BillingResponseCode.NETWORK_ERROR,
                            -> if (attempt < ACK_ATTEMPTS - 1) {
                                delay(wait)
                                wait *= 2
                            }
                            else -> return@launch
                        }
                    }
                } finally {
                    acknowledging.remove(token)
                }
            }
        }
    }

    private suspend fun applyCache() {
        if (cache.await()) {
            _state.update { if (it.entitlement == Entitlement.UNKNOWN) it.copy(entitlement = Entitlement.UNLOCKED) else it }
        }
    }

    private suspend fun setEntitlement(entitlement: Entitlement, persist: Boolean) {
        _state.update { it.copy(entitlement = entitlement) }
        if (persist) store.setUnlocked(entitlement == Entitlement.UNLOCKED)
    }

    private fun settle(storeState: StoreState) {
        _state.update { it.copy(entitlement = EntitlementRules.settle(it.entitlement), store = storeState) }
    }

    private suspend fun connect(): BillingResult {
        if (setupDone) return OK_RESULT
        if (setupBroken) return UNAVAILABLE_RESULT
        val pending = setup?.takeIf { it.isActive } ?: scope.async {
            startConnection().also { if (it.responseCode == BillingResponseCode.OK) setupDone = true }
        }.also { setup = it }
        return pending.await()
    }

    private suspend fun startConnection(): BillingResult =
        suspendCancellableCoroutine { cont ->
            try {
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (cont.isActive) cont.resume(billingResult)
                    }

                    // Automatic reconnection re-binds on the next call; this only stops a sync that is still
                    // waiting for setup from hanging. Never call startConnection from here.
                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resume(DISCONNECTED_RESULT)
                    }
                })
            } catch (e: RuntimeException) {
                // queryIntentServices or bindService threw inside the library: never crash onResume over it.
                setupBroken = true
                if (cont.isActive) cont.resume(UNAVAILABLE_RESULT)
            }
        }

    private suspend fun loadProductDetails(): Boolean {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val (result, found) = suspendCancellableCoroutine<Pair<BillingResult, ProductDetails?>> { cont ->
            client.queryProductDetailsAsync(params) { billingResult, queryResult ->
                // An unfetched product (PRODUCT_NOT_FOUND, NO_ELIGIBLE_OFFER) is simply absent here.
                val product = queryResult.productDetailsList.firstOrNull { it.productId == productId }
                if (cont.isActive) cont.resume(billingResult to product)
            }
        }
        val ok = result.responseCode == BillingResponseCode.OK
        if (ok) details = found
        _state.update {
            it.copy(
                store = EntitlementRules.storeStateFor(result.responseCode, productFound = found != null),
                price = details?.let(::bestOffer)?.formattedPrice,
            )
        }
        return ok
    }

    // Only currently owned, non-consumed one-time purchases (PURCHASED and PENDING).
    private suspend fun queryPurchases(): Pair<BillingResult, List<Purchase>> =
        suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) { result, list ->
                if (cont.isActive) cont.resume(result to list)
            }
        }

    private suspend fun acknowledge(token: String): BillingResult = suspendCancellableCoroutine { cont ->
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
            if (cont.isActive) cont.resume(result)
        }
    }

    private fun Purchase.toOwned() = OwnedPurchase(
        productIds = products,
        state = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> OwnedPurchase.State.PURCHASED
            Purchase.PurchaseState.PENDING -> OwnedPurchase.State.PENDING
            else -> OwnedPurchase.State.OTHER
        },
        acknowledged = isAcknowledged,
        token = purchaseToken,
    )

    private companion object {
        const val SYNC_TIMEOUT_MS = 30_000L
        const val ACK_ATTEMPTS = 3
        const val ACK_FIRST_RETRY_MS = 2_000L
        val OK_RESULT: BillingResult = BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build()
        val UNAVAILABLE_RESULT: BillingResult =
            BillingResult.newBuilder().setResponseCode(BillingResponseCode.BILLING_UNAVAILABLE).build()
        val DISCONNECTED_RESULT: BillingResult =
            BillingResult.newBuilder().setResponseCode(BillingResponseCode.SERVICE_DISCONNECTED).build()
    }
}

/**
 * The offer to sell: the cheapest eligible entry of getOneTimePurchaseOfferDetailsList() (non-null in
 * 9.1.0 for a product with an offer), else getOneTimePurchaseOfferDetails(). Both are @Nullable.
 */
internal fun bestOffer(details: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? =
    details.oneTimePurchaseOfferDetailsList?.minByOrNull { it.priceAmountMicros }
        ?: details.oneTimePurchaseOfferDetails

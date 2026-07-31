package com.shelfie.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.shelfie.core.datastore.ShelfiePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Billing wrapper for the single one-time unlock.
 *
 * Two things worth knowing:
 *
 *  - **This does not need the INTERNET permission.** The Billing Library talks to
 *    the installed Play Store app over IPC and the Play Store performs the
 *    network calls. Verified against the library's own AAR manifest, which
 *    declares only `com.android.vending.BILLING`.
 *  - **Entitlement is cached in DataStore**, so a paid user keeps their unlock
 *    with no Play Store connection at all. Play remains the source of truth and
 *    refreshes the cache whenever it is reachable.
 */
@Singleton
class ShelfieBilling @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: ShelfiePreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Connecting)
    val billingState = _billingState.asStateFlow()

    /**
     * The entitlement the app actually acts on: the locally cached flag.
     *
     * Deliberately not gated on a live Play connection — offline users must not
     * lose a feature they paid for.
     */
    val entitlement: Flow<Entitlement> =
        preferences.isFullVersion.map { Entitlement(isFullVersion = it) }

    private var productDetails: ProductDetails? = null
    private var pendingPurchase: CompletableDeferred<PurchaseResult>? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        scope.launch { handlePurchaseUpdate(result, purchases) }
    }

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
    }

    /** Connects, refreshes the entitlement, and loads the price. Safe to re-call. */
    suspend fun initialise() = connectionMutex.withLock {
        if (!connect()) return@withLock

        refreshPurchases()
        loadProductDetails()
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true

        val connected = CompletableDeferred<Boolean>()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                if (!ok) {
                    _billingState.value = BillingState.Unavailable(
                        result.debugMessage.ifBlank { "Play Billing unavailable" },
                    )
                }
                if (!connected.isCompleted) connected.complete(ok)
            }

            override fun onBillingServiceDisconnected() {
                if (!connected.isCompleted) connected.complete(false)
            }
        })
        return runCatching { connected.await() }.getOrDefault(false)
    }

    /**
     * Re-reads ownership from Play. This is the "restore purchases" path — there
     * is nothing for the user to do manually, it just happens on launch.
     */
    suspend fun refreshPurchases() {
        if (!client.isReady && !connect()) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val purchases = CompletableDeferred<List<Purchase>>()
        client.queryPurchasesAsync(params) { _, list ->
            if (!purchases.isCompleted) purchases.complete(list)
        }

        val owned = runCatching { purchases.await() }.getOrDefault(emptyList())
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .any { ShelfieProducts.FULL_VERSION in it.products }

        preferences.setFullVersion(owned)
        if (owned) {
            _billingState.value = BillingState.Owned
            // Acknowledge anything Play reports as unacknowledged: an
            // unacknowledged purchase is auto-refunded after three days.
            runCatching { purchases.await() }.getOrDefault(emptyList())
                .filter { !it.isAcknowledged }
                .forEach { acknowledge(it) }
        }
    }

    private suspend fun loadProductDetails() {
        if (_billingState.value is BillingState.Owned) return

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ShelfieProducts.FULL_VERSION)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        val details = CompletableDeferred<List<ProductDetails>>()
        client.queryProductDetailsAsync(params) { _, result ->
            if (!details.isCompleted) details.complete(result.productDetailsList)
        }

        val product = runCatching { details.await() }.getOrDefault(emptyList()).firstOrNull()
        productDetails = product

        _billingState.value = if (product == null) {
            BillingState.Unavailable("Product not available")
        } else {
            BillingState.Available(formattedPrice = product.formattedPriceOrFallback())
        }
    }

    /** Launches the purchase flow. Suspends until Play reports an outcome. */
    suspend fun purchase(activity: Activity): PurchaseResult {
        val product = productDetails
            ?: return PurchaseResult.Failed("Purchase is not available right now")

        val deferred = CompletableDeferred<PurchaseResult>()
        pendingPurchase = deferred

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    // One-time products need no offer token.
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()

        val launch = client.launchBillingFlow(activity, params)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            return PurchaseResult.Failed(
                launch.debugMessage.ifBlank { "Could not start the purchase" },
            )
        }
        return runCatching { deferred.await() }
            .getOrDefault(PurchaseResult.Failed("Purchase interrupted"))
    }

    private suspend fun handlePurchaseUpdate(result: BillingResult, purchases: List<Purchase>?) {
        val outcome = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val relevant = purchases.orEmpty()
                    .filter { ShelfieProducts.FULL_VERSION in it.products }

                when {
                    relevant.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> {
                        relevant.filter { !it.isAcknowledged }.forEach { acknowledge(it) }
                        preferences.setFullVersion(true)
                        _billingState.value = BillingState.Owned
                        PurchaseResult.Success
                    }

                    relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                        // Deferred payment methods are common in India. Do not
                        // grant the unlock until Play confirms.
                        PurchaseResult.Pending

                    else -> PurchaseResult.Failed("Purchase not completed")
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refreshPurchases()
                PurchaseResult.AlreadyOwned
            }

            else -> PurchaseResult.Failed(
                result.debugMessage.ifBlank { "Purchase failed" },
            )
        }

        pendingPurchase?.let { if (!it.isCompleted) it.complete(outcome) }
        pendingPurchase = null
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { /* Retried on the next refresh. */ }
    }

    private fun ProductDetails.formattedPriceOrFallback(): String =
        runCatching { oneTimePurchaseOfferDetails?.formattedPrice }.getOrNull().orEmpty()
            .ifBlank { "Unlock" }
}

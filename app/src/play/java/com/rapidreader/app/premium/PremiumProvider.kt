package com.rapidreader.app.premium

import android.app.Activity
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Managed one-time product, created in the Play Console. Renaming it later is impossible. */
private const val PRODUCT_ID = "premium_unlock"
private const val PREFS = "premium"
private const val KEY_ENTITLED = "entitled"
private const val TAG = "Premium"

/**
 * Play Billing entitlement for the `play` flavour.
 *
 * There is no server to verify against — the app has no backend and is
 * otherwise fully offline — so entitlement is whatever Play reports for this
 * account, cached to disk. That is the standard arrangement for a one-time
 * unlock with no server component; it is not proof against a determined
 * attacker, and buying a server just to defend a single cheap product would
 * cost more than the leakage.
 */
private class BillingPremium(context: Context) : PremiumSource, PurchasesUpdatedListener {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Seeded from disk on purpose: this app works with no network at all, and
    // someone who has paid must not be shown a paywall on a plane just because
    // Play is unreachable.
    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_ENTITLED, false))
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    override val price: StateFlow<String?> = _price.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val client = BillingClient.newBuilder(app)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        connect()
    }

    override fun refresh() = connect()

    private fun connect() {
        if (client.isReady) {
            queryEntitlement()
            queryPrice()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryEntitlement()
                    queryPrice()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            // Left to the next refresh() rather than retried in a loop — the
            // cached entitlement already keeps a paying user unlocked.
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Restores the purchase on a reinstall or a second device. */
    private fun queryEntitlement() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any { it.isUnlockOf(PRODUCT_ID) }
            purchases.filter { it.isUnlockOf(PRODUCT_ID) }.forEach(::acknowledge)
            setEntitled(owned)
        }
    }

    private fun queryPrice() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            productDetails = details.productDetailsList.firstOrNull()
            _price.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    override fun launchPurchase(activity: Activity) {
        val details = productDetails ?: run {
            // Price never arrived, so there is nothing to buy yet. Try again.
            connect()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().filter { it.isUnlockOf(PRODUCT_ID) }.forEach {
                    acknowledge(it)
                    setEntitled(true)
                }
            }
            // The user backing out is not an error worth surfacing.
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> Log.w(TAG, "Purchase failed: ${result.debugMessage}")
        }
    }

    /**
     * Play refunds anything left unacknowledged for three days, so this is not
     * optional — a purchase that is never acknowledged silently reverses.
     */
    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    private fun setEntitled(value: Boolean) {
        _isPremium.value = value
        prefs.edit().putBoolean(KEY_ENTITLED, value).apply()
    }
}

/** PENDING purchases (cash, slow cards) must not unlock anything until they clear. */
private fun Purchase.isUnlockOf(productId: String): Boolean =
    purchaseState == Purchase.PurchaseState.PURCHASED && products.contains(productId)

object PremiumProvider {
    fun create(context: Context): PremiumSource = BillingPremium(context)
}

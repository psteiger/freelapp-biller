package com.freelapp.components.biller.android.impl

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.freelapp.components.biller.entity.purchase.BillingFlow
import com.freelapp.components.biller.android.impl.ktx.*
import com.freelapp.components.biller.android.impl.ktx.isOk
import com.freelapp.components.biller.android.impl.ktx.querySkuDetails
import com.freelapp.components.biller.entity.purchase.PurchaseState
import com.freelapp.components.biller.entity.sku.AcknowledgeableSku
import com.freelapp.components.biller.entity.sku.ConsumableSku
import com.freelapp.components.biller.entity.sku.SkuContract
import com.freelapp.components.biller.entity.sku.SkuType
import com.freelapp.flowlifecycleobserver.observe
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BillerImpl(
    context: Context,
    private val acknowledgeableSkus: Set<AcknowledgeableSku> = emptySet(),
    private val consumableSkus: Set<ConsumableSku> = emptySet()
) : PurchaseState.Owner {

    override val purchaseState: PurchaseState = PurchaseStateImpl()

    private val skuDetailsMap: MutableMap<SkuContract, SkuDetails> = mutableMapOf()

    /** Can come from internally launched billing flows or outside sources (Play Store). */
    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                ProcessLifecycleOwner.get().lifecycleScope.launch {
                    val purchaseSet = purchases.orEmpty().toSet()
                    purchaseSet
                        .associateBy { acknowledgeableSkus.getBySku(it.sku) }
                        .filterKeys { it != null }
                        .mapKeys { (sku, _) -> sku as AcknowledgeableSku }
                        .forEach { (sku, purchase) ->
                            if (billingClient.acknowledge(purchase)) {
                                purchaseState.addAcknowledged(sku)
                            }
                        }

                    purchaseSet
                        .associateBy { consumableSkus.getBySku(it.sku) }
                        .filterKeys { it != null }
                        .mapKeys { (sku, _) -> sku as ConsumableSku }
                        .forEach { (sku, purchase) ->
                            if (billingClient.consume(purchase)) launch {
                                purchaseState.addConsumed(sku)
                            }
                        }
                }
        }
    }

    private val clientStateListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
            billingClientStatus.tryEmit(result.responseCode)
        }

        override fun onBillingServiceDisconnected() {
            billingClientStatus.tryEmit(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        }
    }

    private val billingClient: BillingClient =
        BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

    private val billingClientStatus = MutableSharedFlow<Int>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )


    private suspend fun BillingClient.updateSkuPrices() {
        setOf(SkuType.SUBS, SkuType.INAPP).forEach { updateSkuPrices(it) }
    }

    private suspend fun BillingClient.updateSkuPrices(skuType: SkuType) {
        val skus = (acknowledgeableSkus + consumableSkus)
            .filter { it.type == skuType }
            .toSet()
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(skus.map { it.sku })
            .setType(skuType.toBillingClientSkuType())
            .build()

        with (querySkuDetails(params)) {
            if (result.isOk()) {
                skuDetailsList?.forEach { skuDetails ->
                    skus.getBySku(skuDetails.sku)?.let {
                        it.price.value = skuDetails.price
                        skuDetailsMap[it] = skuDetails
                    }
                }
            }
        }
    }

    /**
     * @return whether the flow was launched successfully (not whether the item was purchased
     *         successfully)
     */
    private suspend fun launchBillingFlow(activity: Activity, sku: SkuContract): Boolean {
        val skuDetails = skuDetailsMap[sku] ?: return false
        val params = BillingFlowParams
            .newBuilder()
            .setSkuDetails(skuDetails)
            .build()
        if (!requireBillingClientSetup()) return false
        return billingClient.launchBillingFlow(activity, params).isOk()
    }

    private suspend fun requireBillingClientSetup(): Boolean =
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            billingClientStatus.first { it == BillingClient.BillingResponseCode.OK }
            true
        } ?: false

    init {
        billingClientStatus.tryEmit(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        billingClientStatus.observe(ProcessLifecycleOwner.get()) {
            when (it) {
                BillingClient.BillingResponseCode.OK -> {
                    billingClient.updateSkuPrices()
                    val (acknowledged, consumed) = billingClient.queryAcknowledgeAndConsumePurchases(
                        acknowledgeableSkus + consumableSkus
                    )
                    purchaseState.addAcknowledged(*acknowledged.toTypedArray())
                    purchaseState.addConsumed(*consumed.toTypedArray())
                }
                else -> {
                    delay(RETRY_MILLIS)
                    billingClient.startConnection(clientStateListener)
                }
            }
        }
    }

    inner class BillingFlowImpl(private val activity: Activity) : BillingFlow {
        override suspend fun invoke(sku: SkuContract): Boolean = launchBillingFlow(activity, sku)
    }

    private companion object {
        private const val TIMEOUT_MILLIS = 2000L
        private const val RETRY_MILLIS = 3000L
    }
}
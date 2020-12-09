package com.freelapp.components.biller.android.impl

import android.app.Activity
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
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
import com.freelapp.flowlifecycleobserver.observeIn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Singleton

@ExperimentalCoroutinesApi
@Singleton
class BillerImpl(
    @ApplicationContext context: Context,
    private val acknowledgeableSkus: Set<@JvmSuppressWildcards AcknowledgeableSku>,
    private val consumableSkus: Set<@JvmSuppressWildcards ConsumableSku>
) : PurchaseState.Owner,
    DefaultLifecycleObserver {

    override val purchaseState: PurchaseState = PurchaseStateImpl()

    private val purchasesUpdatedListenerResult = MutableStateFlow<PurchasesUpdatedListenerResult?>(null)

    /** Can come from internally launched billing flows or outside sources (Play Store). */
    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        purchasesUpdatedListenerResult.value = PurchasesUpdatedListenerResult(result, purchases)
    }

    private val billingClient: BillingClient =
        BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

    private val connectionManager = ConnectionManagerImpl(billingClient)

    private val skuDetails: StateFlow<List<SkuDetails>> =
        connectionManager
            .connectionStatus
            .filter { it == BillingClient.BillingResponseCode.OK }
            .map { billingClient.querySkuDetails(acknowledgeableSkus + consumableSkus) }
            .onEach { skuDetailList ->
                skuDetailList.forEach {
                    (acknowledgeableSkus + consumableSkus).getBySku(it.sku)?.let { sku ->
                        sku.price.value = it.price
                        sku.paymentPeriod.value = it.paymentPeriod
                    }
                }
            }
            .stateIn(
                ProcessLifecycleOwner.get().lifecycleScope,
                SharingStarted.Eagerly,
                emptyList()
            )

    /**
     * @return whether the flow was launched successfully (not whether the item was purchased
     *         successfully)
     */
    private suspend fun launchBillingFlow(activity: Activity, sku: SkuContract): Boolean {
        val skuDetails = skuDetails.value.firstOrNull { it.sku == sku.sku } ?: return false
        val params = BillingFlowParams
            .newBuilder()
            .setSkuDetails(skuDetails)
            .build()
        if (!requireBillingClientSetup()) return false
        return billingClient.launchBillingFlow(activity, params).isOk()
    }

    private suspend fun requireBillingClientSetup(): Boolean =
        withTimeoutOrNull(TIMEOUT_MILLIS) {
            connectionManager
                .connectionStatus
                .first { it == BillingClient.BillingResponseCode.OK }
            true
        } ?: false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        purchasesUpdatedListenerResult
            .filterNotNull()
            .onEach { (result, purchases) ->
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK,
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                        val purchasesMap =
                            purchases
                                .orEmpty()
                                .toSet()
                                .associateBy { (acknowledgeableSkus + consumableSkus).getBySku(it.sku) }
                                .filterKeys { it != null }

                        purchasesMap
                            .filterKeys { it is AcknowledgeableSku }
                            .forEach { (sku, purchase) ->
                                if (billingClient.acknowledge(purchase)) {
                                    purchaseState.addAcknowledged(sku as AcknowledgeableSku)
                                }
                            }

                        purchasesMap
                            .filterKeys { it is ConsumableSku }
                            .forEach { (sku, purchase) ->
                                if (billingClient.consume(purchase))
                                    purchaseState.addConsumed(sku as ConsumableSku)
                            }
                    }
                }
            }
            .observeIn(ProcessLifecycleOwner.get())

        connectionManager
            .connectionStatus
            .filter { it == BillingClient.BillingResponseCode.OK }
            .onEach {
                val (acknowledged, consumed) = billingClient.queryAcknowledgeAndConsumePurchases(
                    acknowledgeableSkus + consumableSkus
                )
                purchaseState.addAcknowledged(*acknowledged.toTypedArray())
                purchaseState.addConsumed(*consumed.toTypedArray())
            }
            .observeIn(ProcessLifecycleOwner.get())
    }

    inner class BillingFlowImpl(private val activity: Activity) : BillingFlow {
        override suspend fun invoke(sku: SkuContract): Boolean = launchBillingFlow(activity, sku)
    }

    private companion object {
        private const val TIMEOUT_MILLIS = 2000L
    }
}
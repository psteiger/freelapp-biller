package com.freelapp.components.biller.android.impl.impl

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.*
import com.freelapp.components.biller.android.impl.BillingClientOwner
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
import javax.inject.Inject
import javax.inject.Singleton

@ExperimentalCoroutinesApi
@Singleton
class BillerImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val acknowledgeableSkus: Set<@JvmSuppressWildcards AcknowledgeableSku>,
    private val consumableSkus: Set<@JvmSuppressWildcards ConsumableSku>
) : PurchaseState.Owner {

    override val purchaseState: PurchaseState = PurchaseStateImpl()
    private val billingClientOwner: BillingClientOwner = BillingClientOwnerImpl(context)

    private val billingConnection =
        billingClientOwner
            .billingClient
            .filterNotNull()
            .flatMapLatest { billingClient ->
                billingClient
                    .connectionAsFlow()
                    .retry {
                        delay(RETRY_MILLIS)
                        true
                    }
            }
            .shareIn(
                ProcessLifecycleOwner.get().lifecycleScope,
                SharingStarted.Eagerly,
                replay = 1
            )

    private val skuDetails: StateFlow<List<SkuDetails>> =
        billingClientOwner
            .billingClient
            .waitUntilReady()
            .map { it.querySkuDetails(acknowledgeableSkus + consumableSkus) }
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
    internal suspend fun launchBillingFlow(activity: Activity, sku: SkuContract): Boolean {
        val skuDetails = skuDetails.value.firstOrNull { it.sku == sku.sku } ?: return false
        val params = BillingFlowParams
            .newBuilder()
            .setSkuDetails(skuDetails)
            .build()
        return billingClientOwner
            .billingClient
            .waitUntilReady(TIMEOUT_MILLIS)
            .firstOrNull()
            ?.launchBillingFlow(activity, params)
            ?.isOk()
            ?: false
    }

    private fun observeNewPurchases() =
        billingClientOwner
            .purchasesUpdatedListenerResult
            .filterBillingResultOk()
            .combine(billingClientOwner.billingClient.waitUntilReady()) { purchases, billingClient ->
                billingClient to purchases
            }
            .onEach { (billingClient, purchases) ->
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
            .observeIn(ProcessLifecycleOwner.get())

    private fun queryAcknowledgeAndConsumePurchases() =
        billingClientOwner
            .billingClient
            .waitUntilReady()
            .onEach {
                val (acknowledged, consumed) = it.queryAcknowledgeAndConsumePurchases(
                    acknowledgeableSkus + consumableSkus
                )
                purchaseState.addAcknowledged(*acknowledged.toTypedArray())
                purchaseState.addConsumed(*consumed.toTypedArray())
            }
            .observeIn(ProcessLifecycleOwner.get())

    init {
        observeNewPurchases()
        queryAcknowledgeAndConsumePurchases()
    }

    private companion object {
        private const val TIMEOUT_MILLIS = 2000L
        private const val RETRY_MILLIS = 3000L
    }
}
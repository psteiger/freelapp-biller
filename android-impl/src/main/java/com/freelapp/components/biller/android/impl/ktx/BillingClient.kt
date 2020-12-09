package com.freelapp.components.biller.android.impl.ktx

import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.SkuType.INAPP
import com.android.billingclient.api.BillingClient.SkuType.SUBS
import com.freelapp.components.biller.entity.sku.AcknowledgeableSku
import com.freelapp.components.biller.entity.sku.ConsumableSku
import com.freelapp.components.biller.entity.sku.SkuContract
import com.freelapp.components.biller.entity.sku.SkuType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume

@ExperimentalCoroutinesApi
internal suspend fun BillingClient.connectionAsFlow() =
    callbackFlow<Int> {
        val listener = object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val responseCode = result.responseCode
                runCatching { offer(responseCode) }
                if (responseCode != BillingClient.BillingResponseCode.OK) {
                    cancel(CancellationException("onBillingSetupFinished failed with responseCode=$responseCode"))
                }
            }
            override fun onBillingServiceDisconnected() {
                runCatching { offer(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED) }
                cancel(CancellationException("onBillingServiceDisconnected"))
            }
        }
        startConnection(listener)
        awaitClose { endConnection() }
    }

internal suspend fun BillingClient.acknowledge(purchase: Purchase): Boolean {
    return when (purchase.purchaseState) {
        Purchase.PurchaseState.PURCHASED -> {
            if (purchase.isAcknowledged) return true
            val params =
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            acknowledgePurchase(params).isOk()
        }
        Purchase.PurchaseState.PENDING -> {
            // Here you can confirm to the user that they've started the pending
            // purchase, and to complete it, they should follow instructions that
            // are given to them. You can also choose to remind the user in the
            // future to complete the purchase if you detect that it is still
            // pending.
            false
        }
        else -> false
    }
}

internal suspend fun BillingClient.consume(purchase: Purchase): Boolean {
    return when (purchase.purchaseState) {
        Purchase.PurchaseState.PURCHASED -> {
            val params =
                ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            consumePurchase(params).result.isOk()
        }
        Purchase.PurchaseState.PENDING -> {
            // Here you can confirm to the user that they've started the pending
            // purchase, and to complete it, they should follow instructions that
            // are given to them. You can also choose to remind the user in the
            // future to complete the purchase if you detect that it is still
            // pending.
            false
        }
        else -> false
    }
}

data class QueryResult(
    val acknowledged: Set<AcknowledgeableSku> = setOf(),
    val consumed: Set<ConsumableSku> = setOf()
)

internal suspend fun BillingClient.acknowledgeOrConsume(
    purchase: Purchase,
    sku: SkuContract
): Boolean =
    when (sku) {
        is AcknowledgeableSku -> acknowledge(purchase)
        is ConsumableSku -> consume(purchase)
        else -> false
    }

internal fun BillingClient.queryAllPurchases(): List<Purchase> =
    setOf(INAPP, SUBS).flatMap { queryPurchases(it).purchasesList.orEmpty() }

internal suspend fun BillingClient.queryAcknowledgeAndConsumePurchases(
    skus: Set<SkuContract>
): QueryResult {
    val purchases = queryAllPurchases()
    val acknowledgedOrConsumed =
        purchases
            .mapNotNull {
                skus.getBySku(it.sku)?.let { sku -> sku to acknowledgeOrConsume(it, sku) }
            }
            .toMap()
            .filterValues { it }
            .keys

    val acknowledged = acknowledgedOrConsumed.filterIsInstance<AcknowledgeableSku>().toSet()
    val consumed = acknowledgedOrConsumed.filterIsInstance<ConsumableSku>().toSet()

    return QueryResult(acknowledged, consumed)
}

internal suspend fun BillingClient.querySkuDetails(skus: Set<SkuContract>): List<SkuDetails> =
    setOf(SkuType.SUBS, SkuType.INAPP)
        .map { querySkuDetails(skus, it) }
        .filter { (result, _) -> result.isOk() }
        .flatMap { (_, list) -> list.orEmpty() }

internal suspend fun BillingClient.querySkuDetails(
    skus: Set<SkuContract>,
    skuType: SkuType
): QuerySkuDetailsResult {
    val params = SkuDetailsParams.newBuilder()
        .setSkusList(
            skus
                .filter { it.type == skuType }
                .toSet()
                .map { it.sku }
        )
        .setType(skuType.toBillingClientSkuType())
        .build()
    return querySkuDetails(params)
}
// billing-ktx 3.0.1 conflicts with coroutines 4.0.10. implementing ourselves...

internal suspend fun BillingClient.querySkuDetails(
    params: SkuDetailsParams
): QuerySkuDetailsResult = suspendCancellableCoroutine {
    querySkuDetailsAsync(params) { result, skuDetails ->
        it.resume(QuerySkuDetailsResult(result, skuDetails))
    }
}

internal suspend fun BillingClient.acknowledgePurchase(
    params: AcknowledgePurchaseParams
): BillingResult = suspendCancellableCoroutine {
    acknowledgePurchase(params) { result ->
        it.resume(result)
    }
}

internal suspend fun BillingClient.consumePurchase(
    params: ConsumeParams
): ConsumeResult = suspendCancellableCoroutine {
    consumeAsync(params) { result, purchaseToken ->
        it.resume(ConsumeResult(result, purchaseToken))
    }
}

data class ConsumeResult(
    val result: BillingResult,
    val purchaseToken: String
)

data class QuerySkuDetailsResult(
    val result: BillingResult,
    val skuDetailsList: List<SkuDetails>?
)

data class PurchasesUpdatedListenerResult(
    val result: BillingResult,
    val purchases: List<Purchase>?
)
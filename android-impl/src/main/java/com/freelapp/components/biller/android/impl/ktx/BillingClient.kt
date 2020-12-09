package com.freelapp.components.biller.android.impl.ktx

import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.SkuType.INAPP
import com.android.billingclient.api.BillingClient.SkuType.SUBS
import com.freelapp.components.biller.entity.sku.AcknowledgeableSku
import com.freelapp.components.biller.entity.sku.ConsumableSku
import com.freelapp.components.biller.entity.sku.SkuContract
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
    val acknowledged =
        purchases
            .mapNotNull {
                skus.getBySku(it.sku)?.let { sku ->
                    sku to acknowledgeOrConsume(it, sku)
                }
            }
            .toMap()
            .filterValues { it }
            .mapKeys { it as AcknowledgeableSku }
            .keys
    val consumed =
        purchases
            .mapNotNull {
                skus.getBySku(it.sku)?.let { sku ->
                    sku to acknowledgeOrConsume(it, sku)
                }
            }
            .toMap()
            .filterValues { it }
            .mapKeys { it as ConsumableSku }
            .keys

    return QueryResult(acknowledged, consumed)
}

// billing-ktx 3.0.1 conflicts with coroutines 4.0.10. implementing ourselves...

internal suspend fun BillingClient.querySkuDetails(
    params: SkuDetailsParams
): QuerySkuDetailsResult = suspendCoroutine {
    querySkuDetailsAsync(params) { result, skuDetails ->
        it.resume(QuerySkuDetailsResult(result, skuDetails))
    }
}

internal suspend fun BillingClient.acknowledgePurchase(
    params: AcknowledgePurchaseParams
): BillingResult = suspendCoroutine {
    acknowledgePurchase(params) { result ->
        it.resume(result)
    }
}

internal suspend fun BillingClient.consumePurchase(
    params: ConsumeParams
): ConsumeResult = suspendCoroutine {
    consumeAsync(params) { result, purchaseToken ->
        it.resume(ConsumeResult(result, purchaseToken))
    }
}

data class ConsumeResult(
    val result: BillingResult,
    val purchaseToken: String
)

data class BillingFlowResult(
    val result: BillingResult,
    val purchases: List<Purchase>?
)

data class QuerySkuDetailsResult(
    val result: BillingResult,
    val skuDetailsList: List<SkuDetails>?
)
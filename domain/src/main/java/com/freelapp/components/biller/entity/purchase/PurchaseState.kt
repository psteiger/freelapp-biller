package com.freelapp.components.biller.entity.purchase

import com.freelapp.components.biller.entity.sku.AcknowledgeableSku
import com.freelapp.components.biller.entity.sku.ConsumableSku
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PurchaseState {
    val acknowledged: StateFlow<Set<AcknowledgeableSku>>
    val consumed: SharedFlow<ConsumableSku>
    fun addAcknowledged(vararg skus: AcknowledgeableSku)
    suspend fun addConsumed(vararg skus: ConsumableSku)

    interface Owner {
        val purchaseState: PurchaseState
    }
}
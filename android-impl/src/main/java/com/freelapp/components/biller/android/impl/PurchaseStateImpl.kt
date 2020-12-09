package com.freelapp.components.biller.android.impl

import com.freelapp.components.biller.entity.purchase.PurchaseState
import com.freelapp.components.biller.entity.sku.AcknowledgeableSku
import com.freelapp.components.biller.entity.sku.ConsumableSku
import kotlinx.coroutines.flow.*

class PurchaseStateImpl : PurchaseState {

    private val _acknowledged = MutableStateFlow<Set<AcknowledgeableSku>>(emptySet())
    override val acknowledged: StateFlow<Set<AcknowledgeableSku>> = _acknowledged.asStateFlow()

    private val _consumed = MutableSharedFlow<ConsumableSku>()
    override val consumed: SharedFlow<ConsumableSku> = _consumed.asSharedFlow()

    override fun addAcknowledged(vararg skus: AcknowledgeableSku) {
        _acknowledged.value += skus
    }

    override suspend fun addConsumed(vararg skus: ConsumableSku) {
        skus.forEach { _consumed.emit(it) }
    }
}
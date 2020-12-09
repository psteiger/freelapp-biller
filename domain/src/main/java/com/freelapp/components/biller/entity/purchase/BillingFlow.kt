package com.freelapp.components.biller.entity.purchase

import com.freelapp.components.biller.entity.sku.SkuContract

interface BillingFlow {
    suspend fun invoke(sku: SkuContract): Boolean
}
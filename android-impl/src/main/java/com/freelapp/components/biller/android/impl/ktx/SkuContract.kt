package com.freelapp.components.biller.android.impl.ktx

import com.freelapp.components.biller.entity.sku.SkuContract

internal fun <T : SkuContract> Set<T>?.getBySku(sku: String): T? =
    orEmpty().firstOrNull { it.sku == sku }
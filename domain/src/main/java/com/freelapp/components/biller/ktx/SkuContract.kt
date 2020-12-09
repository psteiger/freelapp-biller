package com.freelapp.components.biller.ktx

import com.freelapp.components.biller.entity.sku.AcknowledgeableSku
import com.freelapp.components.biller.entity.sku.ConsumableSku
import com.freelapp.components.biller.entity.sku.SkuContract

operator fun AcknowledgeableSku.plus(other: AcknowledgeableSku) = setOf(this, other)

operator fun ConsumableSku.plus(other: ConsumableSku) = setOf(this, other)

operator fun SkuContract.plus(other: SkuContract) = setOf(this, other)
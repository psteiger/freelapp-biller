package com.freelapp.components.biller.android.impl.ktx

import com.android.billingclient.api.BillingClient
import com.freelapp.components.biller.entity.sku.SkuType

fun SkuType.toBillingClientSkuType() = when (this) {
    SkuType.INAPP -> BillingClient.SkuType.INAPP
    SkuType.SUBS -> BillingClient.SkuType.SUBS
}
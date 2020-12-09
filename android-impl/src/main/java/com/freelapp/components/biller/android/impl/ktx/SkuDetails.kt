package com.freelapp.components.biller.android.impl.ktx

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.freelapp.components.biller.entity.sku.SkuContract

val SkuDetails.paymentPeriod get() =
    if (type == BillingClient.SkuType.SUBS) {
        when (subscriptionPeriod) {
            "P1D" -> SkuContract.Period.DAY
            "P1W" -> SkuContract.Period.WEEK
            "P1M" -> SkuContract.Period.MONTH
            "P1Y" -> SkuContract.Period.YEAR
            else -> SkuContract.Period.UNKNOWN
        }
    } else SkuContract.Period.ONCE
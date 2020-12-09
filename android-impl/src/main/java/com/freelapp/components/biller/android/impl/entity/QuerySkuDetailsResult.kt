package com.freelapp.components.biller.android.impl.entity

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.SkuDetails
import com.freelapp.components.biller.android.impl.BillingResultOwner

data class QuerySkuDetailsResult(
    override val result: BillingResult,
    val skuDetailsList: List<SkuDetails>?
) : BillingResultOwner<List<SkuDetails>?>
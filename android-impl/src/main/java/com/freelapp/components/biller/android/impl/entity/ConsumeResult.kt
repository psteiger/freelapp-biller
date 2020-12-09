package com.freelapp.components.biller.android.impl.entity

import com.android.billingclient.api.BillingResult
import com.freelapp.components.biller.android.impl.BillingResultOwner

data class ConsumeResult(
    override val result: BillingResult,
    val purchaseToken: String
) : BillingResultOwner<String>
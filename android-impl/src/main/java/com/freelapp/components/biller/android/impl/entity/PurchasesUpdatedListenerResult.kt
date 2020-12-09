package com.freelapp.components.biller.android.impl.entity

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.freelapp.components.biller.android.impl.BillingResultOwner

data class PurchasesUpdatedListenerResult(
    override val result: BillingResult,
    val purchases: List<Purchase>?
) : BillingResultOwner<List<Purchase>?>
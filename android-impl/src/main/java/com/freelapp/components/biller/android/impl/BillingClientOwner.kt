package com.freelapp.components.biller.android.impl

import com.android.billingclient.api.BillingClient
import com.freelapp.components.biller.android.impl.entity.PurchasesUpdatedListenerResult
import kotlinx.coroutines.flow.StateFlow

interface BillingClientOwner {
    val billingClient: StateFlow<BillingClient?>
    val purchasesUpdatedListenerResult: StateFlow<PurchasesUpdatedListenerResult?>
}
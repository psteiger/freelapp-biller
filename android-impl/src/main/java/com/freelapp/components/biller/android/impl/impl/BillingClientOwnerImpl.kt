package com.freelapp.components.biller.android.impl.impl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.freelapp.components.biller.android.impl.BillingClientOwner
import com.freelapp.components.biller.android.impl.entity.PurchasesUpdatedListenerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingClientOwnerImpl(
    private val context: Context
) : BillingClientOwner,
    DefaultLifecycleObserver {

    private val _billingClient = MutableStateFlow<BillingClient?>(null)
    override val billingClient = _billingClient.asStateFlow()

    private val _purchasesUpdatedListenerResult = MutableStateFlow<PurchasesUpdatedListenerResult?>(null)
    override val purchasesUpdatedListenerResult = _purchasesUpdatedListenerResult.asStateFlow()

    /** Can come from internally launched billing flows or outside sources (Play Store). */
    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        _purchasesUpdatedListenerResult.value = PurchasesUpdatedListenerResult(result, purchases)
    }

    override fun onCreate(owner: LifecycleOwner) {
        _billingClient.value = BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        _billingClient.value?.endConnection()
        _billingClient.value = null
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
}
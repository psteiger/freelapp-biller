package com.freelapp.components.biller.android.impl

import android.app.Activity
import com.freelapp.components.biller.entity.purchase.BillingFlow
import com.freelapp.components.biller.entity.sku.SkuContract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@ExperimentalCoroutinesApi
class BillingFlowImpl @Inject constructor(
    private val biller: BillerImpl,
    private val activity: Activity
) : BillingFlow {

    override suspend fun invoke(sku: SkuContract): Boolean = biller.launchBillingFlow(activity, sku)
}
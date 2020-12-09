package com.freelapp.components.biller.android.domain

import android.app.Activity
import com.freelapp.components.biller.entity.sku.SkuContract

interface BillerFlowLauncher {
    suspend fun launchBillingFlow(activity: Activity, sku: SkuContract): Boolean
}
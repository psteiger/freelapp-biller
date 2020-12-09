package com.freelapp.components.biller.android.impl.ktx

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult

internal fun BillingResult.isOk(): Boolean = responseCode == BillingClient.BillingResponseCode.OK

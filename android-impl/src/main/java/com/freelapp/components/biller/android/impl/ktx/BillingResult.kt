package com.freelapp.components.biller.android.impl.ktx

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.freelapp.components.biller.android.impl.BillingResultOwner
import kotlinx.coroutines.flow.*

internal fun BillingResult.isOk(): Boolean = responseCode == BillingClient.BillingResponseCode.OK

internal fun <T> BillingResultOwner<T>.isBillingResultOk() = result.isOk()

internal fun <U, T : BillingResultOwner<U>> Flow<T?>.filterBillingResultOk(): Flow<U> =
    filterNotNull()
        .filter { it.isBillingResultOk() }
        .map { it.component2() }
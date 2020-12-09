package com.freelapp.components.biller.android.impl

import com.android.billingclient.api.BillingResult

interface BillingResultOwner<T> {
    val result: BillingResult
    fun component2(): T
}
package com.freelapp.components.biller.entity.sku

import kotlinx.coroutines.flow.MutableStateFlow

interface SkuContract {
    val sku: String
    val price: MutableStateFlow<String> // TODO should expose only as StateFlow
    val type: SkuType
    val paymentPeriod: MutableStateFlow<Period> // TODO should expose only as StateFlow

    enum class Period { ONCE, DAY, WEEK, MONTH, YEAR, UNKNOWN }
}


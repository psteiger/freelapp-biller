package com.freelapp.components.biller.entity.sku

import kotlinx.coroutines.flow.MutableStateFlow

interface SkuContract {
    val sku: String
    val price: MutableStateFlow<String> // TODO should expose only as StateFlow
    val type: SkuType
}


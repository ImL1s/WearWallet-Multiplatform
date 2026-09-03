package com.cbstudio.wearwallet.core.domain.model

import kotlinx.serialization.Serializable

/**
 * 錢包餘額資訊
 */
@Serializable
data class Balance(
    val amount: String,
    val currency: String = "USD",
    val change24h: Double? = null,
    val changePercentage24h: Double? = null
)
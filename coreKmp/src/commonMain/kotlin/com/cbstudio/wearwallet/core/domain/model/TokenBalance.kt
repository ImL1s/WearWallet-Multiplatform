package com.cbstudio.wearwallet.core.domain.model

import kotlinx.serialization.Serializable

/**
 * 代幣餘額資訊
 */
@Serializable
data class TokenBalance(
    val token: Token,
    val balance: String,
    val usdValue: Double = 0.0,
    val formattedBalance: String = "",
    val priceChange24h: Double? = null
) {
    companion object {
        fun fromToken(token: Token): TokenBalance {
            return TokenBalance(
                token = token,
                balance = token.balance,
                usdValue = token.usdValue,
                formattedBalance = token.displayBalance,
                priceChange24h = null
            )
        }
    }
}
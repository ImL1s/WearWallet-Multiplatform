package com.cbstudio.wearwallet.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
data class Token(
    val id: String = "",
    val address: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val chainType: ChainType,
    val logoUrl: String? = null,
    val balance: String = "0",
    val usdPrice: Double? = null,
    val isNative: Boolean = false
) {
    val displayBalance: String
        get() = formatBalance(balance, decimals)
    
    val usdValue: Double
        get() = usdPrice?.let { 
            val balanceDouble = balance.toDoubleOrNull() ?: 0.0
            val divisor = 10.0.pow(decimals)
            (balanceDouble / divisor) * it
        } ?: 0.0
    
    companion object {
        fun formatBalance(balance: String, decimals: Int): String {
            return try {
                val balanceDouble = balance.toDoubleOrNull() ?: return "0"
                val divisor = 10.0.pow(decimals)
                val result = balanceDouble / divisor
                result.toString()
            } catch (e: Exception) {
                "0"
            }
        }
    }
}
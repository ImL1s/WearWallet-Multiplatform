package com.cbstudio.wearwallet.core.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * 交易費用估算結果
 * 
 * Created: 2025-01-17
 */
data class TransactionEstimate(
    val gasLimit: String,
    val gasPrice: String,
    val maxFeePerGas: String? = null,
    val maxPriorityFeePerGas: String? = null,
    val estimatedFee: String,
    val estimatedFeeInUSD: String? = null,
    val chainType: ChainType,
    val nonce: Long,
    val estimatedTime: String = "15-30 seconds",
    val warning: String? = null,
    val timestamp: Instant = Clock.System.now()
) {
    /**
     * 是否為 EIP-1559 交易
     */
    val isEIP1559: Boolean
        get() = maxFeePerGas != null && maxPriorityFeePerGas != null
    
    /**
     * 獲取格式化的費用顯示
     */
    fun getFormattedFee(): String {
        return buildString {
            append(estimatedFee)
            append(" ")
            append(chainType.nativeToken)
            
            if (!estimatedFeeInUSD.isNullOrBlank()) {
                append(" (≈$")
                append(estimatedFeeInUSD)
                append(")")
            }
        }
    }
    
    /**
     * 獲取 Gas 價格顯示（Gwei）
     */
    fun getGasPriceInGwei(): String {
        return if (isEIP1559) {
            "Max: ${maxFeePerGas ?: "0"} Gwei"
        } else {
            "$gasPrice Gwei"
        }
    }
}
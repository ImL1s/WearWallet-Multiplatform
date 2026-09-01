package com.cbstudio.wearwallet.domain.model

/**
 * Coin - MAINTENANCE MODE
 * ULTRATHINK Phase 16 - 激進類型簡化策略
 */
data class Coin(
    val symbol: String,
    val name: String,
    val address: String? = null,
    val decimals: Int = 18,
    val chainId: Int = 1
)
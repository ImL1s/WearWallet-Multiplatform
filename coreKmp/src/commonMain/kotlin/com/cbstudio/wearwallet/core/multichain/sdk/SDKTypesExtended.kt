package com.cbstudio.wearwallet.core.multichain.sdk

/**
 * 擴展的 SDK 類型定義
 * 
 * 為了保持兼容性，這裡定義了一些額外的類型和擴展屬性
 */

/**
 * UnsignedTransaction 擴展屬性
 */
val UnsignedTransaction.transactionId: String?
    get() = null // UnsignedTransaction 沒有 hash

/**
 * TransactionResult 擴展屬性
 */
val TransactionResult.transactionHash: String
    get() = this.hash

val TransactionResult.confirmations: Int
    get() = 1 // 預設值

/**
 * NetworkStatus 擴展屬性
 */
val NetworkStatus.latency: Long
    get() = 100L // 預設延遲

val NetworkStatus.peerCount: Int
    get() = this.peersCount ?: 0

/**
 * 簡化的 TransactionFee 創建方法
 */
fun TransactionFee(
    amount: String,
    symbol: String,
    priority: TransactionPriority,
    estimatedTime: Long
): TransactionFee {
    return TransactionFee(
        gasLimit = "21000",
        gasPrice = amount,
        estimatedCost = amount,
        usdValue = null,
        priority = priority
    )
}

// SDKCapability 已在 SDKAdapter.kt 中定義

/**
 * Transaction 擴展屬性
 */
// Transaction 已經有 amount, fee, timestamp 屬性了
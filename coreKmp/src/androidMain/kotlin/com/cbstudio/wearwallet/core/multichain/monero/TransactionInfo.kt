package com.cbstudio.wearwallet.core.multichain.monero

/**
 * Monero 交易資訊
 */
data class TransactionInfo(
    val txId: String,
    val amount: Long,           // atomic units
    val fee: Long,              // atomic units
    val isOutgoing: Boolean,
    val description: String,
    val timestamp: Long,
    val confirmations: Int,
    val height: Long = 0        // block height (for backward compatibility)
) {
    val amountXmr: Double
        get() = amount / 1e12

    val feeXmr: Double
        get() = fee / 1e12

    val direction: String
        get() = if (isOutgoing) "SENT" else "RECEIVED"

    // Backward compatibility with other TransactionInfo classes
    val isIncoming: Boolean
        get() = !isOutgoing
}
package com.cbstudio.wearwallet.crypto

import java.math.BigDecimal

/**
 * ULTRATHINK - 交易結果數據類
 * 
 * 封裝區塊鏈交易的結果信息
 */
data class TransactionResult(
    val transactionHash: String,
    val blockNumber: Long,
    val from: String,
    val to: String,
    val value: BigDecimal,
    val gasUsed: BigDecimal,
    val gasPrice: BigDecimal,
    val timestamp: Long,
    val status: Status,
    val confirmations: Int = 0
) {
    enum class Status {
        SUCCESS,
        FAILED,
        PENDING
    }
    
    /**
     * 檢查交易是否已確認
     * @param requiredConfirmations 需要的確認數
     * @return 是否已確認
     */
    fun isConfirmed(requiredConfirmations: Int = 12): Boolean {
        return status == Status.SUCCESS && confirmations >= requiredConfirmations
    }
    
    /**
     * 計算實際的手續費
     * @return Gas費用（ETH）
     */
    fun calculateGasFee(): BigDecimal {
        return gasUsed.multiply(gasPrice)
    }
}

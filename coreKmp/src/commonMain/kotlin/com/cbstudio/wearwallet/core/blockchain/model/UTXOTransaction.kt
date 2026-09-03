package com.cbstudio.wearwallet.core.blockchain.model

import kotlinx.datetime.Instant
import com.cbstudio.wearwallet.core.domain.model.ChainType

/**
 * UTXO 交易模型
 */
data class UTXOTransaction(
    val txId: String,
    val blockHeight: Long?,
    val timestamp: Instant?,
    val inputs: List<UTXOInput>,
    val outputs: List<UTXOOutput>,
    val fee: Long,
    val size: Int,
    val weight: Int? = null,
    val confirmations: Int,
    val status: TransactionStatus,
    val chainType: ChainType
)

/**
 * UTXO 交易輸入
 */
data class UTXOInput(
    val txId: String,
    val vout: Int,
    val scriptSig: String,
    val sequence: Long,
    val address: String?,
    val value: Long?
)

/**
 * UTXO 交易輸出
 */
data class UTXOOutput(
    val index: Int,
    val value: Long,
    val scriptPubKey: String,
    val address: String?,
    val spent: Boolean = false
)

/**
 * 交易狀態
 */
enum class TransactionStatus {
    PENDING,      // 待確認
    CONFIRMED,    // 已確認
    FAILED,       // 失敗
    REPLACED      // 被替換（RBF）
}

/**
 * 交易歷史項目
 */
data class UTXOTransactionHistory(
    val txId: String,
    val timestamp: Instant,
    val type: TransactionType,
    val amount: Long,
    val fee: Long,
    val fromAddress: String,
    val toAddress: String,
    val confirmations: Int,
    val status: TransactionStatus,
    val memo: String? = null,
    val chainType: ChainType
)

/**
 * 交易類型
 */
enum class TransactionType {
    SEND,       // 發送
    RECEIVE,    // 接收
    SELF        // 自己轉自己
}

/**
 * 交易摘要
 */
data class UTXOTransactionSummary(
    val totalReceived: Long,
    val totalSent: Long,
    val totalFees: Long,
    val transactionCount: Int,
    val firstTransaction: Instant?,
    val lastTransaction: Instant?
)
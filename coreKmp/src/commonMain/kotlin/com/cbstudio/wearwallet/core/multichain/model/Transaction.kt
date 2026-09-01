package com.cbstudio.wearwallet.core.multichain.model

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.serialization.Serializable

/**
 * 統一的交易模型
 * 支援所有區塊鏈的交易表示
 */
@Serializable
data class MultiChainTransaction(
    val hash: String,
    val chainType: MultiChainType,
    val fromAddress: String,
    val toAddress: String,
    val amount: String, // 使用字串避免精度問題
    val fee: String,
    val timestamp: Long,
    val status: TransactionStatus,
    val blockHeight: Long? = null,
    val confirmations: Int? = null,
    val memo: String? = null,
    val gasUsed: String? = null, // 適用於 Ethereum、TRON 等
    val gasPrice: String? = null,
    val chainSpecific: Map<String, String> = emptyMap() // 鏈特定欄位
) {
    /**
     * 檢查交易是否已確認
     */
    val isConfirmed: Boolean
        get() = status == TransactionStatus.CONFIRMED
    
    /**
     * 檢查交易是否仍在處理中
     */
    val isPending: Boolean
        get() = status == TransactionStatus.PENDING
    
    /**
     * 檢查交易是否失敗
     */
    val isFailed: Boolean
        get() = status == TransactionStatus.FAILED
    
    /**
     * 取得顯示用的確認數
     */
    fun getDisplayConfirmations(): String {
        return when {
            confirmations == null -> "Unknown"
            confirmations >= 12 -> "12+"
            else -> confirmations.toString()
        }
    }
    
    /**
     * 取得格式化的金額顯示
     */
    fun getFormattedAmount(): String {
        val amountValue = amount.toDoubleOrNull() ?: return amount
        // 簡化的格式化實現
        return try {
            // 直接回傳數值字串，避免複雜的格式化
            amountValue.toString()
        } catch (e: Exception) {
            amount
        }
    }
    
    /**
     * 取得區塊鏈瀏覽器連結
     */
    fun getExplorerUrl(): String {
        return when (chainType) {
            MultiChainType.BITCOIN -> "https://blockstream.info/tx/$hash"
            MultiChainType.ETHEREUM -> "https://etherscan.io/tx/$hash"
            MultiChainType.SOLANA -> "https://solscan.io/tx/$hash"
            MultiChainType.CARDANO -> "https://cardanoscan.io/transaction/$hash"
            MultiChainType.TRON -> "https://tronscan.org/#/transaction/$hash"
            MultiChainType.POLKADOT -> "https://polkadot.subscan.io/extrinsic/$hash"
            MultiChainType.MONERO -> "https://xmrchain.net/tx/$hash"
            else -> "#" // 預設值
        }
    }
}

/**
 * 交易狀態
 */
@Serializable
enum class TransactionStatus {
    /**
     * 待處理 - 交易已廣播但尚未被礦工確認
     */
    PENDING,
    
    /**
     * 已確認 - 交易已被納入區塊並得到足夠確認
     */
    CONFIRMED,
    
    /**
     * 失敗 - 交易執行失敗
     */
    FAILED,
    
    /**
     * 已取消 - 交易被取消（某些鏈支援）
     */
    CANCELLED,
    
    /**
     * 已丟棄 - 交易因為各種原因被丟棄
     */
    DROPPED
}

/**
 * 交易類型
 */
@Serializable
enum class TransactionType {
    /**
     * 轉帳
     */
    TRANSFER,
    
    /**
     * 合約調用
     */
    CONTRACT_CALL,
    
    /**
     * 代幣轉帳
     */
    TOKEN_TRANSFER,
    
    /**
     * NFT 轉帳
     */
    NFT_TRANSFER,
    
    /**
     * 其他類型
     */
    OTHER
}
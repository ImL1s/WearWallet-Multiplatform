package com.cbstudio.wearwallet.bridge

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

/**
 * CoreKmp 橋接器 (Disabled in release production)
 */
class CoreKmpBridge(
    private val capabilityGate: CapabilityGate
) {
    
    companion object {
        private const val TAG = "CoreKmpBridge"
    }

    /**
     * Maps MultiChainType to ChainType.
     * FAIL-CLOSED: unknown chain types throw TypedUnsupportedTransactionException.
     */
    private fun mapMultiChainTypeToChainType(multiChainType: MultiChainType): com.cbstudio.wearwallet.core.domain.model.ChainType {
        return try {
            com.cbstudio.wearwallet.core.domain.model.ChainType.valueOf(multiChainType.name)
        } catch (e: Exception) {
            throw TypedUnsupportedTransactionException(
                "Unknown chain type '${multiChainType.name}' cannot be mapped. Fail-closed: refusing to default to any chain."
            )
        }
    }
    
    /**
     * 初始化 CoreKmp WalletManager — DISABLED in production release.
     */
    suspend fun initialize(mnemonic: String): Result<Unit> {
        return Result.Failure(
            TypedUnsupportedTransactionException("CoreKmpBridge secondary pipeline is disabled in release production")
        )
    }
    
    /**
     * 獲取錢包地址 — DISABLED in production release.
     */
    fun getWalletAddress(chainType: MultiChainType): Result<String> {
        return Result.Failure(
            TypedUnsupportedTransactionException("CoreKmpBridge secondary pipeline is disabled in release production")
        )
    }
    
    /**
     * 獲取錢包餘額 — DISABLED in production release.
     */
    suspend fun getBalance(
        chainType: MultiChainType, 
        address: String
    ): Result<BalanceInfo> {
        return Result.Failure(
            TypedUnsupportedTransactionException("CoreKmpBridge secondary pipeline is disabled in release production")
        )
    }
    
    /**
     * 創建交易 — DISABLED in production release builds.
     */
    suspend fun createTransaction(
        chainType: MultiChainType,
        fromAddress: String,
        toAddress: String,
        amount: String,
        tokenAddress: String? = null
    ): Result<UnsignedTransactionInfo> {
        return Result.Failure(
            TypedUnsupportedTransactionException(
                "CoreKmpBridge.createTransaction is disabled. " +
                "Use SendTransactionUseCase with typed quantities for all transaction creation."
            )
        )
    }
    
    /**
     * 簽名並發送交易 — DISABLED in production release builds.
     */
    suspend fun signAndSendTransaction(
        chainType: MultiChainType,
        unsignedTx: UnsignedTransactionInfo
    ): Result<String> {
        return Result.Failure(
            TypedUnsupportedTransactionException(
                "CoreKmpBridge.signAndSendTransaction is disabled. " +
                "Use SendTransactionUseCase with SecureKeyManager instead."
            )
        )
    }
    
    /**
     * 檢查所有支援鏈的餘額
     */
    suspend fun checkAllBalances(): Map<String, String> {
        return emptyMap()
    }
    
    /**
     * 取得支援的區塊鏈類型
     */
    fun getSupportedChains(): List<MultiChainType> {
        return MultiChainType.values().filter { capabilityGate.isChainSupported(it) }
    }
    
    /**
     * 清理資源
     */
    fun dispose() {
        // No-op
    }
}

/**
 * 餘額信息數據類
 */
data class BalanceInfo(
    val amount: String,
    val symbol: String,
    val decimals: Int = 18,
    val usdValue: String? = null
)

/**
 * 未簽名交易信息數據類
 */
data class UnsignedTransactionInfo(
    val rawData: String,
    val chainType: String,
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val gasPrice: String? = null,
    val gasLimit: String? = null,
    val nonce: Long? = null,
    val fee: String = "0"
)
package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result

/**
 * 區塊鏈 SDK 基礎抽象類別
 * 提供共同的實現和簡化 SDK 開發
 */
abstract class BaseBlockchainSDK : BlockchainSDKAdapter {
    
    protected var _isInitialized: Boolean = false
    
    override fun isInitialized(): Boolean = _isInitialized
    
    /**
     * 生成新的帳戶
     */
    abstract suspend fun generateAccount(): Result<AccountInfo>
    
    /**
     * 簽名交易
     */
    abstract override suspend fun signTransaction(
        unsignedTransaction: UnsignedTransaction,
        privateKey: String
    ): Result<SignedTransaction>

    
    /**
     * 廣播已簽名交易 - 重寫父介面方法，統一返回類型
     */
    override suspend fun broadcastTransaction(signedTransaction: SignedTransaction): Result<TransactionResult> {
        // 這裡提供默認實現，子類別可以重寫
        TODO("Subclasses should implement this method")
    }
    
    /**
     * 驗證地址格式 - 重寫父介面方法為同步
     */
    abstract override fun validateAddress(address: String): Result<AddressValidation>
}

/**
 * 帳戶資訊
 */
data class AccountInfo(
    val address: String,                    // 帳戶地址
    val publicKey: String,                  // 公鑰
    val privateKey: String,                 // 私鑰
    val network: String,                    // 網路類型
    val addressType: String? = null,        // 地址類型
    val metadata: Map<String, String> = emptyMap()  // 附加資訊
)

/**
 * 交易資訊
 */
data class TransactionInfo(
    val hash: String,                       // 交易雜湊
    val timestamp: Long,                    // 時間戳
    val blockNumber: Long,                  // 區塊號
    val fromAddress: String,                // 發送方
    val toAddress: String,                  // 接收方
    val amount: String,                     // 金額
    val fee: Double,                        // 手續費
    val status: String,                     // 狀態
    val confirmations: Int = 0              // 確認數
)
package com.cbstudio.wearwallet.core.multichain.backend

import com.cbstudio.wearwallet.core.multichain.common.CoinType
import kotlinx.coroutines.flow.Flow

/**
 * 統一的錢包後端介面
 * 定義所有區塊鏈後端必須實現的核心功能
 */
interface WalletBackend {
    
    /** 後端支援的區塊鏈類型 */
    val coinType: CoinType
    
    /** 後端當前狀態 */
    val state: Flow<BackendState>
    
    /** 後端配置 */
    val config: BackendConfig
    
    /**
     * 初始化錢包
     * @param mnemonic 助記詞（可以是 BIP39、XMR25 等格式）
     * @param config 後端配置
     */
    suspend fun initialize(mnemonic: String, config: BackendConfig = BackendConfig())
    
    /**
     * 獲取錢包地址
     * @param index 地址索引（某些鏈支援多地址）
     * @return 錢包地址
     */
    suspend fun getAddress(index: Int = 0): String
    
    /**
     * 獲取餘額
     * @param address 指定地址，null 表示使用當前錢包
     * @return 餘額資訊
     */
    suspend fun getBalance(address: String? = null): Balance
    
    /**
     * 同步錢包狀態
     * @param force 是否強制同步
     * @return 同步結果
     */
    suspend fun sync(force: Boolean = false): SyncResult
    
    /**
     * 創建交易
     * @param request 交易請求
     * @return 未簽名的交易
     */
    suspend fun createTransaction(request: TransactionRequest): UnsignedTransaction
    
    /**
     * 簽名交易
     * @param transaction 未簽名的交易
     * @return 已簽名的交易
     */
    suspend fun signTransaction(transaction: UnsignedTransaction): SignedTransaction
    
    /**
     * 發送交易
     * @param transaction 已簽名的交易
     * @return 交易哈希
     */
    suspend fun sendTransaction(transaction: SignedTransaction): String
    
    /**
     * 獲取交易歷史
     * @param limit 限制數量
     * @param offset 偏移量
     * @return 交易列表
     */
    suspend fun getTransactionHistory(limit: Int = 20, offset: Int = 0): List<TransactionRecord>
    
    /**
     * 獲取交易詳情
     * @param txHash 交易哈希
     * @return 交易詳情
     */
    suspend fun getTransactionDetail(txHash: String): TransactionDetail?
    
    /**
     * 估算手續費
     * @param request 交易請求
     * @return 手續費估算
     */
    suspend fun estimateFee(request: TransactionRequest): FeeEstimate
    
    /**
     * 釋放資源
     */
    suspend fun dispose()
}

/**
 * 後端狀態
 */
sealed class BackendState {
    object Idle : BackendState()
    object Initializing : BackendState()
    object Ready : BackendState()
    data class Syncing(val progress: Float) : BackendState()
    data class Error(val message: String) : BackendState()
}

/**
 * 後端配置
 */
data class BackendConfig(
    val networkType: NetworkType = NetworkType.MAINNET,
    val nodeUrl: String? = null,
    val lightWalletUrl: String? = null,
    val rpcUrl: String? = null,
    val cacheDir: String? = null,
    val autoSync: Boolean = true,
    val syncInterval: Long = 60_000L, // 60 seconds
    val extras: Map<String, Any> = emptyMap()
)

/**
 * 網路類型
 */
enum class NetworkType {
    MAINNET,
    TESTNET,
    STAGENET,
    DEVNET
}

/**
 * 餘額資訊
 */
data class Balance(
    val total: String,          // 總餘額
    val available: String,       // 可用餘額
    val locked: String? = null,  // 鎖定餘額（某些鏈支援）
    val pending: String? = null, // 待確認餘額
    val decimals: Int,          // 小數位數
    val symbol: String          // 貨幣符號
)

/**
 * 同步結果
 */
data class SyncResult(
    val success: Boolean,
    val blockHeight: Long,
    val syncedHeight: Long,
    val message: String? = null
)

/**
 * 交易請求
 */
data class TransactionRequest(
    val to: String,
    val amount: String,
    val fee: String? = null,
    val memo: String? = null,
    val priority: TransactionPriority = TransactionPriority.NORMAL,
    val extras: Map<String, Any> = emptyMap()
)

/**
 * 交易優先級
 */
enum class TransactionPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * 未簽名交易
 */
data class UnsignedTransaction(
    val rawTx: ByteArray,
    val fee: String,
    val extras: Map<String, Any> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as UnsignedTransaction
        return rawTx.contentEquals(other.rawTx) && fee == other.fee
    }
    
    override fun hashCode(): Int {
        var result = rawTx.contentHashCode()
        result = 31 * result + fee.hashCode()
        return result
    }
}

/**
 * 已簽名交易
 */
data class SignedTransaction(
    val signedTx: ByteArray,
    val txHash: String? = null,
    val extras: Map<String, Any> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SignedTransaction
        return signedTx.contentEquals(other.signedTx) && txHash == other.txHash
    }
    
    override fun hashCode(): Int {
        var result = signedTx.contentHashCode()
        result = 31 * result + (txHash?.hashCode() ?: 0)
        return result
    }
}

/**
 * 交易記錄
 */
data class TransactionRecord(
    val txHash: String,
    val from: String,
    val to: String,
    val amount: String,
    val fee: String,
    val timestamp: Long,
    val blockHeight: Long,
    val confirmations: Int,
    val status: TransactionStatus,
    val type: TransactionType,
    val extras: Map<String, Any> = emptyMap()
)

/**
 * 交易狀態
 */
enum class TransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED
}

/**
 * 交易類型
 */
enum class TransactionType {
    SEND,
    RECEIVE,
    SELF,
    CONTRACT,
    TOKEN,
    UNKNOWN
}

/**
 * 交易詳情
 */
data class TransactionDetail(
    val record: TransactionRecord,
    val inputs: List<TransactionInput> = emptyList(),
    val outputs: List<TransactionOutput> = emptyList(),
    val memo: String? = null,
    val rawTx: String? = null,
    val extras: Map<String, Any> = emptyMap()
)

/**
 * 交易輸入
 */
data class TransactionInput(
    val address: String,
    val amount: String,
    val index: Int
)

/**
 * 交易輸出
 */
data class TransactionOutput(
    val address: String,
    val amount: String,
    val index: Int
)

/**
 * 手續費估算
 */
data class FeeEstimate(
    val low: FeeOption,
    val normal: FeeOption,
    val high: FeeOption,
    val urgent: FeeOption? = null
)

/**
 * 手續費選項
 */
data class FeeOption(
    val fee: String,
    val estimatedTime: Long, // in seconds
    val priority: TransactionPriority
)
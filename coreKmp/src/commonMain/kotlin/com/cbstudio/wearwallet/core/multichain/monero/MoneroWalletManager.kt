package com.cbstudio.wearwallet.core.multichain.monero

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.monero.crypto.*
import com.cbstudio.wearwallet.core.multichain.storage.PlatformCacheStorage
import com.cbstudio.wearwallet.core.multichain.storage.CacheStorageAdapter
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

import com.ionspin.kotlin.bignum.decimal.BigDecimal

class MoneroWalletManager {
    
    companion object {
        // NetworkType 枚舉和其他常量
        private const val MAINNET_NODE = "https://node.monero.net:18089"
        private const val STAGENET_NODE = "http://stagenet.monerujo.io:38089" 
        private const val TESTNET_NODE = "http://testnet.monerujo.io:28089"
        
        /**
         * Network types for Monero
         */
        enum class NetworkType(val value: Int) {
            MAINNET(0),
            TESTNET(1),
            STAGENET(2)
        }
    }
    
    // HTTP 客戶端
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }
    
    // 快取儲存
    private val cacheStorage = PlatformCacheStorage()
    
    // 同步器管理
    private val synchronizers = mutableMapOf<String, MoneroPersistentSynchronizer>()
    
    // 後台同步任務
    private val backgroundJobs = mutableMapOf<String, Job>()
    
    // 錢包密鑰快取
    private val walletKeys = mutableMapOf<String, MoneroKeys>()
    
    // 網路配置
    private var networkType = Companion.NetworkType.MAINNET
    
    // 節點 URL
    private var daemonUrl = MAINNET_NODE
    
    // ===== 新增：為每個錢包保持獨立的 CryptoProvider 實例 =====
    private val cryptoProviders = mutableMapOf<String, MoneroCryptoProvider>()
    
    
    /**
     * 設置網路類型和節點
     */
    fun setNetwork(type: Companion.NetworkType, nodeUrl: String? = null) {
        networkType = type
        daemonUrl = nodeUrl ?: when (type) {
            Companion.NetworkType.MAINNET -> MAINNET_NODE
            Companion.NetworkType.TESTNET -> TESTNET_NODE
            Companion.NetworkType.STAGENET -> STAGENET_NODE
        }
        println("🌐 已設置網路：$type，節點：$daemonUrl")
    }
    
    /**
     * 從助記詞初始化錢包
     */
    suspend fun initializeWallet(
        walletId: String,
        mnemonic: String,
        passphrase: String = ""
    ): Result<WalletInfo> {
        return Result.Failure(TypedUnsupportedTransactionException("Monero operation is unsupported in release"))
    }
    
    /**
     * 初始化只讀錢包
     */
    suspend fun initializeViewOnlyWallet(
        walletId: String,
        address: String,
        viewKey: String
    ): Result<WalletInfo> {
        return Result.Failure(TypedUnsupportedTransactionException("Monero operation is unsupported in release"))
    }
    
    /**
     * 同步並獲取餘額
     */
    suspend fun syncAndGetBalance(
        walletId: String,
        forceFullScan: Boolean = false,
        restoreHeight: Long = 0
    ): Result<BalanceInfo> = Result.Failure(TypedUnsupportedTransactionException("Monero operation is unsupported in release"))
    
    /**
     * 獲取快取餘額（不執行同步）
     */
    fun getCachedBalance(walletId: String): BalanceInfo? {
        return null
    }
    
    /**
     * 創建新賬戶
     */
    suspend fun createAccount(
        walletId: String,
        label: String? = null
    ): Result<AccountInfo> {
        return Result.Failure(TypedUnsupportedTransactionException("Monero operation is unsupported in release"))
    }
    
    /**
     * 創建新子地址
     */
    suspend fun createSubaddress(
        walletId: String,
        accountIndex: Int,
        label: String? = null
    ): Result<AddressInfo> {
        return Result.Failure(TypedUnsupportedTransactionException("Monero operation is unsupported in release"))
    }
    
    /**
     * 啟動後台同步
     */
    fun startBackgroundSync(walletId: String, intervalMs: Long = 30000) {
        // No-op in release production
    }
    
    /**
     * 停止後台同步
     */
    fun stopBackgroundSync(walletId: String) {
        // No-op in release production
    }
    
    /**
     * 停止所有後台同步
     */
    fun stopAllBackgroundSync() {
        // No-op in release production
    }
    
    /**
     * 創建交易
     */
    suspend fun createTransaction(
        walletId: String,
        toAddress: String,
        amount: Double
    ): Result<MoneroTransactionResult> = Result.Failure(TypedUnsupportedTransactionException("Monero operation is unsupported in release"))
    
    /**
     * 獲取交易歷史
     */
    fun getTransactionHistory(walletId: String, limit: Int = 20): Result<List<MoneroTransactionInfo>> {
        return Result.Failure(TypedUnsupportedTransactionException("Monero operation is unsupported in release"))
    }
    
    /**
     * 清理錢包
     * @param walletId 錢包識別符
     */
    fun clearWallet(walletId: String) {
        // 停止後台同步
        stopBackgroundSync(walletId)
        
        // 清理資源
        synchronizers.remove(walletId)
        walletKeys.remove(walletId)
        cryptoProviders.remove(walletId)  // 清理 provider
        
        // 清理快取
        cacheStorage.deleteMoneroCache(walletId)
    }
    
    /**
     * 清理所有錢包
     */
    fun clearAllWallets() {
        stopAllBackgroundSync()
        synchronizers.clear()
        walletKeys.clear()
        cryptoProviders.clear()  // 清理所有 providers
    }
    
    /**
     * 釋放資源
     */
    fun dispose() {
        clearAllWallets()
        httpClient.close()
    }
    
    // ===== 修改：為每個錢包保持獨立的 provider 實例 =====
    /**
     * 獲取或創建錢包的 CryptoProvider
     * @param walletId 錢包識別符
     * @return CryptoProvider 實例
     */
    private fun getOrCreateCryptoProvider(walletId: String): MoneroCryptoProvider {
        return cryptoProviders.getOrPut(walletId) {
            println("🔧 為錢包 $walletId 創建新的 CryptoProvider 實例")
            getMoneroCryptoProvider()
        }
    }
    
    // ===== 私有方法 =====
    private fun getOrCreateSynchronizer(walletId: String): MoneroPersistentSynchronizer {
        return synchronizers.getOrPut(walletId) {
            MoneroPersistentSynchronizer(
                httpClient = httpClient,
                daemonUrl = daemonUrl,
                cacheStorage = CacheStorageAdapter(cacheStorage),
                isTestnet = networkType == Companion.NetworkType.TESTNET
            )
        }
    }
    
    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    /**
     * 創建 Monero 交易的私有方法
     */
    private suspend fun createMoneroTransaction(
        cryptoProvider: MoneroCryptoProvider,
        request: MoneroTransactionRequest,
        keys: MoneroKeys
    ): Result<MoneroTransactionData> {
        return try {
            // 將 atomic units 轉換為 XMR (1 XMR = 10^12 atomic units)
            val amountInXmr = BigDecimal.fromLong(request.amount) / BigDecimal.parseString("1000000000000")
            
            // 準備交易輸出
            val outputs = listOf(
                TransactionOutput(
                    address = request.toAddress,
                    amount = amountInXmr
                )
            )
            
            // 使用 cryptoProvider 創建真實交易
            val transactionResult = cryptoProvider.createTransaction(
                inputs = emptyList(), // 讓錢包自動選擇 UTXOs
                outputs = outputs,
                changeAddress = keys.address, // 找零回到同一地址
                feeAmount = BigDecimal.ZERO // 讓錢包自動計算手續費
            )
            
            when (transactionResult) {
                is Result.Success -> {
                    val tx = transactionResult.data
                    // fee 已經是 BigDecimal，直接轉換為 atomic units
                    val feeInAtomicUnits = (tx.fee * BigDecimal.parseString("1000000000000")).longValue(false)
                    Result.Success(MoneroTransactionData(
                        hash = tx.txHash,
                        fee = feeInAtomicUnits,
                        rawData = tx.txHex
                    ))
                }
                is Result.Failure -> {
                    Result.Failure(transactionResult.exception)
                }
                is Result.Loading -> {
                    Result.Failure(Exception("Unexpected loading state"))
                }
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}

// ===== Monero 特定的類型定義 =====

/**
 * Monero 交易請求
 */
data class MoneroTransactionRequest(
    val fromAddress: String,
    val toAddress: String,
    val amount: Long, // atomic units
    val feeLevel: FeeLevel = FeeLevel.NORMAL
) {
    enum class FeeLevel {
        LOW, NORMAL, HIGH, URGENT
    }
}

/**
 * Monero 交易數據
 */
data class MoneroTransactionData(
    val hash: String,
    val fee: Long,
    val rawData: String
)

/**
 * Monero 交易結果
 */
data class MoneroTransactionResult(
    val txHash: String,
    val fee: Long,
    val amount: Long,
    val status: MoneroTransactionStatus
)

/**
 * Monero 交易狀態
 */
enum class MoneroTransactionStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED
}

/**
 * Monero 交易信息
 */
data class MoneroTransactionInfo(
    val hash: String,
    val amount: Long,
    val fee: Long,
    val timestamp: Long,
    val height: Long,
    val direction: TransactionDirection,
    val status: MoneroTransactionStatus
) {
    enum class TransactionDirection {
        INCOMING, OUTGOING
    }
}

/**
 * 錢包資訊
 */
data class WalletInfo(
    val id: String,
    val address: String,
    val viewKey: String,
    val spendKey: String,
    val isViewOnly: Boolean,
    val network: MoneroWalletManager.Companion.NetworkType
)

/**
 * 餘額資訊
 */
data class BalanceInfo(
    val totalBalance: Long,
    val unlockedBalance: Long,
    val totalXmr: Double,
    val unlockedXmr: Double,
    val lastSyncHeight: Long,
    val accounts: List<AccountInfo>
)

/**
 * 賬戶資訊
 */
data class AccountInfo(
    val index: Int,
    val label: String,
    val balance: Long,
    val addresses: List<AddressInfo>
)

/**
 * 地址資訊
 */
data class AddressInfo(
    val index: Int,
    val address: String,
    val label: String,
    val used: Boolean,
    val balance: Long
)


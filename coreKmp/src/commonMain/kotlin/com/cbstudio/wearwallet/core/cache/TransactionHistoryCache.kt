package com.cbstudio.wearwallet.core.cache

import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.blockchain.model.UTXOTransaction
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.Transaction_record
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 交易歷史快取系統
 * 
 * 特性：
 * 1. 多層快取架構（記憶體 + SQLDelight 資料庫）
 * 2. 智能同步策略（增量更新、批量獲取）
 * 3. 支援多鏈交易
 * 4. 自動過期和清理機制
 * 5. 並發安全
 */
class TransactionHistoryCache(
    private val database: CoreWalletDatabase,
    private val sdkManager: SDKAdapterManager = RealSDKFactory.createRealManager(),
    private val utxoApiClient: UTXOApiClient = UTXOApiClient()
) {
    
    // 記憶體快取
    private val memoryCache = mutableMapOf<CacheKey, CachedTransactions>()
    private val cacheMutex = Mutex()
    
    // 同步狀態
    private val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.IDLE)
    private val syncJobs = mutableMapOf<CacheKey, Job>()
    
    // 配置
    private val config = CacheConfig()
    
    /**
     * 獲取交易歷史
     * 優先從快取獲取，必要時從區塊鏈同步
     */
    suspend fun getTransactionHistory(
        address: String,
        chainType: MultiChainType,
        forceRefresh: Boolean = false,
        limit: Int = 50
    ): Result<List<TransactionData>> {
        val cacheKey = CacheKey(address, chainType)
        
        // 如果強制刷新，清除快取
        if (forceRefresh) {
            cacheMutex.withLock {
                memoryCache.remove(cacheKey)
            }
        }
        
        // 嘗試從記憶體快取獲取
        val cachedData = getCachedData(cacheKey)
        if (cachedData != null && !cachedData.isExpired()) {
            return Result.Success(cachedData.transactions)
        }
        
        // 從資料庫獲取
        val dbTransactions = loadFromDatabase(address, chainType, limit)
        
        // 如果資料庫有資料且不需要同步，直接返回
        if (dbTransactions.isNotEmpty() && !shouldSync(cacheKey, dbTransactions)) {
            updateMemoryCache(cacheKey, dbTransactions)
            return Result.Success(dbTransactions)
        }
        
        // 需要從區塊鏈同步
        return syncWithBlockchain(address, chainType, limit)
    }
    
    /**
     * 批量獲取多個地址的交易歷史
     */
    suspend fun getBatchTransactionHistory(
        addresses: List<String>,
        chainType: MultiChainType
    ): Result<Map<String, List<TransactionData>>> {
        return try {
            val results = mutableMapOf<String, List<TransactionData>>()
            
            // 使用並發加速批量獲取
            coroutineScope {
                addresses.map { address ->
                    async {
                        val history = getTransactionHistory(address, chainType)
                        if (history is Result.Success) {
                            address to history.data
                        } else {
                            address to emptyList()
                        }
                    }
                }.awaitAll().forEach { (address, transactions) ->
                    results[address] = transactions
                }
            }
            
            Result.Success(results)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 監控交易狀態更新
     */
    fun monitorTransaction(
        txHash: String,
        chainType: MultiChainType
    ): Flow<TransactionStatus> = flow {
        val monitor = TransactionMonitor(sdkManager)
        
        monitor.monitorTransaction(chainType, txHash)
            .collect { status ->
                // 更新資料庫中的交易狀態
                updateTransactionStatus(txHash, status)
                
                // 發送狀態更新
                emit(when (status) {
                    is MonitorStatus.PENDING -> TransactionStatus.PENDING
                    is MonitorStatus.CONFIRMED -> TransactionStatus.CONFIRMED
                    is MonitorStatus.CONFIRMING -> TransactionStatus.CONFIRMING(
                        status.current,
                        status.required
                    )
                    is MonitorStatus.FINALIZED -> TransactionStatus.FINALIZED
                    is MonitorStatus.FAILED -> TransactionStatus.FAILED
                    is MonitorStatus.DROPPED -> TransactionStatus.DROPPED
                    is MonitorStatus.TIMEOUT -> TransactionStatus.TIMEOUT
                })
            }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 預載入交易歷史
     * 在背景預先載入常用地址的交易歷史
     */
    suspend fun preloadTransactionHistory(
        addresses: List<String>,
        chains: List<MultiChainType>
    ) {
        coroutineScope {
            addresses.forEach { address ->
                chains.forEach { chain ->
                    launch {
                        getTransactionHistory(address, chain, limit = 20)
                    }
                }
            }
        }
    }
    
    /**
     * 清理過期快取
     */
    suspend fun cleanupExpiredCache() {
        cacheMutex.withLock {
            val now = Clock.System.now()
            memoryCache.entries.removeAll { (_, cached) ->
                cached.isExpired(now)
            }
        }
        
        // 清理資料庫中的舊交易
        cleanupOldDatabaseTransactions()
    }
    
    /**
     * 獲取同步狀態
     */
    fun getSyncStatus(): StateFlow<SyncStatus> = syncStatus.asStateFlow()
    
    // ===== 私有方法 =====
    
    private suspend fun getCachedData(key: CacheKey): CachedTransactions? {
        return cacheMutex.withLock {
            memoryCache[key]
        }
    }
    
    private suspend fun updateMemoryCache(
        key: CacheKey,
        transactions: List<TransactionData>
    ) {
        cacheMutex.withLock {
            memoryCache[key] = CachedTransactions(
                transactions = transactions,
                timestamp = Clock.System.now(),
                expiresAt = Clock.System.now() + config.cacheExpiration
            )
        }
    }
    
    private suspend fun loadFromDatabase(
        address: String,
        chainType: MultiChainType,
        limit: Int
    ): List<TransactionData> {
        return try {
            database.transactionQueries
                .selectByAddress(address, address, getChainId(chainType).toLong())
                .executeAsList()
                .take(limit)
                .map { record ->
                    TransactionData(
                        hash = record.tx_hash,
                        from = record.from_address,
                        to = record.to_address,
                        value = record.value_,
                        fee = record.fee_amount ?: "0",
                        timestamp = record.block_timestamp?.let { 
                            Instant.fromEpochMilliseconds(it * 1000)
                        },
                        status = parseTransactionStatus(record.status),
                        chainType = chainType,
                        tokenSymbol = record.token_symbol,
                        tokenAddress = record.token_address,
                        memo = record.memo
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private suspend fun syncWithBlockchain(
        address: String,
        chainType: MultiChainType,
        limit: Int
    ): Result<List<TransactionData>> {
        val cacheKey = CacheKey(address, chainType)
        
        // 防止重複同步
        syncJobs[cacheKey]?.let { job ->
            if (job.isActive) {
                job.join()
                return getCachedData(cacheKey)?.let { 
                    Result.Success(it.transactions)
                } ?: Result.Failure(Exception("同步失敗"))
            }
        }
        
        // 開始新的同步任務
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                syncStatus.emit(SyncStatus.SYNCING(address, chainType))
                
                val transactions = when {
                    MultiChainType.isUtxoChain(chainType) -> {
                        // UTXO 鏈使用專門的 API
                        fetchUTXOTransactions(address, chainType, limit)
                    }
                    else -> {
                        // 其他鏈使用 SDK
                        fetchAccountTransactions(address, chainType, limit)
                    }
                }
                
                // 保存到資料庫
                saveToDatabase(transactions)
                
                // 更新記憶體快取
                updateMemoryCache(cacheKey, transactions)
                
                syncStatus.emit(SyncStatus.SUCCESS(address, chainType))
            } catch (e: Exception) {
                syncStatus.emit(SyncStatus.ERROR(address, chainType, e.message ?: "Unknown error"))
            }
        }
        
        syncJobs[cacheKey] = job
        job.join()
        
        return getCachedData(cacheKey)?.let { 
            Result.Success(it.transactions)
        } ?: Result.Failure(Exception("同步失敗"))
    }
    
    private suspend fun fetchUTXOTransactions(
        address: String,
        chainType: MultiChainType,
        limit: Int
    ): List<TransactionData> {
        val utxoChainType = when (chainType) {
            MultiChainType.BITCOIN -> ChainType.BITCOIN
            MultiChainType.LITECOIN -> ChainType.LITECOIN
            MultiChainType.DOGECOIN -> ChainType.DOGECOIN
            MultiChainType.BITCOIN_CASH -> ChainType.BITCOIN_CASH
            else -> throw IllegalArgumentException("Not a UTXO chain: $chainType")
        }
        
        val utxoTransactions = utxoApiClient.getTransactionHistory(
            address = address,
            chainType = utxoChainType,
            limit = limit
        )
        
        return utxoTransactions.map { tx ->
            TransactionData(
                hash = tx.txId,
                from = tx.inputs.firstOrNull()?.address ?: "",
                to = tx.outputs.firstOrNull()?.address ?: "",
                value = tx.outputs.sumOf { it.value }.toString(),
                fee = tx.fee.toString(),
                timestamp = tx.timestamp,
                status = when (tx.status) {
                    com.cbstudio.wearwallet.core.blockchain.model.TransactionStatus.PENDING -> TransactionStatus.PENDING
                    com.cbstudio.wearwallet.core.blockchain.model.TransactionStatus.CONFIRMED -> TransactionStatus.CONFIRMED
                    else -> TransactionStatus.PENDING
                },
                chainType = chainType,
                confirmations = tx.confirmations
            )
        }
    }
    
    private suspend fun fetchAccountTransactions(
        address: String,
        chainType: MultiChainType,
        limit: Int
    ): List<TransactionData> {
        val sdk = sdkManager.getAdapter(chainType)
            ?: throw IllegalArgumentException("SDK not found for $chainType")
        
        val result = sdk.getTransactionHistory(address, limit, 0)
        
        return when (result) {
            is Result.Success -> {
                result.data.map { tx ->
                    TransactionData(
                        hash = tx.hash,
                        from = tx.fromAddress,
                        to = tx.toAddress,
                        value = tx.amount,
                        fee = tx.fee,
                        timestamp = Instant.fromEpochMilliseconds(tx.timestamp),
                        status = when (tx.status) {
                            com.cbstudio.wearwallet.core.multichain.sdk.TransactionStatus.PENDING -> TransactionStatus.PENDING
                            com.cbstudio.wearwallet.core.multichain.sdk.TransactionStatus.CONFIRMED -> TransactionStatus.CONFIRMED
                            com.cbstudio.wearwallet.core.multichain.sdk.TransactionStatus.FAILED -> TransactionStatus.FAILED
                            else -> TransactionStatus.PENDING
                        },
                        chainType = chainType,
                        memo = tx.memo
                    )
                }
            }
            is Result.Failure -> {
                throw result.exception
            }
            else -> emptyList()
        }
    }
    
    private suspend fun saveToDatabase(transactions: List<TransactionData>) {
        transactions.forEach { tx ->
            try {
                database.transactionQueries.insert(
                    wallet_id = 0, // TODO: 從上下文獲取
                    tx_hash = tx.hash,
                    from_address = tx.from,
                    to_address = tx.to,
                    value_ = tx.value,
                    gas_price = null,
                    gas_limit = null,
                    gas_used = null,
                    nonce = null,
                    data_ = null,
                    status = tx.status.toString(),
                    type = "TRANSFER",
                    chain_type = tx.chainType.name,
                    chain_id = getChainId(tx.chainType).toLong(),
                    block_number = null,
                    block_timestamp = tx.timestamp?.toEpochMilliseconds()?.div(1000),
                    token_address = tx.tokenAddress,
                    token_symbol = tx.tokenSymbol,
                    token_decimals = null,
                    fee_amount = tx.fee,
                    fee_currency = null,
                    keystone_sign_request_id = null,
                    keystone_signature = null,
                    memo = tx.memo,
                    metadata = "{}"
                )
            } catch (e: Exception) {
                // 忽略重複插入錯誤
            }
        }
    }
    
    private suspend fun updateTransactionStatus(
        txHash: String,
        status: MonitorStatus
    ) {
        try {
            val statusString = when (status) {
                is MonitorStatus.PENDING -> "PENDING"
                is MonitorStatus.CONFIRMED -> "SUCCESS"
                is MonitorStatus.FINALIZED -> "SUCCESS"
                is MonitorStatus.FAILED -> "FAILED"
                is MonitorStatus.DROPPED -> "CANCELLED"
                is MonitorStatus.TIMEOUT -> "FAILED"
                is MonitorStatus.CONFIRMING -> "PENDING"
            }
            
            database.transactionQueries.updateStatus(
                status = statusString,
                block_number = null,
                block_timestamp = null,
                gas_used = null,
                tx_hash = txHash
            )
        } catch (e: Exception) {
            // 忽略更新錯誤
        }
    }
    
    private suspend fun cleanupOldDatabaseTransactions() {
        try {
            // 保留每個錢包最近 1000 筆交易
            database.transactionQueries.deleteOldTransactions(0, 0)
        } catch (e: Exception) {
            // 忽略清理錯誤
        }
    }
    
    private fun shouldSync(
        key: CacheKey,
        dbTransactions: List<TransactionData>
    ): Boolean {
        // 如果資料庫沒有交易，需要同步
        if (dbTransactions.isEmpty()) return true
        
        // 如果最新交易超過同步間隔，需要同步
        val latestTransaction = dbTransactions.firstOrNull()
        val now = Clock.System.now()
        
        latestTransaction?.timestamp?.let { timestamp ->
            val age = now - timestamp
            if (age > config.syncInterval) {
                return true
            }
        }
        
        return false
    }
    
    private fun parseTransactionStatus(status: String): TransactionStatus {
        return when (status) {
            "PENDING" -> TransactionStatus.PENDING
            "SUCCESS" -> TransactionStatus.CONFIRMED
            "FAILED" -> TransactionStatus.FAILED
            "CANCELLED" -> TransactionStatus.DROPPED
            else -> TransactionStatus.PENDING
        }
    }
    
    private fun getChainId(chainType: MultiChainType): Int {
        return when (chainType) {
            MultiChainType.ETHEREUM -> 1
            MultiChainType.BSC -> 56
            MultiChainType.POLYGON -> 137
            MultiChainType.AVALANCHE -> 43114
            MultiChainType.ARBITRUM -> 42161
            MultiChainType.OPTIMISM -> 10
            MultiChainType.CRONOS -> 25
            MultiChainType.BITCOIN -> 0
            MultiChainType.LITECOIN -> 2
            MultiChainType.DOGECOIN -> 3
            MultiChainType.BITCOIN_CASH -> 145
            else -> 0
        }
    }
}

/**
 * 快取鍵
 */
data class CacheKey(
    val address: String,
    val chainType: MultiChainType
)

/**
 * 快取的交易資料
 */
data class CachedTransactions(
    val transactions: List<TransactionData>,
    val timestamp: Instant,
    val expiresAt: Instant
) {
    fun isExpired(now: Instant = Clock.System.now()): Boolean {
        return now > expiresAt
    }
}

/**
 * 交易資料
 */
data class TransactionData(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,
    val fee: String,
    val timestamp: Instant?,
    val status: TransactionStatus,
    val chainType: MultiChainType,
    val tokenSymbol: String? = null,
    val tokenAddress: String? = null,
    val memo: String? = null,
    val confirmations: Int = 0
)

/**
 * 交易狀態
 */
sealed class TransactionStatus {
    object PENDING : TransactionStatus()
    object CONFIRMED : TransactionStatus()
    data class CONFIRMING(val current: Int, val required: Int) : TransactionStatus()
    object FINALIZED : TransactionStatus()
    object FAILED : TransactionStatus()
    object DROPPED : TransactionStatus()
    object TIMEOUT : TransactionStatus()
}

/**
 * 同步狀態
 */
sealed class SyncStatus {
    object IDLE : SyncStatus()
    data class SYNCING(val address: String, val chainType: MultiChainType) : SyncStatus()
    data class SUCCESS(val address: String, val chainType: MultiChainType) : SyncStatus()
    data class ERROR(val address: String, val chainType: MultiChainType, val message: String) : SyncStatus()
}

/**
 * 快取配置
 */
data class CacheConfig(
    val cacheExpiration: Duration = 5.minutes,
    val syncInterval: Duration = 30.seconds,
    val maxCacheSize: Int = 1000,
    val maxTransactionsPerAddress: Int = 100,
    val batchSize: Int = 50
)

/**
 * 資料庫介面（簡化版）
 */
interface WearWalletDatabase {
    val transactionQueries: TransactionQueries
}

interface TransactionQueries {
    fun selectByAddress(from: String, to: String, chainId: Long): Query<Transaction_record>
    fun insert(
        wallet_id: Long,
        tx_hash: String,
        from_address: String,
        to_address: String,
        value: String,
        gas_price: String?,
        gas_limit: String?,
        gas_used: String?,
        nonce: Int?,
        data: String?,
        status: String,
        type: String,
        chain_type: String,
        chain_id: Int,
        block_number: Long?,
        block_timestamp: Long?,
        token_address: String?,
        token_symbol: String?,
        token_decimals: Int?,
        fee_amount: String?,
        fee_currency: String?,
        keystone_sign_request_id: String?,
        keystone_signature: String?,
        memo: String?,
        metadata: String
    )
    fun updateStatus(
        status: String,
        block_number: Long?,
        block_timestamp: Long?,
        gas_used: String?,
        tx_hash: String
    )
    fun deleteOldTransactions(wallet_id: Long, wallet_id2: Long)
}

interface Query<T> {
    fun executeAsList(): List<T>
}
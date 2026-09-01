package com.cbstudio.wearwallet.core.cache

import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.utils.format
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * 緩存條目
 */
internal data class CacheEntry<T>(
    val data: T,
    val timestamp: Instant,
    val ttl: Long // 秒
) {
    fun isExpired(now: Instant = Clock.System.now()): Boolean {
        val age = now.epochSeconds - timestamp.epochSeconds
        return age > ttl
    }
}

/**
 * 交易緩存管理器
 * 提供高效的交易數據緩存，減少網路請求
 * 
 * 特性：
 * - LRU 緩存策略
 * - 過期時間管理
 * - 線程安全
 * - 內存優化
 */
class TransactionCache(
    private val maxSize: Int = 1000,
    private val ttlSeconds: Long = 300 // 5 分鐘
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, CacheEntry<List<Transaction>>>()
    
    private val balanceCache = mutableMapOf<String, CacheEntry<String>>()
    
    private val tokenBalanceCache = mutableMapOf<String, CacheEntry<Map<String, String>>>()
    
    /**
     * 緩存交易歷史
     */
    suspend fun putTransactions(
        address: String,
        chainId: String,
        transactions: List<Transaction>,
        page: Int = 1
    ) = mutex.withLock {
        val key = "$address:$chainId:$page"
        val entry = CacheEntry(
            data = transactions,
            timestamp = Clock.System.now(),
            ttl = ttlSeconds
        )
        
        // 添加到緩存
        cache[key] = entry
        
        // 檢查緩存大小
        if (cache.size > maxSize) {
            // 移除最舊的條目（LRU）
            val iterator = cache.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }
    
    /**
     * 獲取緩存的交易歷史
     */
    suspend fun getTransactions(
        address: String,
        chainId: String,
        page: Int = 1
    ): List<Transaction>? = mutex.withLock {
        val key = "$address:$chainId:$page"
        val entry = cache[key] ?: return@withLock null
        
        // 檢查是否過期
        if (entry.isExpired()) {
            cache.remove(key)
            return@withLock null
        }
        
        return@withLock entry.data
    }
    
    /**
     * 緩存餘額
     */
    suspend fun putBalance(
        address: String,
        chainId: String,
        balance: String
    ) = mutex.withLock {
        val key = "$address:$chainId:balance"
        val entry = CacheEntry(
            data = balance,
            timestamp = Clock.System.now(),
            ttl = 60 // 餘額緩存 1 分鐘
        )
        balanceCache[key] = entry
        
        // 限制緩存大小
        if (balanceCache.size > 100) {
            val iterator = balanceCache.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }
    
    /**
     * 獲取緩存的餘額
     */
    suspend fun getBalance(
        address: String,
        chainId: String
    ): String? = mutex.withLock {
        val key = "$address:$chainId:balance"
        val entry = balanceCache[key] ?: return@withLock null
        
        if (entry.isExpired()) {
            balanceCache.remove(key)
            return@withLock null
        }
        
        return@withLock entry.data
    }
    
    /**
     * 緩存代幣餘額
     */
    suspend fun putTokenBalances(
        address: String,
        chainId: String,
        balances: Map<String, String>
    ) = mutex.withLock {
        val key = "$address:$chainId:tokens"
        val entry = CacheEntry(
            data = balances,
            timestamp = Clock.System.now(),
            ttl = 120 // 代幣餘額緩存 2 分鐘
        )
        tokenBalanceCache[key] = entry
        
        // 限制緩存大小
        if (tokenBalanceCache.size > 50) {
            val iterator = tokenBalanceCache.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }
    
    /**
     * 獲取緩存的代幣餘額
     */
    suspend fun getTokenBalances(
        address: String,
        chainId: String
    ): Map<String, String>? = mutex.withLock {
        val key = "$address:$chainId:tokens"
        val entry = tokenBalanceCache[key] ?: return@withLock null
        
        if (entry.isExpired()) {
            tokenBalanceCache.remove(key)
            return@withLock null
        }
        
        return@withLock entry.data
    }
    
    /**
     * 使特定地址的緩存失效
     */
    suspend fun invalidateAddress(address: String) = mutex.withLock {
        // 移除所有包含該地址的緩存條目
        cache.keys.filter { it.startsWith("$address:") }.forEach { cache.remove(it) }
        balanceCache.keys.filter { it.startsWith("$address:") }.forEach { balanceCache.remove(it) }
        tokenBalanceCache.keys.filter { it.startsWith("$address:") }.forEach { tokenBalanceCache.remove(it) }
    }
    
    /**
     * 使特定鏈的緩存失效
     */
    suspend fun invalidateChain(chainId: String) = mutex.withLock {
        cache.keys.filter { it.contains(":$chainId:") }.forEach { cache.remove(it) }
        balanceCache.keys.filter { it.contains(":$chainId:") }.forEach { balanceCache.remove(it) }
        tokenBalanceCache.keys.filter { it.contains(":$chainId:") }.forEach { tokenBalanceCache.remove(it) }
    }
    
    /**
     * 清除所有緩存
     */
    suspend fun clear() = mutex.withLock {
        cache.clear()
        balanceCache.clear()
        tokenBalanceCache.clear()
    }
    
    /**
     * 清除過期的緩存條目
     */
    suspend fun cleanupExpired() = mutex.withLock {
        val now = Clock.System.now()
        
        // 清理交易緩存
        cache.entries.removeAll { entry -> entry.value.isExpired(now) }
        
        // 清理餘額緩存
        balanceCache.entries.removeAll { entry -> entry.value.isExpired(now) }
        
        // 清理代幣餘額緩存
        tokenBalanceCache.entries.removeAll { entry -> entry.value.isExpired(now) }
    }
    
    suspend fun getStats(): CacheStats = mutex.withLock {
        CacheStats(
            name = "TransactionCache",
            size = cache.size + balanceCache.size + tokenBalanceCache.size,
            maxSize = maxSize,
            hitCount = 0L,  // 可以添加實際統計
            missCount = 0L,  // 可以添加實際統計
            evictionCount = 0L,  // 可以添加實際統計
            hitRate = 0.0  // 可以添加實際統計
        )
    }
    
    /**
     * 預加載緩存
     * 可以在應用啟動時預加載常用數據
     */
    suspend fun preload(
        addresses: List<String>,
        chainIds: List<String>,
        loader: suspend (String, String) -> List<Transaction>
    ) {
        addresses.forEach { address ->
            chainIds.forEach { chainId ->
                try {
                    val transactions = loader(address, chainId)
                    putTransactions(address, chainId, transactions)
                } catch (e: Exception) {
                    // 忽略預加載錯誤
                    println("⚠️ 預加載失敗: $address:$chainId - ${e.message}")
                }
            }
        }
    }
}

/**
 * 交易緩存統計
 */
data class TransactionCacheStats(
    val transactionCacheSize: Int,
    val balanceCacheSize: Int,
    val tokenBalanceCacheSize: Int,
    val totalSize: Int,
    val maxSize: Int
) {
    val utilizationPercent: Double
        get() = (totalSize.toDouble() / maxSize) * 100
    
    fun toDebugString(): String {
        return """
            |Cache Statistics:
            |  Transactions: $transactionCacheSize entries
            |  Balances: $balanceCacheSize entries
            |  Token Balances: $tokenBalanceCacheSize entries
            |  Total: $totalSize / $maxSize (${utilizationPercent.format(1)}%)
        """.trimMargin()
    }
}

// 擴展函數已移至 utils/Extensions.kt
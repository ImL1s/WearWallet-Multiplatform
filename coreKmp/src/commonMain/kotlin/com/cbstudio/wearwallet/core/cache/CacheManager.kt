package com.cbstudio.wearwallet.core.cache

import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours

/**
 * 快取管理器
 * 
 * 提供多層級快取策略，優化資料存取效能
 * 
 * Created: 2025-01-17
 */
class CacheManager<K, V>(
    private val maxSize: Int = 100,
    private val defaultTtl: Duration = 5.minutes,
    private val name: String = "Cache"
) {
    private val cache = mutableMapOf<K, ManagerCacheEntry<V>>()
    private val mutex = Mutex()
    private val accessOrder = mutableListOf<K>()
    
    /**
     * 快取統計
     */
    private var hitCount = 0L
    private var missCount = 0L
    private var evictionCount = 0L
    
    /**
     * 獲取快取值
     */
    suspend fun get(key: K): V? = mutex.withLock {
        val entry = cache[key]
        
        return if (entry != null && !entry.isExpired()) {
            hitCount++
            // 更新 LRU 順序
            accessOrder.remove(key)
            accessOrder.add(key)
            Logger.d("CacheManager", "[$name] Cache hit for key: $key")
            entry.value
        } else {
            missCount++
            if (entry?.isExpired() == true) {
                cache.remove(key)
                accessOrder.remove(key)
                Logger.d("CacheManager", "[$name] Cache expired for key: $key")
            }
            null
        }
    }
    
    /**
     * 設置快取值
     */
    suspend fun put(
        key: K, 
        value: V, 
        ttl: Duration = defaultTtl
    ) = mutex.withLock {
        // 檢查容量
        if (cache.size >= maxSize && !cache.containsKey(key)) {
            evictLeastRecentlyUsed()
        }
        
        cache[key] = ManagerCacheEntry(
            value = value,
            createdAt = Clock.System.now(),
            ttl = ttl
        )
        
        // 更新 LRU 順序
        accessOrder.remove(key)
        accessOrder.add(key)
        
        Logger.d("CacheManager", "[$name] Cached key: $key with TTL: $ttl")
    }
    
    /**
     * 獲取或計算值
     */
    suspend fun getOrPut(
        key: K,
        ttl: Duration = defaultTtl,
        compute: suspend () -> V
    ): V {
        get(key)?.let { return it }
        
        val value = compute()
        put(key, value, ttl)
        return value
    }
    
    /**
     * 移除快取值
     */
    suspend fun remove(key: K) = mutex.withLock {
        cache.remove(key)
        accessOrder.remove(key)
        Logger.d("CacheManager", "[$name] Removed key: $key")
    }
    
    /**
     * 清空快取
     */
    suspend fun clear() = mutex.withLock {
        val size = cache.size
        cache.clear()
        accessOrder.clear()
        Logger.d("CacheManager", "[$name] Cleared $size entries")
    }
    
    /**
     * 清理過期項目
     */
    suspend fun evictExpired() = mutex.withLock {
        val expired = cache.filter { it.value.isExpired() }.keys
        expired.forEach { key ->
            cache.remove(key)
            accessOrder.remove(key)
            evictionCount++
        }
        
        if (expired.isNotEmpty()) {
            Logger.d("CacheManager", 
                "[$name] Evicted ${expired.size} expired entries")
        }
    }
    
    /**
     * LRU 驅逐
     */
    private fun evictLeastRecentlyUsed() {
        if (accessOrder.isNotEmpty()) {
            val lruKey = accessOrder.removeAt(0)
            cache.remove(lruKey)
            evictionCount++
            Logger.d("CacheManager", "[$name] Evicted LRU key: $lruKey")
        }
    }
    
    /**
     * 獲取快取統計
     */
    fun getStats(): CacheStats {
        val total = hitCount + missCount
        return CacheStats(
            name = name,
            size = cache.size,
            maxSize = maxSize,
            hitCount = hitCount,
            missCount = missCount,
            evictionCount = evictionCount,
            hitRate = if (total > 0) hitCount.toDouble() / total else 0.0
        )
    }
    
    /**
     * 管理器快取項目
     */
    private data class ManagerCacheEntry<V>(
        val value: V,
        val createdAt: Instant,
        val ttl: Duration
    ) {
        fun isExpired(): Boolean {
            val now = Clock.System.now()
            return (now - createdAt) > ttl
        }
    }
}

/**
 * 快取統計
 */
data class CacheStats(
    val name: String,
    val size: Int,
    val maxSize: Int,
    val hitCount: Long,
    val missCount: Long,
    val evictionCount: Long,
    val hitRate: Double
) {
    fun printStats() {
        println("""
            ╔════════════════════════════════════════╗
            ║         Cache Statistics: $name         ║
            ╚════════════════════════════════════════╝
            
            📊 Size: $size / $maxSize
            ✅ Hits: $hitCount
            ❌ Misses: $missCount
            🗑️ Evictions: $evictionCount
            📈 Hit Rate: ${(hitRate * 100).toInt()}%
        """.trimIndent())
    }
}

/**
 * 多層級快取
 */
class MultiLevelCache<K, V>(
    private val l1MaxSize: Int = 50,
    private val l2MaxSize: Int = 200,
    private val l1Ttl: Duration = 1.minutes,
    private val l2Ttl: Duration = 10.minutes
) {
    private val l1Cache = CacheManager<K, V>(l1MaxSize, l1Ttl, "L1")
    private val l2Cache = CacheManager<K, V>(l2MaxSize, l2Ttl, "L2")
    
    /**
     * 獲取值（優先從 L1，再從 L2）
     */
    suspend fun get(key: K): V? {
        // 嘗試從 L1 獲取
        l1Cache.get(key)?.let { 
            Logger.d("MultiLevelCache", "L1 hit for key: $key")
            return it 
        }
        
        // 嘗試從 L2 獲取
        l2Cache.get(key)?.let { value ->
            Logger.d("MultiLevelCache", "L2 hit for key: $key, promoting to L1")
            // 提升到 L1
            l1Cache.put(key, value)
            return value
        }
        
        Logger.d("MultiLevelCache", "Cache miss for key: $key")
        return null
    }
    
    /**
     * 設置值（同時設置 L1 和 L2）
     */
    suspend fun put(key: K, value: V) {
        l1Cache.put(key, value)
        l2Cache.put(key, value)
        Logger.d("MultiLevelCache", "Cached key: $key in L1 and L2")
    }
    
    /**
     * 獲取或計算
     */
    suspend fun getOrPut(
        key: K,
        compute: suspend () -> V
    ): V {
        get(key)?.let { return it }
        
        val value = compute()
        put(key, value)
        return value
    }
    
    /**
     * 清空所有層級
     */
    suspend fun clear() {
        l1Cache.clear()
        l2Cache.clear()
    }
    
    /**
     * 獲取統計
     */
    fun getStats(): Pair<CacheStats, CacheStats> {
        return l1Cache.getStats() to l2Cache.getStats()
    }
}

/**
 * 全局快取實例
 */
object GlobalCacheManager {
    // 錢包資料快取
    val walletCache = CacheManager<String, Any>(
        maxSize = 50,
        defaultTtl = 5.minutes,
        name = "Wallet"
    )
    
    // 代幣價格快取
    val priceCache = CacheManager<String, Double>(
        maxSize = 100,
        defaultTtl = 1.minutes,
        name = "Price"
    )
    
    // 交易歷史快取
    val transactionCache = MultiLevelCache<String, List<Any>>(
        l1MaxSize = 20,
        l2MaxSize = 100,
        l1Ttl = 30.minutes.div(30), // 1 minute
        l2Ttl = 30.minutes
    )
    
    // NFT 資料快取
    val nftCache = CacheManager<String, Any>(
        maxSize = 200,
        defaultTtl = 1.hours,
        name = "NFT"
    )
    
    /**
     * 清空所有快取
     */
    suspend fun clearAll() {
        walletCache.clear()
        priceCache.clear()
        transactionCache.clear()
        nftCache.clear()
        Logger.d("GlobalCacheManager", "All caches cleared")
    }
    
    /**
     * 打印所有快取統計
     */
    fun printAllStats() {
        println("\n=== Global Cache Statistics ===\n")
        walletCache.getStats().printStats()
        priceCache.getStats().printStats()
        
        val (l1Stats, l2Stats) = transactionCache.getStats()
        println("\n--- Transaction Cache ---")
        l1Stats.printStats()
        l2Stats.printStats()
        
        nftCache.getStats().printStats()
    }
}
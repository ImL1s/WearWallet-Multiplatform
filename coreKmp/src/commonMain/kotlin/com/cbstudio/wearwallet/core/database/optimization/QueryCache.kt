package com.cbstudio.wearwallet.core.database.optimization

import com.cbstudio.wearwallet.core.cache.CacheManager
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 資料庫查詢快取
 * 
 * 快取頻繁查詢的結果以減少資料庫存取
 * 
 * Created: 2025-01-17
 */
class QueryCache<K, V>(
    private val maxSize: Int = 1000,
    private val defaultTtl: Duration = 5.minutes
) {
    
    private val cache = CacheManager<K, V>(
        maxSize = maxSize,
        defaultTtl = defaultTtl,
        name = "QueryCache"
    )
    
    private val invalidationRules = mutableMapOf<String, MutableSet<K>>()
    private val mutex = Mutex()
    
    /**
     * 獲取或計算快取值
     */
    suspend fun getOrCompute(
        key: K,
        ttl: Duration = defaultTtl,
        compute: suspend () -> V
    ): V {
        return cache.getOrPut(key, ttl) {
            compute()
        }
    }
    
    /**
     * 註冊失效規則
     */
    suspend fun registerInvalidationRule(
        tableName: String,
        affectedKeys: Set<K>
    ) = mutex.withLock {
        invalidationRules.getOrPut(tableName) { mutableSetOf() }.addAll(affectedKeys)
    }
    
    /**
     * 當表更新時失效相關快取
     */
    suspend fun invalidateOnTableUpdate(tableName: String) = mutex.withLock {
        val keysToInvalidate = invalidationRules[tableName] ?: emptySet()
        
        keysToInvalidate.forEach { key ->
            cache.remove(key)
        }
        
        if (keysToInvalidate.isNotEmpty()) {
            Logger.d("QueryCache", 
                "Invalidated ${keysToInvalidate.size} cache entries for table $tableName")
        }
    }
    
    /**
     * 清除所有快取
     */
    suspend fun clear() {
        cache.clear()
        mutex.withLock {
            invalidationRules.clear()
        }
    }
    
    /**
     * 獲取快取統計
     */
    fun getStats() = cache.getStats()
}

/**
 * 查詢結果快取包裝器
 */
class CachedQueryExecutor(
    private val queryCache: QueryCache<String, Any> = QueryCache()
) {
    
    /**
     * 執行快取查詢
     */
    suspend fun <T> executeCached(
        queryId: String,
        cacheDuration: Duration = 1.minutes,
        invalidateOnTables: List<String> = emptyList(),
        query: suspend () -> T
    ): T {
        // 註冊失效規則
        if (invalidateOnTables.isNotEmpty()) {
            queryCache.registerInvalidationRule(
                invalidateOnTables.first(),
                setOf(queryId)
            )
        }
        
        @Suppress("UNCHECKED_CAST")
        return queryCache.getOrCompute(
            key = queryId,
            ttl = cacheDuration
        ) {
            query() as Any
        } as T
    }
    
    /**
     * 執行並快取列表查詢
     */
    suspend fun <T> executeCachedList(
        queryId: String,
        cacheDuration: Duration = 1.minutes,
        query: suspend () -> List<T>
    ): List<T> {
        @Suppress("UNCHECKED_CAST")
        return queryCache.getOrCompute(
            key = queryId,
            ttl = cacheDuration
        ) {
            query() as Any
        } as List<T>
    }
    
    /**
     * 執行並快取單一結果查詢
     */
    suspend fun <T> executeCachedSingle(
        queryId: String,
        cacheDuration: Duration = 30.seconds,
        query: suspend () -> T?
    ): T? {
        @Suppress("UNCHECKED_CAST")
        return queryCache.getOrCompute(
            key = queryId,
            ttl = cacheDuration
        ) {
            query() ?: CacheNull
        }.let { 
            if (it is CacheNull) null else it as T
        }
    }
    
    /**
     * 執行並快取計數查詢
     */
    suspend fun executeCachedCount(
        queryId: String,
        cacheDuration: Duration = 10.seconds,
        query: suspend () -> Long
    ): Long {
        return queryCache.getOrCompute(
            key = queryId,
            ttl = cacheDuration
        ) {
            query()
        } as Long
    }
    
    /**
     * 通知表更新
     */
    suspend fun notifyTableUpdate(tableName: String) {
        queryCache.invalidateOnTableUpdate(tableName)
    }
    
    /**
     * 清除所有快取
     */
    suspend fun clearCache() {
        queryCache.clear()
    }
    
    /**
     * 獲取快取統計
     */
    fun getCacheStats() = queryCache.getStats()
}

/**
 * 空值標記（用於區分 null 和未快取）
 */
private object CacheNull

/**
 * 智能查詢快取策略
 */
object SmartCacheStrategy {
    
    /**
     * 根據查詢類型決定快取時間
     */
    fun getCacheDuration(queryType: QueryType): Duration {
        return when (queryType) {
            QueryType.STATIC_DATA -> 30.minutes      // 靜態資料（如設定）
            QueryType.USER_DATA -> 5.minutes         // 使用者資料
            QueryType.PRICE_DATA -> 30.seconds       // 價格資料
            QueryType.TRANSACTION -> 10.seconds      // 交易資料
            QueryType.COUNT -> 5.seconds             // 計數查詢
            QueryType.SEARCH -> 1.minutes            // 搜尋結果
            QueryType.AGGREGATE -> 30.seconds        // 聚合查詢
        }
    }
    
    /**
     * 判斷查詢是否應該快取
     */
    fun shouldCache(queryType: QueryType, resultSize: Int): Boolean {
        return when {
            // 不快取超大結果集
            resultSize > 10000 -> false
            
            // 總是快取靜態資料
            queryType == QueryType.STATIC_DATA -> true
            
            // 根據類型決定
            queryType == QueryType.TRANSACTION -> resultSize < 100
            queryType == QueryType.SEARCH -> resultSize < 500
            
            // 預設快取
            else -> true
        }
    }
}

/**
 * 查詢類型
 */
enum class QueryType {
    STATIC_DATA,    // 靜態資料
    USER_DATA,      // 使用者資料
    PRICE_DATA,     // 價格資料
    TRANSACTION,    // 交易資料
    COUNT,          // 計數查詢
    SEARCH,         // 搜尋查詢
    AGGREGATE       // 聚合查詢
}

/**
 * 全局查詢快取
 */
object GlobalQueryCache {
    
    private val caches = mutableMapOf<String, CachedQueryExecutor>()
    
    /**
     * 獲取或創建快取執行器
     */
    fun getExecutor(name: String = "default"): CachedQueryExecutor {
        return caches.getOrPut(name) { CachedQueryExecutor() }
    }
    
    /**
     * 清除所有快取
     */
    suspend fun clearAll() {
        caches.values.forEach { it.clearCache() }
        Logger.d("GlobalQueryCache", "All query caches cleared")
    }
    
    /**
     * 打印快取統計
     */
    fun printStats() {
        println("""
            ╔════════════════════════════════════════╗
            ║       Query Cache Statistics           ║
            ╚════════════════════════════════════════╝
        """.trimIndent())
        
        caches.forEach { (name, executor) ->
            val stats = executor.getCacheStats()
            println("""
                📊 Cache: $name
                • Size: ${stats.size}/${stats.maxSize}
                • Hit Rate: ${(stats.hitRate * 100).toInt()}%
                • Hits: ${stats.hitCount}
                • Misses: ${stats.missCount}
                • Evictions: ${stats.evictionCount}
            """.trimIndent())
        }
    }
}
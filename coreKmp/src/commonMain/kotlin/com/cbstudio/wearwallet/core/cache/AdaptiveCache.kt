package com.cbstudio.wearwallet.core.cache

import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 自適應快取管理器
 * 
 * 根據存取模式動態調整快取策略
 * 
 * Created: 2025-01-17
 */
class AdaptiveCache<K, V>(
    private val maxSize: Int = 1000,
    private val minTtl: Duration = 30.seconds,
    private val maxTtl: Duration = 30.minutes
) {
    
    private val cache = mutableMapOf<K, AdaptiveCacheEntry<V>>()
    private val accessHistory = mutableMapOf<K, AccessPattern>()
    private val mutex = Mutex()
    
    /**
     * 獲取或計算值（自適應 TTL）
     */
    suspend fun getOrCompute(
        key: K,
        compute: suspend () -> V
    ): V = mutex.withLock {
        val now = Clock.System.now()
        val entry = cache[key]
        
        // 更新存取歷史
        updateAccessHistory(key, now)
        
        // 檢查快取是否有效
        if (entry != null && !entry.isExpired(now)) {
            entry.lastAccess = now
            entry.hitCount++
            return entry.value
        }
        
        // 計算新值
        val value = compute()
        
        // 計算自適應 TTL
        val adaptiveTtl = calculateAdaptiveTtl(key)
        
        // 儲存到快取
        cache[key] = AdaptiveCacheEntry(
            value = value,
            createdAt = now,
            ttl = adaptiveTtl,
            lastAccess = now
        )
        
        // 清理過期項目
        evictIfNeeded()
        
        return value
    }
    
    /**
     * 計算自適應 TTL
     */
    private fun calculateAdaptiveTtl(key: K): Duration {
        val pattern = accessHistory[key] ?: return minTtl
        
        // 基於存取頻率計算 TTL
        val frequency = pattern.accessCount.toDouble() / 
            (Clock.System.now() - pattern.firstAccess).inWholeMinutes.coerceAtLeast(1)
        
        // 高頻存取 = 長 TTL，低頻存取 = 短 TTL
        val ttl = when {
            frequency > 10 -> maxTtl                                        // 非常頻繁
            frequency > 5 -> (maxTtl.inWholeMilliseconds * 0.75).toLong().milliseconds    // 頻繁
            frequency > 2 -> (maxTtl.inWholeMilliseconds * 0.5).toLong().milliseconds     // 中等
            frequency > 1 -> (maxTtl.inWholeMilliseconds * 0.25).toLong().milliseconds    // 偶爾
            else -> minTtl                                                   // 罕見
        }
        
        // 基於存取間隔調整
        val avgInterval = pattern.averageInterval
        if (avgInterval < 1.minutes) {
            // 快速連續存取，延長 TTL
            val extendedTtl = (ttl.inWholeMilliseconds * 1.5).toLong().milliseconds
            return if (extendedTtl.inWholeMilliseconds < maxTtl.inWholeMilliseconds) extendedTtl else maxTtl
        }
        
        return ttl
    }
    
    /**
     * 更新存取歷史
     */
    private fun updateAccessHistory(key: K, now: Instant) {
        val pattern = accessHistory.getOrPut(key) {
            AccessPattern(firstAccess = now)
        }
        
        // 更新存取計數和時間
        pattern.accessCount++
        pattern.lastAccess?.let { last ->
            val interval = now - last
            pattern.intervals.add(interval)
            
            // 只保留最近 10 次間隔
            if (pattern.intervals.size > 10) {
                pattern.intervals.removeAt(0)
            }
        }
        pattern.lastAccess = now
    }
    
    /**
     * 清理過期或超量項目
     */
    private fun evictIfNeeded() {
        val now = Clock.System.now()
        
        // 移除過期項目
        val toRemove = cache.entries.filter { (_, entry) ->
            entry.isExpired(now)
        }.map { it.key }
        toRemove.forEach { cache.remove(it) }
        
        // 如果仍超過大小限制，使用 LFU 策略
        if (cache.size > maxSize) {
            val toRemove = cache.size - maxSize
            cache.entries
                .sortedBy { it.value.score }
                .take(toRemove)
                .forEach { cache.remove(it.key) }
        }
    }
    
    /**
     * 獲取快取統計
     */
    fun getStatistics(): CacheStatistics {
        val now = Clock.System.now()
        val entries = cache.values
        
        return CacheStatistics(
            size = cache.size,
            totalHits = entries.sumOf { it.hitCount },
            averageTtl = if (entries.isNotEmpty()) {
                entries.map { it.ttl }.reduce { acc, duration -> acc + duration } / entries.size
            } else Duration.ZERO,
            hotKeys = accessHistory.entries
                .sortedByDescending { it.value.accessCount }
                .take(10)
                .map { it.key to it.value.accessCount }
        )
    }
    
    /**
     * 清除所有快取
     */
    suspend fun clear() = mutex.withLock {
        cache.clear()
        accessHistory.clear()
    }
    
    /**
     * 預熱快取
     */
    suspend fun warmUp(
        keys: List<K>,
        loader: suspend (K) -> V
    ) {
        keys.forEach { key ->
            getOrCompute(key) { loader(key) }
        }
        Logger.d("AdaptiveCache", "Warmed up ${keys.size} cache entries")
    }
}

/**
 * 自適應快取項目
 */
private data class AdaptiveCacheEntry<V>(
    val value: V,
    val createdAt: Instant,
    val ttl: Duration,
    var lastAccess: Instant,
    var hitCount: Int = 0
) {
    fun isExpired(now: Instant): Boolean {
        return now - createdAt > ttl
    }
    
    // 計算項目分數（用於 LFU 清理）
    val score: Double
        get() {
            val age = (Clock.System.now() - createdAt).inWholeSeconds
            val recency = (Clock.System.now() - lastAccess).inWholeSeconds
            return hitCount.toDouble() / (age + recency + 1)
        }
}

/**
 * 存取模式
 */
private data class AccessPattern(
    val firstAccess: Instant,
    var lastAccess: Instant? = null,
    var accessCount: Int = 0,
    val intervals: MutableList<Duration> = mutableListOf()
) {
    val averageInterval: Duration
        get() = if (intervals.isNotEmpty()) {
            intervals.reduce { acc, duration -> acc + duration } / intervals.size
        } else Duration.INFINITE
}

/**
 * 快取統計
 */
data class CacheStatistics(
    val size: Int,
    val totalHits: Int,
    val averageTtl: Duration,
    val hotKeys: List<Pair<Any?, Int>>
)

/**
 * 分層快取管理器
 */
class TieredCacheManager<K, V>(
    private val l1MaxSize: Int = 100,    // 記憶體快取
    private val l2MaxSize: Int = 1000,   // 磁碟快取
    private val l3MaxSize: Int = 10000   // 遠端快取
) {
    
    private val l1Cache = AdaptiveCache<K, V>(l1MaxSize)
    private val l2Cache = AdaptiveCache<K, V>(l2MaxSize)
    private val l3Cache = AdaptiveCache<K, V>(l3MaxSize)
    
    /**
     * 獲取值（逐層查找）
     */
    suspend fun get(
        key: K,
        loader: suspend () -> V
    ): V {
        // L1 查找
        return try {
            l1Cache.getOrCompute(key) {
                // L2 查找
                l2Cache.getOrCompute(key) {
                    // L3 查找
                    l3Cache.getOrCompute(key) {
                        // 最終載入
                        loader()
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("TieredCache", "Failed to get value for key: $key", e)
            loader() // 降級到直接載入
        }
    }
    
    /**
     * 預熱所有層級
     */
    suspend fun warmUpAll(
        keys: List<K>,
        loader: suspend (K) -> V
    ) {
        // 預熱 L3（最大容量）
        l3Cache.warmUp(keys, loader)
        
        // 預熱 L2（中等容量）
        val l2Keys = keys.take(l2MaxSize)
        l2Cache.warmUp(l2Keys, loader)
        
        // 預熱 L1（最小容量，最熱資料）
        val l1Keys = keys.take(l1MaxSize)
        l1Cache.warmUp(l1Keys, loader)
    }
    
    /**
     * 獲取所有層級統計
     */
    fun getAllStatistics(): TieredCacheStatistics {
        return TieredCacheStatistics(
            l1Stats = l1Cache.getStatistics(),
            l2Stats = l2Cache.getStatistics(),
            l3Stats = l3Cache.getStatistics()
        )
    }
}

/**
 * 分層快取統計
 */
data class TieredCacheStatistics(
    val l1Stats: CacheStatistics,
    val l2Stats: CacheStatistics,
    val l3Stats: CacheStatistics
) {
    fun printReport() {
        println("""
            ╔════════════════════════════════════════╗
            ║      Tiered Cache Statistics           ║
            ╚════════════════════════════════════════╝
            
            📊 L1 Cache (Memory):
            • Size: ${l1Stats.size}
            • Hits: ${l1Stats.totalHits}
            • Avg TTL: ${l1Stats.averageTtl.inWholeSeconds}s
            
            💾 L2 Cache (Disk):
            • Size: ${l2Stats.size}
            • Hits: ${l2Stats.totalHits}
            • Avg TTL: ${l2Stats.averageTtl.inWholeSeconds}s
            
            ☁️ L3 Cache (Remote):
            • Size: ${l3Stats.size}
            • Hits: ${l3Stats.totalHits}
            • Avg TTL: ${l3Stats.averageTtl.inWholeSeconds}s
            
            🔥 Hot Keys:
            ${l1Stats.hotKeys.take(5).joinToString("\n") { 
                "  • ${it.first}: ${it.second} hits"
            }}
        """.trimIndent())
    }
}
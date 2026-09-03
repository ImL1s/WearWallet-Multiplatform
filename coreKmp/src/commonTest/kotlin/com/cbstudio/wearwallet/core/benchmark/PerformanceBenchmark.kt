package com.cbstudio.wearwallet.core.benchmark

import com.cbstudio.wearwallet.core.cache.*
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.utils.PerformanceMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * 效能基準測試套件
 * 
 * 測試各種操作的效能指標
 * 
 * Created: 2025-01-17
 */
class PerformanceBenchmark {
    
    private lateinit var cacheManager: CacheManager<String, String>
    private lateinit var multiLevelCache: MultiLevelCache<String, List<String>>
    
    @BeforeTest
    fun setup() {
        cacheManager = CacheManager(
            maxSize = 1000,
            defaultTtl = Duration.INFINITE
        )
        
        multiLevelCache = MultiLevelCache()
        
        PerformanceMonitor.setEnabled(true)
        PerformanceMonitor.clearMetrics()
    }
    
    @AfterTest
    fun teardown() = runTest {
        GlobalCacheManager.clearAll()
        PerformanceMonitor.clearMetrics()
    }
    
    /**
     * 測試快取效能
     */
    @Test
    fun benchmark_CachePerformance() = runTest {
        val iterations = 1000
        val testData = (1..iterations).map { "key_$it" to "value_$it" }
        
        // 測試寫入效能
        val writeTime = measureTime {
            testData.forEach { (key, value) ->
                cacheManager.put(key, value)
            }
        }
        
        println("Cache Write Performance:")
        println("  • Total time: ${writeTime.inWholeMilliseconds}ms")
        println("  • Avg per operation: ${writeTime.inWholeMilliseconds / iterations.toDouble()}ms")
        
        // 測試讀取效能（快取命中）
        val readHitTime = measureTime {
            testData.forEach { (key, _) ->
                cacheManager.get(key)
            }
        }
        
        println("\nCache Read Performance (Hit):")
        println("  • Total time: ${readHitTime.inWholeMilliseconds}ms")
        println("  • Avg per operation: ${readHitTime.inWholeMilliseconds / iterations.toDouble()}ms")
        
        // 測試讀取效能（快取未命中）
        val readMissTime = measureTime {
            (iterations..iterations * 2).forEach { 
                cacheManager.get("missing_key_$it")
            }
        }
        
        println("\nCache Read Performance (Miss):")
        println("  • Total time: ${readMissTime.inWholeMilliseconds}ms")
        println("  • Avg per operation: ${readMissTime.inWholeMilliseconds / iterations.toDouble()}ms")
        
        // 驗證快取統計
        val stats = cacheManager.getStats()
        assertEquals(iterations.toLong(), stats.hitCount)
        assertEquals(iterations.toLong(), stats.missCount)
        assertEquals(0.5, stats.hitRate, 0.01)
        
        // 效能斷言
        assertTrue(writeTime < 100.milliseconds, "Cache write should be fast")
        assertTrue(readHitTime < 50.milliseconds, "Cache hit should be very fast")
        assertTrue(readMissTime < 50.milliseconds, "Cache miss should be fast")
    }
    
    /**
     * 測試多層級快取效能
     */
    @Test
    fun benchmark_MultiLevelCachePerformance() = runTest {
        val testData = (1..100).map { 
            "key_$it" to List(10) { j -> "item_${it}_$j" }
        }
        
        // 預熱快取
        testData.forEach { (key, value) ->
            multiLevelCache.put(key, value)
        }
        
        // 測試 L1 命中效能
        val l1HitTime = measureTime {
            repeat(1000) {
                multiLevelCache.get("key_1") // 應該在 L1
            }
        }
        
        println("Multi-Level Cache L1 Hit:")
        println("  • 1000 operations: ${l1HitTime.inWholeMilliseconds}ms")
        println("  • Avg: ${l1HitTime.inWholeMicroseconds / 1000}μs")
        
        // 清除 L1，測試 L2 命中效能
        multiLevelCache.clear()
        testData.forEach { (key, value) ->
            multiLevelCache.put(key, value)
        }
        
        // 等待 L1 過期（實際測試中可能需要調整）
        delay(100)
        
        val (l1Stats, l2Stats) = multiLevelCache.getStats()
        println("\nCache Statistics:")
        println("L1: ${l1Stats.hitCount} hits, ${l1Stats.missCount} misses")
        println("L2: ${l2Stats.hitCount} hits, ${l2Stats.missCount} misses")
        
        assertTrue(l1HitTime < 10.milliseconds, "L1 hit should be extremely fast")
    }
    
    /**
     * 測試並發存取效能
     */
    @Test
    fun benchmark_ConcurrentAccess() = runTest {
        val concurrency = 100
        val operationsPerJob = 100
        
        // 預填充一些資料
        repeat(50) {
            cacheManager.put("shared_$it", "value_$it")
        }
        
        val concurrentTime = measureTime {
            val jobs = (1..concurrency).map { jobId ->
                async {
                    repeat(operationsPerJob) { op ->
                        when (op % 3) {
                            0 -> cacheManager.put("job_${jobId}_$op", "value")
                            1 -> cacheManager.get("shared_${op % 50}")
                            2 -> cacheManager.get("job_${jobId}_${op - 1}")
                        }
                    }
                }
            }
            
            jobs.awaitAll()
        }
        
        val totalOperations = concurrency * operationsPerJob
        println("Concurrent Access Performance:")
        println("  • Concurrency: $concurrency")
        println("  • Total operations: $totalOperations")
        println("  • Total time: ${concurrentTime.inWholeMilliseconds}ms")
        println("  • Throughput: ${totalOperations * 1000 / concurrentTime.inWholeMilliseconds} ops/sec")
        
        assertTrue(
            concurrentTime < 1000.milliseconds,
            "Concurrent operations should complete quickly"
        )
    }
    
    /**
     * 測試 LRU 驅逐效能
     */
    @Test
    fun benchmark_LRUEviction() = runTest {
        val smallCache = CacheManager<Int, String>(
            maxSize = 100,
            defaultTtl = Duration.INFINITE
        )
        
        val evictionTime = measureTime {
            // 插入超過容量的項目
            repeat(1000) { i ->
                smallCache.put(i, "value_$i")
            }
        }
        
        println("LRU Eviction Performance:")
        println("  • 1000 insertions with max size 100")
        println("  • Total time: ${evictionTime.inWholeMilliseconds}ms")
        println("  • Avg per eviction: ${evictionTime.inWholeMicroseconds / 900}μs")
        
        val stats = smallCache.getStats()
        assertEquals(100, stats.size)
        assertTrue(stats.evictionCount >= 900)
        
        // 驗證 LRU 順序
        assertNull(smallCache.get(0)) // 最早的應該被驅逐
        assertNotNull(smallCache.get(999)) // 最新的應該還在
        
        assertTrue(
            evictionTime < 100.milliseconds,
            "LRU eviction should be efficient"
        )
    }
    
    /**
     * 測試過期清理效能
     */
    @Test
    fun benchmark_ExpirationCleanup() = runTest {
        val shortLivedCache = CacheManager<String, String>(
            maxSize = 1000,
            defaultTtl = 10.milliseconds
        )
        
        // 插入會快速過期的項目
        repeat(500) { i ->
            shortLivedCache.put("expired_$i", "value_$i")
        }
        
        // 等待過期
        delay(15)
        
        // 插入新項目
        repeat(500) { i ->
            shortLivedCache.put("fresh_$i", "value_$i")
        }
        
        val cleanupTime = measureTime {
            shortLivedCache.evictExpired()
        }
        
        println("Expiration Cleanup Performance:")
        println("  • Cleanup time: ${cleanupTime.inWholeMilliseconds}ms")
        
        val stats = shortLivedCache.getStats()
        assertEquals(500, stats.size) // 只有新項目
        
        assertTrue(
            cleanupTime < 50.milliseconds,
            "Expiration cleanup should be fast"
        )
    }
    
    /**
     * 測試記憶體使用
     */
    @Test
    fun benchmark_MemoryUsage() = runTest {
        val largeCache = CacheManager<String, ByteArray>(
            maxSize = 1000,
            defaultTtl = Duration.INFINITE
        )
        
        // 插入大量資料
        val dataSize = 1024 // 1KB per entry
        repeat(1000) { i ->
            largeCache.put("data_$i", ByteArray(dataSize))
        }
        
        val stats = largeCache.getStats()
        println("Memory Usage Test:")
        println("  • Entries: ${stats.size}")
        println("  • Estimated size: ${stats.size * dataSize / 1024}KB")
        
        // 測試快取清理
        val clearTime = measureTime {
            largeCache.clear()
        }
        
        println("  • Clear time: ${clearTime.inWholeMilliseconds}ms")
        
        assertEquals(0, largeCache.getStats().size)
        assertTrue(clearTime < 10.milliseconds, "Clear should be fast")
    }
    
    /**
     * 綜合效能測試
     */
    @Test
    fun benchmark_OverallPerformance() = runTest {
        println("\n=== Overall Performance Benchmark ===\n")
        
        val results = mutableMapOf<String, Duration>()
        
        // 測試各種操作
        results["Simple Put"] = measureTime {
            cacheManager.put("test", "value")
        }
        
        results["Simple Get (Hit)"] = measureTime {
            cacheManager.get("test")
        }
        
        results["Simple Get (Miss)"] = measureTime {
            cacheManager.get("missing")
        }
        
        results["GetOrPut"] = measureTime {
            cacheManager.getOrPut("computed") {
                delay(1) // 模擬計算
                "computed_value"
            }
        }
        
        // 批量操作
        results["Batch Put (100)"] = measureTime {
            repeat(100) { i ->
                cacheManager.put("batch_$i", "value_$i")
            }
        }
        
        results["Batch Get (100)"] = measureTime {
            repeat(100) { i ->
                cacheManager.get("batch_$i")
            }
        }
        
        // 打印結果
        println("Performance Results:")
        results.forEach { (operation, duration) ->
            println("  • $operation: ${duration.inWholeMicroseconds}μs")
        }
        
        // 效能斷言
        assertTrue(results["Simple Put"]!! < 1.milliseconds)
        assertTrue(results["Simple Get (Hit)"]!! < 1.milliseconds)
        assertTrue(results["Simple Get (Miss)"]!! < 1.milliseconds)
        assertTrue(results["Batch Put (100)"]!! < 10.milliseconds)
        assertTrue(results["Batch Get (100)"]!! < 5.milliseconds)
        
        // 獲取 PerformanceMonitor 報告
        val report = PerformanceMonitor.getMetricsReport()
        report.printSummary()
    }
}

/**
 * 效能基準執行器
 */
object BenchmarkRunner {
    
    suspend fun runAllBenchmarks() {
        println("""
            ╔════════════════════════════════════════╗
            ║      WearWallet Performance Benchmark   ║
            ╚════════════════════════════════════════╝
        """.trimIndent())
        
        val benchmark = PerformanceBenchmark()
        
        val tests = listOf(
            "Cache Performance" to { benchmark.benchmark_CachePerformance() },
            "Multi-Level Cache" to { benchmark.benchmark_MultiLevelCachePerformance() },
            "Concurrent Access" to { benchmark.benchmark_ConcurrentAccess() },
            "LRU Eviction" to { benchmark.benchmark_LRUEviction() },
            "Expiration Cleanup" to { benchmark.benchmark_ExpirationCleanup() },
            "Memory Usage" to { benchmark.benchmark_MemoryUsage() },
            "Overall Performance" to { benchmark.benchmark_OverallPerformance() }
        )
        
        tests.forEach { (name, test) ->
            println("\n📊 Running: $name")
            println("=" * 50)
            
            try {
                val duration = measureTime {
                    benchmark.setup()
                    test()
                    benchmark.teardown()
                }
                
                println("\n✅ $name completed in ${duration.inWholeMilliseconds}ms")
            } catch (e: Exception) {
                println("\n❌ $name failed: ${e.message}")
            }
        }
        
        println("\n" + "=" * 50)
        println("Benchmark Suite Completed!")
    }
}

// Kotlin 沒有內建的字串重複運算子，這是一個擴展函數
private operator fun String.times(count: Int): String = repeat(count)
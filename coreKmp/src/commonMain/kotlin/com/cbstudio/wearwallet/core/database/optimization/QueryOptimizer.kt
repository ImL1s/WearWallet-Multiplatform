package com.cbstudio.wearwallet.core.database.optimization

import com.cbstudio.wearwallet.core.monitoring.FirebasePerformance
import com.cbstudio.wearwallet.core.monitoring.GlobalMetrics
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * 資料庫查詢優化器
 * 
 * 提供查詢效能分析、優化建議和自動索引管理
 * 
 * Created: 2025-01-17
 */
class QueryOptimizer {
    
    private val queryStats = mutableMapOf<String, QueryStatistics>()
    private val slowQueryThreshold = 100.milliseconds
    private val indexSuggestions = mutableSetOf<IndexSuggestion>()
    
    /**
     * 分析查詢效能
     */
    suspend fun <T> analyzeQuery(
        queryName: String,
        query: suspend () -> T
    ): T {
        val trace = FirebasePerformance.startTrace("DB_Query_$queryName")
        
        val result: T
        val duration = measureTime {
            result = query()
        }
        
        // 記錄到 Firebase
        trace.putMetric("duration_ms", duration.inWholeMilliseconds)
        trace.setAttribute("query_name", queryName)
        
        // 更新統計
        updateStatistics(queryName, duration)
        
        // 檢查是否為慢查詢
        if (duration > slowQueryThreshold) {
            handleSlowQuery(queryName, duration)
            trace.setAttribute("slow_query", "true")
        }
        
        trace.stop()
        
        // 記錄到全局指標
        GlobalMetrics.collector.recordHistogram(
            "db_query_duration_${queryName.lowercase()}", 
            duration.inWholeMilliseconds
        )
        
        return result
    }
    
    /**
     * 批量查詢優化
     */
    suspend fun <T> optimizeBatchQuery(
        queryName: String,
        items: List<T>,
        batchSize: Int = 100,
        query: suspend (List<T>) -> Unit
    ) {
        if (items.isEmpty()) return
        
        val trace = FirebasePerformance.startTrace("DB_Batch_$queryName")
        trace.putMetric("total_items", items.size.toLong())
        trace.putMetric("batch_size", batchSize.toLong())
        
        val duration = measureTime {
            coroutineScope {
                items.chunked(batchSize).map { batch ->
                    async {
                        analyzeQuery("${queryName}_batch") {
                            query(batch)
                        }
                    }
                }.awaitAll()
            }
        }
        
        trace.putMetric("duration_ms", duration.inWholeMilliseconds)
        trace.putMetric("avg_per_item_ms", duration.inWholeMilliseconds / items.size)
        trace.stop()
        
        Logger.d("QueryOptimizer", 
            "Batch query $queryName: ${items.size} items in ${duration.inWholeMilliseconds}ms")
    }
    
    /**
     * 更新查詢統計
     */
    private fun updateStatistics(queryName: String, duration: Duration) {
        val stats = queryStats.getOrPut(queryName) { 
            QueryStatistics(queryName) 
        }
        
        queryStats[queryName] = stats.copy(
            executionCount = stats.executionCount + 1,
            totalDuration = stats.totalDuration + duration,
            minDuration = minOf(stats.minDuration, duration),
            maxDuration = maxOf(stats.maxDuration, duration),
            lastExecutionTime = Clock.System.now()
        )
    }
    
    /**
     * 處理慢查詢
     */
    private fun handleSlowQuery(queryName: String, duration: Duration) {
        Logger.w("QueryOptimizer", 
            "⚠️ Slow query detected: $queryName took ${duration.inWholeMilliseconds}ms")
        
        // 分析可能的優化
        suggestOptimizations(queryName)
        
        // 記錄到 Firebase
        FirebasePerformance.setAttribute("slow_query_detected", queryName)
        GlobalMetrics.collector.incrementCounter("slow_queries")
    }
    
    /**
     * 建議優化方案
     */
    private fun suggestOptimizations(queryName: String) {
        when {
            queryName.contains("selectBy", ignoreCase = true) -> {
                suggestIndex(queryName)
            }
            queryName.contains("join", ignoreCase = true) -> {
                Logger.i("QueryOptimizer", 
                    "💡 Consider denormalizing data to avoid JOIN operations")
            }
            queryName.contains("count", ignoreCase = true) -> {
                Logger.i("QueryOptimizer", 
                    "💡 Consider caching count results or using approximate counts")
            }
            queryName.contains("like", ignoreCase = true) -> {
                Logger.i("QueryOptimizer", 
                    "💡 Consider using FTS (Full Text Search) for text searches")
            }
        }
    }
    
    /**
     * 建議索引
     */
    private fun suggestIndex(queryName: String) {
        val suggestion = when {
            queryName.contains("wallet_id", ignoreCase = true) -> {
                IndexSuggestion("wallet_id", "idx_wallet_id", IndexType.STANDARD)
            }
            queryName.contains("address", ignoreCase = true) -> {
                IndexSuggestion("address", "idx_address", IndexType.STANDARD)
            }
            queryName.contains("timestamp", ignoreCase = true) -> {
                IndexSuggestion("timestamp", "idx_timestamp", IndexType.STANDARD)
            }
            queryName.contains("status", ignoreCase = true) -> {
                IndexSuggestion("status", "idx_status", IndexType.STANDARD)
            }
            else -> null
        }
        
        suggestion?.let { 
            if (indexSuggestions.add(it)) {
                Logger.i("QueryOptimizer", 
                    "💡 Consider adding index on ${it.column}: CREATE INDEX ${it.indexName} ON table(${it.column})")
            }
        }
    }
    
    /**
     * 獲取效能報告
     */
    fun getPerformanceReport(): DatabasePerformanceReport {
        val sortedStats = queryStats.values.sortedByDescending { it.averageDuration }
        
        return DatabasePerformanceReport(
            totalQueries = queryStats.size,
            totalExecutions = sortedStats.sumOf { it.executionCount },
            averageQueryTime = if (sortedStats.isNotEmpty()) {
                sortedStats.map { it.averageDuration }.reduce { acc, duration -> 
                    acc + duration 
                } / sortedStats.size
            } else Duration.ZERO,
            slowestQuery = sortedStats.firstOrNull(),
            mostFrequentQuery = sortedStats.maxByOrNull { it.executionCount },
            indexSuggestions = indexSuggestions.toList(),
            queryStatistics = sortedStats
        )
    }
    
    /**
     * 清除統計資料
     */
    fun clearStatistics() {
        queryStats.clear()
        indexSuggestions.clear()
        Logger.d("QueryOptimizer", "Query statistics cleared")
    }
}

/**
 * 查詢統計
 */
data class QueryStatistics(
    val queryName: String,
    val executionCount: Int = 0,
    val totalDuration: Duration = Duration.ZERO,
    val minDuration: Duration = Duration.INFINITE,
    val maxDuration: Duration = Duration.ZERO,
    val lastExecutionTime: kotlinx.datetime.Instant? = null
) {
    val averageDuration: Duration
        get() = if (executionCount > 0) totalDuration / executionCount else Duration.ZERO
}

/**
 * 索引建議
 */
data class IndexSuggestion(
    val column: String,
    val indexName: String,
    val type: IndexType,
    val reason: String = "Frequent queries on this column"
)

/**
 * 索引類型
 */
enum class IndexType {
    STANDARD,      // 標準 B-tree 索引
    UNIQUE,        // 唯一索引
    COMPOSITE,     // 複合索引
    PARTIAL,       // 部分索引
    EXPRESSION     // 表達式索引
}

/**
 * 資料庫效能報告
 */
data class DatabasePerformanceReport(
    val totalQueries: Int,
    val totalExecutions: Int,
    val averageQueryTime: Duration,
    val slowestQuery: QueryStatistics?,
    val mostFrequentQuery: QueryStatistics?,
    val indexSuggestions: List<IndexSuggestion>,
    val queryStatistics: List<QueryStatistics>
) {
    fun printReport() {
        println("""
            ╔════════════════════════════════════════╗
            ║     Database Performance Report         ║
            ╚════════════════════════════════════════╝
            
            📊 Overview:
            • Total Queries: $totalQueries
            • Total Executions: $totalExecutions
            • Average Query Time: ${averageQueryTime.inWholeMilliseconds}ms
            
            🐌 Slowest Query:
            ${slowestQuery?.let { 
                "• ${it.queryName}: ${it.maxDuration.inWholeMilliseconds}ms (max)"
            } ?: "N/A"}
            
            🔥 Most Frequent Query:
            ${mostFrequentQuery?.let { 
                "• ${it.queryName}: ${it.executionCount} executions"
            } ?: "N/A"}
            
            💡 Index Suggestions:
            ${indexSuggestions.joinToString("\n") { 
                "• Add ${it.type} index on ${it.column}: ${it.indexName}"
            }}
            
            📈 Top 5 Slowest Queries:
            ${queryStatistics.take(5).mapIndexed { index, stats ->
                "${index + 1}. ${stats.queryName}: avg ${stats.averageDuration.inWholeMilliseconds}ms (${stats.executionCount} calls)"
            }.joinToString("\n            ")}
        """.trimIndent())
    }
}

/**
 * 全局查詢優化器實例
 */
object GlobalQueryOptimizer {
    val optimizer = QueryOptimizer()
    
    /**
     * 分析查詢
     */
    suspend fun <T> analyze(
        queryName: String,
        query: suspend () -> T
    ): T = optimizer.analyzeQuery(queryName, query)
    
    /**
     * 獲取報告
     */
    fun getReport(): DatabasePerformanceReport = optimizer.getPerformanceReport()
    
    /**
     * 打印報告
     */
    fun printReport() = optimizer.getPerformanceReport().printReport()
}
package com.cbstudio.wearwallet.core.utils

import com.cbstudio.wearwallet.core.monitoring.FirebasePerformance
import com.cbstudio.wearwallet.core.monitoring.GlobalMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * 效能監控工具
 * 
 * 用於追蹤 UseCase 執行時間、記憶體使用和錯誤率
 * 整合 Firebase Performance Monitoring
 * 
 * Created: 2025-01-17
 */
object PerformanceMonitor {
    
    private val metrics = mutableMapOf<String, UseCaseMetrics>()
    private var isEnabled = true
    private var firebaseIntegrationEnabled = true
    
    /**
     * 監控 Flow 執行
     */
    fun <T> Flow<T>.withPerformanceMonitoring(
        useCaseName: String,
        metadata: Map<String, Any> = emptyMap()
    ): Flow<T> {
        if (!isEnabled) return this

        var startTime: Long = 0L
        var endTime: Long = 0L
        var hasError = false
        
        // 開始 Firebase 追蹤
        val firebaseTrace = if (firebaseIntegrationEnabled) {
            FirebasePerformance.startTrace("UseCase_$useCaseName")
        } else null
        
        // 設定 Firebase 追蹤屬性
        firebaseTrace?.apply {
            metadata.forEach { (key, value) ->
                setAttribute(key, value.toString())
            }
        }
        
        return this
            .onStart {
                startTime = currentTimeMillis()
                logStart(useCaseName, metadata)
            }
            .onEach { value ->
                // Track intermediate emissions
                if (value is com.cbstudio.wearwallet.core.common.Result.Failure) {
                    hasError = true
                    firebaseTrace?.incrementMetric("error_count")
                }
            }
            .onCompletion { throwable ->
                endTime = currentTimeMillis()
                
                if (throwable != null) {
                    hasError = true
                    logError(useCaseName, throwable)
                    firebaseTrace?.incrementMetric("error_count")
                }
                
                val duration = if (startTime > 0 && endTime > 0) {
                    endTime - startTime
                } else {
                    0L
                }
                
                // 記錄到 Firebase
                firebaseTrace?.apply {
                    putMetric("duration_ms", duration)
                    setAttribute("success", (!hasError).toString())
                    stop()
                }
                
                // 記錄到全局指標
                if (firebaseIntegrationEnabled) {
                    GlobalMetrics.collector.recordHistogram("usecase_duration_$useCaseName", duration)
                    if (hasError) {
                        GlobalMetrics.collector.incrementCounter("usecase_errors_$useCaseName")
                    }
                }
                
                recordMetrics(
                    useCaseName = useCaseName,
                    duration = duration,
                    hasError = hasError,
                    metadata = metadata
                )
                
                logEnd(useCaseName, duration, hasError)
            }
    }
    
    /**
     * 記錄指標
     */
    private fun recordMetrics(
        useCaseName: String,
        duration: Long,
        hasError: Boolean,
        metadata: Map<String, Any>
    ) {
        val existing = metrics[useCaseName] ?: UseCaseMetrics(useCaseName)
        
        metrics[useCaseName] = existing.copy(
            totalExecutions = existing.totalExecutions + 1,
            totalDuration = existing.totalDuration + duration,
            minDuration = minOf(existing.minDuration, duration),
            maxDuration = maxOf(existing.maxDuration, duration),
            errorCount = if (hasError) existing.errorCount + 1 else existing.errorCount,
            lastExecutionTime = Clock.System.now(),
            lastMetadata = metadata
        )
    }
    
    /**
     * 獲取指標報告
     */
    fun getMetricsReport(): PerformanceReport {
        val sortedMetrics = metrics.values.sortedByDescending { it.totalExecutions }
        
        return PerformanceReport(
            totalUseCases = metrics.size,
            totalExecutions = sortedMetrics.sumOf { it.totalExecutions },
            averageExecutionTime = if (sortedMetrics.isNotEmpty()) {
                sortedMetrics.sumOf { it.averageDuration } / sortedMetrics.size
            } else 0L,
            slowestUseCase = sortedMetrics.maxByOrNull { it.maxDuration },
            mostFrequentUseCase = sortedMetrics.firstOrNull(),
            errorRate = calculateErrorRate(),
            useCaseMetrics = sortedMetrics
        )
    }
    
    /**
     * 清除指標
     */
    fun clearMetrics() {
        metrics.clear()
        Logger.d("PerformanceMonitor", "Metrics cleared")
    }
    
    /**
     * 啟用/禁用監控
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        Logger.d("PerformanceMonitor", "Monitoring ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * 啟用/禁用 Firebase 整合
     */
    fun setFirebaseIntegration(enabled: Boolean) {
        firebaseIntegrationEnabled = enabled
        FirebasePerformance.setEnabled(enabled)
        Logger.d("PerformanceMonitor", "Firebase integration ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * 計算錯誤率
     */
    private fun calculateErrorRate(): Double {
        val totalExecutions = metrics.values.sumOf { it.totalExecutions }
        val totalErrors = metrics.values.sumOf { it.errorCount }
        
        return if (totalExecutions > 0) {
            (totalErrors.toDouble() / totalExecutions) * 100
        } else {
            0.0
        }
    }
    
    /**
     * 日誌輸出
     */
    private fun logStart(useCaseName: String, metadata: Map<String, Any>) {
        Logger.d(
            "PerformanceMonitor",
            "▶️ START: $useCaseName" + if (metadata.isNotEmpty()) " | Meta: $metadata" else ""
        )
    }
    
    private fun logEnd(useCaseName: String, duration: Long, hasError: Boolean) {
        val emoji = when {
            hasError -> "❌"
            duration < 100 -> "🚀"
            duration < 500 -> "✅"
            duration < 1000 -> "⚠️"
            else -> "🐌"
        }
        
        Logger.d(
            "PerformanceMonitor",
            "$emoji END: $useCaseName | Duration: ${duration}ms" + if (hasError) " | ERROR" else ""
        )
    }
    
    private fun logError(useCaseName: String, throwable: Throwable) {
        Logger.e(
            "PerformanceMonitor",
            "❌ ERROR in $useCaseName: ${throwable.message}",
            throwable
        )
    }
    
    /**
     * 導出指標為 JSON
     */
    fun exportMetricsAsJson(): String {
        val report = getMetricsReport()
        return buildString {
            appendLine("{")
            appendLine("  \"timestamp\": ${currentTimeMillis()},")
            appendLine("  \"totalUseCases\": ${report.totalUseCases},")
            appendLine("  \"totalExecutions\": ${report.totalExecutions},")
            appendLine("  \"averageExecutionTime\": ${report.averageExecutionTime},")
            appendLine("  \"errorRate\": ${report.errorRate},")
            appendLine("  \"useCases\": [")
            
            report.useCaseMetrics.forEachIndexed { index, metrics ->
                appendLine("    {")
                appendLine("      \"name\": \"${metrics.name}\",")
                appendLine("      \"executions\": ${metrics.totalExecutions},")
                appendLine("      \"avgDuration\": ${metrics.averageDuration},")
                appendLine("      \"minDuration\": ${metrics.minDuration},")
                appendLine("      \"maxDuration\": ${metrics.maxDuration},")
                appendLine("      \"errorCount\": ${metrics.errorCount},")
                appendLine("      \"errorRate\": ${metrics.errorRate}")
                append("    }")
                if (index < report.useCaseMetrics.size - 1) append(",")
                appendLine()
            }
            
            appendLine("  ]")
            append("}")
        }
    }
}

/**
 * UseCase 指標
 */
data class UseCaseMetrics(
    val name: String,
    val totalExecutions: Int = 0,
    val totalDuration: Long = 0L,
    val minDuration: Long = Long.MAX_VALUE,
    val maxDuration: Long = 0L,
    val errorCount: Int = 0,
    val lastExecutionTime: Instant? = null,
    val lastMetadata: Map<String, Any> = emptyMap()
) {
    val averageDuration: Long
        get() = if (totalExecutions > 0) totalDuration / totalExecutions else 0L
    
    val errorRate: Double
        get() = if (totalExecutions > 0) (errorCount.toDouble() / totalExecutions) * 100 else 0.0
}

/**
 * 效能報告
 */
data class PerformanceReport(
    val totalUseCases: Int,
    val totalExecutions: Int,
    val averageExecutionTime: Long,
    val slowestUseCase: UseCaseMetrics?,
    val mostFrequentUseCase: UseCaseMetrics?,
    val errorRate: Double,
    val useCaseMetrics: List<UseCaseMetrics>
) {
    fun printSummary() {
        println("""
            ╔════════════════════════════════════════╗
            ║       Performance Report Summary        ║
            ╚════════════════════════════════════════╝
            
            📊 Overview:
            • Total UseCases: $totalUseCases
            • Total Executions: $totalExecutions
            • Average Execution Time: ${averageExecutionTime}ms
            • Error Rate: ${(errorRate * 100).toInt() / 100.0}%
            
            🐌 Slowest UseCase:
            ${slowestUseCase?.let { 
                "• ${it.name}: ${it.maxDuration}ms (max)"
            } ?: "N/A"}
            
            🔥 Most Frequent UseCase:
            ${mostFrequentUseCase?.let { 
                "• ${it.name}: ${it.totalExecutions} executions"
            } ?: "N/A"}
            
            📈 Top 5 UseCases by Execution Count:
            ${useCaseMetrics.take(5).mapIndexed { index, metrics ->
                "${index + 1}. ${metrics.name}: ${metrics.totalExecutions} (avg: ${metrics.averageDuration}ms)"
            }.joinToString("\n            ")}
        """.trimIndent())
    }
}

/**
 * Extension function for easy monitoring
 */
inline fun <T> measurePerformance(
    name: String,
    metadata: Map<String, Any> = emptyMap(),
    block: () -> T
): T {
    val startTime = currentTimeMillis()

    // 使用 Firebase 追蹤
    return FirebasePerformance.trace(name) {
        metadata.forEach { (key, value) ->
            setAttribute(key, value.toString())
        }

        try {
            block().also {
                val duration = currentTimeMillis() - startTime
                putMetric("duration_ms", duration)
                setAttribute("success", "true")
                GlobalMetrics.collector.recordHistogram("operation_duration_$name", duration)
                Logger.d("Performance", "✅ $name completed in ${duration}ms")
            }
        } catch (e: Exception) {
            val duration = currentTimeMillis() - startTime
            putMetric("duration_ms", duration)
            setAttribute("success", "false")
            setAttribute("error", e.message ?: "Unknown")
            GlobalMetrics.collector.incrementCounter("operation_errors_$name")
            Logger.e("Performance", "❌ $name failed after ${duration}ms", e)
            throw e
        }
    }
}
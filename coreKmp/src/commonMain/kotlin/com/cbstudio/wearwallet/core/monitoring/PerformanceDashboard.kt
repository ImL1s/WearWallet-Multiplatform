package com.cbstudio.wearwallet.core.monitoring

import com.cbstudio.wearwallet.core.utils.Logger
import com.cbstudio.wearwallet.core.utils.MemoryLeakDetector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 即時效能監控儀表板
 * 
 * 提供即時效能指標和視覺化
 * 
 * Created: 2025-01-17
 */
class PerformanceDashboard {
    
    private val metrics = MutableStateFlow(DashboardMetrics())
    private val alerts = MutableSharedFlow<PerformanceAlert>()
    private val updateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        // 定期更新指標
        updateScope.launch {
            while (isActive) {
                updateMetrics()
                delay(1.seconds)
            }
        }
        
        // 監控效能警報
        updateScope.launch {
            monitorPerformance()
        }
    }
    
    /**
     * 更新效能指標
     */
    private suspend fun updateMetrics() {
        val currentMetrics = DashboardMetrics(
            timestamp = Clock.System.now(),
            cpuUsage = getCpuUsage(),
            memoryUsage = getMemoryUsage(),
            activeFlows = getActiveFlowCount(),
            activeCoroutines = getActiveCoroutineCount(),
            averageResponseTime = calculateAverageResponseTime(),
            errorRate = calculateErrorRate(),
            cacheHitRate = calculateCacheHitRate(),
            databaseQueryTime = calculateDatabaseQueryTime(),
            networkLatency = calculateNetworkLatency()
        )
        
        metrics.value = currentMetrics
        
        // 檢查警報條件
        checkAlertConditions(currentMetrics)
    }
    
    /**
     * 監控效能
     */
    private suspend fun monitorPerformance() {
        metrics.collect { current ->
            // 記錄到 Firebase
            FirebasePerformance.setAttribute("cpu_usage", current.cpuUsage.toLong().toString())
            FirebasePerformance.setAttribute("memory_usage", current.memoryUsage.toLong().toString())
            FirebasePerformance.setAttribute("error_rate", (current.errorRate * 100).toLong().toString())
            
            // 更新全局指標
            GlobalMetrics.collector.recordHistogram("dashboard.cpu_usage", current.cpuUsage.toLong())
            GlobalMetrics.collector.recordHistogram("dashboard.memory_usage", current.memoryUsage.toLong())
            GlobalMetrics.collector.recordHistogram("dashboard.cache_hit_rate", (current.cacheHitRate * 100).toLong())
        }
    }
    
    /**
     * 檢查警報條件
     */
    private suspend fun checkAlertConditions(metrics: DashboardMetrics) {
        // CPU 使用率過高
        if (metrics.cpuUsage > 80) {
            emitAlert(
                PerformanceAlert(
                    type = AlertType.HIGH_CPU,
                    severity = AlertSeverity.WARNING,
                    message = "CPU usage is high: ${metrics.cpuUsage}%",
                    value = metrics.cpuUsage
                )
            )
        }
        
        // 記憶體使用率過高
        if (metrics.memoryUsage > 85) {
            emitAlert(
                PerformanceAlert(
                    type = AlertType.HIGH_MEMORY,
                    severity = AlertSeverity.WARNING,
                    message = "Memory usage is high: ${metrics.memoryUsage}%",
                    value = metrics.memoryUsage
                )
            )
        }
        
        // 錯誤率過高
        if (metrics.errorRate > 0.05) { // 5%
            emitAlert(
                PerformanceAlert(
                    type = AlertType.HIGH_ERROR_RATE,
                    severity = AlertSeverity.ERROR,
                    message = "Error rate is high: ${(metrics.errorRate * 100).toInt()}%",
                    value = metrics.errorRate
                )
            )
        }
        
        // 響應時間過長
        if (metrics.averageResponseTime > 1.seconds) {
            emitAlert(
                PerformanceAlert(
                    type = AlertType.SLOW_RESPONSE,
                    severity = AlertSeverity.WARNING,
                    message = "Response time is slow: ${metrics.averageResponseTime.inWholeMilliseconds}ms",
                    value = metrics.averageResponseTime.inWholeMilliseconds.toDouble()
                )
            )
        }
        
        // 快取命中率過低
        if (metrics.cacheHitRate < 0.5) { // 50%
            emitAlert(
                PerformanceAlert(
                    type = AlertType.LOW_CACHE_HIT,
                    severity = AlertSeverity.INFO,
                    message = "Cache hit rate is low: ${(metrics.cacheHitRate * 100).toInt()}%",
                    value = metrics.cacheHitRate
                )
            )
        }
    }
    
    /**
     * 發送警報
     */
    private suspend fun emitAlert(alert: PerformanceAlert) {
        alerts.emit(alert)
        Logger.w("PerformanceDashboard", "${alert.severity}: ${alert.message}")
        
        // 記錄到 Firebase
        FirebasePerformance.setAttribute("alert_type", alert.type.name)
        FirebasePerformance.setAttribute("alert_severity", alert.severity.name)
        FirebasePerformance.setAttribute("alert_value", alert.value.toString())
    }
    
    /**
     * 獲取即時指標
     */
    fun getMetrics(): StateFlow<DashboardMetrics> = metrics.asStateFlow()
    
    /**
     * 獲取警報流
     */
    fun getAlerts(): SharedFlow<PerformanceAlert> = alerts.asSharedFlow()
    
    /**
     * 生成效能報告
     */
    fun generateReport(): PerformanceReport {
        val current = metrics.value
        return PerformanceReport(
            timestamp = current.timestamp,
            summary = PerformanceSummary(
                overallHealth = calculateOverallHealth(current),
                cpuStatus = getResourceStatus(current.cpuUsage),
                memoryStatus = getResourceStatus(current.memoryUsage),
                responseTimeStatus = getResponseTimeStatus(current.averageResponseTime),
                errorRateStatus = getErrorRateStatus(current.errorRate)
            ),
            metrics = current,
            recommendations = generateRecommendations(current)
        )
    }
    
    /**
     * 計算整體健康度
     */
    private fun calculateOverallHealth(metrics: DashboardMetrics): HealthStatus {
        val scores = listOf(
            if (metrics.cpuUsage < 70) 100 else if (metrics.cpuUsage < 85) 70 else 30,
            if (metrics.memoryUsage < 70) 100 else if (metrics.memoryUsage < 85) 70 else 30,
            if (metrics.errorRate < 0.01) 100 else if (metrics.errorRate < 0.05) 70 else 30,
            if (metrics.averageResponseTime < 500.milliseconds) 100 else if (metrics.averageResponseTime < 1.seconds) 70 else 30,
            if (metrics.cacheHitRate > 0.8) 100 else if (metrics.cacheHitRate > 0.5) 70 else 30
        )
        
        val averageScore = scores.average()
        
        return when {
            averageScore >= 90 -> HealthStatus.EXCELLENT
            averageScore >= 70 -> HealthStatus.GOOD
            averageScore >= 50 -> HealthStatus.FAIR
            else -> HealthStatus.POOR
        }
    }
    
    /**
     * 生成優化建議
     */
    private fun generateRecommendations(metrics: DashboardMetrics): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (metrics.cpuUsage > 80) {
            recommendations.add("Consider optimizing heavy computations or moving them to background threads")
        }
        
        if (metrics.memoryUsage > 85) {
            recommendations.add("Review memory allocations and check for memory leaks")
        }
        
        if (metrics.cacheHitRate < 0.6) {
            recommendations.add("Increase cache size or adjust cache TTL for better hit rate")
        }
        
        if (metrics.averageResponseTime > 1.seconds) {
            recommendations.add("Optimize database queries and consider adding indexes")
        }
        
        if (metrics.errorRate > 0.03) {
            recommendations.add("Investigate error patterns and add retry mechanisms")
        }
        
        if (metrics.activeFlows > 100) {
            recommendations.add("Review Flow collectors and ensure proper cancellation")
        }
        
        return recommendations
    }
    
    // 輔助函數（平台特定實作）
    private fun getCpuUsage(): Double = 0.0 // 需要平台特定實作
    private fun getMemoryUsage(): Double = 0.0 // 需要平台特定實作
    private fun getActiveFlowCount(): Int = MemoryLeakDetector.getActiveFlowCount()
    private fun getActiveCoroutineCount(): Int = MemoryLeakDetector.getActiveCoroutineCount()
    
    private fun calculateAverageResponseTime(): Duration {
        // Simplified implementation - would need actual metrics collection
        return 200.milliseconds
    }
    
    private fun calculateErrorRate(): Double {
        // Simplified implementation - would need actual metrics collection
        return 0.01
    }
    
    private fun calculateCacheHitRate(): Double {
        // Simplified implementation - would need actual metrics collection
        return 0.75
    }
    
    private fun calculateDatabaseQueryTime(): Duration {
        // Simplified implementation - would need actual metrics collection
        return 50.milliseconds
    }
    
    private fun calculateNetworkLatency(): Duration {
        // Simplified implementation - would need actual metrics collection
        return 100.milliseconds
    }
    
    private fun getResourceStatus(usage: Double): ResourceStatus {
        return when {
            usage < 50 -> ResourceStatus.LOW
            usage < 70 -> ResourceStatus.NORMAL
            usage < 85 -> ResourceStatus.HIGH
            else -> ResourceStatus.CRITICAL
        }
    }
    
    private fun getResponseTimeStatus(time: Duration): ResponseTimeStatus {
        return when {
            time < 200.milliseconds -> ResponseTimeStatus.FAST
            time < 500.milliseconds -> ResponseTimeStatus.NORMAL
            time < 1.seconds -> ResponseTimeStatus.SLOW
            else -> ResponseTimeStatus.VERY_SLOW
        }
    }
    
    private fun getErrorRateStatus(rate: Double): ErrorRateStatus {
        return when {
            rate < 0.01 -> ErrorRateStatus.LOW
            rate < 0.03 -> ErrorRateStatus.NORMAL
            rate < 0.05 -> ErrorRateStatus.HIGH
            else -> ErrorRateStatus.CRITICAL
        }
    }
}

/**
 * 儀表板指標
 */
data class DashboardMetrics(
    val timestamp: Instant = Clock.System.now(),
    val cpuUsage: Double = 0.0,              // 百分比
    val memoryUsage: Double = 0.0,           // 百分比
    val activeFlows: Int = 0,
    val activeCoroutines: Int = 0,
    val averageResponseTime: Duration = Duration.ZERO,
    val errorRate: Double = 0.0,             // 0-1
    val cacheHitRate: Double = 0.0,          // 0-1
    val databaseQueryTime: Duration = Duration.ZERO,
    val networkLatency: Duration = Duration.ZERO
)

/**
 * 效能警報
 */
data class PerformanceAlert(
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val value: Double,
    val timestamp: Instant = Clock.System.now()
)

/**
 * 警報類型
 */
enum class AlertType {
    HIGH_CPU,
    HIGH_MEMORY,
    HIGH_ERROR_RATE,
    SLOW_RESPONSE,
    LOW_CACHE_HIT,
    MEMORY_LEAK,
    DATABASE_SLOW,
    NETWORK_ISSUE
}

/**
 * 警報嚴重程度
 */
enum class AlertSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * 效能報告
 */
data class PerformanceReport(
    val timestamp: Instant,
    val summary: PerformanceSummary,
    val metrics: DashboardMetrics,
    val recommendations: List<String>
) {
    fun printReport() {
        println("""
            ╔════════════════════════════════════════╗
            ║      Performance Report                ║
            ╚════════════════════════════════════════╝
            
            📊 Overall Health: ${summary.overallHealth}
            
            💻 System Resources:
            • CPU: ${metrics.cpuUsage.toInt()}% (${summary.cpuStatus})
            • Memory: ${metrics.memoryUsage.toInt()}% (${summary.memoryStatus})
            • Active Flows: ${metrics.activeFlows}
            • Active Coroutines: ${metrics.activeCoroutines}
            
            ⚡ Performance:
            • Response Time: ${metrics.averageResponseTime.inWholeMilliseconds}ms (${summary.responseTimeStatus})
            • Error Rate: ${(metrics.errorRate * 100).toInt()}% (${summary.errorRateStatus})
            • Cache Hit Rate: ${(metrics.cacheHitRate * 100).toInt()}%
            • DB Query Time: ${metrics.databaseQueryTime.inWholeMilliseconds}ms
            • Network Latency: ${metrics.networkLatency.inWholeMilliseconds}ms
            
            💡 Recommendations:
            ${recommendations.joinToString("\n") { "• $it" }}
        """.trimIndent())
    }
}

/**
 * 效能摘要
 */
data class PerformanceSummary(
    val overallHealth: HealthStatus,
    val cpuStatus: ResourceStatus,
    val memoryStatus: ResourceStatus,
    val responseTimeStatus: ResponseTimeStatus,
    val errorRateStatus: ErrorRateStatus
)

/**
 * 健康狀態
 */
enum class HealthStatus {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR
}

/**
 * 資源狀態
 */
enum class ResourceStatus {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

/**
 * 響應時間狀態
 */
enum class ResponseTimeStatus {
    FAST,
    NORMAL,
    SLOW,
    VERY_SLOW
}

/**
 * 錯誤率狀態
 */
enum class ErrorRateStatus {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL
}

/**
 * 全局效能儀表板
 */
object GlobalPerformanceDashboard {
    val dashboard = PerformanceDashboard()
    
    /**
     * 訂閱效能警報
     */
    fun subscribeToAlerts(handler: (PerformanceAlert) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            dashboard.getAlerts().collect { alert ->
                handler(alert)
            }
        }
    }
    
    /**
     * 獲取即時效能報告
     */
    fun getReport(): PerformanceReport = dashboard.generateReport()
    
    /**
     * 打印效能報告
     */
    fun printReport() = dashboard.generateReport().printReport()
}
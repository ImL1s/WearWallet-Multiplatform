package com.cbstudio.wearwallet.domain.usecase.performance

import com.cbstudio.wearwallet.core.monitoring.GlobalPerformanceDashboard
import com.cbstudio.wearwallet.core.monitoring.GlobalAlertingSystem
import com.cbstudio.wearwallet.core.monitoring.PerformanceAlert
import com.cbstudio.wearwallet.core.monitoring.AlertRule
import com.cbstudio.wearwallet.core.monitoring.MetricCondition
import com.cbstudio.wearwallet.core.monitoring.ComparisonOperator
import com.cbstudio.wearwallet.core.monitoring.AlertSeverity
import com.cbstudio.wearwallet.core.monitoring.AlertAction
import com.cbstudio.wearwallet.core.optimization.GlobalAdaptiveOptimizer
import com.cbstudio.wearwallet.core.optimization.OptimizationStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

/**
 * 監控效能 UseCase
 * 
 * 橋接 KMP 優化系統與 Wear OS 應用
 */
class MonitorPerformanceUseCase @Inject constructor() {
    
    private val dashboard = GlobalPerformanceDashboard.dashboard
    private val alerting = GlobalAlertingSystem.alerting
    private val optimizer = GlobalAdaptiveOptimizer.optimizer
    
    /**
     * 獲取即時效能指標
     */
    fun getPerformanceMetrics() = dashboard.getMetrics()
    
    /**
     * 獲取效能警報
     */
    fun getPerformanceAlerts(): Flow<PerformanceAlert> = 
        alerting.getAlertNotifications().map { notification ->
            PerformanceAlert(
                type = mapAlertType(notification.title),
                severity = notification.severity,
                message = notification.message,
                value = 0.0, // 從 notification 中提取
                timestamp = notification.timestamp
            )
        }
    
    /**
     * 獲取當前優化策略
     */
    fun getOptimizationStrategy() = optimizer.getCurrentStrategy()
    
    /**
     * 強制應用優化策略
     */
    suspend fun forceOptimization(aggressive: Boolean = false) {
        val strategy = if (aggressive) {
            OptimizationStrategy.AGGRESSIVE
        } else {
            OptimizationStrategy.BALANCED
        }
        
        optimizer.forceStrategy(strategy)
    }
    
    /**
     * 設置自定義警報規則
     */
    fun setupWearOSAlerts() {
        // Wear OS 特定的警報規則
        
        // 電池效能規則
        alerting.addRule(
            AlertRule(
                id = "wearos_battery_performance",
                name = "Wear OS Battery Performance",
                condition = MetricCondition(
                    metric = "cpu_usage",
                    operator = ComparisonOperator.GREATER_THAN,
                    threshold = 60.0, // Wear OS 較低的閾值
                    duration = 30.seconds
                ),
                severity = AlertSeverity.WARNING,
                actions = listOf(
                    AlertAction.LOG,
                    AlertAction.AUTO_OPTIMIZE
                )
            )
        )
        
        // 記憶體限制規則
        alerting.addRule(
            AlertRule(
                id = "wearos_memory_limit",
                name = "Wear OS Memory Limit",
                condition = MetricCondition(
                    metric = "memory_usage",
                    operator = ComparisonOperator.GREATER_THAN,
                    threshold = 70.0, // Wear OS 較嚴格的記憶體限制
                    duration = 20.seconds
                ),
                severity = AlertSeverity.ERROR,
                actions = listOf(
                    AlertAction.LOG,
                    AlertAction.TRIGGER_GC,
                    AlertAction.OPTIMIZE_CACHE
                )
            )
        )
        
        // 動畫效能規則
        alerting.addRule(
            AlertRule(
                id = "wearos_animation_performance",
                name = "Animation Performance",
                condition = MetricCondition(
                    metric = "frame_rate",
                    operator = ComparisonOperator.LESS_THAN,
                    threshold = 30.0, // 30 FPS 警告
                    duration = 5.seconds
                ),
                severity = AlertSeverity.WARNING,
                actions = listOf(
                    AlertAction.LOG,
                    AlertAction.AUTO_OPTIMIZE
                )
            )
        )
    }
    
    /**
     * 生成效能報告
     */
    fun generatePerformanceReport(): PerformanceReport {
        val report = dashboard.generateReport()
        val activeAlerts = alerting.getActiveAlerts()
        
        return PerformanceReport(
            timestamp = System.currentTimeMillis(),
            overallHealth = report.summary.overallHealth.name,
            metrics = PerformanceMetricsData(
                cpuUsage = report.metrics.cpuUsage,
                memoryUsage = report.metrics.memoryUsage,
                responseTime = report.metrics.averageResponseTime.inWholeMilliseconds,
                errorRate = report.metrics.errorRate,
                cacheHitRate = report.metrics.cacheHitRate,
                activeFlows = report.metrics.activeFlows,
                activeCoroutines = report.metrics.activeCoroutines
            ),
            activeAlerts = activeAlerts.size,
            recommendations = report.recommendations,
            optimizationStrategy = optimizer.getCurrentStrategy().value.name
        )
    }
    
    /**
     * 清理和優化
     */
    suspend fun performCleanup() {
        // 清除過期快取
        com.cbstudio.wearwallet.core.database.optimization.GlobalQueryCache.clearAll()
        
        // 清理資源
        GlobalAdaptiveOptimizer.cleanup()
        
        // 觸發 GC
        System.gc()
    }
    
    /**
     * 優化 Wear OS 特定設置
     */
    fun optimizeForWearOS() {
        // 調整 Wear OS 優化參數
        com.cbstudio.wearwallet.core.optimization.OptimizationConfig.apply {
            maxConcurrentOperations = 2  // 減少並發
            maxCacheSize = 200           // 較小的快取
            maxFlowCollectors = 20       // 限制 Flow 收集器
            enableAnimations = false     // 關閉複雜動畫
            updateInterval = 2.minutes   // 減少更新頻率
        }
    }
    
    private fun mapAlertType(title: String): com.cbstudio.wearwallet.core.monitoring.AlertType {
        return when {
            title.contains("CPU", ignoreCase = true) -> com.cbstudio.wearwallet.core.monitoring.AlertType.HIGH_CPU
            title.contains("Memory", ignoreCase = true) -> com.cbstudio.wearwallet.core.monitoring.AlertType.HIGH_MEMORY
            title.contains("Response", ignoreCase = true) -> com.cbstudio.wearwallet.core.monitoring.AlertType.SLOW_RESPONSE
            title.contains("Error", ignoreCase = true) -> com.cbstudio.wearwallet.core.monitoring.AlertType.HIGH_ERROR_RATE
            title.contains("Cache", ignoreCase = true) -> com.cbstudio.wearwallet.core.monitoring.AlertType.LOW_CACHE_HIT
            else -> com.cbstudio.wearwallet.core.monitoring.AlertType.NETWORK_ISSUE
        }
    }
}

/**
 * 效能報告
 */
data class PerformanceReport(
    val timestamp: Long,
    val overallHealth: String,
    val metrics: PerformanceMetricsData,
    val activeAlerts: Int,
    val recommendations: List<String>,
    val optimizationStrategy: String
)

/**
 * 效能指標資料
 */
data class PerformanceMetricsData(
    val cpuUsage: Double,
    val memoryUsage: Double,
    val responseTime: Long,
    val errorRate: Double,
    val cacheHitRate: Double,
    val activeFlows: Int,
    val activeCoroutines: Int
)
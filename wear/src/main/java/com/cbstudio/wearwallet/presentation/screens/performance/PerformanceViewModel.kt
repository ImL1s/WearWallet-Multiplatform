package com.cbstudio.wearwallet.presentation.screens.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.monitoring.GlobalPerformanceDashboard
import com.cbstudio.wearwallet.core.monitoring.GlobalAlertingSystem
import com.cbstudio.wearwallet.core.optimization.GlobalAdaptiveOptimizer
import com.cbstudio.wearwallet.core.monitoring.DashboardMetrics
import com.cbstudio.wearwallet.core.monitoring.PerformanceAlert as CoreAlert
import com.cbstudio.wearwallet.core.monitoring.AlertSeverity as CoreAlertSeverity
import com.cbstudio.wearwallet.core.monitoring.HealthStatus as CoreHealthStatus
import com.cbstudio.wearwallet.core.monitoring.ResourceStatus
import com.cbstudio.wearwallet.core.monitoring.ResponseTimeStatus
import com.cbstudio.wearwallet.core.optimization.OptimizationStrategy as CoreStrategy
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * ViewModel for Performance Monitor Screen
 */
class PerformanceViewModel @Inject constructor() : ViewModel() {
    
    private val dashboard = GlobalPerformanceDashboard.dashboard
    private val alertingSystem = GlobalAlertingSystem.alerting
    private val optimizer = GlobalAdaptiveOptimizer.optimizer
    
    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()
    
    private val _activeAlerts = MutableStateFlow<List<PerformanceAlert>>(emptyList())
    val activeAlerts: StateFlow<List<PerformanceAlert>> = _activeAlerts.asStateFlow()
    
    val optimizationStrategy = optimizer.getCurrentStrategy()
        .map { strategy ->
            when (strategy) {
                CoreStrategy.AGGRESSIVE -> OptimizationStrategy.AGGRESSIVE
                CoreStrategy.BALANCED -> OptimizationStrategy.BALANCED
                CoreStrategy.PERFORMANCE_FOCUS -> OptimizationStrategy.PERFORMANCE_FOCUS
                CoreStrategy.SAFE_MODE -> OptimizationStrategy.SAFE_MODE
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            OptimizationStrategy.BALANCED
        )
    
    init {
        startMonitoring()
        subscribeToAlerts()
    }
    
    /**
     * 開始監控效能指標
     */
    private fun startMonitoring() {
        viewModelScope.launch {
            dashboard.getMetrics().collect { dashboardMetrics ->
                _metrics.value = mapMetrics(dashboardMetrics)
            }
        }
    }
    
    /**
     * 訂閱警報通知
     */
    private fun subscribeToAlerts() {
        viewModelScope.launch {
            alertingSystem.getAlertNotifications().collect { notification ->
                val alert = PerformanceAlert(
                    message = notification.message,
                    severity = mapAlertSeverity(notification.severity),
                    timestamp = notification.timestamp.toEpochMilliseconds()
                )
                
                _activeAlerts.value = (_activeAlerts.value + alert)
                    .sortedByDescending { it.timestamp }
                    .take(10) // 只保留最近 10 條警報
            }
        }
    }
    
    /**
     * 映射效能指標
     */
    private fun mapMetrics(dashboardMetrics: DashboardMetrics): PerformanceMetrics {
        val report = dashboard.generateReport()
        
        return PerformanceMetrics(
            overallHealth = mapHealthStatus(report.summary.overallHealth),
            cpuUsage = dashboardMetrics.cpuUsage,
            cpuStatus = mapResourceStatus(report.summary.cpuStatus),
            memoryUsage = dashboardMetrics.memoryUsage,
            memoryStatus = mapResourceStatus(report.summary.memoryStatus),
            responseTime = dashboardMetrics.averageResponseTime.inWholeMilliseconds,
            responseStatus = mapResponseStatus(report.summary.responseTimeStatus),
            cacheHitRate = dashboardMetrics.cacheHitRate
        )
    }
    
    /**
     * 映射健康狀態
     */
    private fun mapHealthStatus(status: CoreHealthStatus): HealthStatus {
        return when (status) {
            CoreHealthStatus.EXCELLENT -> HealthStatus.EXCELLENT
            CoreHealthStatus.GOOD -> HealthStatus.GOOD
            CoreHealthStatus.FAIR -> HealthStatus.FAIR
            CoreHealthStatus.POOR -> HealthStatus.POOR
        }
    }
    
    /**
     * 映射資源狀態
     */
    private fun mapResourceStatus(status: ResourceStatus): MetricStatus {
        return when (status) {
            ResourceStatus.LOW -> MetricStatus.GOOD
            ResourceStatus.NORMAL -> MetricStatus.GOOD
            ResourceStatus.HIGH -> MetricStatus.WARNING
            ResourceStatus.CRITICAL -> MetricStatus.CRITICAL
        }
    }
    
    /**
     * 映射響應時間狀態
     */
    private fun mapResponseStatus(status: ResponseTimeStatus): MetricStatus {
        return when (status) {
            ResponseTimeStatus.FAST -> MetricStatus.GOOD
            ResponseTimeStatus.NORMAL -> MetricStatus.GOOD
            ResponseTimeStatus.SLOW -> MetricStatus.WARNING
            ResponseTimeStatus.VERY_SLOW -> MetricStatus.ERROR
        }
    }
    
    /**
     * 映射警報嚴重程度
     */
    private fun mapAlertSeverity(severity: CoreAlertSeverity): AlertSeverity {
        return when (severity) {
            CoreAlertSeverity.INFO -> AlertSeverity.INFO
            CoreAlertSeverity.WARNING -> AlertSeverity.WARNING
            CoreAlertSeverity.ERROR -> AlertSeverity.ERROR
            CoreAlertSeverity.CRITICAL -> AlertSeverity.CRITICAL
        }
    }
    
    /**
     * 強制執行優化
     */
    fun forceOptimization() {
        viewModelScope.launch {
            // 根據當前狀態選擇優化策略
            val currentMetrics = _metrics.value
            val strategy = when {
                currentMetrics.cpuUsage > 80 -> CoreStrategy.AGGRESSIVE
                currentMetrics.memoryUsage > 80 -> CoreStrategy.AGGRESSIVE
                currentMetrics.responseTime > 1000 -> CoreStrategy.PERFORMANCE_FOCUS
                else -> CoreStrategy.BALANCED
            }
            
            optimizer.forceStrategy(strategy)
            
            // 添加通知
            _activeAlerts.value = listOf(
                PerformanceAlert(
                    messageRes = com.cbstudio.wearwallet.R.string.optimization_applied,
                    args = arrayOf(strategy.name),
                    severity = AlertSeverity.INFO
                )
            ) + _activeAlerts.value
        }
    }
    
    /**
     * 清除快取
     */
    fun clearCache() {
        viewModelScope.launch {
            // 清除各層級快取
            com.cbstudio.wearwallet.core.database.optimization.GlobalQueryCache.clearAll()
            
            // 添加通知
            _activeAlerts.value = listOf(
                PerformanceAlert(
                    messageRes = com.cbstudio.wearwallet.R.string.cache_cleared,
                    severity = AlertSeverity.INFO
                )
            ) + _activeAlerts.value
        }
    }
    
    /**
     * 生成效能報告
     */
    fun generateReport(): String {
        val report = dashboard.generateReport()
        return buildString {
            appendLine("=== 效能報告 ===")
            appendLine("整體健康度: ${report.summary.overallHealth}")
            appendLine("CPU: ${report.metrics.cpuUsage.toInt()}%")
            appendLine("記憶體: ${report.metrics.memoryUsage.toInt()}%")
            appendLine("響應時間: ${report.metrics.averageResponseTime.inWholeMilliseconds}ms")
            appendLine("錯誤率: ${(report.metrics.errorRate * 100).toInt()}%")
            appendLine("快取命中率: ${(report.metrics.cacheHitRate * 100).toInt()}%")
            
            if (report.recommendations.isNotEmpty()) {
                appendLine("\n=== 優化建議 ===")
                report.recommendations.forEach { recommendation ->
                    appendLine("• $recommendation")
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // 清理資源
    }
}
package com.cbstudio.wearwallet.core.monitoring

import com.cbstudio.wearwallet.core.utils.Logger
import com.cbstudio.wearwallet.core.optimization.GlobalAdaptiveOptimizer
import com.cbstudio.wearwallet.core.optimization.OptimizationStrategy
import com.cbstudio.wearwallet.core.optimization.OptimizationConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 效能警報系統
 * 
 * 監控系統效能並在異常時發送警報
 * 
 * Created: 2025-01-17
 */
class PerformanceAlertingSystem {
    
    private val alertRules = mutableListOf<AlertRule>()
    private val alertHistory = mutableListOf<AlertHistoryEntry>()
    private val activeAlerts = mutableMapOf<String, ActiveAlert>()
    private val alertChannel = MutableSharedFlow<AlertNotification>()
    private val monitoringScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    init {
        // 設定預設警報規則
        setupDefaultRules()
        
        // 開始監控
        startMonitoring()
    }
    
    /**
     * 設定預設警報規則
     */
    private fun setupDefaultRules() {
        // CPU 使用率規則
        addRule(
            AlertRule(
                id = "cpu_high",
                name = "High CPU Usage",
                condition = MetricCondition(
                    metric = "cpu_usage",
                    operator = ComparisonOperator.GREATER_THAN,
                    threshold = 80.0,
                    duration = 30.seconds
                ),
                severity = AlertSeverity.WARNING,
                actions = listOf(
                    AlertAction.LOG,
                    AlertAction.NOTIFY,
                    AlertAction.AUTO_OPTIMIZE
                )
            )
        )
        
        // 記憶體使用率規則
        addRule(
            AlertRule(
                id = "memory_high",
                name = "High Memory Usage",
                condition = MetricCondition(
                    metric = "memory_usage",
                    operator = ComparisonOperator.GREATER_THAN,
                    threshold = 85.0,
                    duration = 1.minutes
                ),
                severity = AlertSeverity.WARNING,
                actions = listOf(
                    AlertAction.LOG,
                    AlertAction.NOTIFY,
                    AlertAction.TRIGGER_GC
                )
            )
        )
        
        // 錯誤率規則
        addRule(
            AlertRule(
                id = "error_rate_high",
                name = "High Error Rate",
                condition = MetricCondition(
                    metric = "error_rate",
                    operator = ComparisonOperator.GREATER_THAN,
                    threshold = 0.05,
                    duration = 2.minutes
                ),
                severity = AlertSeverity.ERROR,
                actions = listOf(
                    AlertAction.LOG,
                    AlertAction.NOTIFY,
                    AlertAction.ENABLE_SAFE_MODE
                )
            )
        )
        
        // 響應時間規則
        addRule(
            AlertRule(
                id = "response_slow",
                name = "Slow Response Time",
                condition = MetricCondition(
                    metric = "response_time",
                    operator = ComparisonOperator.GREATER_THAN,
                    threshold = 1000.0, // milliseconds
                    duration = 1.minutes
                ),
                severity = AlertSeverity.WARNING,
                actions = listOf(
                    AlertAction.LOG,
                    AlertAction.OPTIMIZE_CACHE
                )
            )
        )
        
        // 記憶體洩漏規則
        addRule(
            AlertRule(
                id = "memory_leak",
                name = "Potential Memory Leak",
                condition = CompositeCondition(
                    conditions = listOf(
                        MetricCondition("memory_usage", ComparisonOperator.GREATER_THAN, 70.0),
                        TrendCondition("memory_usage", TrendDirection.INCREASING, 5.minutes)
                    ),
                    operator = LogicalOperator.AND
                ),
                severity = AlertSeverity.CRITICAL,
                actions = listOf(
                    AlertAction.LOG,
                    AlertAction.NOTIFY,
                    AlertAction.DUMP_MEMORY
                )
            )
        )
    }
    
    /**
     * 開始監控
     */
    private fun startMonitoring() {
        monitoringScope.launch {
            GlobalPerformanceDashboard.dashboard.getMetrics().collect { metrics ->
                evaluateRules(metrics)
            }
        }
        
        // 定期清理過期警報
        monitoringScope.launch {
            while (isActive) {
                cleanupExpiredAlerts()
                delay(1.minutes)
            }
        }
    }
    
    /**
     * 評估警報規則
     */
    private suspend fun evaluateRules(metrics: DashboardMetrics) {
        alertRules.forEach { rule ->
            val triggered = evaluateCondition(rule.condition, metrics)
            
            if (triggered) {
                handleTriggeredRule(rule, metrics)
            } else {
                handleResolvedRule(rule)
            }
        }
    }
    
    /**
     * 評估條件
     */
    private fun evaluateCondition(condition: AlertCondition, metrics: DashboardMetrics): Boolean {
        return when (condition) {
            is MetricCondition -> evaluateMetricCondition(condition, metrics)
            is CompositeCondition -> evaluateCompositeCondition(condition, metrics)
            is TrendCondition -> evaluateTrendCondition(condition, metrics)
            else -> false
        }
    }
    
    /**
     * 評估指標條件
     */
    private fun evaluateMetricCondition(condition: MetricCondition, metrics: DashboardMetrics): Boolean {
        val value = getMetricValue(condition.metric, metrics) ?: return false
        
        return when (condition.operator) {
            ComparisonOperator.GREATER_THAN -> value > condition.threshold
            ComparisonOperator.LESS_THAN -> value < condition.threshold
            ComparisonOperator.EQUAL -> value == condition.threshold
            ComparisonOperator.NOT_EQUAL -> value != condition.threshold
            ComparisonOperator.GREATER_THAN_OR_EQUAL -> value >= condition.threshold
            ComparisonOperator.LESS_THAN_OR_EQUAL -> value <= condition.threshold
        }
    }
    
    /**
     * 評估複合條件
     */
    private fun evaluateCompositeCondition(condition: CompositeCondition, metrics: DashboardMetrics): Boolean {
        val results = condition.conditions.map { evaluateCondition(it, metrics) }
        
        return when (condition.operator) {
            LogicalOperator.AND -> results.all { it }
            LogicalOperator.OR -> results.any { it }
            LogicalOperator.NOT -> !results.first()
        }
    }
    
    /**
     * 評估趨勢條件
     */
    private fun evaluateTrendCondition(condition: TrendCondition, metrics: DashboardMetrics): Boolean {
        // 簡化實作：檢查最近的變化趨勢
        // 實際實作需要歷史資料
        return false
    }
    
    /**
     * 獲取指標值
     */
    private fun getMetricValue(metric: String, metrics: DashboardMetrics): Double? {
        return when (metric) {
            "cpu_usage" -> metrics.cpuUsage
            "memory_usage" -> metrics.memoryUsage
            "error_rate" -> metrics.errorRate * 100
            "response_time" -> metrics.averageResponseTime.inWholeMilliseconds.toDouble()
            "cache_hit_rate" -> metrics.cacheHitRate * 100
            "active_flows" -> metrics.activeFlows.toDouble()
            "active_coroutines" -> metrics.activeCoroutines.toDouble()
            else -> null
        }
    }
    
    /**
     * 處理觸發的規則
     */
    private suspend fun handleTriggeredRule(rule: AlertRule, metrics: DashboardMetrics) {
        val alertId = rule.id
        
        // 檢查是否已有活躍警報
        val existingAlert = activeAlerts[alertId]
        if (existingAlert != null) {
            // 更新現有警報
            existingAlert.lastTriggered = Clock.System.now()
            existingAlert.triggerCount++
            
            // 檢查是否需要升級嚴重程度
            if (existingAlert.triggerCount > 5 && rule.severity < AlertSeverity.CRITICAL) {
                existingAlert.escalated = true
                executeAlertActions(rule.copy(severity = AlertSeverity.CRITICAL), metrics)
            }
        } else {
            // 創建新警報
            val newAlert = ActiveAlert(
                rule = rule,
                firstTriggered = Clock.System.now(),
                lastTriggered = Clock.System.now(),
                triggerCount = 1
            )
            activeAlerts[alertId] = newAlert
            
            // 執行警報動作
            executeAlertActions(rule, metrics)
            
            // 發送通知
            sendAlertNotification(rule, metrics)
        }
    }
    
    /**
     * 處理已解決的規則
     */
    private suspend fun handleResolvedRule(rule: AlertRule) {
        val alertId = rule.id
        val activeAlert = activeAlerts[alertId] ?: return
        
        // 記錄到歷史
        alertHistory.add(
            AlertHistoryEntry(
                rule = rule,
                triggeredAt = activeAlert.firstTriggered,
                resolvedAt = Clock.System.now(),
                triggerCount = activeAlert.triggerCount,
                escalated = activeAlert.escalated
            )
        )
        
        // 移除活躍警報
        activeAlerts.remove(alertId)
        
        // 發送解決通知
        sendResolvedNotification(rule)
    }
    
    /**
     * 執行警報動作
     */
    private suspend fun executeAlertActions(rule: AlertRule, metrics: DashboardMetrics) {
        rule.actions.forEach { action ->
            when (action) {
                AlertAction.LOG -> {
                    Logger.w("PerformanceAlert", 
                        "${rule.severity}: ${rule.name} triggered")
                }
                
                AlertAction.NOTIFY -> {
                    // 發送通知（平台特定）
                }
                
                AlertAction.AUTO_OPTIMIZE -> {
                    // 觸發自動優化
                    GlobalAdaptiveOptimizer.forceStrategy(OptimizationStrategy.AGGRESSIVE)
                }
                
                AlertAction.TRIGGER_GC -> {
                    // Trigger GC - platform specific
                    Logger.w("PerformanceAlert", "GC requested")
                }
                
                AlertAction.ENABLE_SAFE_MODE -> {
                    GlobalAdaptiveOptimizer.forceStrategy(OptimizationStrategy.SAFE_MODE)
                }
                
                AlertAction.OPTIMIZE_CACHE -> {
                    OptimizationConfig.cacheAggressiveness = 0.9
                    OptimizationConfig.maxCacheSize = 1000
                }
                
                AlertAction.DUMP_MEMORY -> {
                    // 記憶體快照（平台特定）
                    Logger.w("PerformanceAlert", "Memory dump requested")
                }
                
                AlertAction.RESTART_SERVICE -> {
                    // 重啟服務（平台特定）
                    Logger.w("PerformanceAlert", "Service restart requested")
                }
            }
        }
    }
    
    /**
     * 發送警報通知
     */
    private suspend fun sendAlertNotification(rule: AlertRule, metrics: DashboardMetrics) {
        val notification = AlertNotification(
            title = "Performance Alert: ${rule.name}",
            message = buildAlertMessage(rule, metrics),
            severity = rule.severity,
            timestamp = Clock.System.now(),
            ruleId = rule.id
        )
        
        alertChannel.emit(notification)
    }
    
    /**
     * 發送解決通知
     */
    private suspend fun sendResolvedNotification(rule: AlertRule) {
        val notification = AlertNotification(
            title = "Alert Resolved: ${rule.name}",
            message = "The performance alert has been resolved",
            severity = AlertSeverity.INFO,
            timestamp = Clock.System.now(),
            ruleId = rule.id
        )
        
        alertChannel.emit(notification)
    }
    
    /**
     * 建立警報訊息
     */
    private fun buildAlertMessage(rule: AlertRule, metrics: DashboardMetrics): String {
        return when (rule.id) {
            "cpu_high" -> "CPU usage is at ${metrics.cpuUsage.toInt()}%"
            "memory_high" -> "Memory usage is at ${metrics.memoryUsage.toInt()}%"
            "error_rate_high" -> "Error rate is at ${(metrics.errorRate * 100).toInt()}%"
            "response_slow" -> "Response time is ${metrics.averageResponseTime.inWholeMilliseconds}ms"
            else -> rule.name
        }
    }
    
    /**
     * 清理過期警報
     */
    private fun cleanupExpiredAlerts() {
        val now = Clock.System.now()
        val expiredAlerts = activeAlerts.filter { (_, alert) ->
            now - alert.lastTriggered > 10.minutes
        }
        
        expiredAlerts.forEach { (id, _) ->
            activeAlerts.remove(id)
        }
        
        // 清理歷史記錄（只保留最近 1000 條）
        if (alertHistory.size > 1000) {
            val toRemove = alertHistory.size - 1000
            repeat(toRemove) {
                alertHistory.removeAt(0)
            }
        }
    }
    
    /**
     * 添加警報規則
     */
    fun addRule(rule: AlertRule) {
        alertRules.add(rule)
    }
    
    /**
     * 移除警報規則
     */
    fun removeRule(ruleId: String) {
        alertRules.removeAll { it.id == ruleId }
    }
    
    /**
     * 獲取警報通知流
     */
    fun getAlertNotifications(): SharedFlow<AlertNotification> = alertChannel.asSharedFlow()
    
    /**
     * 獲取活躍警報
     */
    fun getActiveAlerts(): List<ActiveAlert> = activeAlerts.values.toList()
    
    /**
     * 獲取警報歷史
     */
    fun getAlertHistory(): List<AlertHistoryEntry> = alertHistory.toList()
}

/**
 * 警報規則
 */
data class AlertRule(
    val id: String,
    val name: String,
    val condition: AlertCondition,
    val severity: AlertSeverity,
    val actions: List<AlertAction>,
    val enabled: Boolean = true
)

/**
 * 警報條件（介面）
 */
sealed class AlertCondition

/**
 * 指標條件
 */
data class MetricCondition(
    val metric: String,
    val operator: ComparisonOperator,
    val threshold: Double,
    val duration: Duration = Duration.ZERO
) : AlertCondition()

/**
 * 複合條件
 */
data class CompositeCondition(
    val conditions: List<AlertCondition>,
    val operator: LogicalOperator
) : AlertCondition()

/**
 * 趨勢條件
 */
data class TrendCondition(
    val metric: String,
    val direction: TrendDirection,
    val duration: Duration
) : AlertCondition()

/**
 * 比較運算子
 */
enum class ComparisonOperator {
    GREATER_THAN,
    LESS_THAN,
    EQUAL,
    NOT_EQUAL,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN_OR_EQUAL
}

/**
 * 邏輯運算子
 */
enum class LogicalOperator {
    AND,
    OR,
    NOT
}

/**
 * 趨勢方向
 */
enum class TrendDirection {
    INCREASING,
    DECREASING,
    STABLE
}

/**
 * 警報動作
 */
enum class AlertAction {
    LOG,
    NOTIFY,
    AUTO_OPTIMIZE,
    TRIGGER_GC,
    ENABLE_SAFE_MODE,
    OPTIMIZE_CACHE,
    DUMP_MEMORY,
    RESTART_SERVICE
}

/**
 * 活躍警報
 */
data class ActiveAlert(
    val rule: AlertRule,
    val firstTriggered: Instant,
    var lastTriggered: Instant,
    var triggerCount: Int,
    var escalated: Boolean = false
)

/**
 * 警報歷史記錄
 */
data class AlertHistoryEntry(
    val rule: AlertRule,
    val triggeredAt: Instant,
    val resolvedAt: Instant,
    val triggerCount: Int,
    val escalated: Boolean
)

/**
 * 警報通知
 */
data class AlertNotification(
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Instant,
    val ruleId: String
)

/**
 * 全局警報系統
 */
object GlobalAlertingSystem {
    val alerting = PerformanceAlertingSystem()
    
    /**
     * 訂閱警報通知
     */
    fun subscribeToNotifications(handler: (AlertNotification) -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            alerting.getAlertNotifications().collect { notification ->
                handler(notification)
            }
        }
    }
    
    /**
     * 獲取活躍警報
     */
    fun getActiveAlerts() = alerting.getActiveAlerts()
    
    /**
     * 添加自定義規則
     */
    fun addCustomRule(rule: AlertRule) {
        alerting.addRule(rule)
    }
}
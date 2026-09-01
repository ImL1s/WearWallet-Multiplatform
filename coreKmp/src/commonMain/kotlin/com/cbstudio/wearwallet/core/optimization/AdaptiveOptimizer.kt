package com.cbstudio.wearwallet.core.optimization

import com.cbstudio.wearwallet.core.cache.AdaptiveCache
import com.cbstudio.wearwallet.core.cache.TieredCacheManager
import com.cbstudio.wearwallet.core.monitoring.GlobalPerformanceDashboard
import com.cbstudio.wearwallet.core.monitoring.PerformanceAlert
import com.cbstudio.wearwallet.core.monitoring.AlertType
import com.cbstudio.wearwallet.core.monitoring.AlertSeverity
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 自適應效能優化器
 * 
 * 根據系統狀態動態調整優化策略
 * 
 * Created: 2025-01-17
 */
class AdaptiveOptimizer {
    
    private val optimizationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val currentStrategy = MutableStateFlow(OptimizationStrategy.BALANCED)
    private val optimizationHistory = mutableListOf<OptimizationAction>()
    
    init {
        // 訂閱效能警報
        GlobalPerformanceDashboard.subscribeToAlerts { alert ->
            handlePerformanceAlert(alert)
        }
        
        // 定期評估和調整策略
        optimizationScope.launch {
            while (isActive) {
                evaluateAndOptimize()
                delay(30.seconds)
            }
        }
    }
    
    /**
     * 處理效能警報
     */
    private fun handlePerformanceAlert(alert: PerformanceAlert) {
        when (alert.type) {
            AlertType.HIGH_CPU -> {
                if (alert.severity >= AlertSeverity.WARNING) {
                    applyOptimization(OptimizationAction.REDUCE_CPU_LOAD)
                }
            }
            AlertType.HIGH_MEMORY -> {
                if (alert.severity >= AlertSeverity.WARNING) {
                    applyOptimization(OptimizationAction.REDUCE_MEMORY_USAGE)
                }
            }
            AlertType.SLOW_RESPONSE -> {
                applyOptimization(OptimizationAction.IMPROVE_RESPONSE_TIME)
            }
            AlertType.LOW_CACHE_HIT -> {
                applyOptimization(OptimizationAction.OPTIMIZE_CACHE)
            }
            AlertType.HIGH_ERROR_RATE -> {
                if (alert.severity >= AlertSeverity.ERROR) {
                    applyOptimization(OptimizationAction.ENABLE_FALLBACK_MODE)
                }
            }
            else -> {
                Logger.d("AdaptiveOptimizer", "Unhandled alert type: ${alert.type}")
            }
        }
    }
    
    /**
     * 評估和優化
     */
    private suspend fun evaluateAndOptimize() {
        val report = GlobalPerformanceDashboard.getReport()
        val metrics = report.metrics
        
        // 根據系統狀態選擇策略
        val newStrategy = when {
            // 高負載模式
            metrics.cpuUsage > 85 || metrics.memoryUsage > 85 -> {
                OptimizationStrategy.AGGRESSIVE
            }
            // 錯誤多發模式
            metrics.errorRate > 0.05 -> {
                OptimizationStrategy.SAFE_MODE
            }
            // 低效能模式
            metrics.averageResponseTime > 1.seconds -> {
                OptimizationStrategy.PERFORMANCE_FOCUS
            }
            // 正常模式
            else -> {
                OptimizationStrategy.BALANCED
            }
        }
        
        // 如果策略改變，應用新策略
        if (newStrategy != currentStrategy.value) {
            Logger.i("AdaptiveOptimizer", 
                "Switching strategy from ${currentStrategy.value} to $newStrategy")
            applyStrategy(newStrategy)
            currentStrategy.value = newStrategy
        }
    }
    
    /**
     * 應用優化策略
     */
    private fun applyStrategy(strategy: OptimizationStrategy) {
        when (strategy) {
            OptimizationStrategy.AGGRESSIVE -> {
                // 激進優化：犧牲功能換取效能
                applyOptimization(OptimizationAction.DISABLE_NON_ESSENTIAL_FEATURES)
                applyOptimization(OptimizationAction.REDUCE_CACHE_SIZE)
                applyOptimization(OptimizationAction.INCREASE_BATCH_SIZE)
                applyOptimization(OptimizationAction.REDUCE_UPDATE_FREQUENCY)
            }
            
            OptimizationStrategy.BALANCED -> {
                // 平衡模式：效能和功能平衡
                applyOptimization(OptimizationAction.RESTORE_DEFAULT_SETTINGS)
            }
            
            OptimizationStrategy.PERFORMANCE_FOCUS -> {
                // 效能優先：提升響應速度
                applyOptimization(OptimizationAction.INCREASE_CACHE_SIZE)
                applyOptimization(OptimizationAction.ENABLE_PRELOADING)
                applyOptimization(OptimizationAction.OPTIMIZE_QUERIES)
            }
            
            OptimizationStrategy.SAFE_MODE -> {
                // 安全模式：穩定性優先
                applyOptimization(OptimizationAction.ENABLE_FALLBACK_MODE)
                applyOptimization(OptimizationAction.INCREASE_RETRY_ATTEMPTS)
                applyOptimization(OptimizationAction.REDUCE_CONCURRENT_OPERATIONS)
            }
        }
    }
    
    /**
     * 應用單一優化動作
     */
    private fun applyOptimization(action: OptimizationAction) {
        Logger.d("AdaptiveOptimizer", "Applying optimization: $action")
        
        when (action) {
            OptimizationAction.REDUCE_CPU_LOAD -> {
                // 減少 CPU 負載
                OptimizationConfig.maxConcurrentOperations = 2
                OptimizationConfig.enableBackgroundProcessing = false
            }
            
            OptimizationAction.REDUCE_MEMORY_USAGE -> {
                // 減少記憶體使用
                OptimizationConfig.maxCacheSize = 100
                OptimizationConfig.maxFlowCollectors = 10
                // 觸發 GC - platform specific
                Logger.d("AdaptiveOptimizer", "GC requested")
            }
            
            OptimizationAction.IMPROVE_RESPONSE_TIME -> {
                // 改善響應時間
                OptimizationConfig.enablePreloading = true
                OptimizationConfig.cacheAggressiveness = 0.9
            }
            
            OptimizationAction.OPTIMIZE_CACHE -> {
                // 優化快取
                OptimizationConfig.cacheAggressiveness = 0.8
                OptimizationConfig.maxCacheSize = 500
            }
            
            OptimizationAction.ENABLE_FALLBACK_MODE -> {
                // 啟用降級模式
                OptimizationConfig.useFallbackEndpoints = true
                OptimizationConfig.enableOfflineMode = true
            }
            
            OptimizationAction.DISABLE_NON_ESSENTIAL_FEATURES -> {
                // 關閉非必要功能
                OptimizationConfig.enableAnalytics = false
                OptimizationConfig.enableBackgroundSync = false
                OptimizationConfig.enableAnimations = false
            }
            
            OptimizationAction.RESTORE_DEFAULT_SETTINGS -> {
                // 恢復預設設定
                OptimizationConfig.reset()
            }
            
            OptimizationAction.INCREASE_CACHE_SIZE -> {
                OptimizationConfig.maxCacheSize = 1000
            }
            
            OptimizationAction.REDUCE_CACHE_SIZE -> {
                OptimizationConfig.maxCacheSize = 50
            }
            
            OptimizationAction.ENABLE_PRELOADING -> {
                OptimizationConfig.enablePreloading = true
            }
            
            OptimizationAction.OPTIMIZE_QUERIES -> {
                OptimizationConfig.enableQueryOptimization = true
            }
            
            OptimizationAction.INCREASE_RETRY_ATTEMPTS -> {
                OptimizationConfig.maxRetryAttempts = 5
            }
            
            OptimizationAction.REDUCE_CONCURRENT_OPERATIONS -> {
                OptimizationConfig.maxConcurrentOperations = 1
            }
            
            OptimizationAction.INCREASE_BATCH_SIZE -> {
                OptimizationConfig.batchSize = 100
            }
            
            OptimizationAction.REDUCE_UPDATE_FREQUENCY -> {
                OptimizationConfig.updateInterval = 5.minutes
            }
        }
        
        // 記錄優化歷史
        optimizationHistory.add(action)
        
        // 只保留最近 100 條記錄
        if (optimizationHistory.size > 100) {
            optimizationHistory.removeAt(0)
        }
    }
    
    /**
     * 獲取當前優化策略
     */
    fun getCurrentStrategy(): StateFlow<OptimizationStrategy> = currentStrategy.asStateFlow()
    
    /**
     * 獲取優化歷史
     */
    fun getOptimizationHistory(): List<OptimizationAction> = optimizationHistory.toList()
    
    /**
     * 強制應用策略
     */
    fun forceStrategy(strategy: OptimizationStrategy) {
        Logger.i("AdaptiveOptimizer", "Force applying strategy: $strategy")
        applyStrategy(strategy)
        currentStrategy.value = strategy
    }
}

/**
 * 優化策略
 */
enum class OptimizationStrategy {
    AGGRESSIVE,         // 激進優化
    BALANCED,          // 平衡模式
    PERFORMANCE_FOCUS, // 效能優先
    SAFE_MODE          // 安全模式
}

/**
 * 優化動作
 */
enum class OptimizationAction {
    REDUCE_CPU_LOAD,
    REDUCE_MEMORY_USAGE,
    IMPROVE_RESPONSE_TIME,
    OPTIMIZE_CACHE,
    ENABLE_FALLBACK_MODE,
    DISABLE_NON_ESSENTIAL_FEATURES,
    RESTORE_DEFAULT_SETTINGS,
    INCREASE_CACHE_SIZE,
    REDUCE_CACHE_SIZE,
    ENABLE_PRELOADING,
    OPTIMIZE_QUERIES,
    INCREASE_RETRY_ATTEMPTS,
    REDUCE_CONCURRENT_OPERATIONS,
    INCREASE_BATCH_SIZE,
    REDUCE_UPDATE_FREQUENCY
}

/**
 * 優化配置
 */
object OptimizationConfig {
    var maxConcurrentOperations = 4
    var maxCacheSize = 500
    var maxFlowCollectors = 50
    var maxRetryAttempts = 3
    var batchSize = 50
    var updateInterval = 1.minutes
    var cacheAggressiveness = 0.7  // 0-1
    
    var enablePreloading = true
    var enableBackgroundProcessing = true
    var enableAnalytics = true
    var enableBackgroundSync = true
    var enableAnimations = true
    var enableQueryOptimization = true
    var enableOfflineMode = false
    var useFallbackEndpoints = false
    
    fun reset() {
        maxConcurrentOperations = 4
        maxCacheSize = 500
        maxFlowCollectors = 50
        maxRetryAttempts = 3
        batchSize = 50
        updateInterval = 1.minutes
        cacheAggressiveness = 0.7
        
        enablePreloading = true
        enableBackgroundProcessing = true
        enableAnalytics = true
        enableBackgroundSync = true
        enableAnimations = true
        enableQueryOptimization = true
        enableOfflineMode = false
        useFallbackEndpoints = false
    }
}

/**
 * 資源管理器
 */
class ResourceManager {
    
    private val resourcePool = mutableMapOf<String, ResourcePool>()
    
    /**
     * 獲取資源池
     */
    fun getPool(name: String, maxSize: Int = 10): ResourcePool {
        return resourcePool.getOrPut(name) {
            ResourcePool(name, maxSize)
        }
    }
    
    /**
     * 釋放所有資源
     */
    suspend fun releaseAll() {
        resourcePool.values.forEach { pool ->
            pool.release()
        }
        resourcePool.clear()
    }
}

/**
 * 資源池
 */
class ResourcePool(
    private val name: String,
    private val maxSize: Int
) {
    private val resources = mutableListOf<Any>()
    private val available = Channel<Any>(maxSize)
    
    /**
     * 獲取資源
     */
    suspend fun acquire(): Any {
        return available.tryReceive().getOrNull() 
            ?: createResource()
    }
    
    /**
     * 歸還資源
     */
    suspend fun returnResource(resource: Any) {
        if (resources.size < maxSize) {
            available.send(resource)
        }
    }
    
    /**
     * 創建新資源
     */
    private fun createResource(): Any {
        // 實際實作會根據資源類型創建
        return Any()
    }
    
    /**
     * 釋放所有資源
     */
    suspend fun release() {
        available.close()
        resources.clear()
    }
}

/**
 * 全局自適應優化器
 */
object GlobalAdaptiveOptimizer {
    val optimizer = AdaptiveOptimizer()
    val resourceManager = ResourceManager()
    
    /**
     * 初始化優化器
     */
    fun initialize() {
        Logger.i("GlobalAdaptiveOptimizer", "Adaptive optimizer initialized")
    }
    
    /**
     * 獲取當前策略
     */
    fun getCurrentStrategy() = optimizer.getCurrentStrategy()
    
    /**
     * 強制策略
     */
    fun forceStrategy(strategy: OptimizationStrategy) {
        optimizer.forceStrategy(strategy)
    }
    
    /**
     * 清理資源
     */
    suspend fun cleanup() {
        resourceManager.releaseAll()
    }
}
package com.cbstudio.wearwallet.production

import com.cbstudio.wearwallet.bridge.CoreKmpBridge
import com.cbstudio.wearwallet.domain.usecase.CoreKmpSendTransactionUseCase
import com.cbstudio.wearwallet.domain.usecase.CoreKmpGetBalanceUseCase
import com.cbstudio.wearwallet.domain.usecase.CoreKmpWalletManagementUseCase
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.shared.utils.Logger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.component.KoinComponent

class ProductionMonitoringSystem : KoinComponent {
    
    companion object {
        private const val TAG = "ProductionMonitoringSystem"
        
        // 性能基準值
        private const val MAX_INITIALIZATION_TIME_MS = 30000L // 30 秒
        private const val MAX_BALANCE_QUERY_TIME_MS = 10000L  // 10 秒
        private const val MAX_TRANSACTION_TIME_MS = 60000L    // 60 秒
        private const val MIN_SUCCESS_RATE = 0.9             // 90% 成功率
    }
    
    // 注入依賴 (optional / disabled in release)
    private val coreKmpBridge: CoreKmpBridge? by lazy { getKoin().getOrNull() }
    private val walletManagementUseCase: CoreKmpWalletManagementUseCase? by lazy { getKoin().getOrNull() }
    private val getBalanceUseCase: CoreKmpGetBalanceUseCase? by lazy { getKoin().getOrNull() }
    private val sendTransactionUseCase: CoreKmpSendTransactionUseCase? by lazy { getKoin().getOrNull() }
    
    // 監控作用域
    private val monitoringScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 系統狀態
    private val _systemHealth = MutableStateFlow(SystemHealth())
    val systemHealth: StateFlow<SystemHealth> = _systemHealth.asStateFlow()
    
    // 性能指標
    private val _performanceMetrics = MutableStateFlow(PerformanceMetrics())
    val performanceMetrics: StateFlow<PerformanceMetrics> = _performanceMetrics.asStateFlow()
    
    /**
     * 系統健康狀態
     */
    data class SystemHealth(
        val overallStatus: HealthStatus = HealthStatus.UNKNOWN,
        val coreKmpStatus: HealthStatus = HealthStatus.UNKNOWN,
        val bridgeStatus: HealthStatus = HealthStatus.UNKNOWN,
        val useCaseStatus: HealthStatus = HealthStatus.UNKNOWN,
        val chainStatuses: Map<MultiChainType, HealthStatus> = emptyMap(),
        val lastUpdateTime: Long = System.currentTimeMillis(),
        val uptime: Long = 0L,
        val errorCount: Int = 0,
        val warningCount: Int = 0
    )
    
    /**
     * 性能指標
     */
    data class PerformanceMetrics(
        val initializationTime: Long = 0L,
        val averageBalanceQueryTime: Long = 0L,
        val averageTransactionTime: Long = 0L,
        val successRate: Double = 0.0,
        val totalOperations: Long = 0L,
        val successfulOperations: Long = 0L,
        val failedOperations: Long = 0L,
        val memoryUsage: Long = 0L,
        val chainPerformance: Map<MultiChainType, ChainPerformance> = emptyMap()
    )
    
    /**
     * 鏈性能指標
     */
    data class ChainPerformance(
        val averageResponseTime: Long,
        val successRate: Double,
        val totalRequests: Long,
        val successfulRequests: Long,
        val lastError: String? = null
    )
    
    enum class HealthStatus {
        HEALTHY,    // 健康
        WARNING,    // 警告
        CRITICAL,   // 危險
        UNKNOWN     // 未知
    }
    
    /**
     * 啟動生產監控
     */
    suspend fun startProductionMonitoring() {
        try {
            Logger.i(TAG, "啟動生產監控系統...")
            
            val startTime = System.currentTimeMillis()
            
            // 1. 系統初始化健康檢查
            performSystemHealthCheck()
            
            // 2. 核心組件狀態檢查
            checkCoreComponents()
            
            // 3. 多鏈連接狀態檢查
            checkChainConnectivity()
            
            // 4. 性能基準測試
            performPerformanceBenchmark()
            
            val endTime = System.currentTimeMillis()
            val initTime = endTime - startTime
            
            updatePerformanceMetrics { metrics ->
                metrics.copy(initializationTime = initTime)
            }
            
            Logger.i(TAG, "生產監控系統啟動完成，耗時: ${initTime}ms")
            
            // 更新系統狀態
            updateSystemHealth { health ->
                health.copy(
                    overallStatus = if (initTime < MAX_INITIALIZATION_TIME_MS) {
                        HealthStatus.HEALTHY
                    } else {
                        HealthStatus.WARNING
                    },
                    uptime = System.currentTimeMillis()
                )
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "生產監控啟動失敗", e)
            updateSystemHealth { health ->
                health.copy(
                    overallStatus = HealthStatus.CRITICAL,
                    errorCount = health.errorCount + 1
                )
            }
        }
    }
    
    /**
     * 系統健康檢查
     */
    private suspend fun performSystemHealthCheck() {
        try {
            Logger.d(TAG, "執行系統健康檢查...")
            
            // 檢查 CoreKmp 初始化狀態
            val isInit = coreKmpBridge?.getWalletAddress(com.cbstudio.wearwallet.core.multichain.MultiChainType.ETHEREUM) is Result.Success
            val coreKmpStatus = if (isInit) HealthStatus.HEALTHY else HealthStatus.UNKNOWN
            
            updateSystemHealth { health ->
                health.copy(coreKmpStatus = coreKmpStatus)
            }
            
            Logger.d(TAG, "CoreKmp 狀態: $coreKmpStatus")
            
        } catch (e: Exception) {
            Logger.e(TAG, "系統健康檢查失敗", e)
            updateSystemHealth { health ->
                health.copy(
                    coreKmpStatus = HealthStatus.CRITICAL,
                    errorCount = health.errorCount + 1
                )
            }
        }
    }
    
    /**
     * 核心組件檢查
     */
    private suspend fun checkCoreComponents() {
        try {
            Logger.d(TAG, "檢查核心組件...")
            
            // 檢查橋接器
            val bridgeStatus = try {
                val supportedChains = coreKmpBridge?.getSupportedChains() ?: emptyList()
                if (supportedChains.isNotEmpty()) HealthStatus.HEALTHY else HealthStatus.WARNING
            } catch (e: Exception) {
                Logger.w(TAG, "橋接器檢查失敗", e)
                HealthStatus.CRITICAL
            }
            
            // 檢查 UseCase
            val useCaseStatus = try {
                val chains = walletManagementUseCase?.getSupportedChains() ?: emptyList()
                if (chains.isNotEmpty()) HealthStatus.HEALTHY else HealthStatus.WARNING
            } catch (e: Exception) {
                Logger.w(TAG, "UseCase 檢查失敗", e)
                HealthStatus.CRITICAL
            }
            
            updateSystemHealth { health ->
                health.copy(
                    bridgeStatus = bridgeStatus,
                    useCaseStatus = useCaseStatus
                )
            }
            
            Logger.d(TAG, "核心組件狀態 - Bridge: $bridgeStatus, UseCase: $useCaseStatus")
            
        } catch (e: Exception) {
            Logger.e(TAG, "核心組件檢查失敗", e)
            updateSystemHealth { health ->
                health.copy(errorCount = health.errorCount + 1)
            }
        }
    }
    
    /**
     * 檢查區塊鏈連接狀態
     */
    private suspend fun checkChainConnectivity() {
        try {
            Logger.d(TAG, "檢查區塊鏈連接狀態...")
            
            val supportedChains = coreKmpBridge?.getSupportedChains() ?: emptyList()
            val chainStatuses = mutableMapOf<MultiChainType, HealthStatus>()
            val chainPerformance = mutableMapOf<MultiChainType, ChainPerformance>()
            
            supportedChains.forEach { chainType ->
                try {
                    val startTime = System.currentTimeMillis()
                    
                    // 測試地址獲取
                    val address = walletManagementUseCase?.getWalletAddress(chainType) ?: ""
                    val addressSuccess = address.isNotEmpty()
                    
                    // 測試餘額查詢（如果有地址）
                    var balanceSuccess = true
                    if (addressSuccess && getBalanceUseCase != null) {
                        try {
                            getBalanceUseCase?.invoke(address, chainType)?.first()
                        } catch (e: Exception) {
                            balanceSuccess = false
                            Logger.w(TAG, "$chainType 餘額查詢失敗", e)
                        }
                    }
                    
                    val endTime = System.currentTimeMillis()
                    val responseTime = endTime - startTime
                    
                    val status = when {
                        addressSuccess && balanceSuccess -> HealthStatus.HEALTHY
                        addressSuccess -> HealthStatus.WARNING
                        else -> HealthStatus.CRITICAL
                    }
                    
                    chainStatuses[chainType] = status
                    chainPerformance[chainType] = ChainPerformance(
                        averageResponseTime = responseTime,
                        successRate = if (addressSuccess && balanceSuccess) 1.0 else 0.5,
                        totalRequests = 1,
                        successfulRequests = if (status == HealthStatus.HEALTHY) 1 else 0
                    )
                    
                    Logger.d(TAG, "$chainType 狀態: $status, 響應時間: ${responseTime}ms")
                    
                } catch (e: Exception) {
                    Logger.w(TAG, "$chainType 連接檢查失敗", e)
                    chainStatuses[chainType] = HealthStatus.CRITICAL
                    chainPerformance[chainType] = ChainPerformance(
                        averageResponseTime = MAX_BALANCE_QUERY_TIME_MS,
                        successRate = 0.0,
                        totalRequests = 1,
                        successfulRequests = 0,
                        lastError = e.message
                    )
                }
            }
            
            updateSystemHealth { health ->
                health.copy(chainStatuses = chainStatuses)
            }
            
            updatePerformanceMetrics { metrics ->
                metrics.copy(chainPerformance = chainPerformance)
            }
            
            val healthyChains = chainStatuses.values.count { it == HealthStatus.HEALTHY }
            Logger.i(TAG, "區塊鏈連接檢查完成: $healthyChains/${supportedChains.size} 健康")
            
        } catch (e: Exception) {
            Logger.e(TAG, "區塊鏈連接檢查失敗", e)
            updateSystemHealth { health ->
                health.copy(errorCount = health.errorCount + 1)
            }
        }
    }
    
    /**
     * 性能基準測試
     */
    private suspend fun performPerformanceBenchmark() {
        try {
            Logger.d(TAG, "執行性能基準測試...")
            
            var totalOperations = 0L
            var successfulOperations = 0L
            var totalResponseTime = 0L
            
            // 測試主要鏈的性能
            val testChains = listOf(
                MultiChainType.ETHEREUM,
                MultiChainType.BSC,
                MultiChainType.POLYGON
            )
            
            testChains.forEach { chainType ->
                try {
                    val startTime = System.currentTimeMillis()
                    
                    val address = walletManagementUseCase?.getWalletAddress(chainType) ?: ""
                    if (address.isNotEmpty()) {
                        // 測試餘額查詢性能
                        getBalanceUseCase?.invoke(address, chainType)?.first()
                        
                        // 測試 Gas 估算性能
                        sendTransactionUseCase?.estimateGas(
                            fromAddress = address,
                            toAddress = address,
                            amount = "0.001",
                            chainType = chainType
                        )
                    }
                    
                    val endTime = System.currentTimeMillis()
                    val responseTime = endTime - startTime
                    
                    totalOperations++
                    successfulOperations++
                    totalResponseTime += responseTime
                    
                    Logger.d(TAG, "$chainType 性能測試: ${responseTime}ms")
                    
                } catch (e: Exception) {
                    totalOperations++
                    Logger.w(TAG, "$chainType 性能測試失敗", e)
                }
            }
            
            val averageResponseTime = if (totalOperations > 0) {
                totalResponseTime / totalOperations
            } else 0L
            
            val successRate = if (totalOperations > 0) {
                successfulOperations.toDouble() / totalOperations
            } else 0.0
            
            updatePerformanceMetrics { metrics ->
                metrics.copy(
                    averageBalanceQueryTime = averageResponseTime,
                    totalOperations = totalOperations,
                    successfulOperations = successfulOperations,
                    failedOperations = totalOperations - successfulOperations,
                    successRate = successRate,
                    memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                )
            }
            
            Logger.i(TAG, "性能基準測試完成 - 平均響應時間: ${averageResponseTime}ms, 成功率: ${(successRate * 100).toInt()}%")
            
        } catch (e: Exception) {
            Logger.e(TAG, "性能基準測試失敗", e)
            updateSystemHealth { health ->
                health.copy(errorCount = health.errorCount + 1)
            }
        }
    }
    
    /**
     * 獲取生產就緒狀態報告
     */
    fun getProductionReadinessReport(): ProductionReadinessReport {
        val health = _systemHealth.value
        val metrics = _performanceMetrics.value
        
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // 檢查初始化時間
        if (metrics.initializationTime > MAX_INITIALIZATION_TIME_MS) {
            issues.add("初始化時間過長: ${metrics.initializationTime}ms (標準: <${MAX_INITIALIZATION_TIME_MS}ms)")
            recommendations.add("優化初始化流程，考慮延遲載入非關鍵組件")
        }
        
        // 檢查成功率
        if (metrics.successRate < MIN_SUCCESS_RATE) {
            issues.add("操作成功率過低: ${(metrics.successRate * 100).toInt()}% (標準: >${(MIN_SUCCESS_RATE * 100).toInt()}%)")
            recommendations.add("檢查網路連接穩定性和錯誤處理機制")
        }
        
        // 檢查鏈狀態
        val unhealthyChains = health.chainStatuses.filterValues { it != HealthStatus.HEALTHY }
        if (unhealthyChains.isNotEmpty()) {
            issues.add("部分區塊鏈連接異常: ${unhealthyChains.keys.joinToString()}")
            recommendations.add("檢查區塊鏈節點配置和網路設定")
        }
        
        val readinessScore = calculateReadinessScore(health, metrics)
        
        return ProductionReadinessReport(
            readinessScore = readinessScore,
            overallStatus = health.overallStatus,
            issues = issues,
            recommendations = recommendations,
            healthSummary = health,
            performanceSummary = metrics,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * 計算生產就緒分數
     */
    private fun calculateReadinessScore(health: SystemHealth, metrics: PerformanceMetrics): Double {
        var score = 0.0
        
        // 系統健康狀態 (40%)
        when (health.overallStatus) {
            HealthStatus.HEALTHY -> score += 40.0
            HealthStatus.WARNING -> score += 25.0
            HealthStatus.CRITICAL -> score += 5.0
            HealthStatus.UNKNOWN -> score += 0.0
        }
        
        // 性能指標 (35%)
        if (metrics.initializationTime < MAX_INITIALIZATION_TIME_MS) score += 15.0
        if (metrics.successRate >= MIN_SUCCESS_RATE) score += 20.0
        
        // 區塊鏈支援 (25%)
        val healthyChains = health.chainStatuses.values.count { it == HealthStatus.HEALTHY }
        val totalChains = health.chainStatuses.size
        if (totalChains > 0) {
            score += (healthyChains.toDouble() / totalChains) * 25.0
        }
        
        return score.coerceIn(0.0, 100.0)
    }
    
    // 輔助方法
    private fun updateSystemHealth(update: (SystemHealth) -> SystemHealth) {
        _systemHealth.value = update(_systemHealth.value)
    }
    
    private fun updatePerformanceMetrics(update: (PerformanceMetrics) -> PerformanceMetrics) {
        _performanceMetrics.value = update(_performanceMetrics.value)
    }
}

/**
 * 生產就緒報告
 */
data class ProductionReadinessReport(
    val readinessScore: Double,
    val overallStatus: ProductionMonitoringSystem.HealthStatus,
    val issues: List<String>,
    val recommendations: List<String>,
    val healthSummary: ProductionMonitoringSystem.SystemHealth,
    val performanceSummary: ProductionMonitoringSystem.PerformanceMetrics,
    val timestamp: Long
)
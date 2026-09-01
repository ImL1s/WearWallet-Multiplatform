package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.multichain.sdk.*
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import co.touchlab.kermit.Logger
import kotlin.math.abs
import kotlin.math.min
import kotlinx.datetime.Clock

/**
 * 智能路由系統
 * 
 * 自動選擇最佳交易路徑、優化手續費和確認時間
 */
class SmartRouter(
    private val walletManager: MultiChainWalletManager,
    private val bridge: CrossChainBridge? = null
) {
    
    private val logger = Logger.withTag("SmartRouter")
    
    // 路由狀態
    private val _routerState = MutableStateFlow(RouterState())
    val routerState: StateFlow<RouterState> = _routerState.asStateFlow()
    
    /**
     * 路由器狀態
     */
    data class RouterState(
        val isActive: Boolean = false,
        val networkConditions: Map<MultiChainType, NetworkCondition> = emptyMap(),
        val routeCache: Map<String, Route> = emptyMap(),
        val statistics: RouterStatistics = RouterStatistics()
    )
    
    /**
     * 網路條件
     */
    data class NetworkCondition(
        val chainType: MultiChainType,
        val congestionLevel: CongestionLevel,
        val averageGasPrice: String,
        val averageConfirmationTime: Long,
        val reliability: Double, // 0.0 - 1.0
        val lastUpdated: Long
    )
    
    /**
     * 擁塞等級
     */
    enum class CongestionLevel {
        LOW,
        NORMAL,
        HIGH,
        SEVERE
    }
    
    /**
     * 路由
     */
    data class Route(
        val id: String,
        val source: ChainEndpoint,
        val destination: ChainEndpoint,
        val path: List<ChainHop>,
        val estimatedCost: RouteCost,
        val estimatedTime: Long,
        val reliability: Double,
        val score: Double
    )
    
    /**
     * 鏈端點
     */
    data class ChainEndpoint(
        val chainType: MultiChainType,
        val address: String,
        val token: String? = null
    )
    
    /**
     * 鏈跳躍
     */
    data class ChainHop(
        val chainType: MultiChainType,
        val action: HopAction,
        val protocol: String? = null,
        val estimatedFee: String,
        val estimatedTime: Long
    )
    
    /**
     * 跳躍動作
     */
    enum class HopAction {
        TRANSFER,      // 簡單轉帳
        SWAP,          // 代幣交換
        BRIDGE,        // 跨鏈橋接
        WRAP,          // 包裝/解包裝
        STAKE,         // 質押
        UNSTAKE        // 解除質押
    }
    
    /**
     * 路由成本
     */
    data class RouteCost(
        val totalFee: String,
        val totalFeeUsd: String,
        val breakdown: Map<MultiChainType, String>
    )
    
    /**
     * 路由統計
     */
    data class RouterStatistics(
        val totalRoutesCalculated: Int = 0,
        val successfulRoutes: Int = 0,
        val failedRoutes: Int = 0,
        val averageSavings: Double = 0.0,
        val averageTimeReduction: Long = 0
    )
    
    /**
     * 路由請求
     */
    data class RouteRequest(
        val from: ChainEndpoint,
        val to: ChainEndpoint,
        val amount: String,
        val preferences: RoutePreferences = RoutePreferences()
    )
    
    /**
     * 路由偏好
     */
    data class RoutePreferences(
        val priority: RoutePriority = RoutePriority.BALANCED,
        val maxHops: Int = 3,
        val maxFeeUsd: String? = null,
        val maxTime: Long? = null,
        val avoidChains: Set<MultiChainType> = emptySet(),
        val preferredProtocols: Set<String> = emptySet()
    )
    
    /**
     * 路由優先級
     */
    enum class RoutePriority {
        LOWEST_COST,    // 最低成本
        FASTEST,        // 最快速度
        BALANCED,       // 平衡
        MOST_RELIABLE   // 最可靠
    }
    
    /**
     * 初始化智能路由器
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            logger.i("Initializing Smart Router")
            
            // 更新網路條件
            val conditions = updateNetworkConditions()
            
            _routerState.value = _routerState.value.copy(
                isActive = true,
                networkConditions = conditions
            )
            
            logger.i("Smart Router initialized with ${conditions.size} networks")
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to initialize Smart Router", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 查找最佳路由
     */
    suspend fun findBestRoute(request: RouteRequest): Result<Route> {
        return try {
            logger.i("Finding best route: ${request.from.chainType} -> ${request.to.chainType}")
            
            // 檢查緩存
            val cacheKey = getCacheKey(request)
            _routerState.value.routeCache[cacheKey]?.let { cachedRoute ->
                if (isRouteValid(cachedRoute)) {
                    logger.i("Using cached route: ${cachedRoute.id}")
                    return Result.Success(cachedRoute)
                }
            }
            
            // 生成所有可能的路由
            val possibleRoutes = generatePossibleRoutes(request)
            
            if (possibleRoutes.isEmpty()) {
                return Result.Failure(Exception("No routes available"))
            }
            
            // 評分和排序路由
            val scoredRoutes = coroutineScope {
                possibleRoutes.map { route ->
                    async {
                        scoreRoute(route, request.preferences)
                    }
                }.awaitAll()
            }
            
            // 選擇最佳路由
            val bestRoute = scoredRoutes.maxByOrNull { it.score }
                ?: return Result.Failure(Exception("Failed to score routes"))
            
            // 緩存路由
            cacheRoute(cacheKey, bestRoute)
            
            // 更新統計
            updateStatistics(bestRoute)
            
            logger.i("Best route found: ${bestRoute.path.size} hops, cost: ${bestRoute.estimatedCost.totalFeeUsd} USD")
            Result.Success(bestRoute)
        } catch (e: Exception) {
            logger.e("Failed to find best route", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 執行路由
     */
    suspend fun executeRoute(
        route: Route,
        userPrivateKey: String? = null // 實際應用中需要安全處理
    ): Result<RouteExecutionResult> {
        return try {
            logger.i("Executing route: ${route.id}")
            
            val executionSteps = mutableListOf<ExecutionStep>()
            var currentAmount = route.source.token ?: "native"
            
            // 執行每個跳躍
            for ((index, hop) in route.path.withIndex()) {
                logger.i("Executing hop ${index + 1}/${route.path.size}: ${hop.action} on ${hop.chainType}")
                
                val stepResult = executeHop(hop, currentAmount, userPrivateKey)
                
                if (stepResult is Result.Failure) {
                    return Result.Failure(stepResult.exception)
                }
                
                val step = (stepResult as Result.Success).data
                executionSteps.add(step)
                
                // 更新當前金額（可能因為交換而改變）
                currentAmount = step.outputAmount ?: currentAmount
            }
            
            val result = RouteExecutionResult(
                routeId = route.id,
                success = true,
                steps = executionSteps,
                totalCost = route.estimatedCost,
                totalTime = executionSteps.sumOf { it.executionTime },
                finalAmount = currentAmount
            )
            
            logger.i("Route executed successfully: ${result.finalAmount} received")
            Result.Success(result)
        } catch (e: Exception) {
            logger.e("Failed to execute route", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 路由執行結果
     */
    data class RouteExecutionResult(
        val routeId: String,
        val success: Boolean,
        val steps: List<ExecutionStep>,
        val totalCost: RouteCost,
        val totalTime: Long,
        val finalAmount: String
    )
    
    /**
     * 執行步驟
     */
    data class ExecutionStep(
        val hop: ChainHop,
        val transactionHash: String,
        val status: TransactionStatus,
        val actualFee: String,
        val executionTime: Long,
        val outputAmount: String? = null
    )
    
    /**
     * 優化交易參數
     */
    suspend fun optimizeTransactionParams(
        chainType: MultiChainType,
        baseRequest: TransactionRequest
    ): Result<TransactionRequest> {
        return try {
            logger.i("Optimizing transaction parameters for ${chainType}")
            
            val condition = _routerState.value.networkConditions[chainType]
                ?: return Result.Success(baseRequest)
            
            // 根據網路擁塞調整優先級
            val optimizedPriority = when (condition.congestionLevel) {
                CongestionLevel.LOW -> TransactionPriority.LOW
                CongestionLevel.NORMAL -> TransactionPriority.NORMAL
                CongestionLevel.HIGH -> TransactionPriority.HIGH
                CongestionLevel.SEVERE -> TransactionPriority.URGENT
            }
            
            // 計算最優 Gas 價格
            val optimizedGasPrice = calculateOptimalGasPrice(
                condition.averageGasPrice,
                optimizedPriority
            )
            
            val optimizedRequest = baseRequest.copy(
                priority = optimizedPriority,
                customGasPrice = optimizedGasPrice,
                customGasLimit = calculateOptimalGasLimit(chainType, baseRequest)
            )
            
            logger.i("Transaction optimized: priority=${optimizedPriority}, gasPrice=${optimizedGasPrice}")
            Result.Success(optimizedRequest)
        } catch (e: Exception) {
            logger.e("Failed to optimize transaction", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取網路建議
     */
    fun getNetworkRecommendations(): List<NetworkRecommendation> {
        val recommendations = mutableListOf<NetworkRecommendation>()
        
        _routerState.value.networkConditions.forEach { (chain, condition) ->
            when (condition.congestionLevel) {
                CongestionLevel.SEVERE -> {
                    recommendations.add(
                        NetworkRecommendation(
                            chainType = chain,
                            level = RecommendationLevel.WARNING,
                            message = "Network severely congested. Consider delaying transactions.",
                            alternativeChains = findAlternativeChains(chain)
                        )
                    )
                }
                CongestionLevel.HIGH -> {
                    recommendations.add(
                        NetworkRecommendation(
                            chainType = chain,
                            level = RecommendationLevel.INFO,
                            message = "Network congestion high. Higher fees may apply.",
                            suggestedGasPrice = calculateSuggestedGasPrice(condition)
                        )
                    )
                }
                else -> {
                    // 正常或低擁塞，無需建議
                }
            }
        }
        
        return recommendations
    }
    
    /**
     * 網路建議
     */
    data class NetworkRecommendation(
        val chainType: MultiChainType,
        val level: RecommendationLevel,
        val message: String,
        val alternativeChains: List<MultiChainType> = emptyList(),
        val suggestedGasPrice: String? = null
    )
    
    /**
     * 建議等級
     */
    enum class RecommendationLevel {
        INFO,
        WARNING,
        CRITICAL
    }
    
    // === 私有輔助方法 ===
    
    private suspend fun updateNetworkConditions(): Map<MultiChainType, NetworkCondition> {
        val conditions = mutableMapOf<MultiChainType, NetworkCondition>()
        
        // 獲取所有活躍鏈的網路狀態
        walletManager.getSupportedChains().forEach { chain ->
            val status = walletManager.getNetworkStatus(chain)
            if (status is Result.Success) {
                conditions[chain] = NetworkCondition(
                    chainType = chain,
                    congestionLevel = estimateCongestionLevel(status.data),
                    averageGasPrice = estimateGasPrice(chain),
                    averageConfirmationTime = status.data.averageBlockTime ?: 30000,
                    reliability = calculateReliability(status.data),
                    lastUpdated = Clock.System.now().toEpochMilliseconds()
                )
            }
        }
        
        return conditions
    }
    
    private fun estimateCongestionLevel(status: NetworkStatus): CongestionLevel {
        // 簡化的擁塞估算
        val syncProgress = status.syncProgress ?: 1.0
        return when {
            syncProgress < 0.9 -> CongestionLevel.SEVERE
            syncProgress < 0.95 -> CongestionLevel.HIGH
            syncProgress < 0.99 -> CongestionLevel.NORMAL
            else -> CongestionLevel.LOW
        }
    }
    
    private fun estimateGasPrice(chain: MultiChainType): String {
        // 簡化的 Gas 價格估算
        return when (chain) {
            MultiChainType.ETHEREUM -> "30"
            MultiChainType.BSC -> "5"
            MultiChainType.POLYGON -> "30"
            MultiChainType.SOLANA -> "0.00025"
            else -> "1"
        }
    }
    
    private fun calculateReliability(status: NetworkStatus): Double {
        // 基於網路狀態計算可靠性
        return if (status.isConnected) {
            min(1.0, (status.peersCount ?: 0) / 10.0)
        } else {
            0.0
        }
    }
    
    private fun generatePossibleRoutes(request: RouteRequest): List<Route> {
        val routes = mutableListOf<Route>()
        
        // 直接路由（如果在同一條鏈）
        if (request.from.chainType == request.to.chainType) {
            routes.add(createDirectRoute(request))
        }
        
        // 橋接路由（如果在不同鏈）
        if (request.from.chainType != request.to.chainType && bridge != null) {
            if (bridge.isBridgeSupported(request.from.chainType, request.to.chainType)) {
                routes.add(createBridgeRoute(request))
            }
            
            // 多跳路由
            if (request.preferences.maxHops > 1) {
                routes.addAll(createMultiHopRoutes(request))
            }
        }
        
        return routes.filter { route ->
            // 過濾掉包含要避免的鏈的路由
            route.path.none { hop ->
                hop.chainType in request.preferences.avoidChains
            }
        }
    }
    
    private fun createDirectRoute(request: RouteRequest): Route {
        val hop = ChainHop(
            chainType = request.from.chainType,
            action = HopAction.TRANSFER,
            protocol = null,
            estimatedFee = estimateTransferFee(request.from.chainType, request.amount),
            estimatedTime = estimateTransferTime(request.from.chainType)
        )
        
        return Route(
            id = "direct_${Clock.System.now().toEpochMilliseconds()}",
            source = request.from,
            destination = request.to,
            path = listOf(hop),
            estimatedCost = RouteCost(
                totalFee = hop.estimatedFee,
                totalFeeUsd = convertToUsd(hop.estimatedFee, request.from.chainType),
                breakdown = mapOf(request.from.chainType to hop.estimatedFee)
            ),
            estimatedTime = hop.estimatedTime,
            reliability = 0.99,
            score = 0.0
        )
    }
    
    private fun createBridgeRoute(request: RouteRequest): Route {
        val bridgeHop = ChainHop(
            chainType = request.from.chainType,
            action = HopAction.BRIDGE,
            protocol = "generic_bridge",
            estimatedFee = estimateBridgeFee(request.from.chainType, request.to.chainType, request.amount),
            estimatedTime = estimateBridgeTime(request.from.chainType, request.to.chainType)
        )
        
        return Route(
            id = "bridge_${Clock.System.now().toEpochMilliseconds()}",
            source = request.from,
            destination = request.to,
            path = listOf(bridgeHop),
            estimatedCost = RouteCost(
                totalFee = bridgeHop.estimatedFee,
                totalFeeUsd = convertToUsd(bridgeHop.estimatedFee, request.from.chainType),
                breakdown = mapOf(
                    request.from.chainType to bridgeHop.estimatedFee
                )
            ),
            estimatedTime = bridgeHop.estimatedTime,
            reliability = 0.95,
            score = 0.0
        )
    }
    
    private fun createMultiHopRoutes(request: RouteRequest): List<Route> {
        // 簡化的多跳路由生成
        val routes = mutableListOf<Route>()
        
        // 通過中間鏈的路由
        val intermediateChains = listOf(
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON
        ).filter { 
            it != request.from.chainType && 
            it != request.to.chainType &&
            it !in request.preferences.avoidChains
        }
        
        intermediateChains.forEach { intermediate ->
            val hop1 = ChainHop(
                chainType = request.from.chainType,
                action = HopAction.BRIDGE,
                protocol = "bridge_to_${intermediate}",
                estimatedFee = estimateBridgeFee(request.from.chainType, intermediate, request.amount),
                estimatedTime = estimateBridgeTime(request.from.chainType, intermediate)
            )
            
            val hop2 = ChainHop(
                chainType = intermediate,
                action = HopAction.BRIDGE,
                protocol = "bridge_to_${request.to.chainType}",
                estimatedFee = estimateBridgeFee(intermediate, request.to.chainType, request.amount),
                estimatedTime = estimateBridgeTime(intermediate, request.to.chainType)
            )
            
            routes.add(
                Route(
                    id = "multihop_${intermediate}_${Clock.System.now().toEpochMilliseconds()}",
                    source = request.from,
                    destination = request.to,
                    path = listOf(hop1, hop2),
                    estimatedCost = RouteCost(
                        totalFee = (hop1.estimatedFee.toDoubleOrNull() ?: 0.0 + 
                                   (hop2.estimatedFee.toDoubleOrNull() ?: 0.0)).toString(),
                        totalFeeUsd = (convertToUsd(hop1.estimatedFee, request.from.chainType).toDoubleOrNull() ?: 0.0 +
                                      (convertToUsd(hop2.estimatedFee, intermediate).toDoubleOrNull() ?: 0.0)).toString(),
                        breakdown = mapOf(
                            request.from.chainType to hop1.estimatedFee,
                            intermediate to hop2.estimatedFee
                        )
                    ),
                    estimatedTime = hop1.estimatedTime + hop2.estimatedTime,
                    reliability = 0.9,
                    score = 0.0
                )
            )
        }
        
        return routes
    }
    
    private suspend fun scoreRoute(route: Route, preferences: RoutePreferences): Route {
        var score = 100.0
        
        // 根據優先級調整評分
        when (preferences.priority) {
            RoutePriority.LOWEST_COST -> {
                score -= route.estimatedCost.totalFeeUsd.toDoubleOrNull() ?: 0.0
            }
            RoutePriority.FASTEST -> {
                score -= route.estimatedTime / 1000.0 // 轉換為秒
            }
            RoutePriority.MOST_RELIABLE -> {
                score += route.reliability * 50
            }
            RoutePriority.BALANCED -> {
                val costPenalty = (route.estimatedCost.totalFeeUsd.toDoubleOrNull() ?: 0.0) * 0.5
                val timePenalty = (route.estimatedTime / 1000.0) * 0.3
                val reliabilityBonus = route.reliability * 20
                score = score - costPenalty - timePenalty + reliabilityBonus
            }
        }
        
        // 懲罰過多跳躍
        score -= (route.path.size - 1) * 5
        
        // 獎勵使用偏好的協議
        route.path.forEach { hop ->
            if (hop.protocol in preferences.preferredProtocols) {
                score += 10
            }
        }
        
        return route.copy(score = score)
    }
    
    private suspend fun executeHop(
        hop: ChainHop,
        amount: String,
        privateKey: String?
    ): Result<ExecutionStep> {
        // 模擬執行跳躍
        return Result.Success(
            ExecutionStep(
                hop = hop,
                transactionHash = "0x${Clock.System.now().toEpochMilliseconds()}",
                status = TransactionStatus.CONFIRMED,
                actualFee = hop.estimatedFee,
                executionTime = hop.estimatedTime,
                outputAmount = amount
            )
        )
    }
    
    private fun estimateTransferFee(chain: MultiChainType, amount: String): String {
        // 簡化的手續費估算
        return when (chain) {
            MultiChainType.ETHEREUM -> "0.001"
            MultiChainType.BSC -> "0.0001"
            MultiChainType.SOLANA -> "0.00025"
            else -> "0.001"
        }
    }
    
    private fun estimateTransferTime(chain: MultiChainType): Long {
        // 簡化的時間估算（毫秒）
        return when (chain) {
            MultiChainType.SOLANA -> 400
            MultiChainType.BSC -> 3000
            MultiChainType.ETHEREUM -> 15000
            else -> 30000
        }
    }
    
    private fun estimateBridgeFee(from: MultiChainType, to: MultiChainType, amount: String): String {
        // 簡化的橋接費用估算
        val baseFee = 0.003 // 0.3%
        return (amount.toDoubleOrNull() ?: 0.0 * baseFee).toString()
    }
    
    private fun estimateBridgeTime(from: MultiChainType, to: MultiChainType): Long {
        // 簡化的橋接時間估算（毫秒）
        return 300000 // 5 分鐘
    }
    
    private fun convertToUsd(amount: String, chain: MultiChainType): String {
        // 簡化的 USD 轉換
        val price = when (chain) {
            MultiChainType.ETHEREUM -> 2000.0
            MultiChainType.BSC -> 300.0
            MultiChainType.SOLANA -> 100.0
            else -> 1.0
        }
        return (amount.toDoubleOrNull() ?: 0.0 * price).toString()
    }
    
    private fun calculateOptimalGasPrice(basePrice: String, priority: TransactionPriority): String {
        val multiplier = when (priority) {
            TransactionPriority.LOW -> 0.8
            TransactionPriority.NORMAL -> 1.0
            TransactionPriority.HIGH -> 1.5
            TransactionPriority.URGENT -> 2.0
        }
        return ((basePrice.toDoubleOrNull() ?: 0.0) * multiplier).toString()
    }
    
    private fun calculateOptimalGasLimit(chain: MultiChainType, request: TransactionRequest): String {
        // 簡化的 Gas 限制計算
        return when {
            request.tokenAddress != null -> "100000" // 代幣轉帳
            else -> "21000" // 原生幣轉帳
        }
    }
    
    private fun calculateSuggestedGasPrice(condition: NetworkCondition): String {
        val multiplier = when (condition.congestionLevel) {
            CongestionLevel.LOW -> 0.9
            CongestionLevel.NORMAL -> 1.0
            CongestionLevel.HIGH -> 1.3
            CongestionLevel.SEVERE -> 1.6
        }
        return ((condition.averageGasPrice.toDoubleOrNull() ?: 0.0) * multiplier).toString()
    }
    
    private fun findAlternativeChains(chain: MultiChainType): List<MultiChainType> {
        // 根據鏈類型建議替代鏈
        return when (chain) {
            MultiChainType.ETHEREUM -> listOf(MultiChainType.POLYGON, MultiChainType.BSC, MultiChainType.ARBITRUM)
            MultiChainType.BSC -> listOf(MultiChainType.POLYGON, MultiChainType.AVALANCHE)
            else -> emptyList()
        }
    }
    
    private fun getCacheKey(request: RouteRequest): String {
        return "${request.from.chainType}_${request.to.chainType}_${request.amount}_${request.preferences.priority}"
    }
    
    private fun isRouteValid(route: Route): Boolean {
        // 檢查路由是否仍然有效（例如，未過期）
        val cacheTimeout = 60000L // 1 分鐘
        return (Clock.System.now().toEpochMilliseconds() - (route.estimatedTime ?: 0)) < cacheTimeout
    }
    
    private fun cacheRoute(key: String, route: Route) {
        val currentCache = _routerState.value.routeCache.toMutableMap()
        currentCache[key] = route
        
        // 限制緩存大小
        if (currentCache.size > 100) {
            currentCache.remove(currentCache.keys.first())
        }
        
        _routerState.value = _routerState.value.copy(routeCache = currentCache)
    }
    
    private fun updateStatistics(route: Route) {
        val stats = _routerState.value.statistics
        _routerState.value = _routerState.value.copy(
            statistics = stats.copy(
                totalRoutesCalculated = stats.totalRoutesCalculated + 1,
                successfulRoutes = stats.successfulRoutes + 1
            )
        )
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        logger.i("Cleaning up Smart Router")
        _routerState.value = RouterState()
    }
}
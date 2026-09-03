package com.cbstudio.wearwallet.core.multichain.bridge

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.TransferRequest
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import co.touchlab.kermit.Logger

/**
 * 跨鏈橋接管理器
 * 統一管理多個跨鏈橋服務，提供智能路由和最佳化選擇
 */
class BridgeManager(
    private val bridges: List<CrossChainBridge>,
    private val logger: Logger = Logger.withTag("BridgeManager")
) {
    
    /**
     * 取得支援指定跨鏈轉移的所有橋接協定
     */
    fun getSupportedBridges(
        sourceChain: MultiChainType,
        targetChain: MultiChainType
    ): List<CrossChainBridge> {
        return bridges.filter { it.isSupported(sourceChain, targetChain) }
    }
    
    /**
     * 智能選擇最佳的跨鏈橋
     * 基於手續費、時間、安全性等因素
     */
    suspend fun selectOptimalBridge(
        request: TransferRequest,
        targetChain: MultiChainType,
        preference: BridgeSelectionPreference = BridgeSelectionPreference.BALANCED
    ): BridgeRecommendation {
        logger.i("Selecting optimal bridge for ${request.chainType.symbol} -> ${targetChain.symbol}")
        
        val supportedBridges = getSupportedBridges(request.chainType, targetChain)
        
        if (supportedBridges.isEmpty()) {
            throw BlockchainException.UnsupportedOperationException(
                request.chainType,
                "No bridge supports ${request.chainType.symbol} -> ${targetChain.symbol}"
            )
        }
        
        // 並行取得所有橋接的手續費估算
        val bridgeOptions = mutableListOf<BridgeOption>()
        
        supportedBridges.forEach { bridge ->
            try {
                val feeEstimate = bridge.estimateBridgeFee(request, targetChain)
                val score = calculateBridgeScore(bridge, feeEstimate, preference)
                
                bridgeOptions.add(
                    BridgeOption(
                        bridge = bridge,
                        feeEstimate = feeEstimate,
                        score = score
                    )
                )
                
                logger.d("Bridge ${bridge.protocolName}: fee=${feeEstimate.totalFee}, time=${feeEstimate.estimatedTime}, score=$score")
            } catch (e: Exception) {
                logger.w("Failed to get estimate from ${bridge.protocolName}", e)
            }
        }
        
        if (bridgeOptions.isEmpty()) {
            throw BlockchainException.GenericException(
                request.chainType,
                "No bridge could provide fee estimates"
            )
        }
        
        // 按分數排序，選擇最佳選項
        val sortedOptions = bridgeOptions.sortedByDescending { it.score }
        val recommended = sortedOptions.first()
        val alternatives = sortedOptions.drop(1)
        
        return BridgeRecommendation(
            recommended = recommended,
            alternatives = alternatives,
            selectionReason = generateSelectionReason(recommended, preference)
        )
    }
    
    /**
     * 執行跨鏈轉移（使用推薦的橋接）
     */
    suspend fun executeBridge(
        request: TransferRequest,
        targetChain: MultiChainType,
        targetAddress: String,
        privateKey: String,
        preference: BridgeSelectionPreference = BridgeSelectionPreference.BALANCED
    ): BridgeResult {
        val recommendation = selectOptimalBridge(request, targetChain, preference)
        
        logger.i("Executing bridge using ${recommendation.recommended.bridge.protocolName}")
        
        return recommendation.recommended.bridge.executeBridge(
            request = request,
            targetChain = targetChain,
            targetAddress = targetAddress,
            privateKey = privateKey
        )
    }
    
    /**
     * 批量查詢橋接狀態
     */
    suspend fun getBridgeStatuses(bridgeTransactionIds: List<String>): Map<String, BridgeStatus> {
        val results = mutableMapOf<String, BridgeStatus>()
        
        // 並行查詢所有狀態
        bridgeTransactionIds.forEach { transactionId ->
            // 嘗試從所有橋接查詢狀態（因為我們不知道是哪個橋接）
            bridges.forEach { bridge ->
                try {
                    val status = bridge.getBridgeStatus(transactionId)
                    results[transactionId] = status
                    return@forEach // 找到了就停止
                } catch (e: Exception) {
                    // 繼續嘗試下一個橋接
                }
            }
        }
        
        return results
    }
    
    /**
     * 取得所有支援的跨鏈對
     */
    fun getAllSupportedChainPairs(): List<ChainPair> {
        return bridges.flatMap { it.supportedChainPairs }.distinct()
    }
    
    /**
     * 取得特定鏈對的支援代幣列表
     */
    suspend fun getSupportedTokens(
        chainPair: ChainPair,
        bridgeProtocol: String? = null
    ): Map<String, List<BridgeToken>> {
        val results = mutableMapOf<String, List<BridgeToken>>()
        
        val targetBridges = if (bridgeProtocol != null) {
            bridges.filter { it.protocolName == bridgeProtocol }
        } else {
            bridges.filter { it.isSupported(chainPair.sourceChain, chainPair.targetChain) }
        }
        
        targetBridges.forEach { bridge ->
            try {
                val tokens = bridge.getSupportedTokens(chainPair)
                results[bridge.protocolName] = tokens
            } catch (e: Exception) {
                logger.w("Failed to get supported tokens from ${bridge.protocolName}", e)
            }
        }
        
        return results
    }
    
    /**
     * 監控橋接交易進度
     */
    suspend fun monitorBridge(
        bridgeTransactionId: String,
        onUpdate: (BridgeStatus) -> Unit
    ) {
        logger.i("Starting bridge monitoring for $bridgeTransactionId")
        
        var attempts = 0
        val maxAttempts = 60 // 30 分鐘 (每30秒檢查一次)
        
        while (attempts < maxAttempts) {
            try {
                // 嘗試從所有橋接取得狀態
                bridges.forEach { bridge ->
                    try {
                        val status = bridge.getBridgeStatus(bridgeTransactionId)
                        onUpdate(status)
                        
                        // 如果已完成或失敗，停止監控
                        if (status.status == BridgeTransactionStatus.COMPLETED ||
                            status.status == BridgeTransactionStatus.FAILED ||
                            status.status == BridgeTransactionStatus.REFUNDED) {
                            logger.i("Bridge monitoring completed with status: ${status.status}")
                            return
                        }
                        
                        return@forEach // 成功取得狀態，停止嘗試其他橋接
                    } catch (e: Exception) {
                        // 繼續嘗試下一個橋接
                    }
                }
                
                // 等待30秒後重試
                kotlinx.coroutines.delay(30_000)
                attempts++
                
            } catch (e: Exception) {
                logger.e("Error during bridge monitoring", e)
                kotlinx.coroutines.delay(30_000)
                attempts++
            }
        }
        
        logger.w("Bridge monitoring timeout for $bridgeTransactionId")
    }
    
    // 私有輔助方法
    
    private fun calculateBridgeScore(
        bridge: CrossChainBridge,
        feeEstimate: BridgeFeeEstimate,
        preference: BridgeSelectionPreference
    ): Double {
        var score = 0.0
        
        // 解析手續費和時間
        val totalFee = feeEstimate.totalFee.toDoubleOrNull() ?: Double.MAX_VALUE
        val estimatedMinutes = parseEstimatedTime(feeEstimate.estimatedTime)
        
        when (preference) {
            BridgeSelectionPreference.LOWEST_FEE -> {
                score = 1.0 / (totalFee + 0.001) // 手續費越低分數越高
            }
            BridgeSelectionPreference.FASTEST -> {
                score = 1.0 / (estimatedMinutes + 1) // 時間越短分數越高
            }
            BridgeSelectionPreference.MOST_SECURE -> {
                score = getBridgeSecurityScore(bridge)
            }
            BridgeSelectionPreference.BALANCED -> {
                val feeScore = 1.0 / (totalFee + 0.001)
                val timeScore = 1.0 / (estimatedMinutes + 1)
                val securityScore = getBridgeSecurityScore(bridge)
                score = (feeScore * 0.4 + timeScore * 0.3 + securityScore * 0.3)
            }
        }
        
        return score
    }
    
    private fun parseEstimatedTime(timeString: String): Double {
        // 解析時間字串，轉換為分鐘
        return when {
            timeString.contains("second", ignoreCase = true) -> {
                timeString.filter { it.isDigit() }.toDoubleOrNull()?.div(60) ?: 1.0
            }
            timeString.contains("minute", ignoreCase = true) -> {
                timeString.filter { it.isDigit() }.toDoubleOrNull() ?: 10.0
            }
            timeString.contains("hour", ignoreCase = true) -> {
                timeString.filter { it.isDigit() }.toDoubleOrNull()?.times(60) ?: 60.0
            }
            else -> {
                // 嘗試解析 "15-20 minutes" 格式
                val numbers = Regex("\\d+").findAll(timeString).map { it.value.toDouble() }.toList()
                if (numbers.isNotEmpty()) numbers.average() else 15.0
            }
        }
    }
    
    private fun getBridgeSecurityScore(bridge: CrossChainBridge): Double {
        // 基於橋接協定的安全性給分
        return when (bridge.protocolName) {
            "Wormhole" -> 0.9 // 高安全性，經過大量審計
            "LayerZero" -> 0.85 // 新興但技術先進
            "Multichain" -> 0.8 // 成熟但中心化
            else -> 0.7 // 預設分數
        }
    }
    
    private fun generateSelectionReason(
        recommended: BridgeOption,
        preference: BridgeSelectionPreference
    ): String {
        val bridge = recommended.bridge
        val fee = recommended.feeEstimate
        
        return when (preference) {
            BridgeSelectionPreference.LOWEST_FEE -> 
                "${bridge.protocolName} selected for lowest total fee: ${fee.totalFee}"
            BridgeSelectionPreference.FASTEST -> 
                "${bridge.protocolName} selected for fastest completion: ${fee.estimatedTime}"
            BridgeSelectionPreference.MOST_SECURE -> 
                "${bridge.protocolName} selected for highest security rating"
            BridgeSelectionPreference.BALANCED -> 
                "${bridge.protocolName} selected for best balance of fee (${fee.totalFee}), time (${fee.estimatedTime}), and security"
        }
    }
}

/**
 * 橋接選擇偏好
 */
enum class BridgeSelectionPreference {
    LOWEST_FEE,    // 最低手續費
    FASTEST,       // 最快速度
    MOST_SECURE,   // 最高安全性
    BALANCED       // 平衡考量
}

/**
 * 橋接選項
 */
data class BridgeOption(
    val bridge: CrossChainBridge,
    val feeEstimate: BridgeFeeEstimate,
    val score: Double
)

/**
 * 橋接推薦結果
 */
data class BridgeRecommendation(
    val recommended: BridgeOption,
    val alternatives: List<BridgeOption>,
    val selectionReason: String
) {
    /**
     * 取得所有選項（推薦 + 替代）
     */
    val allOptions: List<BridgeOption>
        get() = listOf(recommended) + alternatives
}

/**
 * 預設的橋接管理器工廠
 */
object BridgeManagerFactory {
    
    /**
     * 創建包含所有支援橋接的管理器
     */
    fun createDefaultBridgeManager(): BridgeManager {
        val bridges = listOf(
            WormholeBridge(),
            LayerZeroBridge()
            // 未來可添加更多橋接協定
        )
        
        return BridgeManager(bridges)
    }
    
    /**
     * 創建自訂橋接組合的管理器
     */
    fun createCustomBridgeManager(bridges: List<CrossChainBridge>): BridgeManager {
        return BridgeManager(bridges)
    }
    
    /**
     * 創建僅包含特定協定的管理器
     */
    fun createSingleProtocolManager(protocolName: String): BridgeManager {
        val bridge = when (protocolName.lowercase()) {
            "wormhole" -> WormholeBridge()
            "layerzero" -> LayerZeroBridge()
            else -> throw IllegalArgumentException("Unsupported bridge protocol: $protocolName")
        }
        
        return BridgeManager(listOf(bridge))
    }
}
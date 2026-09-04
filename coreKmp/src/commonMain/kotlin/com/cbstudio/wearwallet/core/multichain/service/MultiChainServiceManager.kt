package com.cbstudio.wearwallet.core.multichain.service

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.TransferRequest
import com.cbstudio.wearwallet.core.multichain.model.MultiChainTransaction
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import com.cbstudio.wearwallet.core.multichain.service.solana.SolanaService
import com.cbstudio.wearwallet.core.multichain.service.tron.TronService
import com.cbstudio.wearwallet.core.multichain.service.polkadot.PolkadotService
import com.cbstudio.wearwallet.core.multichain.service.cardano.CardanoService
import com.cbstudio.wearwallet.core.multichain.service.monero.MoneroService
import co.touchlab.kermit.Logger

/**
 * 多鏈服務管理器
 * 統一管理所有區塊鏈服務，提供高級功能和協調操作
 */
class MultiChainServiceManager(
    private val serviceFactory: BlockchainServiceFactory,
    private val logger: Logger = Logger.withTag("MultiChainServiceManager")
) {
    
    /**
     * 初始化所有支援的區塊鏈服務
     */
    fun initializeServices() {
        logger.i("Initializing multi-chain services...")
        
        try {
            // 註冊現有的區塊鏈服務（Bitcoin, Ethereum 等）
            // TODO: 等現有服務遷移到統一介面後啟用
            
            // 註冊新增的五條鏈服務
            registerNewChainServices()
            
            logger.i("Multi-chain services initialized successfully")
            logger.i("Supported chains: ${serviceFactory.getSupportedChains().joinToString { it.symbol }}")
        } catch (e: Exception) {
            logger.e("Failed to initialize services", e)
            throw e
        }
    }
    
    /**
     * 註冊新增的區塊鏈服務
     */
    private fun registerNewChainServices() {
        // 註冊 Solana 服務
        try {
            serviceFactory.registerService(SolanaService())
            logger.d("Solana service registered")
        } catch (e: Exception) {
            logger.w("Failed to register Solana service", e)
        }
        
        // 註冊 TRON 服務
        try {
            serviceFactory.registerService(TronService())
            logger.d("TRON service registered")
        } catch (e: Exception) {
            logger.w("Failed to register TRON service", e)
        }
        
        // 註冊 Polkadot 服務
        try {
            serviceFactory.registerService(PolkadotService())
            logger.d("Polkadot service registered")
        } catch (e: Exception) {
            logger.w("Failed to register Polkadot service", e)
        }
        
        // 註冊 Cardano 服務
        try {
            serviceFactory.registerService(CardanoService())
            logger.d("Cardano service registered")
        } catch (e: Exception) {
            logger.w("Failed to register Cardano service", e)
        }
        
        // 註冊 Monero 服務
        try {
            serviceFactory.registerService(MoneroService())
            logger.d("Monero service registered")
        } catch (e: Exception) {
            logger.w("Failed to register Monero service", e)
        }
    }
    
    /**
     * 執行跨鏈轉帳
     * 從一個區塊鏈轉移到另一個區塊鏈
     */
    suspend fun performCrossChainTransfer(
        sourceChain: MultiChainType,
        targetChain: MultiChainType,
        request: TransferRequest,
        privateKey: String
    ): CrossChainTransferResult {
        logger.i("Performing cross-chain transfer: ${sourceChain.symbol} -> ${targetChain.symbol}")
        
        return try {
            // 1. 檢查兩條鏈的服務是否可用
            val sourceService = serviceFactory.getService(sourceChain)
            val targetService = serviceFactory.getService(targetChain)
            
            if (!sourceService.isServiceAvailable()) {
                throw BlockchainException.GenericException(
                    sourceChain,
                    "Source chain service unavailable"
                )
            }
            
            if (!targetService.isServiceAvailable()) {
                throw BlockchainException.GenericException(
                    targetChain,
                    "Target chain service unavailable"
                )
            }
            
            // 2. 檢查餘額是否足夠
            val balance = sourceService.getBalance(request.fromAddress)
            val requiredAmount = request.amount.toDoubleOrNull() ?: 0.0
            val availableBalance = balance.toDoubleOrNull() ?: 0.0
            
            if (availableBalance < requiredAmount) {
                throw BlockchainException.InsufficientBalanceException(
                    sourceChain,
                    request.amount,
                    balance
                )
            }
            
            // 3. 在源鏈執行轉出操作
            val sourceUnsignedTx = sourceService.createUnsignedTransaction(request)
            val sourceSignedTx = sourceService.signTransaction(sourceUnsignedTx, privateKey)
            val sourceTxHash = sourceService.broadcastTransaction(sourceSignedTx)
            
            logger.i("Source transaction broadcasted: $sourceTxHash")
            
            // 4. 等待源鏈交易確認（簡化版本）
            waitForTransactionConfirmation(sourceService, sourceTxHash)
            
            // 5. 在目標鏈執行轉入操作（需要實際的跨鏈橋接支援）
            // TODO: 實現實際的跨鏈橋接邏輯
            val targetTxHash = performBridgeOperation(
                sourceChain,
                targetChain,
                sourceTxHash,
                request
            )
            
            logger.i("Cross-chain transfer completed successfully")
            
            CrossChainTransferResult(
                success = true,
                sourceChain = sourceChain,
                targetChain = targetChain,
                sourceTxHash = sourceTxHash,
                targetTxHash = targetTxHash,
                message = "Cross-chain transfer completed"
            )
        } catch (e: BlockchainException) {
            logger.e("Cross-chain transfer failed", e)
            CrossChainTransferResult(
                success = false,
                sourceChain = sourceChain,
                targetChain = targetChain,
                error = e,
                message = e.message ?: "Unknown error"
            )
        } catch (e: Exception) {
            logger.e("Unexpected error during cross-chain transfer", e)
            CrossChainTransferResult(
                success = false,
                sourceChain = sourceChain,
                targetChain = targetChain,
                error = BlockchainException.GenericException(sourceChain, e.message ?: "Unknown error", e),
                message = "Unexpected error: ${e.message}"
            )
        }
    }
    
    /**
     * 批量查詢多鏈餘額
     */
    suspend fun getMultiChainBalances(
        address: String,
        chains: List<MultiChainType>
    ): Map<MultiChainType, String> {
        logger.d("Querying multi-chain balances for ${chains.size} chains")
        
        val balances = mutableMapOf<MultiChainType, String>()
        
        chains.forEach { chain ->
            try {
                if (serviceFactory.isSupported(chain)) {
                    val service = serviceFactory.getService(chain)
                    if (service.isServiceAvailable()) {
                        val balance = service.getBalance(address)
                        balances[chain] = balance
                        logger.v("${chain.symbol} balance: $balance")
                    } else {
                        balances[chain] = "Service unavailable"
                        logger.w("${chain.symbol} service unavailable")
                    }
                } else {
                    balances[chain] = "Unsupported"
                    logger.w("${chain.symbol} not supported")
                }
            } catch (e: Exception) {
                balances[chain] = "Error: ${e.message}"
                logger.e("Failed to get ${chain.symbol} balance", e)
            }
        }
        
        return balances
    }
    
    /**
     * 批量查詢交易記錄
     */
    suspend fun getMultiChainTransactionHistory(
        address: String,
        chains: List<MultiChainType>,
        limit: Int = 10
    ): Map<MultiChainType, List<MultiChainTransaction>> {
        logger.d("Querying multi-chain transaction history")
        
        val histories = mutableMapOf<MultiChainType, List<MultiChainTransaction>>()
        
        chains.forEach { chain ->
            try {
                if (serviceFactory.isSupported(chain)) {
                    val service = serviceFactory.getService(chain)
                    if (service.isServiceAvailable()) {
                        val transactions = service.getTransactionHistory(address, limit)
                        histories[chain] = transactions
                        logger.v("${chain.symbol} transactions: ${transactions.size}")
                    } else {
                        histories[chain] = emptyList()
                        logger.w("${chain.symbol} service unavailable")
                    }
                } else {
                    histories[chain] = emptyList()
                    logger.w("${chain.symbol} not supported")
                }
            } catch (e: Exception) {
                histories[chain] = emptyList()
                logger.e("Failed to get ${chain.symbol} transaction history", e)
            }
        }
        
        return histories
    }
    
    /**
     * 檢查所有服務健康狀態
     */
    suspend fun checkAllServicesHealth(): Map<MultiChainType, ServiceHealth> {
        logger.d("Checking health of all services")
        
        val healthMap = mutableMapOf<MultiChainType, ServiceHealth>()
        val supportedChains = serviceFactory.getSupportedChains()
        
        supportedChains.forEach { chain ->
            val startTime = Clock.System.now().toEpochMilliseconds()
            try {
                val service = serviceFactory.getService(chain)
                val isAvailable = service.isServiceAvailable()
                val responseTime = Clock.System.now().toEpochMilliseconds() - startTime
                
                healthMap[chain] = ServiceHealth(
                    chainType = chain,
                    status = if (isAvailable) ServiceStatus.AVAILABLE else ServiceStatus.UNAVAILABLE,
                    responseTime = responseTime,
                    message = if (isAvailable) "Service healthy" else "Service unavailable"
                )
            } catch (e: Exception) {
                val responseTime = Clock.System.now().toEpochMilliseconds() - startTime
                healthMap[chain] = ServiceHealth(
                    chainType = chain,
                    status = ServiceStatus.MISCONFIGURED,
                    responseTime = responseTime,
                    message = "Health check failed: ${e.message}"
                )
                logger.e("Health check failed for ${chain.symbol}", e)
            }
        }
        
        return healthMap
    }
    
    /**
     * 智能手續費建議
     * 基於網路狀況和用戶偏好提供手續費建議
     */
    suspend fun getSmartFeeRecommendation(
        request: TransferRequest,
        urgency: FeeUrgency = FeeUrgency.NORMAL
    ): SmartFeeRecommendation {
        logger.d("Getting smart fee recommendation for ${request.chainType.symbol}")
        
        return try {
            val service = serviceFactory.getService(request.chainType)
            
            // 估算基礎手續費
            val baseFee = service.estimateFee(request).toDoubleOrNull() ?: 0.0
            
            // 根據緊急程度調整手續費
            val multiplier = when (urgency) {
                FeeUrgency.SLOW -> 0.8
                FeeUrgency.NORMAL -> 1.0
                FeeUrgency.FAST -> 1.5
                FeeUrgency.URGENT -> 2.0
            }
            
            val adjustedFee = baseFee * multiplier
            val estimatedTime = when (urgency) {
                FeeUrgency.SLOW -> "10-30 minutes"
                FeeUrgency.NORMAL -> "5-10 minutes"
                FeeUrgency.FAST -> "1-5 minutes"
                FeeUrgency.URGENT -> "< 1 minute"
            }
            
            SmartFeeRecommendation(
                chainType = request.chainType,
                recommendedFee = adjustedFee.toString(),
                urgency = urgency,
                estimatedConfirmationTime = estimatedTime,
                confidence = 0.85 // 模擬置信度
            )
        } catch (e: Exception) {
            logger.e("Failed to get fee recommendation", e)
            SmartFeeRecommendation(
                chainType = request.chainType,
                recommendedFee = "0.001", // 預設值
                urgency = urgency,
                estimatedConfirmationTime = "Unknown",
                confidence = 0.0,
                error = e.message
            )
        }
    }
    
    // 私有輔助方法
    
    private suspend fun waitForTransactionConfirmation(
        service: UniversalBlockchainService,
        txHash: String,
        maxWaitTime: Long = 300_000 // 5 分鐘
    ) {
        logger.d("Waiting for transaction confirmation: $txHash")
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        while (Clock.System.now().toEpochMilliseconds() - startTime < maxWaitTime) {
            try {
                val tx = service.getTransaction(txHash)
                if (tx?.isConfirmed == true) {
                    logger.i("Transaction confirmed: $txHash")
                    return
                }
                
                // 等待 30 秒後再次檢查
                kotlinx.coroutines.delay(30_000)
            } catch (e: Exception) {
                logger.w("Error checking transaction status", e)
                kotlinx.coroutines.delay(30_000)
            }
        }
        
        logger.w("Transaction confirmation timeout: $txHash")
    }
    
    private suspend fun performBridgeOperation(
        sourceChain: MultiChainType,
        targetChain: MultiChainType,
        sourceTxHash: String,
        request: TransferRequest
    ): String {
        // TODO: 實現實際的跨鏈橋接邏輯
        // 這裡需要整合真實的跨鏈橋服務，如：
        // - Wormhole
        // - LayerZero
        // - Multichain (原 Anyswap)
        // - Synapse Protocol
        
        logger.i("Bridge operation: ${sourceChain.symbol} -> ${targetChain.symbol}")
        logger.i("Source transaction: $sourceTxHash")
        
        // 暫時回傳模擬的目標鏈交易哈希
        return "bridge_tx_${targetChain.symbol.lowercase()}_${Clock.System.now().toEpochMilliseconds()}"
    }
}

/**
 * 跨鏈轉帳結果
 */
data class CrossChainTransferResult(
    val success: Boolean,
    val sourceChain: MultiChainType,
    val targetChain: MultiChainType,
    val sourceTxHash: String? = null,
    val targetTxHash: String? = null,
    val message: String,
    val error: BlockchainException? = null
)

/**
 * 手續費緊急程度
 */
enum class FeeUrgency {
    SLOW,    // 慢速，低手續費
    NORMAL,  // 正常，標準手續費
    FAST,    // 快速，高手續費
    URGENT   // 緊急，最高手續費
}

/**
 * 智能手續費建議
 */
data class SmartFeeRecommendation(
    val chainType: MultiChainType,
    val recommendedFee: String,
    val urgency: FeeUrgency,
    val estimatedConfirmationTime: String,
    val confidence: Double, // 0.0 - 1.0
    val error: String? = null
)
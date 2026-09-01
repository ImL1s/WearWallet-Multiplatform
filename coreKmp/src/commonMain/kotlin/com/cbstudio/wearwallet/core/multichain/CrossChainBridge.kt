package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.multichain.sdk.*
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

/**
 * 跨鏈橋接服務
 * 
 * 提供不同區塊鏈之間的資產轉移功能
 */
class CrossChainBridge(
    private val walletManager: MultiChainWalletManager
) {
    
    private val logger = Logger.withTag("CrossChainBridge")
    
    // 橋接狀態
    private val _bridgeState = MutableStateFlow(BridgeState())
    val bridgeState: StateFlow<BridgeState> = _bridgeState.asStateFlow()
    
    /**
     * 橋接狀態
     */
    data class BridgeState(
        val isActive: Boolean = false,
        val pendingTransfers: List<CrossChainTransfer> = emptyList(),
        val completedTransfers: List<CrossChainTransfer> = emptyList(),
        val supportedRoutes: List<BridgeRoute> = emptyList()
    )
    
    /**
     * 跨鏈轉移
     */
    data class CrossChainTransfer(
        val id: String,
        val sourceChain: MultiChainType,
        val targetChain: MultiChainType,
        val sourceAddress: String,
        val targetAddress: String,
        val asset: String,
        val amount: String,
        val status: TransferStatus,
        val sourceTransactionHash: String? = null,
        val targetTransactionHash: String? = null,
        val estimatedTime: Long = 0,
        val fee: BridgeFee? = null,
        val createdAt: Long = Clock.System.now().toEpochMilliseconds()
    )
    
    /**
     * 轉移狀態
     */
    enum class TransferStatus {
        PENDING,
        SOURCE_CONFIRMED,
        BRIDGING,
        TARGET_PENDING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    /**
     * 橋接路由
     */
    data class BridgeRoute(
        val sourceChain: MultiChainType,
        val targetChain: MultiChainType,
        val supportedAssets: List<String>,
        val minAmount: String,
        val maxAmount: String,
        val estimatedTime: Long, // 毫秒
        val isActive: Boolean = true
    )
    
    /**
     * 橋接費用
     */
    data class BridgeFee(
        val sourceFee: TransactionFee,
        val targetFee: TransactionFee,
        val bridgeFee: String,
        val bridgeFeeSymbol: String,
        val totalFeeUsd: String? = null
    )
    
    /**
     * 初始化橋接服務
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            logger.i("Initializing CrossChainBridge")
            
            // 初始化支援的橋接路由
            val routes = initializeSupportedRoutes()
            
            _bridgeState.value = _bridgeState.value.copy(
                isActive = true,
                supportedRoutes = routes
            )
            
            logger.i("CrossChainBridge initialized with ${routes.size} routes")
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to initialize CrossChainBridge", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 檢查橋接路由是否支援
     */
    fun isBridgeSupported(
        sourceChain: MultiChainType,
        targetChain: MultiChainType,
        asset: String? = null
    ): Boolean {
        return _bridgeState.value.supportedRoutes.any { route ->
            route.sourceChain == sourceChain &&
            route.targetChain == targetChain &&
            route.isActive &&
            (asset == null || asset in route.supportedAssets)
        }
    }
    
    /**
     * 獲取橋接路由
     */
    fun getBridgeRoute(
        sourceChain: MultiChainType,
        targetChain: MultiChainType
    ): BridgeRoute? {
        return _bridgeState.value.supportedRoutes.find { route ->
            route.sourceChain == sourceChain &&
            route.targetChain == targetChain &&
            route.isActive
        }
    }
    
    /**
     * 估算橋接費用
     */
    suspend fun estimateBridgeFee(
        sourceChain: MultiChainType,
        targetChain: MultiChainType,
        amount: String,
        asset: String = "native"
    ): Result<BridgeFee> {
        return try {
            logger.i("Estimating bridge fee: $sourceChain -> $targetChain, $amount $asset")
            
            // 檢查路由是否支援
            if (!isBridgeSupported(sourceChain, targetChain, asset)) {
                return Result.Failure(Exception("Bridge route not supported"))
            }
            
            // 估算源鏈費用
            val sourceFee = estimateChainFee(sourceChain, amount)
            
            // 估算目標鏈費用
            val targetFee = estimateChainFee(targetChain, amount)
            
            // 計算橋接費用（通常是金額的 0.1% - 0.5%）
            val bridgeFeeAmount = (amount.toDoubleOrNull() ?: 0.0) * 0.003 // 0.3%
            
            val bridgeFee = BridgeFee(
                sourceFee = sourceFee,
                targetFee = targetFee,
                bridgeFee = bridgeFeeAmount.toString(),
                bridgeFeeSymbol = asset,
                totalFeeUsd = calculateTotalFeeUsd(sourceFee, targetFee, bridgeFeeAmount)
            )
            
            logger.i("Bridge fee estimated: ${bridgeFee.totalFeeUsd} USD")
            Result.Success(bridgeFee)
        } catch (e: Exception) {
            logger.e("Failed to estimate bridge fee", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 創建跨鏈轉移
     */
    suspend fun createCrossChainTransfer(
        sourceChain: MultiChainType,
        targetChain: MultiChainType,
        sourceAddress: String,
        targetAddress: String,
        amount: String,
        asset: String = "native"
    ): Result<CrossChainTransfer> {
        return try {
            logger.i("Creating cross-chain transfer: $sourceChain -> $targetChain")
            
            // 驗證路由
            val route = getBridgeRoute(sourceChain, targetChain)
                ?: return Result.Failure(Exception("Bridge route not found"))
            
            // 驗證金額
            if (!isAmountValid(amount, route)) {
                return Result.Failure(Exception("Amount out of range"))
            }
            
            // 驗證地址
            val sourceValidation = walletManager.validateAddress(sourceChain, sourceAddress)
            val targetValidation = walletManager.validateAddress(targetChain, targetAddress)
            
            if (sourceValidation is Result.Failure || targetValidation is Result.Failure) {
                return Result.Failure(Exception("Invalid address"))
            }
            
            // 估算費用
            val feeResult = estimateBridgeFee(sourceChain, targetChain, amount, asset)
            val fee = (feeResult as? Result.Success)?.data
            
            // 創建轉移記錄
            val transfer = CrossChainTransfer(
                id = generateTransferId(),
                sourceChain = sourceChain,
                targetChain = targetChain,
                sourceAddress = sourceAddress,
                targetAddress = targetAddress,
                asset = asset,
                amount = amount,
                status = TransferStatus.PENDING,
                estimatedTime = route.estimatedTime,
                fee = fee
            )
            
            // 添加到待處理列表
            _bridgeState.value = _bridgeState.value.copy(
                pendingTransfers = _bridgeState.value.pendingTransfers + transfer
            )
            
            logger.i("Cross-chain transfer created: ${transfer.id}")
            Result.Success(transfer)
        } catch (e: Exception) {
            logger.e("Failed to create cross-chain transfer", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 執行跨鏈轉移
     */
    suspend fun executeCrossChainTransfer(transferId: String): Result<CrossChainTransfer> {
        return try {
            logger.i("Executing cross-chain transfer: $transferId")
            
            // 獲取轉移記錄
            var transfer = _bridgeState.value.pendingTransfers.find { it.id == transferId }
                ?: return Result.Failure(Exception("Transfer not found"))
            
            // 步驟 1: 在源鏈上鎖定資產
            logger.i("Step 1: Locking assets on source chain")
            transfer = transfer.copy(status = TransferStatus.SOURCE_CONFIRMED)
            updateTransferStatus(transfer)
            
            val sourceRequest = TransactionRequest(
                fromAddress = transfer.sourceAddress,
                toAddress = getBridgeContractAddress(transfer.sourceChain),
                amount = transfer.amount,
                priority = TransactionPriority.NORMAL,
                customGasPrice = null,
                customGasLimit = null,
                tokenAddress = null,
                memo = "Bridge: ${transfer.sourceChain} -> ${transfer.targetChain}"
            )
            
            val sourceTxResult = walletManager.createTransaction(transfer.sourceChain, sourceRequest)
            if (sourceTxResult is Result.Failure) {
                transfer = transfer.copy(status = TransferStatus.FAILED)
                updateTransferStatus(transfer)
                return Result.Failure(sourceTxResult.error)
            }
            
            val sourceTx = (sourceTxResult as Result.Success).data
            // sourceTx is UnsignedTransaction, we need to get hash after signing
            transfer = transfer.copy(sourceTransactionHash = "pending")
            
            // 步驟 2: 等待橋接確認
            logger.i("Step 2: Waiting for bridge confirmation")
            transfer = transfer.copy(status = TransferStatus.BRIDGING)
            updateTransferStatus(transfer)
            
            // 模擬橋接延遲
            delay(5000) // 實際應用中需要監聽橋接事件
            
            // 步驟 3: 在目標鏈上釋放資產
            logger.i("Step 3: Releasing assets on target chain")
            transfer = transfer.copy(status = TransferStatus.TARGET_PENDING)
            updateTransferStatus(transfer)
            
            val targetRequest = TransactionRequest(
                fromAddress = getBridgeContractAddress(transfer.targetChain),
                toAddress = transfer.targetAddress,
                amount = calculateTargetAmount(transfer.amount, transfer.fee),
                priority = TransactionPriority.NORMAL,
                customGasPrice = null,
                customGasLimit = null,
                tokenAddress = null,
                memo = "Bridge completion from ${transfer.sourceChain}"
            )
            
            val targetTxResult = walletManager.createTransaction(transfer.targetChain, targetRequest)
            if (targetTxResult is Result.Failure) {
                transfer = transfer.copy(status = TransferStatus.FAILED)
                updateTransferStatus(transfer)
                return Result.Failure(targetTxResult.error)
            }
            
            val targetTx = (targetTxResult as Result.Success).data
            transfer = transfer.copy(
                // targetTx is UnsignedTransaction, will get hash after signing
                targetTransactionHash = "pending",
                status = TransferStatus.COMPLETED
            )
            
            // 步驟 4: 完成轉移
            logger.i("Step 4: Transfer completed")
            completeTransfer(transfer)
            
            Result.Success(transfer)
        } catch (e: Exception) {
            logger.e("Failed to execute cross-chain transfer", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取轉移狀態
     */
    fun getTransferStatus(transferId: String): TransferStatus? {
        val transfer = _bridgeState.value.pendingTransfers.find { it.id == transferId }
            ?: _bridgeState.value.completedTransfers.find { it.id == transferId }
        return transfer?.status
    }
    
    /**
     * 獲取所有待處理的轉移
     */
    fun getPendingTransfers(): List<CrossChainTransfer> {
        return _bridgeState.value.pendingTransfers
    }
    
    /**
     * 獲取已完成的轉移
     */
    fun getCompletedTransfers(limit: Int = 20): List<CrossChainTransfer> {
        return _bridgeState.value.completedTransfers.take(limit)
    }
    
    /**
     * 取消跨鏈轉移
     */
    suspend fun cancelTransfer(transferId: String): Result<Unit> {
        return try {
            logger.i("Cancelling transfer: $transferId")
            
            val transfer = _bridgeState.value.pendingTransfers.find { it.id == transferId }
                ?: return Result.Failure(Exception("Transfer not found"))
            
            // 只能取消待處理的轉移
            if (transfer.status != TransferStatus.PENDING) {
                return Result.Failure(Exception("Cannot cancel transfer in status: ${transfer.status}"))
            }
            
            val cancelledTransfer = transfer.copy(status = TransferStatus.CANCELLED)
            updateTransferStatus(cancelledTransfer)
            completeTransfer(cancelledTransfer)
            
            logger.i("Transfer cancelled: $transferId")
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to cancel transfer", e)
            Result.Failure(e)
        }
    }
    
    // === 私有輔助方法 ===
    
    /**
     * 初始化支援的橋接路由
     */
    private fun initializeSupportedRoutes(): List<BridgeRoute> {
        return listOf(
            // Solana <-> Polygon
            BridgeRoute(
                sourceChain = MultiChainType.SOLANA,
                targetChain = MultiChainType.POLYGON,
                supportedAssets = listOf("SOL", "USDC", "USDT"),
                minAmount = "0.1",
                maxAmount = "10000",
                estimatedTime = 300000 // 5 分鐘
            ),
            
            // Ethereum <-> BSC
            BridgeRoute(
                sourceChain = MultiChainType.ETHEREUM,
                targetChain = MultiChainType.BSC,
                supportedAssets = listOf("ETH", "USDC", "USDT", "WBTC"),
                minAmount = "0.01",
                maxAmount = "100000",
                estimatedTime = 600000 // 10 分鐘
            ),
            
            // Polkadot <-> Ethereum
            BridgeRoute(
                sourceChain = MultiChainType.POLKADOT,
                targetChain = MultiChainType.ETHEREUM,
                supportedAssets = listOf("DOT"),
                minAmount = "1",
                maxAmount = "50000",
                estimatedTime = 900000 // 15 分鐘
            ),
            
            // Cardano <-> Ethereum
            BridgeRoute(
                sourceChain = MultiChainType.CARDANO,
                targetChain = MultiChainType.ETHEREUM,
                supportedAssets = listOf("ADA"),
                minAmount = "10",
                maxAmount = "100000",
                estimatedTime = 1200000 // 20 分鐘
            ),
            
            // TRON <-> BSC
            BridgeRoute(
                sourceChain = MultiChainType.TRON,
                targetChain = MultiChainType.BSC,
                supportedAssets = listOf("TRX", "USDT"),
                minAmount = "10",
                maxAmount = "50000",
                estimatedTime = 450000 // 7.5 分鐘
            )
        )
    }
    
    /**
     * 估算鏈上費用
     */
    private fun estimateChainFee(chain: MultiChainType, amount: String): TransactionFee {
        // 簡化的費用估算
        val baseFee = when (chain) {
            MultiChainType.ETHEREUM -> "0.01"
            MultiChainType.BSC -> "0.001"
            MultiChainType.POLYGON -> "0.0001"
            MultiChainType.SOLANA -> "0.00025"
            MultiChainType.POLKADOT -> "0.1"
            MultiChainType.CARDANO -> "0.17"
            MultiChainType.TRON -> "1"
            MultiChainType.MONERO -> "0.0001"
            else -> "0.001"
        }
        
        return TransactionFee(
            gasLimit = "21000",
            gasPrice = baseFee,
            estimatedCost = baseFee,
            usdValue = null,
            priority = TransactionPriority.NORMAL
        )
    }
    
    /**
     * 獲取鏈的原生代幣
     */
    private fun getChainNativeToken(chain: MultiChainType): String {
        return when (chain) {
            MultiChainType.ETHEREUM -> "ETH"
            MultiChainType.BSC -> "BNB"
            MultiChainType.POLYGON -> "MATIC"
            MultiChainType.SOLANA -> "SOL"
            MultiChainType.POLKADOT -> "DOT"
            MultiChainType.CARDANO -> "ADA"
            MultiChainType.TRON -> "TRX"
            MultiChainType.MONERO -> "XMR"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * 計算總費用（USD）
     */
    private fun calculateTotalFeeUsd(
        sourceFee: TransactionFee,
        targetFee: TransactionFee,
        bridgeFee: Double
    ): String {
        // 簡化計算，實際應該從價格 API 獲取
        val sourceFeeUsd = (sourceFee.estimatedCost.toDoubleOrNull() ?: 0.0) * 100 // 假設價格
        val targetFeeUsd = (targetFee.estimatedCost.toDoubleOrNull() ?: 0.0) * 100
        val bridgeFeeUsd = bridgeFee * 100
        
        return (sourceFeeUsd + targetFeeUsd + bridgeFeeUsd).toString()
    }
    
    /**
     * 驗證金額是否有效
     */
    private fun isAmountValid(amount: String, route: BridgeRoute): Boolean {
        val amountValue = amount.toDoubleOrNull() ?: return false
        val minValue = route.minAmount.toDoubleOrNull() ?: 0.0
        val maxValue = route.maxAmount.toDoubleOrNull() ?: Double.MAX_VALUE
        
        return amountValue in minValue..maxValue
    }
    
    /**
     * 生成轉移 ID
     */
    private fun generateTransferId(): String {
        return "bridge_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}"
    }
    
    /**
     * 獲取橋接合約地址
     */
    private fun getBridgeContractAddress(chain: MultiChainType): String {
        // 實際應該從配置或智能合約獲取
        return when (chain) {
            MultiChainType.ETHEREUM -> "0x1234567890123456789012345678901234567890"
            MultiChainType.BSC -> "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
            MultiChainType.POLYGON -> "0x9876543210987654321098765432109876543210"
            MultiChainType.SOLANA -> "Bridge11111111111111111111111111111111111"
            else -> "0x0000000000000000000000000000000000000000"
        }
    }
    
    /**
     * 計算目標金額（扣除費用）
     */
    private fun calculateTargetAmount(amount: String, fee: BridgeFee?): String {
        val amountValue = amount.toDoubleOrNull() ?: 0.0
        val bridgeFeeValue = fee?.bridgeFee?.toDoubleOrNull() ?: 0.0
        return (amountValue - bridgeFeeValue).toString()
    }
    
    /**
     * 更新轉移狀態
     */
    private fun updateTransferStatus(transfer: CrossChainTransfer) {
        val pendingList = _bridgeState.value.pendingTransfers.toMutableList()
        val index = pendingList.indexOfFirst { it.id == transfer.id }
        if (index >= 0) {
            pendingList[index] = transfer
            _bridgeState.value = _bridgeState.value.copy(pendingTransfers = pendingList)
        }
    }
    
    /**
     * 完成轉移
     */
    private fun completeTransfer(transfer: CrossChainTransfer) {
        val pendingList = _bridgeState.value.pendingTransfers.filter { it.id != transfer.id }
        val completedList = listOf(transfer) + _bridgeState.value.completedTransfers
        
        _bridgeState.value = _bridgeState.value.copy(
            pendingTransfers = pendingList,
            completedTransfers = completedList.take(100) // 保留最近 100 筆
        )
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        logger.i("Cleaning up CrossChainBridge")
        _bridgeState.value = BridgeState()
    }
}
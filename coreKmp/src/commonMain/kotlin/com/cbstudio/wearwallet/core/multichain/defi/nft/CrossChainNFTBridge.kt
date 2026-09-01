package com.cbstudio.wearwallet.core.multichain.defi.nft

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import com.cbstudio.wearwallet.core.multichain.bridge.ChainPair
import co.touchlab.kermit.Logger

/**
 * 跨鏈 NFT 橋接介面
 * 支援 NFT 在不同區塊鏈之間的轉移和元數據同步
 */
interface CrossChainNFTBridge {
    
    /**
     * 橋接協定名稱
     */
    val protocolName: String
    
    /**
     * 支援的鏈對
     */
    val supportedChainPairs: List<ChainPair>
    
    /**
     * 檢查是否支援指定的跨鏈 NFT 轉移
     */
    fun isSupported(sourceChain: MultiChainType, targetChain: MultiChainType): Boolean
    
    /**
     * 估算 NFT 跨鏈橋接手續費
     */
    suspend fun estimateNFTBridgeFee(
        request: NFTBridgeRequest
    ): NFTBridgeFeeEstimate
    
    /**
     * 執行 NFT 跨鏈轉移
     */
    suspend fun bridgeNFT(
        request: NFTBridgeRequest,
        privateKey: String
    ): NFTBridgeResult
    
    /**
     * 查詢 NFT 橋接狀態
     */
    suspend fun getNFTBridgeStatus(
        bridgeTransactionId: String
    ): NFTBridgeStatus
    
    /**
     * 同步 NFT 元數據
     */
    suspend fun syncNFTMetadata(
        sourceContract: String,
        sourceTokenId: String,
        sourceChain: MultiChainType,
        targetChain: MultiChainType
    ): NFTMetadataSyncResult
    
    /**
     * 取得支援的 NFT 標準
     */
    suspend fun getSupportedNFTStandards(): Map<MultiChainType, List<NFTStandard>>
}

/**
 * NFT 橋接請求
 */
data class NFTBridgeRequest(
    val sourceChain: MultiChainType,
    val targetChain: MultiChainType,
    val contractAddress: String,
    val tokenId: String,
    val ownerAddress: String,
    val targetAddress: String,
    val metadata: NFTMetadata? = null
)

/**
 * NFT 橋接手續費估算
 */
data class NFTBridgeFeeEstimate(
    val sourceFee: String,          // 源鏈手續費
    val bridgeFee: String,          // 橋接服務費
    val targetFee: String,          // 目標鏈手續費
    val metadataFee: String,        // 元數據同步費用
    val totalFee: String,           // 總手續費
    val estimatedTime: String,      // 預估完成時間
    val gasEstimate: NFTGasEstimate // Gas 估算詳情
)

/**
 * NFT Gas 估算
 */
data class NFTGasEstimate(
    val sourceGas: String,
    val targetGas: String,
    val bridgeGas: String,
    val metadataGas: String
)

/**
 * NFT 橋接結果
 */
data class NFTBridgeResult(
    val success: Boolean,
    val bridgeTransactionId: String,
    val sourceTransactionHash: String?,
    val targetTransactionHash: String? = null,
    val wrappedTokenInfo: WrappedNFTInfo?,
    val estimatedCompletionTime: Long,
    val message: String,
    val error: BlockchainException? = null
)

/**
 * 包裝 NFT 資訊
 */
data class WrappedNFTInfo(
    val wrappedContract: String,
    val wrappedTokenId: String,
    val targetChain: MultiChainType,
    val originalContract: String,
    val originalTokenId: String,
    val originalChain: MultiChainType
)

/**
 * NFT 橋接狀態
 */
data class NFTBridgeStatus(
    val bridgeTransactionId: String,
    val status: NFTBridgeTransactionStatus,
    val sourceTransactionHash: String?,
    val targetTransactionHash: String?,
    val wrappedTokenInfo: WrappedNFTInfo?,
    val progress: Double,
    val currentStep: String,
    val remainingSteps: List<String>,
    val metadataStatus: NFTMetadataStatus,
    val estimatedCompletionTime: Long?
)

/**
 * NFT 橋接交易狀態
 */
enum class NFTBridgeTransactionStatus {
    INITIATED,           // 已發起
    LOCKING_NFT,        // 鎖定 NFT
    SOURCE_CONFIRMED,   // 源鏈確認
    METADATA_SYNCING,   // 元數據同步中
    WRAPPING,           // 包裝 NFT
    TARGET_MINTING,     // 目標鏈鑄造
    COMPLETED,          // 完成
    FAILED,             // 失敗
    REFUNDED            // 已退款
}

/**
 * NFT 元數據狀態
 */
enum class NFTMetadataStatus {
    PENDING,      // 待處理
    SYNCING,      // 同步中
    SYNCED,       // 已同步
    FAILED        // 同步失敗
}

/**
 * NFT 元數據
 */
data class NFTMetadata(
    val name: String,
    val description: String?,
    val image: String?,
    val imageData: String? = null,
    val externalUrl: String? = null,
    val animationUrl: String? = null,
    val animationUrlData: String? = null,
    val attributes: List<NFTAttribute>,
    val backgroundColor: String? = null,
    val youtubeUrl: String? = null
)

/**
 * NFT 元數據同步結果
 */
data class NFTMetadataSyncResult(
    val success: Boolean,
    val sourceMetadata: NFTMetadata,
    val targetMetadata: NFTMetadata?,
    val ipfsHash: String?,
    val arweaveId: String?,
    val syncTransactionHash: String?,
    val message: String
)

/**
 * NFT 標準
 */
enum class NFTStandard {
    ERC721,     // Ethereum NFT 標準
    ERC1155,    // Ethereum 多代幣標準
    SPL,        // Solana Program Library
    TRC721,     // TRON NFT 標準
    CIP25,      // Cardano NFT 標準
    PSP34,      // Polkadot NFT 標準
    CUSTOM      // 自訂標準
}

/**
 * Wormhole NFT 橋接實現
 */
class WormholeNFTBridge(
    private val logger: Logger = Logger.withTag("WormholeNFTBridge")
) : CrossChainNFTBridge {
    
    override val protocolName = "Wormhole NFT Bridge"
    
    override val supportedChainPairs = listOf(
        ChainPair(MultiChainType.ETHEREUM, MultiChainType.SOLANA),
        ChainPair(MultiChainType.SOLANA, MultiChainType.ETHEREUM),
        // 可添加更多支援的鏈對
    )
    
    override fun isSupported(sourceChain: MultiChainType, targetChain: MultiChainType): Boolean {
        return supportedChainPairs.any { 
            (it.sourceChain == sourceChain && it.targetChain == targetChain) ||
            (it.sourceChain == targetChain && it.targetChain == sourceChain)
        }
    }
    
    override suspend fun estimateNFTBridgeFee(request: NFTBridgeRequest): NFTBridgeFeeEstimate {
        logger.d("Estimating NFT bridge fee: ${request.sourceChain.symbol} -> ${request.targetChain.symbol}")
        
        return try {
            // TODO: 實際的 Wormhole NFT 橋接手續費查詢
            // const wormholeNFT = new WormholeNFTBridge()
            // const feeEstimate = await wormholeNFT.estimateFee(request)
            
            val gasEstimate = NFTGasEstimate(
                sourceGas = getChainNFTGas(request.sourceChain),
                targetGas = getChainNFTGas(request.targetChain),
                bridgeGas = "100000",
                metadataGas = "50000"
            )
            
            NFTBridgeFeeEstimate(
                sourceFee = getChainBaseFee(request.sourceChain),
                bridgeFee = "0.01", // 固定橋接費
                targetFee = getChainBaseFee(request.targetChain),
                metadataFee = "0.005", // 元數據同步費用
                totalFee = calculateTotalNFTFee(request.sourceChain, request.targetChain),
                estimatedTime = getNFTBridgeTime(request.sourceChain, request.targetChain),
                gasEstimate = gasEstimate
            )
        } catch (e: Exception) {
            logger.e("Failed to estimate NFT bridge fee", e)
            throw BlockchainException.ApiException(
                request.sourceChain,
                "wormhole nft bridge fee",
                null,
                "Failed to estimate fee: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun bridgeNFT(
        request: NFTBridgeRequest,
        privateKey: String
    ): NFTBridgeResult {
        logger.i("Bridging NFT: ${request.contractAddress}/${request.tokenId} from ${request.sourceChain.symbol} to ${request.targetChain.symbol}")
        
        return try {
            // TODO: 實際的 Wormhole NFT 橋接執行
            // 1. 鎖定原始 NFT
            // 2. 生成證明
            // 3. 在目標鏈鑄造包裝 NFT
            // 4. 同步元數據
            
            val bridgeTransactionId = "wormhole_nft_${Clock.System.now().toEpochMilliseconds()}"
            val sourceTransactionHash = "source_nft_tx_${Clock.System.now().toEpochMilliseconds()}"
            
            val wrappedTokenInfo = WrappedNFTInfo(
                wrappedContract = "wrapped_nft_contract_${request.targetChain.symbol.lowercase()}",
                wrappedTokenId = request.tokenId,
                targetChain = request.targetChain,
                originalContract = request.contractAddress,
                originalTokenId = request.tokenId,
                originalChain = request.sourceChain
            )
            
            NFTBridgeResult(
                success = true,
                bridgeTransactionId = bridgeTransactionId,
                sourceTransactionHash = sourceTransactionHash,
                wrappedTokenInfo = wrappedTokenInfo,
                estimatedCompletionTime = Clock.System.now().toEpochMilliseconds() + 1800_000, // 30分鐘
                message = "NFT bridge transaction initiated successfully"
            )
        } catch (e: Exception) {
            logger.e("Failed to bridge NFT", e)
            NFTBridgeResult(
                success = false,
                bridgeTransactionId = "",
                sourceTransactionHash = null,
                wrappedTokenInfo = null,
                estimatedCompletionTime = 0,
                message = "NFT bridge failed: ${e.message}",
                error = BlockchainException.GenericException(request.sourceChain, e.message ?: "Unknown error", e)
            )
        }
    }
    
    override suspend fun getNFTBridgeStatus(bridgeTransactionId: String): NFTBridgeStatus {
        logger.d("Querying NFT bridge status: $bridgeTransactionId")
        
        return try {
            // TODO: 實際的 Wormhole NFT 狀態查詢
            
            NFTBridgeStatus(
                bridgeTransactionId = bridgeTransactionId,
                status = NFTBridgeTransactionStatus.METADATA_SYNCING,
                sourceTransactionHash = "source_hash",
                targetTransactionHash = null,
                wrappedTokenInfo = null,
                progress = 0.7, // 70% 完成
                currentStep = "Syncing NFT metadata to IPFS",
                remainingSteps = listOf(
                    "Complete metadata sync",
                    "Mint wrapped NFT on target chain",
                    "Verify ownership transfer"
                ),
                metadataStatus = NFTMetadataStatus.SYNCING,
                estimatedCompletionTime = Clock.System.now().toEpochMilliseconds() + 900_000 // 15分鐘
            )
        } catch (e: Exception) {
            logger.e("Failed to get NFT bridge status", e)
            throw BlockchainException.ApiException(
                MultiChainType.ETHEREUM,
                "wormhole nft bridge status",
                null,
                "Failed to get status: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun syncNFTMetadata(
        sourceContract: String,
        sourceTokenId: String,
        sourceChain: MultiChainType,
        targetChain: MultiChainType
    ): NFTMetadataSyncResult {
        logger.d("Syncing NFT metadata: $sourceContract/$sourceTokenId from ${sourceChain.symbol} to ${targetChain.symbol}")
        
        return try {
            // TODO: 實際的元數據同步邏輯
            // 1. 從源鏈讀取 NFT 元數據
            // 2. 上傳圖片和動畫到 IPFS/Arweave
            // 3. 在目標鏈設置元數據
            
            val sourceMetadata = NFTMetadata(
                name = "Test NFT #$sourceTokenId",
                description = "A test NFT bridged from ${sourceChain.fullName}",
                image = "https://example.com/image.png",
                attributes = listOf(
                    NFTAttribute(
                        traitType = "Origin Chain",
                        value = sourceChain.fullName,
                        rarity = null
                    )
                )
            )
            
            NFTMetadataSyncResult(
                success = true,
                sourceMetadata = sourceMetadata,
                targetMetadata = sourceMetadata,
                ipfsHash = "QmTest123...",
                arweaveId = null,
                syncTransactionHash = "metadata_sync_tx_${Clock.System.now().toEpochMilliseconds()}",
                message = "Metadata synced successfully"
            )
        } catch (e: Exception) {
            logger.e("Failed to sync NFT metadata", e)
            throw BlockchainException.ApiException(
                sourceChain,
                "wormhole nft metadata sync",
                null,
                "Failed to sync metadata: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun getSupportedNFTStandards(): Map<MultiChainType, List<NFTStandard>> {
        return mapOf(
            MultiChainType.ETHEREUM to listOf(NFTStandard.ERC721, NFTStandard.ERC1155),
            MultiChainType.SOLANA to listOf(NFTStandard.SPL),
            MultiChainType.TRON to listOf(NFTStandard.TRC721),
            MultiChainType.CARDANO to listOf(NFTStandard.CIP25),
            MultiChainType.POLKADOT to listOf(NFTStandard.PSP34)
        )
    }
    
    // 私有輔助方法
    
    private fun getChainNFTGas(chain: MultiChainType): String {
        return when (chain) {
            MultiChainType.ETHEREUM -> "200000"
            MultiChainType.SOLANA -> "100000"
            MultiChainType.TRON -> "150000"
            else -> "120000"
        }
    }
    
    private fun getChainBaseFee(chain: MultiChainType): String {
        return when (chain) {
            MultiChainType.ETHEREUM -> "0.005"
            MultiChainType.SOLANA -> "0.00001"
            MultiChainType.TRON -> "2.1"
            else -> "0.001"
        }
    }
    
    private fun calculateTotalNFTFee(sourceChain: MultiChainType, targetChain: MultiChainType): String {
        val sourceFee = getChainBaseFee(sourceChain).toDoubleOrNull() ?: 0.0
        val targetFee = getChainBaseFee(targetChain).toDoubleOrNull() ?: 0.0
        val bridgeFee = 0.01
        val metadataFee = 0.005
        
        return (sourceFee + targetFee + bridgeFee + metadataFee).toString()
    }
    
    private fun getNFTBridgeTime(sourceChain: MultiChainType, targetChain: MultiChainType): String {
        return when {
            sourceChain == MultiChainType.ETHEREUM || targetChain == MultiChainType.ETHEREUM -> "20-30 minutes"
            sourceChain == MultiChainType.SOLANA && targetChain == MultiChainType.SOLANA -> "10-15 minutes"
            else -> "15-25 minutes"
        }
    }
}

/**
 * NFT 橋接管理器
 * 統一管理多個 NFT 橋接協定
 */
class NFTBridgeManager(
    private val bridges: List<CrossChainNFTBridge>,
    private val logger: Logger = Logger.withTag("NFTBridgeManager")
) {
    
    /**
     * 取得支援指定跨鏈 NFT 轉移的橋接
     */
    fun getSupportedNFTBridges(
        sourceChain: MultiChainType,
        targetChain: MultiChainType
    ): List<CrossChainNFTBridge> {
        return bridges.filter { it.isSupported(sourceChain, targetChain) }
    }
    
    /**
     * 選擇最佳的 NFT 橋接協定
     */
    suspend fun selectOptimalNFTBridge(
        request: NFTBridgeRequest
    ): NFTBridgeRecommendation {
        logger.i("Selecting optimal NFT bridge for ${request.sourceChain.symbol} -> ${request.targetChain.symbol}")
        
        val supportedBridges = getSupportedNFTBridges(request.sourceChain, request.targetChain)
        
        if (supportedBridges.isEmpty()) {
            throw BlockchainException.UnsupportedOperationException(
                request.sourceChain,
                "No NFT bridge supports ${request.sourceChain.symbol} -> ${request.targetChain.symbol}"
            )
        }
        
        // 並行取得所有橋接的手續費估算
        val bridgeOptions = mutableListOf<NFTBridgeOption>()
        
        supportedBridges.forEach { bridge ->
            try {
                val feeEstimate = bridge.estimateNFTBridgeFee(request)
                val score = calculateNFTBridgeScore(bridge, feeEstimate)
                
                bridgeOptions.add(
                    NFTBridgeOption(
                        bridge = bridge,
                        feeEstimate = feeEstimate,
                        score = score
                    )
                )
                
                logger.d("NFT Bridge ${bridge.protocolName}: fee=${feeEstimate.totalFee}, time=${feeEstimate.estimatedTime}, score=$score")
            } catch (e: Exception) {
                logger.w("Failed to get NFT estimate from ${bridge.protocolName}", e)
            }
        }
        
        if (bridgeOptions.isEmpty()) {
            throw BlockchainException.GenericException(
                request.sourceChain,
                "No NFT bridge could provide fee estimates"
            )
        }
        
        // 按分數排序，選擇最佳選項
        val sortedOptions = bridgeOptions.sortedByDescending { it.score }
        val recommended = sortedOptions.first()
        val alternatives = sortedOptions.drop(1)
        
        return NFTBridgeRecommendation(
            recommended = recommended,
            alternatives = alternatives,
            selectionReason = "Selected ${recommended.bridge.protocolName} for optimal NFT bridging experience"
        )
    }
    
    /**
     * 執行 NFT 跨鏈轉移
     */
    suspend fun bridgeNFT(
        request: NFTBridgeRequest,
        privateKey: String
    ): NFTBridgeResult {
        val recommendation = selectOptimalNFTBridge(request)
        
        logger.i("Executing NFT bridge using ${recommendation.recommended.bridge.protocolName}")
        
        return recommendation.recommended.bridge.bridgeNFT(request, privateKey)
    }
    
    /**
     * 批量查詢 NFT 橋接狀態
     */
    suspend fun getNFTBridgeStatuses(
        bridgeTransactionIds: List<String>
    ): Map<String, NFTBridgeStatus> {
        val results = mutableMapOf<String, NFTBridgeStatus>()
        
        bridgeTransactionIds.forEach { transactionId ->
            bridges.forEach { bridge ->
                try {
                    val status = bridge.getNFTBridgeStatus(transactionId)
                    results[transactionId] = status
                    return@forEach
                } catch (e: Exception) {
                    // 繼續嘗試下一個橋接
                }
            }
        }
        
        return results
    }
    
    /**
     * 取得所有支援的 NFT 標準
     */
    suspend fun getAllSupportedNFTStandards(): Map<String, Map<MultiChainType, List<NFTStandard>>> {
        val results = mutableMapOf<String, Map<MultiChainType, List<NFTStandard>>>()
        
        bridges.forEach { bridge ->
            try {
                results[bridge.protocolName] = bridge.getSupportedNFTStandards()
            } catch (e: Exception) {
                logger.w("Failed to get NFT standards from ${bridge.protocolName}", e)
            }
        }
        
        return results
    }
    
    // 私有輔助方法
    
    private fun calculateNFTBridgeScore(
        bridge: CrossChainNFTBridge,
        feeEstimate: NFTBridgeFeeEstimate
    ): Double {
        val totalFee = feeEstimate.totalFee.toDoubleOrNull() ?: Double.MAX_VALUE
        val estimatedMinutes = parseEstimatedTime(feeEstimate.estimatedTime)
        
        // NFT 橋接評分考慮因素：手續費、時間、協定可靠性
        val feeScore = 1.0 / (totalFee + 0.001)
        val timeScore = 1.0 / (estimatedMinutes + 1)
        val reliabilityScore = getNFTBridgeReliabilityScore(bridge)
        
        return (feeScore * 0.4 + timeScore * 0.3 + reliabilityScore * 0.3)
    }
    
    private fun parseEstimatedTime(timeString: String): Double {
        return when {
            timeString.contains("minute", ignoreCase = true) -> {
                val numbers = Regex("\\d+").findAll(timeString).map { it.value.toDouble() }.toList()
                if (numbers.isNotEmpty()) numbers.average() else 20.0
            }
            timeString.contains("hour", ignoreCase = true) -> {
                timeString.filter { it.isDigit() }.toDoubleOrNull()?.times(60) ?: 60.0
            }
            else -> 20.0 // 預設20分鐘
        }
    }
    
    private fun getNFTBridgeReliabilityScore(bridge: CrossChainNFTBridge): Double {
        return when (bridge.protocolName) {
            "Wormhole NFT Bridge" -> 0.9
            else -> 0.7
        }
    }
}

/**
 * NFT 橋接選項
 */
data class NFTBridgeOption(
    val bridge: CrossChainNFTBridge,
    val feeEstimate: NFTBridgeFeeEstimate,
    val score: Double
)

/**
 * NFT 橋接推薦結果
 */
data class NFTBridgeRecommendation(
    val recommended: NFTBridgeOption,
    val alternatives: List<NFTBridgeOption>,
    val selectionReason: String
)

/**
 * NFT 橋接管理器工廠
 */
object NFTBridgeManagerFactory {
    
    /**
     * 創建預設的 NFT 橋接管理器
     */
    fun createDefaultNFTBridgeManager(): NFTBridgeManager {
        val bridges = listOf(
            WormholeNFTBridge()
            // 未來可添加更多 NFT 橋接協定：
            // LayerZeroNFTBridge(),
            // PolygonNFTBridge(),
            // AvalancheNFTBridge()
        )
        
        return NFTBridgeManager(bridges)
    }
    
    /**
     * 創建自訂 NFT 橋接組合的管理器
     */
    fun createCustomNFTBridgeManager(bridges: List<CrossChainNFTBridge>): NFTBridgeManager {
        return NFTBridgeManager(bridges)
    }
}
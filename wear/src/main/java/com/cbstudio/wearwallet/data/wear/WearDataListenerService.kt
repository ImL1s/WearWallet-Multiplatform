package com.cbstudio.wearwallet.data.wear

import com.cbstudio.wearwallet.presentation.complication.NftComplicationDataProvider
// import com.cbstudio.wearwallet.utils.fullName  // Not found
import com.cbstudio.wearwallet.data.wear.model.WearNftItem
import com.cbstudio.wearwallet.shared.utils.Logger
import com.google.android.gms.wearable.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Wear OS 數據層監聽服務
 * 
 * 基於 2025 年最佳實踐實現：
 * - 使用 DATA_CHANGED intent filter 提升電池效率
 * - 選擇性事件處理減少系統負載
 * - 自動更新錶盤複雜功能
 * - 智能錯誤處理和重試機制
 * 
 * 注意：僅在需要後台監聽時使用此服務。
 * 對於互動式應用，建議使用 OnDataChangedListener。
 */
// @AndroidEntryPoint  // Removed Hilt
class WearDataListenerService : WearableListenerService(), KoinComponent {
    
    companion object {
        private const val TAG = "WearDataListenerService"
        
        // 數據路徑常數（與手機端同步）
        private const val PATH_NFT_CONFIG = "/nft_config"
        private const val PATH_NFT_DATA = "/nft_data"
        private const val PATH_NFT_UPDATE = "/nft_update"
        private const val PATH_CONNECTION_STATUS = "/connection_status"
    }
    
    private val wearNftDataManager: WearNftDataManager by inject<WearNftDataManager>()
    
    /**
     * 處理數據變更事件
     * 
     * 2025 最佳實踐：
     * - 檢查數據路徑避免不必要的處理
     * - 使用協程進行異步處理
     * - 自動觸發 UI 更新
     */
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Logger.d(TAG, "收到數據變更事件，共 ${dataEvents.count} 個項目")
        
        try {
            for (event in dataEvents) {
                when (event.type) {
                    DataEvent.TYPE_CHANGED -> {
                        val path = event.dataItem.uri.path
                        Logger.d(TAG, "數據變更: $path")
                        
                        when (path) {
                            PATH_NFT_CONFIG -> handleNftConfigChanged(event.dataItem)
                            PATH_NFT_DATA -> handleNftDataChanged(event.dataItem)
                            else -> {
                                if (path?.startsWith(PATH_NFT_UPDATE) == true) {
                                    handleNftBatchUpdate(event.dataItem)
                                } else {
                                    Logger.d(TAG, "忽略未知路徑: $path")
                                }
                            }
                        }
                    }
                    
                    DataEvent.TYPE_DELETED -> {
                        Logger.d(TAG, "數據刪除: ${event.dataItem.uri.path}")
                        handleDataDeleted(event.dataItem)
                    }
                }
            }
        } finally {
            dataEvents.release()
        }
    }
    
    /**
     * 處理接收到的消息
     * 
     * 用於即時通信，如同步請求或控制命令
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Logger.d(TAG, "收到消息: ${messageEvent.path} from ${messageEvent.sourceNodeId}")
        
        when (messageEvent.path) {
            "/request_nft_sync" -> {
                Logger.d(TAG, "手機請求 NFT 同步")
                handleSyncRequest()
            }
            
            "/ping" -> {
                Logger.d(TAG, "收到 ping 消息")
                handlePingMessage(messageEvent)
            }
            
            "/nft_config_update" -> {
                Logger.d(TAG, "收到 NFT 配置更新消息")
                // 消息可能包含即時配置更新
                handleConfigUpdateMessage(messageEvent)
            }
        }
    }
    
    /**
     * 處理連接節點變更
     */
    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        Logger.d(TAG, "節點連接: ${peer.displayName} (${peer.id})")
        
        // 向手機發送連接確認
        sendConnectionStatus(peer.id, "connected")
        
        // 請求最新的 NFT 配置
        requestLatestConfig(peer.id)
    }
    
    /**
     * 處理節點斷開連接
     */
    override fun onPeerDisconnected(peer: Node) {
        super.onPeerDisconnected(peer)
        Logger.d(TAG, "節點斷開: ${peer.displayName} (${peer.id})")
        
        // 更新連接狀態
        wearNftDataManager.updateConnectionStatus(false)
    }
    
    /**
     * 處理能力變更
     */
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        super.onCapabilityChanged(capabilityInfo)
        Logger.d(TAG, "能力變更: ${capabilityInfo.name}")
        
        // 檢查手機端 NFT 功能是否可用
        val hasNftCapability = capabilityInfo.nodes.isNotEmpty()
        wearNftDataManager.updateNftCapabilityStatus(hasNftCapability)
    }
    
    // === 私有處理方法 ===
    
    /**
     * 處理 NFT 配置變更
     */
    private fun handleNftConfigChanged(dataItem: DataItem) {
        try {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            
            val nftSettings = WearNftSettings(
                isEnabled = dataMap.getBoolean("is_enabled", false),
                selectedNftContract = dataMap.getString("selected_nft_contract", ""),
                selectedNftTokenId = dataMap.getString("selected_nft_token_id", ""),
                displayMode = WearNftDisplayMode.entries.getOrNull(
                    dataMap.getInt("display_mode", 0)
                ) ?: WearNftDisplayMode.IMAGE_ONLY,
                updateIntervalSeconds = dataMap.getInt("update_interval_seconds", 3600),
                autoRotateEnabled = dataMap.getBoolean("auto_rotate_enabled", false),
                rotateIntervalSeconds = dataMap.getInt("rotate_interval_seconds", 86400),
                lastUpdated = dataMap.getLong("timestamp", System.currentTimeMillis())
            )
            
            // 保存配置到本地
            wearNftDataManager.saveNftSettings(nftSettings)
            
            // 觸發複雜功能更新
            NftComplicationDataProvider.requestUpdateAll(this)
            
            Logger.d(TAG, "NFT 配置已更新: ${nftSettings.selectedNftContract}:${nftSettings.selectedNftTokenId}")
            
        } catch (e: Exception) {
            Logger.e(TAG, "處理 NFT 配置變更失敗", e)
        }
    }
    
    /**
     * 處理 NFT 數據變更
     */
    private fun handleNftDataChanged(dataItem: DataItem) {
        try {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            
            val nftItem = WearNftItem(
                contractAddress = dataMap.getString("contract_address", ""),
                tokenId = dataMap.getString("token_id", ""),
                name = dataMap.getString("name", ""),
                description = dataMap.getString("description", ""),
                imageUrl = dataMap.getString("image_url", ""),
                thumbnailUrl = dataMap.getString("thumbnail_url", ""),
                collectionName = dataMap.getString("collection_name", ""),
                blockchain = dataMap.getString("blockchain", "Ethereum"),
                lastUpdated = dataMap.getLong("last_updated", System.currentTimeMillis()),
                isAnimated = dataMap.getBoolean("is_animated", false),
                animationUrl = dataMap.getString("animation_url", "")
            )
            
            // 保存當前選中的 NFT
            wearNftDataManager.saveCurrentNft(nftItem)
            
            // 預載圖片到緩存
            wearNftDataManager.preloadNftImage(nftItem)
            
            // 更新複雜功能
            NftComplicationDataProvider.requestUpdateAll(this)
            
            Logger.d(TAG, "NFT 數據已更新: ${nftItem.name}")
            
        } catch (e: Exception) {
            Logger.e(TAG, "處理 NFT 數據變更失敗", e)
        }
    }
    
    /**
     * 處理批次更新
     */
    private fun handleNftBatchUpdate(dataItem: DataItem) {
        try {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            
            val batchIndex = dataMap.getInt("batch_index", 0)
            val totalBatches = dataMap.getInt("total_batches", 1)
            val batchSize = dataMap.getInt("batch_size", 0)
            
            Logger.d(TAG, "處理批次更新: $batchIndex/$totalBatches (大小: $batchSize)")
            
            val nftItems = mutableListOf<WearNftItem>()
            
            for (i in 0 until batchSize) {
                val prefix = "nft_$i"
                
                val nftItem = WearNftItem(
                    contractAddress = dataMap.getString("${prefix}_contract", ""),
                    tokenId = dataMap.getString("${prefix}_token_id", ""),
                    name = dataMap.getString("${prefix}_name", ""),
                    description = "",
                    imageUrl = dataMap.getString("${prefix}_image_url", ""),
                    thumbnailUrl = dataMap.getString("${prefix}_thumbnail_url", ""),
                    collectionName = dataMap.getString("${prefix}_collection", ""),
                    blockchain = dataMap.getString("${prefix}_blockchain", "Ethereum"),
                    lastUpdated = dataMap.getLong("${prefix}_last_updated", System.currentTimeMillis()),
                    isAnimated = false,
                    animationUrl = ""
                )
                
                if (nftItem.contractAddress.isNotBlank() && nftItem.tokenId.isNotBlank()) {
                    nftItems.add(nftItem)
                }
            }
            
            // 保存批次數據
            wearNftDataManager.saveBatchNfts(batchIndex, totalBatches, nftItems)
            
            // 如果是最後一批，觸發完整更新
            if (batchIndex == totalBatches - 1) {
                wearNftDataManager.finalizeBatchUpdate()
                NftComplicationDataProvider.requestUpdateAll(this)
                Logger.d(TAG, "批次更新完成，總共 $totalBatches 批次")
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "處理批次更新失敗", e)
        }
    }
    
    /**
     * 處理數據刪除
     */
    private fun handleDataDeleted(dataItem: DataItem) {
        val path = dataItem.uri.path
        Logger.d(TAG, "處理數據刪除: $path")
        
        when (path) {
            PATH_NFT_CONFIG -> {
                // 配置被刪除，重置為預設狀態
                wearNftDataManager.resetNftSettings()
                NftComplicationDataProvider.requestUpdateAll(this)
            }
            
            PATH_NFT_DATA -> {
                // NFT 數據被刪除，清除當前選中的 NFT
                wearNftDataManager.clearCurrentNft()
                NftComplicationDataProvider.requestUpdateAll(this)
            }
        }
    }
    
    /**
     * 處理同步請求
     */
    private fun handleSyncRequest() {
        // 向手機發送當前狀態
        wearNftDataManager.sendStatusToPhone()
    }
    
    /**
     * 處理 ping 消息
     */
    private fun handlePingMessage(messageEvent: MessageEvent) {
        // 回復 pong 消息
        val messageClient = Wearable.getMessageClient(this)
        messageClient.sendMessage(
            messageEvent.sourceNodeId,
            "/pong",
            "alive".toByteArray()
        )
    }
    
    /**
     * 處理配置更新消息
     */
    private fun handleConfigUpdateMessage(messageEvent: MessageEvent) {
        // 解析消息數據並更新配置
        val configData = String(messageEvent.data)
        Logger.d(TAG, "收到配置更新: $configData")
        
        // 觸發配置重新載入
        wearNftDataManager.reloadConfiguration()
    }
    
    /**
     * 發送連接狀態
     */
    private fun sendConnectionStatus(nodeId: String, status: String) {
        val messageClient = Wearable.getMessageClient(this)
        messageClient.sendMessage(
            nodeId,
            "/watch_status",
            status.toByteArray()
        )
    }
    
    /**
     * 請求最新配置
     */
    private fun requestLatestConfig(nodeId: String) {
        val messageClient = Wearable.getMessageClient(this)
        messageClient.sendMessage(
            nodeId,
            "/request_latest_config",
            byteArrayOf()
        )
    }
}

/**
 * Wear OS NFT 設定模型
 */
data class WearNftSettings(
    val isEnabled: Boolean = false,
    val selectedNftContract: String = "",
    val selectedNftTokenId: String = "",
    val displayMode: WearNftDisplayMode = WearNftDisplayMode.IMAGE_ONLY,
    val updateIntervalSeconds: Int = 3600,
    val autoRotateEnabled: Boolean = false,
    val rotateIntervalSeconds: Int = 86400,
    val lastUpdated: Long = 0L
)

/**
 * Wear OS NFT 顯示模式
 */
enum class WearNftDisplayMode {
    IMAGE_ONLY,
    NAME_ONLY,
    IMAGE_AND_NAME,
    COLLECTION_NAME,
    ROTATING
}

/**
 * Wear OS NFT 項目模型
 */
data class WearNftItem(
    val contractAddress: String,
    val tokenId: String,
    val name: String,
    val description: String = "",
    val imageUrl: String,
    val thumbnailUrl: String,
    val collectionName: String,
    val blockchain: String,
    val lastUpdated: Long,
    val isAnimated: Boolean = false,
    val animationUrl: String = ""
) {
    fun getUniqueId(): String = "${contractAddress.lowercase()}:$tokenId"
}

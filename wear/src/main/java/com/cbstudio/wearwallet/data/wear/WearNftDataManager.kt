package com.cbstudio.wearwallet.data.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import com.cbstudio.wearwallet.R
import com.cbstudio.wearwallet.data.wear.model.WearNftItem
import com.cbstudio.wearwallet.presentation.nft.NftDetailsActivity
import com.cbstudio.wearwallet.presentation.settings.SettingsActivity
import com.cbstudio.wearwallet.shared.utils.Logger
// import com.cbstudio.wearwallet.utils.ImageCache // 暫時移除
import kotlinx.coroutines.tasks.await
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
// Removed Hilt imports, using Koin now
import kotlin.coroutines.CoroutineContext

/**
 * Wear OS NFT 數據管理器
 * 
 * 負責管理手錶端的 NFT 數據，包括：
 * - 本地數據存儲和檢索
 * - 圖片緩存管理
 * - 配置狀態維護
 * - 自動輪換邏輯
 * - 與手機端的通信協調
 * 
 * 特性：
 * - 智能緩存策略
 * - 低功耗設計
 * - 自動錯誤恢復
 * - 流式數據更新
 */

class WearNftDataManager(
    private val context: Context
) : CoroutineScope {
    
    companion object {
        private const val TAG = "WearNftDataManager"
        
        // SharedPreferences 鍵值
        private const val PREFS_NAME = "wear_nft_data"
        private const val KEY_CURRENT_NFT = "current_nft"
        private const val KEY_NFT_SETTINGS = "nft_settings"
        private const val KEY_CONNECTION_STATUS = "connection_status"
        private const val KEY_NFT_COLLECTION = "nft_collection"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val KEY_LAST_ROTATION = "last_rotation"
        
        // 自動輪換配置
        private const val DEFAULT_ROTATION_INTERVAL = 86400000L // 24 hours
        private const val MIN_ROTATION_INTERVAL = 3600000L // 1 hour
        
        // PendingIntent 請求碼
        private const val REQUEST_NFT_DETAILS = 1001
        private const val REQUEST_SETTINGS = 1002
    }
    
    // 協程上下文
    override val coroutineContext: CoroutineContext = 
        SupervisorJob() + Dispatchers.Main + CoroutineName("WearNftDataManager")
    
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 狀態流
    private val _currentNft = MutableStateFlow<WearNftItem?>(null)
    val currentNft: StateFlow<WearNftItem?> = _currentNft.asStateFlow()
    
    private val _nftSettings = MutableStateFlow(WearNftSettings())
    val nftSettings: StateFlow<WearNftSettings> = _nftSettings.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    
    private val _nftCollection = MutableStateFlow<List<WearNftItem>>(emptyList())
    val nftCollection: StateFlow<List<WearNftItem>> = _nftCollection.asStateFlow()
    
    // 自動輪換任務
    private var rotationJob: Job? = null
    
    init {
        // 載入本地數據
        loadLocalData()
        
        // 開始自動輪換（如果啟用）
        startAutoRotationIfEnabled()
    }
    
    /**
     * 檢查 NFT 複雜功能是否啟用
     */
    fun isNftComplicationEnabled(): Boolean {
        return _nftSettings.value.isEnabled
    }
    
    /**
     * 獲取當前選中的 NFT
     */
    fun getCurrentSelectedNft(): WearNftItem? {
        return _currentNft.value
    }
    
    /**
     * 獲取 NFT 收藏大小
     */
    fun getNftCollectionSize(): Int {
        return _nftCollection.value.size
    }
    
    /**
     * 獲取當前 NFT 索引
     */
    fun getCurrentNftIndex(): Int {
        val currentNft = _currentNft.value ?: return 0
        return _nftCollection.value.indexOfFirst { it.getUniqueId() == currentNft.getUniqueId() }
            .coerceAtLeast(0)
    }
    
    /**
     * 載入 NFT 圖示
     */
    suspend fun loadNftIcon(nft: WearNftItem): Icon? = withContext(Dispatchers.IO) {
        return@withContext try {
            val imageUrl = nft.thumbnailUrl.ifBlank { nft.imageUrl }
            if (imageUrl.isBlank()) {
                Logger.w(TAG, "NFT 圖片 URL 為空: ${nft.name}")
                return@withContext null
            }
            
            // TODO: 實現圖片載入邏輯
            // 暫時返回預設圖示
            Logger.d(TAG, "暫時使用預設圖示: ${nft.name}")
            Icon.createWithResource(context, R.drawable.ic_nft_complication)
            
        } catch (e: Exception) {
            Logger.e(TAG, "載入 NFT 圖示失敗: ${nft.name}", e)
            null
        }
    }
    
    /**
     * 預載 NFT 圖片到緩存 - 暫時空實現
     */
    fun preloadNftImage(nft: WearNftItem) {
        // TODO: 實現圖片預載邏輯
        Logger.d(TAG, "預載 NFT 圖片 (暫時跳過): ${nft.name}")
    }
    
    /**
     * 創建 NFT 詳情 PendingIntent
     */
    fun createNftDetailsPendingIntent(nft: WearNftItem, action: String): PendingIntent {
        val intent = Intent(context, NftDetailsActivity::class.java).apply {
            putExtra("nft_contract", nft.contractAddress)
            putExtra("nft_token_id", nft.tokenId)
            putExtra("nft_name", nft.name)
            putExtra("nft_image_url", nft.imageUrl)
            putExtra("nft_collection", nft.collectionName)
            putExtra("action", action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        return PendingIntent.getActivity(
            context,
            REQUEST_NFT_DETAILS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * 創建設定 PendingIntent
     */
    fun createSettingsPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            putExtra("action", action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        return PendingIntent.getActivity(
            context,
            REQUEST_SETTINGS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * 保存 NFT 設定
     */
    fun saveNftSettings(settings: WearNftSettings) {
        _nftSettings.value = settings
        
        // 持久化到 SharedPreferences
        sharedPrefs.edit().apply {
            putBoolean("settings_enabled", settings.isEnabled)
            putString("settings_contract", settings.selectedNftContract)
            putString("settings_token_id", settings.selectedNftTokenId)
            putInt("settings_display_mode", settings.displayMode.ordinal)
            putInt("settings_update_interval", settings.updateIntervalSeconds)
            putBoolean("settings_auto_rotate", settings.autoRotateEnabled)
            putInt("settings_rotate_interval", settings.rotateIntervalSeconds)
            putLong("settings_last_updated", settings.lastUpdated)
            apply()
        }
        
        Logger.d(TAG, "NFT 設定已保存: ${settings.selectedNftContract}:${settings.selectedNftTokenId}")
        
        // 重新開始自動輪換
        startAutoRotationIfEnabled()
    }
    
    /**
     * 保存當前 NFT
     */
    fun saveCurrentNft(nft: WearNftItem) {
        _currentNft.value = nft
        
        // 持久化到 SharedPreferences
        sharedPrefs.edit().apply {
            putString("nft_contract", nft.contractAddress)
            putString("nft_token_id", nft.tokenId)
            putString("nft_name", nft.name)
            putString("nft_description", nft.description)
            putString("nft_image_url", nft.imageUrl)
            putString("nft_thumbnail_url", nft.thumbnailUrl)
            putString("nft_collection", nft.collectionName)
            putString("nft_blockchain", nft.blockchain)
            putLong("nft_last_updated", nft.lastUpdated)
            putBoolean("nft_is_animated", nft.isAnimated)
            putString("nft_animation_url", nft.animationUrl)
            apply()
        }
        
        Logger.d(TAG, "當前 NFT 已保存: ${nft.name}")
    }
    
    /**
     * 保存批次 NFT 數據
     */
    fun saveBatchNfts(batchIndex: Int, totalBatches: Int, nfts: List<WearNftItem>) {
        Logger.d(TAG, "保存批次 NFT: $batchIndex/$totalBatches，${nfts.size} 項目")
        
        // 累積收集批次數據
        val currentCollection = _nftCollection.value.toMutableList()
        
        // 如果是第一批，清除舊數據
        if (batchIndex == 0) {
            currentCollection.clear()
        }
        
        // 添加新數據
        currentCollection.addAll(nfts)
        _nftCollection.value = currentCollection
        
        // 保存進度到 SharedPreferences
        sharedPrefs.edit().apply {
            putInt("batch_progress", batchIndex + 1)
            putInt("batch_total", totalBatches)
            apply()
        }
    }
    
    /**
     * 完成批次更新
     */
    fun finalizeBatchUpdate() {
        val collection = _nftCollection.value
        Logger.d(TAG, "批次更新完成，總共 ${collection.size} 個 NFT")
        
        // 持久化完整收藏到 SharedPreferences
        val collectionJson = serializeNftCollection(collection)
        sharedPrefs.edit().apply {
            putString(KEY_NFT_COLLECTION, collectionJson)
            remove("batch_progress")
            remove("batch_total")
            apply()
        }
        
        // 如果當前沒有選中的 NFT，選擇第一個
        if (_currentNft.value == null && collection.isNotEmpty()) {
            saveCurrentNft(collection.first())
        }
    }
    
    /**
     * 更新連接狀態
     */
    fun updateConnectionStatus(connected: Boolean) {
        _connectionStatus.value = connected
        sharedPrefs.edit().putBoolean(KEY_CONNECTION_STATUS, connected).apply()
        Logger.d(TAG, "連接狀態更新: $connected")
    }
    
    /**
     * 更新 NFT 能力狀態
     */
    fun updateNftCapabilityStatus(hasCapability: Boolean) {
        Logger.d(TAG, "NFT 能力狀態: $hasCapability")
        // 可以根據能力狀態調整行為
    }
    
    /**
     * 重置 NFT 設定
     */
    fun resetNftSettings() {
        _nftSettings.value = WearNftSettings()
        _currentNft.value = null
        
        sharedPrefs.edit().clear().apply()
        Logger.d(TAG, "NFT 設定已重置")
        
        // 停止自動輪換
        rotationJob?.cancel()
    }
    
    /**
     * 清除當前 NFT
     */
    fun clearCurrentNft() {
        _currentNft.value = null
        
        sharedPrefs.edit().apply {
            remove("nft_contract")
            remove("nft_token_id")
            remove("nft_name")
            remove("nft_description")
            remove("nft_image_url")
            remove("nft_thumbnail_url")
            remove("nft_collection")
            remove("nft_blockchain")
            remove("nft_last_updated")
            remove("nft_is_animated")
            remove("nft_animation_url")
            apply()
        }
        
        Logger.d(TAG, "當前 NFT 已清除")
    }
    
    /**
     * 發送狀態到手機
     */
    fun sendStatusToPhone() {
        launch(Dispatchers.IO) {
            try {
                val messageClient = Wearable.getMessageClient(context)
                val nodeClient = Wearable.getNodeClient(context)
                
                val connectedNodes = nodeClient.connectedNodes.await()
                val statusMessage = "watch_alive:${_nftSettings.value.isEnabled}"
                
                for (node in connectedNodes) {
                    messageClient.sendMessage(
                        node.id,
                        "/watch_status_response",
                        statusMessage.toByteArray()
                    ).await()
                }
                
                Logger.d(TAG, "狀態已發送到手機")
            } catch (e: Exception) {
                Logger.e(TAG, "發送狀態到手機失敗", e)
            }
        }
    }
    
    /**
     * 重新載入配置
     */
    fun reloadConfiguration() {
        loadLocalData()
        Logger.d(TAG, "配置已重新載入")
    }
    
    /**
     * 切換到下一個 NFT（手動輪換）
     */
    fun switchToNextNft(): Boolean {
        val collection = _nftCollection.value
        if (collection.size <= 1) return false
        
        val currentIndex = getCurrentNftIndex()
        val nextIndex = (currentIndex + 1) % collection.size
        val nextNft = collection[nextIndex]
        
        saveCurrentNft(nextNft)
        updateLastRotationTime()
        
        Logger.d(TAG, "切換到下一個 NFT: ${nextNft.name}")
        return true
    }
    
    // === 私有方法 ===
    
    /**
     * 載入本地數據
     */
    private fun loadLocalData() {
        // 載入 NFT 設定
        val settings = WearNftSettings(
            isEnabled = sharedPrefs.getBoolean("settings_enabled", false),
            selectedNftContract = sharedPrefs.getString("settings_contract", "") ?: "",
            selectedNftTokenId = sharedPrefs.getString("settings_token_id", "") ?: "",
            displayMode = WearNftDisplayMode.entries.getOrNull(
                sharedPrefs.getInt("settings_display_mode", 0)
            ) ?: WearNftDisplayMode.IMAGE_ONLY,
            updateIntervalSeconds = sharedPrefs.getInt("settings_update_interval", 3600),
            autoRotateEnabled = sharedPrefs.getBoolean("settings_auto_rotate", false),
            rotateIntervalSeconds = sharedPrefs.getInt("settings_rotate_interval", 86400),
            lastUpdated = sharedPrefs.getLong("settings_last_updated", 0L)
        )
        _nftSettings.value = settings
        
        // 載入當前 NFT
        val contractAddress = sharedPrefs.getString("nft_contract", "")
        if (!contractAddress.isNullOrBlank()) {
            val nft = WearNftItem(
                contractAddress = contractAddress,
                tokenId = sharedPrefs.getString("nft_token_id", "") ?: "",
                name = sharedPrefs.getString("nft_name", "") ?: "",
                description = sharedPrefs.getString("nft_description", "") ?: "",
                imageUrl = sharedPrefs.getString("nft_image_url", "") ?: "",
                thumbnailUrl = sharedPrefs.getString("nft_thumbnail_url", "") ?: "",
                collectionName = sharedPrefs.getString("nft_collection", "") ?: "",
                blockchain = sharedPrefs.getString("nft_blockchain", "Ethereum") ?: "Ethereum",
                lastUpdated = sharedPrefs.getLong("nft_last_updated", 0L),
                isAnimated = sharedPrefs.getBoolean("nft_is_animated", false),
                animationUrl = sharedPrefs.getString("nft_animation_url", "") ?: ""
            )
            _currentNft.value = nft
        }
        
        // 載入 NFT 收藏
        val collectionJson = sharedPrefs.getString(KEY_NFT_COLLECTION, "")
        if (!collectionJson.isNullOrBlank()) {
            val collection = deserializeNftCollection(collectionJson)
            _nftCollection.value = collection
        }
        
        // 載入連接狀態
        _connectionStatus.value = sharedPrefs.getBoolean(KEY_CONNECTION_STATUS, false)
        
        Logger.d(TAG, "本地數據載入完成")
    }
    
    /**
     * 開始自動輪換（如果啟用）
     */
    private fun startAutoRotationIfEnabled() {
        rotationJob?.cancel()
        
        val settings = _nftSettings.value
        if (!settings.autoRotateEnabled || _nftCollection.value.size <= 1) {
            return
        }
        
        val intervalMs = (settings.rotateIntervalSeconds * 1000L)
            .coerceAtLeast(MIN_ROTATION_INTERVAL)
        
        rotationJob = launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervalMs)
                
                val lastRotation = sharedPrefs.getLong(KEY_LAST_ROTATION, 0L)
                val now = System.currentTimeMillis()
                
                if (now - lastRotation >= intervalMs) {
                    if (switchToNextNft()) {
                        Logger.d(TAG, "自動輪換執行")
                    }
                }
            }
        }
        
        Logger.d(TAG, "自動輪換已啟用，間隔: ${intervalMs}ms")
    }
    
    /**
     * 更新最後輪換時間
     */
    private fun updateLastRotationTime() {
        sharedPrefs.edit().putLong(KEY_LAST_ROTATION, System.currentTimeMillis()).apply()
    }
    
    /**
     * 序列化 NFT 收藏
     */
    private fun serializeNftCollection(collection: List<WearNftItem>): String {
        // 簡化實現，實際應用中可以使用 JSON 或 protobuf
        return collection.joinToString("|") { nft ->
            "${nft.contractAddress}:${nft.tokenId}:${nft.name}:${nft.imageUrl}:${nft.collectionName}:${nft.blockchain}"
        }
    }
    
    /**
     * 反序列化 NFT 收藏
     */
    private fun deserializeNftCollection(json: String): List<WearNftItem> {
        return try {
            json.split("|").mapNotNull { item ->
                val parts = item.split(":")
                if (parts.size >= 6) {
                    WearNftItem(
                        contractAddress = parts[0],
                        tokenId = parts[1],
                        name = parts[2],
                        imageUrl = parts[3],
                        thumbnailUrl = parts[3],
                        collectionName = parts[4],
                        blockchain = parts[5],
                        lastUpdated = System.currentTimeMillis()
                    )
                } else null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "反序列化 NFT 收藏失敗", e)
            emptyList()
        }
    }
}

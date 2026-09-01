package com.cbstudio.mobile.ui.nftwatchface

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import com.cbstudio.wearwallet.core.domain.model.*
import com.cbstudio.wearwallet.core.domain.model.nft.NftItem
import com.cbstudio.wearwallet.core.domain.model.nft.NftComplicationSettings
import com.cbstudio.wearwallet.core.domain.model.nft.NftDisplayMode
import com.cbstudio.wearwallet.core.domain.repository.NftRepository
import com.cbstudio.mobile.R

/**
 * NFT 錶盤配置 ViewModel
 * 管理 NFT 收藏資料、配置狀態和用戶交互
 */
class NftWatchFaceConfigurationViewModel(
    private val context: Context,
    private val nftRepository: NftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NftWatchFaceConfigurationUiState())
    val uiState: StateFlow<NftWatchFaceConfigurationUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    /**
     * 載入初始資料，包含已保存的配置和 NFT 收藏
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // 並行載入配置和 NFT 收藏
                val configDeferred = async { loadSavedConfiguration() }
                val nftsDeferred = async { loadNftCollection() }
                
                val savedConfig = configDeferred.await()
                val nfts = nftsDeferred.await()
                
                // 尋找已選中的 NFT
                val selectedNft = if (savedConfig.selectedNftContract.isNotEmpty() && savedConfig.selectedNftTokenId.isNotEmpty()) {
                    nfts.find { 
                        it.contractAddress == savedConfig.selectedNftContract && 
                        it.tokenId == savedConfig.selectedNftTokenId 
                    }
                } else null
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isEnabled = savedConfig.isEnabled,
                    displayMode = savedConfig.displayMode,
                    updateIntervalHours = savedConfig.updateIntervalSeconds / 3600,
                    autoRotateEnabled = savedConfig.autoRotateEnabled,
                    rotateIntervalHours = savedConfig.rotateIntervalSeconds / 3600,
                    nftCollection = nfts,
                    selectedNft = selectedNft,
                    favoriteNfts = savedConfig.favoriteNfts.toMutableList()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = context.getString(R.string.error_load_nfts, e.message ?: "")
                )
            }
        }
    }

    /**
     * 載入已保存的 NFT 錶盤配置
     */
    private suspend fun loadSavedConfiguration(): NftComplicationSettings {
        return try {
            nftRepository.getNftComplicationSettings()
        } catch (e: Exception) {
            // 回傳預設配置
            NftComplicationSettings()
        }
    }

    /**
     * 載入用戶的 NFT 收藏
     */
    private suspend fun loadNftCollection(): List<NftItem> {
        return try {
            // 這裡應該從多個錢包地址載入 NFT
            // 目前使用模擬資料進行展示
            nftRepository.getUserNftCollection()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 選擇 NFT
     */
    fun selectNft(nft: NftItem) {
        _uiState.value = _uiState.value.copy(selectedNft = nft)
    }

    /**
     * 更新配置設定
     */
    fun updateSettings(newState: NftWatchFaceConfigurationUiState) {
        _uiState.value = newState
    }

    /**
     * 重新整理 NFT 收藏
     */
    fun refreshNftCollection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            
            try {
                val nfts = loadNftCollection()
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    nftCollection = nfts,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = context.getString(R.string.error_refresh_nfts, e.message ?: "")
                )
            }
        }
    }

    /**
     * 重試載入
     */
    fun retryLoading() {
        loadInitialData()
    }

    /**
     * 將 NFT 加入收藏
     */
    fun toggleFavoriteNft(nft: NftItem) {
        val currentFavorites = _uiState.value.favoriteNfts.toMutableList()
        val nftId = nft.getUniqueId()
        
        if (currentFavorites.any { it.getUniqueId() == nftId }) {
            currentFavorites.removeAll { it.getUniqueId() == nftId }
        } else {
            currentFavorites.add(nft)
        }
        
        _uiState.value = _uiState.value.copy(favoriteNfts = currentFavorites)
    }

    /**
     * 儲存配置
     */
    fun saveConfiguration() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            
            try {
                val currentState = _uiState.value
                val selectedNft = currentState.selectedNft
                
                val settings = NftComplicationSettings(
                    isEnabled = currentState.isEnabled,
                    selectedNftContract = selectedNft?.contractAddress ?: "",
                    selectedNftTokenId = selectedNft?.tokenId ?: "",
                    displayMode = currentState.displayMode,
                    updateIntervalSeconds = currentState.updateIntervalHours * 3600,
                    autoRotateEnabled = currentState.autoRotateEnabled,
                    rotateIntervalSeconds = currentState.rotateIntervalHours * 3600,
                    favoriteNfts = currentState.favoriteNfts
                )
                
                nftRepository.saveNftComplicationSettings(settings)
                
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    showSaveSuccess = true
                )
                
                // 通知 Wear OS 應用配置已更新
                notifyWearOSConfigurationChanged(settings)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = context.getString(R.string.error_save_config, e.message ?: "")
                )
            }
        }
    }

    /**
     * 通知 Wear OS 應用配置已更新
     */
    private suspend fun notifyWearOSConfigurationChanged(settings: NftComplicationSettings) {
        try {
            // 這裡應該透過 Wearable Data API 或 Message API 通知手錶
            // 目前僅記錄日誌
            println("NFT 錶盤配置已更新並通知 Wear OS")
        } catch (e: Exception) {
            println("通知 Wear OS 失敗: ${e.message}")
        }
    }

    /**
     * 清除成功訊息
     */
    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(showSaveSuccess = false)
    }
}

/**
 * NFT 錶盤配置 UI 狀態
 */
data class NftWatchFaceConfigurationUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val showSaveSuccess: Boolean = false,
    
    // 配置設定
    val isEnabled: Boolean = false,
    val displayMode: NftDisplayMode = NftDisplayMode.IMAGE_ONLY,
    val updateIntervalHours: Int = 1,
    val autoRotateEnabled: Boolean = false,
    val rotateIntervalHours: Int = 24,
    
    // NFT 資料
    val nftCollection: List<NftItem> = emptyList(),
    val selectedNft: NftItem? = null,
    val favoriteNfts: List<NftItem> = emptyList()
)
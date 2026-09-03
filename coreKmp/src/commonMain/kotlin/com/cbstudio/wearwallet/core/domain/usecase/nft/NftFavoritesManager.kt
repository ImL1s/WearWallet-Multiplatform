package com.cbstudio.wearwallet.core.domain.usecase.nft

import com.cbstudio.wearwallet.core.domain.model.nft.NftItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NFT 收藏管理器
 * 管理用戶收藏的 NFT 列表
 */
class NftFavoritesManager {
    
    private val _favoriteNfts = MutableStateFlow<List<NftItem>>(emptyList())
    val favoriteNfts: StateFlow<List<NftItem>> = _favoriteNfts.asStateFlow()
    
    private val _selectedNft = MutableStateFlow<NftItem?>(null)
    val selectedNft: StateFlow<NftItem?> = _selectedNft.asStateFlow()
    
    /**
     * 添加 NFT 到收藏
     */
    fun addToFavorites(nft: NftItem) {
        val currentList = _favoriteNfts.value.toMutableList()
        if (!currentList.any { it.getUniqueId() == nft.getUniqueId() }) {
            currentList.add(nft.copy(isFavorite = true))
            _favoriteNfts.value = currentList
        }
    }
    
    /**
     * 從收藏中移除 NFT
     */
    fun removeFromFavorites(nft: NftItem) {
        val currentList = _favoriteNfts.value.toMutableList()
        currentList.removeAll { it.getUniqueId() == nft.getUniqueId() }
        _favoriteNfts.value = currentList
    }
    
    /**
     * 切換 NFT 收藏狀態
     */
    fun toggleFavorite(nft: NftItem) {
        if (isFavorite(nft)) {
            removeFromFavorites(nft)
        } else {
            addToFavorites(nft)
        }
    }
    
    /**
     * 檢查 NFT 是否為收藏
     */
    fun isFavorite(nft: NftItem): Boolean {
        return _favoriteNfts.value.any { it.getUniqueId() == nft.getUniqueId() }
    }
    
    /**
     * 設置選中的 NFT
     */
    fun selectNft(nft: NftItem?) {
        _selectedNft.value = nft
    }
    
    /**
     * 清空所有收藏
     */
    fun clearFavorites() {
        _favoriteNfts.value = emptyList()
        _selectedNft.value = null
    }
    
    /**
     * 批量添加收藏
     */
    fun addMultipleToFavorites(nfts: List<NftItem>) {
        val currentList = _favoriteNfts.value.toMutableList()
        nfts.forEach { nft ->
            if (!currentList.any { it.getUniqueId() == nft.getUniqueId() }) {
                currentList.add(nft.copy(isFavorite = true))
            }
        }
        _favoriteNfts.value = currentList
    }
    
    /**
     * 更新 NFT 信息
     */
    fun updateNft(updatedNft: NftItem) {
        val currentList = _favoriteNfts.value.toMutableList()
        val index = currentList.indexOfFirst { it.getUniqueId() == updatedNft.getUniqueId() }
        if (index != -1) {
            currentList[index] = updatedNft.copy(isFavorite = true)
            _favoriteNfts.value = currentList
        }
        
        // 如果是選中的 NFT，也更新選中狀態
        if (_selectedNft.value?.getUniqueId() == updatedNft.getUniqueId()) {
            _selectedNft.value = updatedNft
        }
    }
    
    /**
     * 獲取收藏數量
     */
    fun getFavoriteCount(): Int {
        return _favoriteNfts.value.size
    }
    
    /**
     * 按收藏品系列分組
     */
    fun getFavoritesByCollection(): Map<String, List<NftItem>> {
        return _favoriteNfts.value.groupBy { it.collectionName }
    }
}
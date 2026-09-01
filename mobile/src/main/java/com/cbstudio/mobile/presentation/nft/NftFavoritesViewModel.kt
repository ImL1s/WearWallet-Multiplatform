package com.cbstudio.mobile.presentation.nft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.domain.model.nft.NftItem
import com.cbstudio.wearwallet.core.domain.usecase.nft.NftFavoritesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * NFT 收藏 ViewModel
 */
class NftFavoritesViewModel(
    private val nftFavoritesManager: NftFavoritesManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(NftFavoritesUiState())
    val uiState: StateFlow<NftFavoritesUiState> = _uiState.asStateFlow()
    
    val favoriteNfts = nftFavoritesManager.favoriteNfts
    val selectedNft = nftFavoritesManager.selectedNft
    
    init {
        loadFavorites()
    }
    
    private fun loadFavorites() {
        viewModelScope.launch {
            nftFavoritesManager.favoriteNfts.collect { nfts ->
                _uiState.value = _uiState.value.copy(
                    favoriteNfts = nfts,
                    isLoading = false
                )
            }
        }
    }
    
    fun toggleFavorite(nft: NftItem) {
        nftFavoritesManager.toggleFavorite(nft)
    }
    
    fun selectNft(nft: NftItem) {
        nftFavoritesManager.selectNft(nft)
        _uiState.value = _uiState.value.copy(selectedNft = nft)
    }
    
    fun clearSelection() {
        nftFavoritesManager.selectNft(null)
        _uiState.value = _uiState.value.copy(selectedNft = null)
    }
    
    fun filterByCollection(collectionName: String?) {
        _uiState.value = _uiState.value.copy(
            selectedCollection = collectionName,
            filteredNfts = if (collectionName != null) {
                _uiState.value.favoriteNfts.filter { it.collectionName == collectionName }
            } else {
                _uiState.value.favoriteNfts
            }
        )
    }
    
    fun sortBy(sortType: NftSortType) {
        _uiState.value = _uiState.value.copy(sortType = sortType)
        val sorted = when (sortType) {
            NftSortType.NAME -> _uiState.value.favoriteNfts.sortedBy { it.name }
            NftSortType.COLLECTION -> _uiState.value.favoriteNfts.sortedBy { it.collectionName }
            NftSortType.VALUE -> _uiState.value.favoriteNfts.sortedByDescending { it.estimatedValueUsd ?: 0.0 }
            NftSortType.RECENT -> _uiState.value.favoriteNfts.sortedByDescending { it.lastUpdated }
        }
        _uiState.value = _uiState.value.copy(filteredNfts = sorted)
    }
}

/**
 * NFT 收藏 UI 狀態
 */
data class NftFavoritesUiState(
    val favoriteNfts: List<NftItem> = emptyList(),
    val filteredNfts: List<NftItem> = emptyList(),
    val selectedNft: NftItem? = null,
    val selectedCollection: String? = null,
    val sortType: NftSortType = NftSortType.NAME,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

/**
 * NFT 排序類型
 */
enum class NftSortType {
    NAME,
    COLLECTION,
    VALUE,
    RECENT
}
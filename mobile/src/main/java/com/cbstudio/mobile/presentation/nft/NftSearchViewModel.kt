package com.cbstudio.mobile.presentation.nft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.domain.model.nft.NftItem
import com.cbstudio.wearwallet.core.domain.repository.NftRepository
import com.cbstudio.wearwallet.core.domain.usecase.nft.NftFavoritesManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * NFT 搜索 ViewModel
 */
class NftSearchViewModel(
    private val nftRepository: NftRepository,
    private val nftFavoritesManager: NftFavoritesManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(NftSearchUiState())
    val uiState: StateFlow<NftSearchUiState> = _uiState.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<NftItem>>(emptyList())
    val searchResults: StateFlow<List<NftItem>> = _searchResults.asStateFlow()
    
    private var searchJob: Job? = null
    
    /**
     * 搜索 NFT
     */
    fun searchNfts(query: String, chainType: com.cbstudio.wearwallet.core.domain.model.ChainType? = null) {
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                searchQuery = ""
            )
            return
        }
        
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            searchQuery = query,
            selectedChain = chainType
        )
        
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            
            try {
                val results = if (chainType != null) {
                    nftRepository.searchNftsByChain(query, chainType)
                } else {
                    nftRepository.searchNftsAsItems(query)
                }
                
                _searchResults.value = results
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = results,
                    errorMessage = if (results.isEmpty()) "找不到相關的 NFT" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "搜索時發生錯誤"
                )
            }
        }
    }
    
    /**
     * 按地址搜索 NFT
     */
    fun searchByAddress(walletAddress: String, chainType: com.cbstudio.wearwallet.core.domain.model.ChainType? = null) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            searchQuery = walletAddress
        )
        
        viewModelScope.launch {
            try {
                val nfts = if (chainType != null) {
                    nftRepository.getNftsByAddressAndChain(walletAddress, chainType)
                } else {
                    nftRepository.getNftsByAddress(walletAddress)
                }
                
                _searchResults.value = nfts
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = nfts,
                    errorMessage = if (nfts.isEmpty()) "該地址沒有 NFT" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "載入 NFT 時發生錯誤"
                )
            }
        }
    }
    
    /**
     * 按收藏品系列搜索
     */
    fun searchByCollection(collectionAddress: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                val nfts = nftRepository.getNftsByCollectionAsItems(collectionAddress)
                _searchResults.value = nfts
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = nfts,
                    errorMessage = if (nfts.isEmpty()) "該系列沒有 NFT" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "載入系列時發生錯誤"
                )
            }
        }
    }
    
    /**
     * 添加到收藏
     */
    fun addToFavorites(nft: NftItem) {
        nftFavoritesManager.addToFavorites(nft)
    }
    
    /**
     * 檢查是否為收藏
     */
    fun isFavorite(nft: NftItem): Boolean {
        return nftFavoritesManager.isFavorite(nft)
    }
    
    /**
     * 清除搜索結果
     */
    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _uiState.value = NftSearchUiState()
    }
    
    /**
     * 設置篩選條件
     */
    fun setFilter(filter: NftSearchFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyFilter()
    }
    
    private fun applyFilter() {
        val filter = _uiState.value.filter
        var filtered = _uiState.value.searchResults
        
        // 按價格範圍篩選
        filter.minPrice?.let { min ->
            filtered = filtered.filter { (it.estimatedValueUsd ?: 0.0) >= min }
        }
        filter.maxPrice?.let { max ->
            filtered = filtered.filter { (it.estimatedValueUsd ?: 0.0) <= max }
        }
        
        // 按收藏品系列篩選
        filter.collections?.let { collections ->
            if (collections.isNotEmpty()) {
                filtered = filtered.filter { it.collectionName in collections }
            }
        }
        
        // 按是否有動畫篩選
        filter.hasAnimation?.let { hasAnimation ->
            filtered = filtered.filter { it.isAnimated == hasAnimation }
        }
        
        _searchResults.value = filtered
    }
}

/**
 * NFT 搜索 UI 狀態
 */
data class NftSearchUiState(
    val searchQuery: String = "",
    val searchResults: List<NftItem> = emptyList(),
    val selectedChain: com.cbstudio.wearwallet.core.domain.model.ChainType? = null,
    val filter: NftSearchFilter = NftSearchFilter(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * NFT 搜索篩選條件
 */
data class NftSearchFilter(
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val collections: Set<String>? = null,
    val hasAnimation: Boolean? = null
)
package com.cbstudio.wearwallet.presentation.wallet.screens.nft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.nft.NftToken
import com.cbstudio.wearwallet.core.domain.model.nft.NftFilter
import com.cbstudio.wearwallet.core.domain.usecase.nft.GetNftsUseCase
import com.cbstudio.wearwallet.core.domain.usecase.nft.ManageNftsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * NFT ViewModel - KMP 架構實現
 * 使用 coreKmp UseCase 進行業務邏輯處理
 */
class NftViewModelKmp : ViewModel(), KoinComponent {

    // 注入 UseCase（來自 coreKmp）
    private val getNftsUseCase: GetNftsUseCase by inject()
    private val manageNftsUseCase: ManageNftsUseCase by inject()

    // UI 狀態
    data class NftUiState(
        val nfts: List<NftToken> = emptyList(),
        val filteredNfts: List<NftToken> = emptyList(),
        val favoriteNfts: List<NftToken> = emptyList(),
        val watchFaceNfts: List<NftToken> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val searchQuery: String = "",
        val selectedCollection: String? = null,
        val selectedChain: ChainType? = null,
        val currentWalletAddress: String? = null,
        val error: String? = null,
        val collections: List<String> = emptyList()
    )

    private val _uiState = MutableStateFlow(NftUiState())
    val uiState: StateFlow<NftUiState> = _uiState.asStateFlow()

    init {
        observeNfts()
    }

    /**
     * 設置當前錢包並載入 NFT
     */
    fun setCurrentWallet(walletAddress: String) {
        _uiState.update { it.copy(currentWalletAddress = walletAddress) }
        loadNfts(walletAddress)
    }

    /**
     * 載入 NFT 列表
     */
    fun loadNfts(walletAddress: String? = null) {
        val address = walletAddress ?: _uiState.value.currentWalletAddress
        if (address == null) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                val result = getNftsUseCase.getNftsByWallet(address)
                when (result) {
                    is Result.Success -> {
                        val nfts = result.data
                        val collections = nfts.map { it.collectionName }.distinct()
                        
                        _uiState.update { 
                            it.copy(
                                nfts = nfts,
                                filteredNfts = nfts,
                                collections = collections,
                                isLoading = false
                            )
                        }
                        
                        // 載入其他數據
                        loadFavoriteNfts()
                        loadWatchFaceNfts()
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "載入 NFT 失敗: ${result.exception.message}"
                            )
                        }
                    }
                    is Result.Loading -> {
                        // 保持載入狀態
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "載入 NFT 異常: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 觀察 NFT 變化
     */
    private fun observeNfts() {
        viewModelScope.launch {
            getNftsUseCase.observeAllNfts()
                .catch { e ->
                    _uiState.update { 
                        it.copy(error = "觀察 NFT 失敗: ${e.message}")
                    }
                }
                .collect { nfts ->
                    val currentAddress = _uiState.value.currentWalletAddress
                    if (currentAddress != null) {
                        val walletNfts = nfts.filter { it.ownerAddress == currentAddress }
                        _uiState.update { 
                            it.copy(
                                nfts = walletNfts,
                                filteredNfts = applyFilters(walletNfts)
                            )
                        }
                    }
                }
        }
    }

    /**
     * 搜索 NFT
     */
    fun searchNfts(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        viewModelScope.launch {
            try {
                val address = _uiState.value.currentWalletAddress
                if (address == null) return@launch
                
                val result = if (query.isBlank()) {
                    getNftsUseCase.getNftsByWallet(address)
                } else {
                    getNftsUseCase.searchNfts(address, query)
                }
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(filteredNfts = result.data)
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "搜索失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 搜索中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "搜索異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 按合集過濾
     */
    fun filterByCollection(collection: String?) {
        _uiState.update { it.copy(selectedCollection = collection) }
        
        viewModelScope.launch {
            try {
                val address = _uiState.value.currentWalletAddress
                if (address == null) return@launch
                
                val result = if (collection == null) {
                    getNftsUseCase.getNftsByWallet(address)
                } else {
                    getNftsUseCase.getNftsByCollection(address, collection)
                }
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(filteredNfts = result.data)
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "過濾失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 過濾中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "過濾異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 按區塊鏈過濾
     */
    fun filterByChain(chainType: ChainType?) {
        _uiState.update { it.copy(selectedChain = chainType) }
        
        viewModelScope.launch {
            try {
                val address = _uiState.value.currentWalletAddress
                if (address == null) return@launch
                
                val result = if (chainType == null) {
                    getNftsUseCase.getNftsByWallet(address)
                } else {
                    getNftsUseCase.getNftsByChain(address, chainType)
                }
                
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(filteredNfts = result.data)
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "過濾失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 過濾中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "過濾異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 切換收藏狀態
     */
    fun toggleFavorite(nftId: String) {
        viewModelScope.launch {
            try {
                val result = manageNftsUseCase.toggleFavorite(nftId)
                when (result) {
                    is Result.Success -> {
                        // 重新載入以更新狀態
                        loadFavoriteNfts()
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "切換收藏失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 切換中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "切換收藏異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 設置為 Watch Face
     */
    fun setAsWatchFace(nftId: String) {
        viewModelScope.launch {
            try {
                val result = manageNftsUseCase.setAsWatchFace(nftId)
                when (result) {
                    is Result.Success -> {
                        // 重新載入以更新狀態
                        loadWatchFaceNfts()
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "設置 Watch Face 失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 設置中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "設置 Watch Face 異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 標記 NFT 為隱藏
     */
    fun hideNft(nftId: String) {
        viewModelScope.launch {
            try {
                val result = manageNftsUseCase.hideNft(nftId)
                when (result) {
                    is Result.Success -> {
                        // 從當前列表移除
                        _uiState.update { state ->
                            state.copy(
                                nfts = state.nfts.filter { it.id != nftId },
                                filteredNfts = state.filteredNfts.filter { it.id != nftId }
                            )
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "隱藏 NFT 失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        // 隱藏中
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "隱藏 NFT 異常: ${e.message}")
                }
            }
        }
    }

    /**
     * 刷新 NFT 列表
     */
    fun refresh() {
        _uiState.value.currentWalletAddress?.let { address ->
            _uiState.update { it.copy(isRefreshing = true) }
            
            viewModelScope.launch {
                try {
                    val result = manageNftsUseCase.refreshNfts(address)
                    when (result) {
                        is Result.Success -> {
                            loadNfts(address)
                        }
                        is Result.Failure -> {
                            _uiState.update { 
                                it.copy(
                                    isRefreshing = false,
                                    error = "刷新失敗: ${result.exception.message}"
                                )
                            }
                        }
                        is Result.Loading -> {
                            // 刷新中
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { 
                        it.copy(
                            isRefreshing = false,
                            error = "刷新異常: ${e.message}"
                        )
                    }
                } finally {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    /**
     * 載入收藏 NFT
     */
    private fun loadFavoriteNfts() {
        viewModelScope.launch {
            try {
                val result = getNftsUseCase.getFavoriteNfts()
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(favoriteNfts = result.data)
                        }
                    }
                    is Result.Failure -> {
                        // 不影響主要功能，靜默失敗
                    }
                    is Result.Loading -> {
                        // 載入中
                    }
                }
            } catch (e: Exception) {
                // 不影響主要功能，靜默失敗
            }
        }
    }

    /**
     * 載入 Watch Face NFT
     */
    private fun loadWatchFaceNfts() {
        viewModelScope.launch {
            try {
                val result = getNftsUseCase.getWatchFaceNfts()
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(watchFaceNfts = result.data)
                        }
                    }
                    is Result.Failure -> {
                        // 不影響主要功能，靜默失敗
                    }
                    is Result.Loading -> {
                        // 載入中
                    }
                }
            } catch (e: Exception) {
                // 不影響主要功能，靜默失敗
            }
        }
    }

    /**
     * 應用過濾條件
     */
    private fun applyFilters(nfts: List<NftToken>): List<NftToken> {
        var filtered = nfts
        
        // 按合集過濾
        _uiState.value.selectedCollection?.let { collection ->
            filtered = filtered.filter { it.collectionName == collection }
        }
        
        // 按區塊鏈過濾
        _uiState.value.selectedChain?.let { chain ->
            filtered = filtered.filter { it.chainType == chain }
        }
        
        // 按搜索查詢過濾
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            filtered = filtered.filter { nft ->
                nft.name?.contains(query, ignoreCase = true) == true ||
                nft.collectionName.contains(query, ignoreCase = true) ||
                nft.description?.contains(query, ignoreCase = true) == true
            }
        }
        
        return filtered
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
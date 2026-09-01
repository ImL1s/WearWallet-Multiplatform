package com.cbstudio.wearwallet.presentation.wearfi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * WearFi NFTs 管理 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 最終編譯修復
 */
class WearFiNFTsViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All NFT services disabled
    
    private val _nfts = MutableStateFlow<List<String>>(emptyList())
    val nfts: StateFlow<List<String>> = _nfts.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    init {
        loadNFTs()
    }
    
    private fun loadNFTs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // MAINTENANCE MODE: No actual NFT loading
                _nfts.value = emptyList()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun refresh() {
        loadNFTs()
    }
    
    fun clearError() {
        _error.value = null
    }
}

/**
 * WearFi NFT 畫面狀態 - MAINTENANCE MODE
 */
data class WearFiNFTsState(
    val nfts: List<String> = emptyList(),
    val unlockedAchievements: List<String> = emptyList(),
    val lockedAchievements: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
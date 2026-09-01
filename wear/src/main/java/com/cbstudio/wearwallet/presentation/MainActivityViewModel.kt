package com.cbstudio.wearwallet.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
// MAINTENANCE MODE: Remove problematic imports

/**
 * MainActivityViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 修復 Chain 引用錯誤
 */
class MainActivityViewModel : ViewModel() {
    
    // MAINTENANCE MODE: Remove problematic imports and DI
    
    // Simple chain management with local type
    private val _currentChain = MutableStateFlow("Ethereum")
    val currentChain: StateFlow<String> = _currentChain.asStateFlow()
    
    /**
     * 切換當前使用的區塊鏈
     */
    fun setCurrentChain(chain: String) {
        _currentChain.value = chain
    }
    
    /**
     * 獲取支持的鏈列表
     */
    fun getSupportedChains(): List<String> {
        return listOf("Ethereum", "BSC", "Polygon")
    }
}

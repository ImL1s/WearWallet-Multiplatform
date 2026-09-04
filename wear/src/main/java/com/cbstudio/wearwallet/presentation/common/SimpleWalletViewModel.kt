package com.cbstudio.wearwallet.presentation.common

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 簡化錢包 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 18 - 最終語法修復
 */
class SimpleWalletViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All wallet services disabled
    
    private val _balance = MutableStateFlow("0.00")
    val balance: StateFlow<String> = _balance.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    fun refresh() {
        // MAINTENANCE MODE: No actual refresh
    }
    
    fun clearError() {
        _error.value = null
    }
}
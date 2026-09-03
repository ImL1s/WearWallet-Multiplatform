package com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 創建熱錢包 ViewModel - 簡化版本
 * ULTRATHINK Phase 13 - 激進清理後的最小化實現
 */
class CreateHotWalletViewModel : ViewModel() {
    
    // 基本狀態
    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    /**
     * 創建熱錢包 - 臨時實現
     */
    fun createWallet(walletName: String) {
        viewModelScope.launch {
            _isCreating.value = true
            try {
                // TODO: 使用 sharedKmp 的 WalletService 創建錢包
                _lastError.value = "錢包創建功能遷移到 KMP 中，即將可用"
            } catch (e: Exception) {
                _lastError.value = e.message ?: "創建錢包失敗"
            } finally {
                _isCreating.value = false
            }
        }
    }
    
    /**
     * 清除錯誤信息
     */
    fun clearError() {
        _lastError.value = null
    }
}
package com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 導入熱錢包 ViewModel - 簡化版本
 * ULTRATHINK Phase 13 - 激進清理後的最小化實現
 */
class ImportHotWalletViewModel : ViewModel() {
    
    // 基本狀態
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    
    private val _mnemonicText = MutableStateFlow("")
    val mnemonicText: StateFlow<String> = _mnemonicText.asStateFlow()
    
    /**
     * 更新助記詞文本
     */
    fun updateMnemonicText(text: String) {
        _mnemonicText.value = text
    }
    
    /**
     * 導入錢包 - 臨時實現
     */
    fun importWallet(mnemonic: String, walletName: String) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                // TODO: 使用 sharedKmp 的 WalletService 導入錢包
                if (mnemonic.isBlank()) {
                    _lastError.value = "請輸入助記詞"
                    return@launch
                }
                
                _lastError.value = "錢包導入功能遷移到 KMP 中，即將可用"
            } catch (e: Exception) {
                _lastError.value = e.message ?: "導入錢包失敗"
            } finally {
                _isImporting.value = false
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
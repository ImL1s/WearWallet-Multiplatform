package com.cbstudio.wearwallet.presentation.wallet.screens.settings.mnemonic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 助記詞顯示 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 19 - 設定畫面維護模式修復
 */
class MnemonicDisplayViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All mnemonic operations disabled
    private val _mnemonicWords = MutableStateFlow<List<String>>(emptyList())
    val mnemonicWords: StateFlow<List<String>> = _mnemonicWords.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isRevealed = MutableStateFlow(false)
    val isRevealed: StateFlow<Boolean> = _isRevealed.asStateFlow()
    
    fun loadMnemonic() {
        // MAINTENANCE MODE: Mnemonic loading disabled
        viewModelScope.launch {
            _isLoading.value = true
            _mnemonicWords.value = listOf("維護模式", "助記詞", "功能", "暫時", "停用")
            _errorMessage.value = "維護模式：助記詞顯示功能暫時停用"
            _isLoading.value = false
        }
    }
    
    fun toggleReveal() {
        _isRevealed.value = !_isRevealed.value
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
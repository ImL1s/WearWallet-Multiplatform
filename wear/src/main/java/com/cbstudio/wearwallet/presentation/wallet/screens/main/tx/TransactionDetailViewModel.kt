package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 交易詳情 ViewModel - MAINTENANCE MODE
 * ULTRATHINK Phase 19 - 交易管理維護模式修復
 */
class TransactionDetailViewModel : ViewModel() {
    
    // MAINTENANCE MODE: All transaction detail operations disabled
    private val _transactionDetail = MutableStateFlow<Map<String, String>>(emptyMap())
    val transactionDetail: StateFlow<Map<String, String>> = _transactionDetail.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    fun loadTransactionDetail(transactionId: String) {
        // MAINTENANCE MODE: Transaction loading disabled
        viewModelScope.launch {
            _isLoading.value = true
            _transactionDetail.value = mapOf(
                "status" to "維護模式",
                "message" to "交易詳情功能暫時停用"
            )
            _isLoading.value = false
        }
    }
    
    fun shareTransaction() {
        // MAINTENANCE MODE: Share disabled
        _errorMessage.value = "維護模式：分享功能暫時停用"
    }
    
    fun copyTransactionHash() {
        // MAINTENANCE MODE: Copy disabled
        _errorMessage.value = "維護模式：複製功能暫時停用"
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
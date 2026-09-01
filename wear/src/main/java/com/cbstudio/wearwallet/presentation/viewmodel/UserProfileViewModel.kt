package com.cbstudio.wearwallet.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 用戶資料 ViewModel - 簡化版本
 * ULTRATHINK Phase 13 - 激進清理後的最小化實現
 */
class UserProfileViewModel : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>("用戶資料功能遷移到 KMP 架構中，即將可用")
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _displayName = MutableStateFlow("Wallet User")
    val displayName: StateFlow<String> = _displayName.asStateFlow()
    
    fun loadUserProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: 使用 sharedKmp 的用戶資料服務
            _errorMessage.value = "用戶資料功能遷移到 KMP 架構中，即將可用"
            _isLoading.value = false
        }
    }
    
    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            _errorMessage.value = "用戶資料功能遷移到 KMP 架構中"
        }
    }
    
    fun exportUserData() {
        viewModelScope.launch {
            _errorMessage.value = "用戶資料匯出功能遷移到 KMP 架構中"
        }
    }
    
    fun deleteAllUserData() {
        viewModelScope.launch {
            _errorMessage.value = "用戶資料刪除功能遷移到 KMP 架構中"
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * 簡化版用戶資料模型
 */
data class UserProfile(
    val displayName: String = "Wallet User",
    val walletCount: Int = 0,
    val subscriptionStatus: String = "Free",
    val joinDate: String = ""
)
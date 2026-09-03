package com.cbstudio.wearwallet.presentation.wearfi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * WearFi Achievements ViewModel - 簡化版本
 * ULTRATHINK Phase 13 - 激進清理後的最小化實現
 */
class WearFiAchievementsViewModel : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>("WearFi 成就系統遷移到 KMP 架構中，即將可用")
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _achievements = MutableStateFlow<List<String>>(emptyList())
    val achievements: StateFlow<List<String>> = _achievements.asStateFlow()
    
    fun loadAchievements() {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: 使用 sharedKmp 的 WearFi 成就服務
            _errorMessage.value = "WearFi 成就系統遷移到 KMP 架構中，即將可用"
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}

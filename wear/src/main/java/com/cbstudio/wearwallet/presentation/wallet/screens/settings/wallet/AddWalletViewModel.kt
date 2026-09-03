package com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// @HiltViewModel  // Removed Hilt - Now using Koin
class AddWalletViewModel : ViewModel(), KoinComponent {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * 清除錯誤消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
} 

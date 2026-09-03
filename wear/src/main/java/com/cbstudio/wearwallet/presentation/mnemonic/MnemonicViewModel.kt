package com.cbstudio.wearwallet.presentation.mnemonic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Mnemonic ViewModel - 簡化版本
 * ULTRATHINK Phase 13 - 激進清理後的最小化實現
 */
class MnemonicViewModel : ViewModel() {

    data class MnemonicUiState(
        val mnemonicWords: List<String> = emptyList(),
        val isComplete: Boolean = false,
        val error: String? = "錢包創建功能遷移到 KMP 架構中，即將可用",
        val isLoading: Boolean = false,
        val isValidMnemonic: Boolean = false
    )

    private val _uiState = MutableStateFlow(MnemonicUiState())
    val uiState: StateFlow<MnemonicUiState> = _uiState.asStateFlow()

    fun createNewWallet() {
        viewModelScope.launch {
            // TODO: 使用 sharedKmp 的錢包創建服務
            _uiState.value = _uiState.value.copy(
                error = "錢包創建功能遷移到 KMP 架構中，即將可用",
                isLoading = false
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

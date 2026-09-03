package com.cbstudio.wearwallet.presentation.wallet.screens.main.token

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.ChainType

/**
 * 新增自訂代幣 ViewModel - MAINTENANCE MODE REMOVED
 * ULTRATHINK Phase 19 - 代幣管理維護模式修復
 */
data class AddCustomTokenUiState(
    val contractAddress: String = "",
    val symbol: String = "",
    val decimals: String = "18",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class AddCustomTokenViewModel(
    private val tokenRepository: TokenRepository,
    private val walletRepository: WalletRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AddCustomTokenUiState())
    val uiState: StateFlow<AddCustomTokenUiState> = _uiState.asStateFlow()

    private var currentChain: ChainType = ChainType.ETHEREUM

    fun updateChain(chain: String) {
        currentChain = ChainType.entries.find { 
            it.name.equals(chain, ignoreCase = true) || 
            it.displayName.equals(chain, ignoreCase = true) 
        } ?: ChainType.ETHEREUM
    }

    fun updateContractAddress(address: String) {
        _uiState.value = _uiState.value.copy(
            contractAddress = address,
            errorMessage = null
        )
    }

    fun updateSymbol(symbol: String) {
        _uiState.value = _uiState.value.copy(
            symbol = symbol,
            errorMessage = null
        )
    }

    fun updateDecimals(decimals: String) {
        _uiState.value = _uiState.value.copy(
            decimals = decimals,
            errorMessage = null
        )
    }

    fun addToken() {
        val state = _uiState.value
        if (state.contractAddress.isBlank()) {
            _uiState.value = state.copy(errorMessage = "請輸入合約地址")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            try {
                // Get Active Wallet
                val walletResult = walletRepository.getActiveWallet()
                if (walletResult is com.cbstudio.wearwallet.core.common.Result.Success && walletResult.data != null) {
                     val wallet = walletResult.data!!
                     
                     val newToken = Token(
                         address = state.contractAddress,
                         name = state.symbol, // Use symbol as name for custom
                         symbol = state.symbol,
                         decimals = state.decimals.toIntOrNull() ?: 18,
                         chainType = currentChain,
                         balance = "0" // Initial balance, will be fetched later
                     )
                     
                     tokenRepository.saveUserToken(wallet.address, newToken)
                     _uiState.value = state.copy(isLoading = false, isSuccess = true)
                } else {
                    _uiState.value = state.copy(isLoading = false, errorMessage = "無法獲取當前錢包")
                }
            } catch (e: Exception) {
                _uiState.value = state.copy(isLoading = false, errorMessage = e.message ?: "新增失敗")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun resetState() {
        _uiState.value = AddCustomTokenUiState()
    }
}

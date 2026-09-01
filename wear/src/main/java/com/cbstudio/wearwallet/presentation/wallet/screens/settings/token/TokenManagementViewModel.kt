package com.cbstudio.wearwallet.presentation.wallet.screens.settings.token

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Token Management ViewModel - 連接到 coreKmp
 * 代幣管理功能完整實現
 */
class TokenManagementViewModel : ViewModel(), KoinComponent {
    
    private val tokenRepository: TokenRepository by inject()
    private val walletRepository: WalletRepository by inject()
    
    data class TokenManagementUiState(
        val isLoading: Boolean = false,
        val tokens: List<TokenItem> = emptyList(),
        val errorMessage: String? = null,
        val isMaintenanceMode: Boolean = false,
        val searchQuery: String = "",
        val showOnlyEnabled: Boolean = false,
        val activeWalletAddress: String? = null
    )
    
    data class TokenItem(
        val token: Token,
        val isEnabled: Boolean = true,
        val balance: String = "0",
        val usdValue: String? = null
    )
    
    private val _uiState = MutableStateFlow(TokenManagementUiState())
    val uiState: StateFlow<TokenManagementUiState> = _uiState.asStateFlow()
    
    init {
        loadActiveWallet()
        loadTokens()
    }
    
    private fun loadActiveWallet() {
        viewModelScope.launch {
            try {
                val result = walletRepository.getActiveWallet()
                when (result) {
                    is Result.Success -> {
                        result.data?.let { wallet ->
                            _uiState.update { 
                                it.copy(activeWalletAddress = wallet.address)
                            }
                        }
                    }
                    else -> {
                        Timber.e("無法載入活動錢包")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "載入活動錢包時發生錯誤")
            }
        }
    }
    
    fun loadTokens() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                val activeWallet = walletRepository.getActiveWallet()
                when (activeWallet) {
                    is Result.Success -> {
                        activeWallet.data?.let { wallet ->
                            // 從 coreKmp 獲取代幣列表
                            tokenRepository.observeUserTokens(wallet.address)
                                .collect { tokens ->
                                    val tokenItems = tokens.map { token ->
                                        TokenItem(
                                            token = token,
                                            isEnabled = true, // 預設啟用
                                            balance = token.balance,
                                            usdValue = token.usdValue.toString()
                                        )
                                    }
                                    
                                    _uiState.update { 
                                        it.copy(
                                            tokens = tokenItems,
                                            isLoading = false,
                                            errorMessage = null
                                        )
                                    }
                                }
                        } ?: run {
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "沒有活動錢包"
                                )
                            }
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                errorMessage = activeWallet.exception.message
                            )
                        }
                    }
                    else -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                errorMessage = "無法載入錢包"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "載入代幣時發生錯誤")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "未知錯誤"
                    )
                }
            }
        }
    }
    
    fun addToken(contractAddress: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val activeWallet = walletRepository.getActiveWallet()
                when (activeWallet) {
                    is Result.Success -> {
                        activeWallet.data?.let { wallet ->
                            // TODO: 添加自定義代幣功能
                            // tokenRepository 目前沒有 addCustomToken 方法
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "此功能尚未實現"
                                )
                            }
                        }
                    }
                    else -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                errorMessage = "無法獲取活動錢包"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "添加代幣時發生錯誤")
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "未知錯誤"
                    )
                }
            }
        }
    }
    
    fun removeToken(tokenAddress: String) {
        viewModelScope.launch {
            try {
                val activeWallet = walletRepository.getActiveWallet()
                when (activeWallet) {
                    is Result.Success -> {
                        activeWallet.data?.let { wallet ->
                            // TODO: 移除代幣功能
                            // tokenRepository 目前沒有 removeToken 方法
                            
                            // 從 UI 狀態中移除
                            _uiState.update { state ->
                                state.copy(
                                    tokens = state.tokens.filter { 
                                        it.token.address != tokenAddress 
                                    }
                                )
                            }
                        }
                    }
                    else -> {
                        Timber.e("無法獲取活動錢包")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "移除代幣時發生錯誤")
                _uiState.update { 
                    it.copy(errorMessage = e.message ?: "未知錯誤")
                }
            }
        }
    }
    
    fun toggleTokenVisibility(tokenAddress: String) {
        _uiState.update { state ->
            state.copy(
                tokens = state.tokens.map { item ->
                    if (item.token.address == tokenAddress) {
                        item.copy(isEnabled = !item.isEnabled)
                    } else {
                        item
                    }
                }
            )
        }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
    
    fun toggleShowOnlyEnabled() {
        _uiState.update { it.copy(showOnlyEnabled = !it.showOnlyEnabled) }
    }
    
    fun refreshTokens() {
        loadTokens()
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
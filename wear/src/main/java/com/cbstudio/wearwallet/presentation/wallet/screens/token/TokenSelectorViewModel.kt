package com.cbstudio.wearwallet.presentation.wallet.screens.token

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.*
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.usecase.token.ScanTokensUseCase
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 代幣選擇 ViewModel
 */
class TokenSelectorViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val tokenRepository: TokenRepository by inject()
    private val scanTokensUseCase: ScanTokensUseCase by inject()
    
    data class TokenSelectorUiState(
        val activeWallet: WalletAccount? = null,
        val currentChain: ChainType = ChainType.ETHEREUM,
        val selectedToken: Token? = null,
        val tokens: List<Token> = emptyList(),
        val searchQuery: String = "",
        val isLoading: Boolean = false,
        val isScanning: Boolean = false,
        val error: String? = null
    )
    
    private val _uiState = MutableStateFlow(TokenSelectorUiState())
    val uiState: StateFlow<TokenSelectorUiState> = _uiState.asStateFlow()
    
    // 過濾後的代幣列表
    val filteredTokens: Flow<List<Token>> = uiState
        .map { state ->
            if (state.searchQuery.isEmpty()) {
                state.tokens
            } else {
                state.tokens.filter { token ->
                    token.symbol.contains(state.searchQuery, ignoreCase = true) ||
                    token.name.contains(state.searchQuery, ignoreCase = true)
                }
            }
        }
    
    init {
        // 從全局狀態管理器獲取當前鏈
        val initialChain = ChainStateManager.getCurrentChain()
        _uiState.update { it.copy(currentChain = initialChain) }
        
        loadActiveWallet()
        
        // 監聽鏈狀態變化
        viewModelScope.launch {
            ChainStateManager.currentChain.collect { chainType ->
                if (chainType != _uiState.value.currentChain) {
                    switchChain(chainType)
                }
            }
        }
    }
    
    private fun loadActiveWallet() {
        viewModelScope.launch {
            try {
                val result = walletRepository.getActiveWallet()
                when (result) {
                    is Result.Success -> {
                        result.data?.let { wallet ->
                            _uiState.update { 
                                it.copy(activeWallet = wallet)
                            }
                            loadTokens(wallet)
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "載入錢包失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "載入錢包失敗")
                _uiState.update { 
                    it.copy(error = "載入錢包時發生錯誤: ${e.message}")
                }
            }
        }
    }
    
    private fun loadTokens(wallet: WalletAccount) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                val currentChain = _uiState.value.currentChain
                
                // 檢查是否為 UTXO 鏈
                val isUTXOChain = currentChain in listOf(
                    ChainType.BITCOIN,
                    ChainType.LITECOIN,
                    ChainType.DOGECOIN,
                    ChainType.BITCOIN_CASH
                )
                
                val tokens = if (isUTXOChain) {
                    // UTXO 鏈只有原生代幣
                    listOf(
                        Token(
                            address = "native",
                            symbol = currentChain.nativeToken,
                            name = currentChain.displayName,
                            decimals = 8, // UTXO 鏈都是 8 位小數
                            balance = "0", // 需要從鏈上查詢
                            chainType = currentChain,
                            isNative = true
                        )
                    )
                } else {
                    // EVM 鏈載入 ERC-20 代幣
                    tokenRepository.scanUserTokens(
                        wallet.address,
                        currentChain
                    )
                }
                
                _uiState.update { 
                    it.copy(
                        tokens = tokens,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "載入代幣失敗")
                _uiState.update { 
                    it.copy(
                        tokens = emptyList(),
                        isLoading = false,
                        error = "載入代幣失敗: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun scanTokens() {
        viewModelScope.launch {
            _uiState.value.activeWallet?.let { wallet ->
                try {
                    _uiState.update { it.copy(isScanning = true) }
                    
                    val currentChain = _uiState.value.currentChain
                    
                    // 檢查是否為 UTXO 鏈
                    val isUTXOChain = currentChain in listOf(
                        ChainType.BITCOIN,
                        ChainType.LITECOIN,
                        ChainType.DOGECOIN,
                        ChainType.BITCOIN_CASH
                    )
                    
                    if (isUTXOChain) {
                        // UTXO 鏈不需要掃描代幣，只顯示原生幣
                        _uiState.update { 
                            it.copy(
                                tokens = listOf(
                                    Token(
                                        address = "native",
                                        symbol = currentChain.nativeToken,
                                        name = currentChain.displayName,
                                        decimals = 8,
                                        balance = "0", // 實際餘額需要從鏈上查詢
                                        chainType = currentChain,
                                        isNative = true
                                    )
                                ),
                                isScanning = false
                            )
                        }
                    } else {
                        // EVM 鏈掃描 ERC-20 代幣
                        scanTokensUseCase(
                            wallet.address,
                            currentChain
                        ).collect { result ->
                            when (result) {
                                is Result.Loading -> {
                                    // 保持掃描狀態
                                }
                                is Result.Success -> {
                                    _uiState.update { 
                                        it.copy(
                                            tokens = result.data,
                                            isScanning = false
                                        )
                                    }
                                }
                                is Result.Failure -> {
                                    _uiState.update { 
                                        it.copy(
                                            error = "掃描代幣失敗: ${result.exception.message}",
                                            isScanning = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "掃描代幣失敗")
                    _uiState.update { 
                        it.copy(
                            error = "掃描代幣時發生錯誤: ${e.message}",
                            isScanning = false
                        )
                    }
                }
            }
        }
    }
    
    fun selectToken(token: Token) {
        _uiState.update { 
            it.copy(selectedToken = token)
        }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { 
            it.copy(searchQuery = query)
        }
    }
    
    fun switchChain(chain: ChainType) {
        _uiState.update { 
            it.copy(
                currentChain = chain,
                tokens = emptyList(),
                selectedToken = null
            )
        }
        // 同步到全局鏈狀態管理器
        ChainStateManager.setCurrentChain(chain)
        
        _uiState.value.activeWallet?.let { loadTokens(it) }
    }
    
    fun clearError() {
        _uiState.update { 
            it.copy(error = null)
        }
    }
}
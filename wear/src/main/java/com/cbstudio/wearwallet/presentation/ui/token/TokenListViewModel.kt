package com.cbstudio.wearwallet.presentation.ui.token

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.tokens.TokenTransferManager
import com.cbstudio.wearwallet.core.domain.usecase.price.GetTokenPriceUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for token list screen
 */
class TokenListViewModel : ViewModel(), KoinComponent {
    
    private val tokenManager = TokenTransferManager()
    private val getTokenPriceUseCase: GetTokenPriceUseCase by inject()
    
    private val _tokens = MutableStateFlow<List<TokenTransferManager.TokenBalance>>(emptyList())
    val tokens: StateFlow<List<TokenTransferManager.TokenBalance>> = _tokens.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _totalValue = MutableStateFlow(0.0)
    val totalValue: StateFlow<Double> = _totalValue.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * Load tokens for the given wallet and chain
     */
    fun loadTokens(walletAddress: String, chainType: MultiChainType) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Get popular tokens for this chain
                val popularTokens = tokenManager.getPopularTokens(chainType)
                val tokenBalances = mutableListOf<TokenTransferManager.TokenBalance>()
                var totalUsdValue = 0.0
                
                // Query balance for each token
                // In production, this should be batched or use a multi-call contract
                for (token in popularTokens) {
                    val balanceResult = tokenManager.getTokenBalance(
                        chainType = chainType,
                        tokenAddress = token.contractAddress,
                        walletAddress = walletAddress
                    )
                    
                    when (balanceResult) {
                        is Result.Success -> {
                            val balance = balanceResult.data
                            if (balance.balance > 0) {
                                // 查詢真實價格
                                try {
                                    val priceResult = getTokenPriceUseCase.getPrice(token.symbol)
                                    if (priceResult is Result.Success) {
                                        val usdValue = balance.balance * priceResult.data
                                        val updatedBalance = balance.copy(usdValue = usdValue)
                                        tokenBalances.add(updatedBalance)
                                        totalUsdValue += usdValue
                                        Timber.d("Token ${token.symbol}: balance=${balance.balance}, price=$${priceResult.data}, value=$${usdValue}")
                                    } else {
                                        tokenBalances.add(balance)
                                        balance.usdValue?.let { totalUsdValue += it }
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to get price for ${token.symbol}")
                                    tokenBalances.add(balance)
                                    balance.usdValue?.let { totalUsdValue += it }
                                }
                            }
                        }
                        is Result.Failure -> {
                            // Log error but continue with other tokens
                            Timber.e("Failed to get balance for ${token.symbol}: ${balanceResult.exception}")
                        }
                        is Result.Loading -> {
                            // Skip loading states
                        }
                    }
                }
                
                // 排序並設定真實數據
                _tokens.value = tokenBalances.sortedByDescending { it.usdValue ?: 0.0 }
                _totalValue.value = totalUsdValue
                
                // 如果沒有代幣餘額，顯示空狀態（不再使用假數據）
                if (tokenBalances.isEmpty()) {
                    Timber.d("No token balances found for $chainType")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "載入代幣失敗")
                _error.value = "載入代幣失敗: ${e.message}"
                // 發生錯誤時清空列表，不再使用假數據
                _tokens.value = emptyList()
                _totalValue.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Transfer token
     */
    fun transferToken(
        token: TokenTransferManager.TokenInfo,
        fromAddress: String,
        toAddress: String,
        amount: Double,
        privateKey: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                val request = TokenTransferManager.TokenTransferRequest(
                    token = token,
                    fromAddress = fromAddress,
                    toAddress = toAddress,
                    amount = amount
                )
                
                val result = tokenManager.transferToken(request, privateKey)
                
                when (result) {
                    is Result.Success -> {
                        // Handle successful transfer
                        // Update balances
                        loadTokens(fromAddress, token.chainType)
                    }
                    is Result.Failure -> {
                        _error.value = "轉帳失敗: ${result.exception.message}"
                    }
                    is Result.Loading -> {
                        // Skip loading states
                    }
                }
            } catch (e: Exception) {
                _error.value = "轉帳失敗: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
}
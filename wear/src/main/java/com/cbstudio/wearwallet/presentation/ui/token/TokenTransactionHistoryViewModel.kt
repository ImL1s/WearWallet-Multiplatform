package com.cbstudio.wearwallet.presentation.ui.token

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import com.cbstudio.wearwallet.core.multichain.tokens.TokenTransferManager
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for token transaction history
 */
class TokenTransactionHistoryViewModel : ViewModel(), KoinComponent {
    
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase by inject()
    
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * Load transaction history for a specific token
     */
    fun loadTransactionHistory(
        token: TokenTransferManager.TokenInfo,
        walletAddress: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Convert MultiChainType to ChainType
                val chainType = multiChainToChainType(token.chainType)
                
                // Get transaction history
                getTransactionHistoryUseCase(
                    walletAddress = walletAddress,
                    chainType = chainType,
                    limit = 50
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            // Filter transactions for this specific token
                            val tokenTransactions = result.data?.filter { transaction ->
                                // 過濾與此代幣相關的交易
                                // 檢查交易是否涉及此代幣合約地址
                                transaction.tokenAddress?.equals(token.contractAddress, ignoreCase = true) == true ||
                                transaction.to?.equals(token.contractAddress, ignoreCase = true) == true
                            } ?: emptyList()
                            
                            _transactions.value = tokenTransactions
                            
                            if (tokenTransactions.isEmpty()) {
                                Timber.d("No transactions found for token ${token.symbol}")
                                // 不再使用假數據，顯示空列表
                            }
                        }
                        is Result.Failure -> {
                            Timber.e(result.exception, "Failed to load transaction history")
                            _error.value = "載入交易歷史失敗: ${result.exception.message}"
                            // 錯誤時顯示空列表，不再使用假數據
                            _transactions.value = emptyList()
                        }
                        is Result.Loading -> {
                            // Keep loading state
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading transaction history")
                _error.value = "載入交易歷史異常: ${e.message}"
                // 異常時顯示空列表，不再使用假數據
                _transactions.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Convert MultiChainType to ChainType
     */
    private fun multiChainToChainType(multiChainType: MultiChainType): ChainType {
        return when (multiChainType) {
            MultiChainType.ETHEREUM -> ChainType.ETHEREUM
            MultiChainType.BSC -> ChainType.BSC
            MultiChainType.POLYGON -> ChainType.POLYGON
            MultiChainType.ARBITRUM -> ChainType.ARBITRUM
            MultiChainType.OPTIMISM -> ChainType.OPTIMISM
            MultiChainType.AVALANCHE -> ChainType.AVALANCHE
            MultiChainType.CRONOS -> ChainType.CRONOS
            MultiChainType.FANTOM -> ChainType.FANTOM
            MultiChainType.BITCOIN -> ChainType.BITCOIN
            MultiChainType.LITECOIN -> ChainType.LITECOIN
            MultiChainType.DOGECOIN -> ChainType.DOGECOIN
            MultiChainType.BITCOIN_CASH -> ChainType.BITCOIN_CASH
            else -> ChainType.ETHEREUM // Default
        }
    }
    
}
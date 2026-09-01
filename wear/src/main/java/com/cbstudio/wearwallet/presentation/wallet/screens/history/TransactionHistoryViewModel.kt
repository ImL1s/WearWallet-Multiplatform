package com.cbstudio.wearwallet.presentation.wallet.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.*
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import com.cbstudio.wearwallet.presentation.wallet.screens.main.tx.TransactionFilter
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 交易歷史 ViewModel
 */
class TransactionHistoryViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase by inject()
    
    data class TransactionHistoryUiState(
        val activeWallet: WalletAccount? = null,
        val currentChain: ChainType = ChainType.ETHEREUM,
        val transactions: List<Transaction> = emptyList(),
        val filter: TransactionFilter = TransactionFilter.ALL,
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val hasMore: Boolean = false,
        val currentPage: Int = 0
    )
    
    private val _uiState = MutableStateFlow(TransactionHistoryUiState())
    val uiState: StateFlow<TransactionHistoryUiState> = _uiState.asStateFlow()
    
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
                            loadTransactions(wallet)
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
    
    private fun loadTransactions(wallet: WalletAccount, refresh: Boolean = false) {
        viewModelScope.launch {
            try {
                val currentChain = _uiState.value.currentChain
                
                // 移除 UTXO 限制，允許所有鏈類型載入歷史
                // UTXO 鏈檢查已在 RealTransactionRepository 中處理
                
                _uiState.update { 
                    it.copy(
                        isLoading = !refresh,
                        isRefreshing = refresh
                    )
                }
                
                // 載入交易記錄 - 使用真實的 UseCase
                var transactions = emptyList<Transaction>()
                
                Timber.d("開始載入交易記錄: address=${wallet.address}, chain=${currentChain.name}")
                
                getTransactionHistoryUseCase(
                    walletAddress = wallet.address,
                    chainType = currentChain,
                    limit = 20
                ).collect { result ->
                    when (result) {
                        is Result.Loading -> {
                            Timber.d("交易記錄載入中...")
                        }
                        is Result.Success -> {
                            transactions = result.data
                            Timber.d("交易記錄載入成功: ${transactions.size} 筆")
                        }
                        is Result.Failure -> {
                            Timber.e(result.exception, "交易記錄載入失敗")
                            throw result.exception
                        }
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        transactions = if (refresh) transactions else it.transactions + transactions,
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = transactions.size == 20,
                        currentPage = if (refresh) 1 else it.currentPage + 1
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "載入交易記錄失敗")
                _uiState.update { 
                    it.copy(
                        transactions = emptyList(),
                        isLoading = false,
                        isRefreshing = false,
                        error = "載入交易記錄失敗: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun refresh() {
        _uiState.value.activeWallet?.let { 
            loadTransactions(it, refresh = true)
        }
    }
    
    fun loadMore() {
        if (!_uiState.value.isLoading && _uiState.value.hasMore) {
            _uiState.value.activeWallet?.let { 
                loadTransactions(it, refresh = false)
            }
        }
    }
    
    fun switchChain(chain: ChainType) {
        _uiState.update { 
            it.copy(
                currentChain = chain,
                transactions = emptyList(),
                currentPage = 0
            )
        }
        // 同步到全局鏈狀態管理器
        ChainStateManager.setCurrentChain(chain)
        
        _uiState.value.activeWallet?.let { 
            loadTransactions(it, refresh = true)
        }
    }
    
    /**
     * 設定交易篩選器
     */
    fun setFilter(filter: TransactionFilter) {
        _uiState.update { 
            it.copy(filter = filter)
        }
    }
    
    /**
     * 刷新交易記錄
     */
    fun refreshTransactions() {
        refresh()
    }
    
    /**
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.update { 
            it.copy(error = null)
        }
    }
}
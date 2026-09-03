package com.cbstudio.wearwallet.presentation.wallet.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * 交易歷史 ViewModel - 完整實現
 * 
 * 功能：
 * 1. 載入交易歷史
 * 2. 下拉刷新
 * 3. 交易狀態顯示
 * 4. 交易詳情
 * 5. 自動更新 pending 交易
 * 
 * 只使用 coreKmp 的 Repository
 */
class TransactionHistoryViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val transactionRepository: TransactionRepository by inject()
    
    /**
     * UI 狀態
     */
    data class TransactionHistoryUiState(
        val activeWallet: WalletAccount? = null,
        val transactions: List<Transaction> = emptyList(),
        val groupedTransactions: Map<String, List<Transaction>> = emptyMap(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val selectedTransaction: Transaction? = null,
        val showTransactionDetail: Boolean = false
    )
    
    private val _uiState = MutableStateFlow(TransactionHistoryUiState())
    val uiState: StateFlow<TransactionHistoryUiState> = _uiState.asStateFlow()
    
    init {
        loadActiveWallet()
        startAutoRefresh()
    }
    
    /**
     * 載入活動錢包
     */
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
                            loadTransactionHistory(wallet)
                            observeTransactions(wallet)
                        } ?: run {
                            _uiState.update { 
                                it.copy(error = "沒有找到活動錢包")
                            }
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
                _uiState.update { 
                    it.copy(error = "載入錢包時發生錯誤: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 載入交易歷史
     */
    private suspend fun loadTransactionHistory(wallet: WalletAccount) {
        try {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // 從 Repository 獲取交易歷史
            val transactions = transactionRepository.getTransactionHistory(
                wallet.address,
                wallet.chainType
            )
            
            // 按日期分組
            val grouped = groupTransactionsByDate(transactions)
            
            _uiState.update { 
                it.copy(
                    transactions = transactions,
                    groupedTransactions = grouped,
                    isLoading = false
                )
            }
            
            Timber.d("載入 ${transactions.size} 筆交易")
            
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    error = "載入交易歷史失敗: ${e.message}"
                )
            }
            Timber.e(e, "載入交易歷史失敗")
        }
    }
    
    /**
     * 觀察交易變化（實時更新）
     */
    private fun observeTransactions(wallet: WalletAccount) {
        viewModelScope.launch {
            transactionRepository.observeTransactions(wallet.address)
                .catch { e ->
                    Timber.e(e, "觀察交易失敗")
                }
                .collect { transactions ->
                    val grouped = groupTransactionsByDate(transactions)
                    _uiState.update { 
                        it.copy(
                            transactions = transactions,
                            groupedTransactions = grouped
                        )
                    }
                }
        }
    }
    
    /**
     * 按日期分組交易
     */
    private fun groupTransactionsByDate(transactions: List<Transaction>): Map<String, List<Transaction>> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        return transactions
            .sortedByDescending { it.timestamp }
            .groupBy { tx ->
                tx.timestamp?.let { 
                    val date = Date(it.toEpochMilliseconds())
                    val today = Date()
                    val yesterday = Date(today.time - 24 * 60 * 60 * 1000)
                    
                    when {
                        dateFormat.format(date) == dateFormat.format(today) -> "今天"
                        dateFormat.format(date) == dateFormat.format(yesterday) -> "昨天"
                        else -> dateFormat.format(date)
                    }
                } ?: "待確認"
            }
    }
    
    /**
     * 刷新交易歷史
     */
    fun refreshTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            
            _uiState.value.activeWallet?.let { wallet ->
                loadTransactionHistory(wallet)
            }
            
            // 模擬延遲，讓用戶看到刷新動畫
            delay(500)
            
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
    
    /**
     * 自動刷新 pending 交易
     */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(10000) // 每 10 秒檢查一次
                
                val hasPendingTx = _uiState.value.transactions.any { 
                    it.status == TransactionStatus.PENDING 
                }
                
                if (hasPendingTx) {
                    _uiState.value.activeWallet?.let { wallet ->
                        updatePendingTransactions(wallet)
                    }
                }
            }
        }
    }
    
    /**
     * 更新 pending 交易狀態
     */
    private suspend fun updatePendingTransactions(wallet: WalletAccount) {
        try {
            val pendingTxs = _uiState.value.transactions.filter { 
                it.status == TransactionStatus.PENDING 
            }
            
            pendingTxs.forEach { tx ->
                try {
                    val updatedTx = transactionRepository.getTransaction(
                        tx.hash,
                        wallet.chainType
                    )
                    
                    updatedTx?.let {
                        if (it.status != TransactionStatus.PENDING) {
                            // 交易狀態已更新，重新載入列表
                            loadTransactionHistory(wallet)
                            return@forEach
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "更新交易 ${tx.hash} 失敗")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "更新 pending 交易失敗")
        }
    }
    
    /**
     * 顯示交易詳情
     */
    fun showTransactionDetail(transaction: Transaction) {
        _uiState.update { 
            it.copy(
                selectedTransaction = transaction,
                showTransactionDetail = true
            )
        }
    }
    
    /**
     * 關閉交易詳情
     */
    fun closeTransactionDetail() {
        _uiState.update { 
            it.copy(
                selectedTransaction = null,
                showTransactionDetail = false
            )
        }
    }
    
    /**
     * 格式化交易金額
     */
    fun formatAmount(amount: String, symbol: String = "ETH"): String {
        return try {
            val value = amount.toDoubleOrNull() ?: 0.0
            when {
                value >= 1 -> String.format("%.4f", value)
                value >= 0.01 -> String.format("%.6f", value)
                else -> String.format("%.8f", value)
            } + " $symbol"
        } catch (e: Exception) {
            "$amount $symbol"
        }
    }
    
    /**
     * 格式化地址（縮短顯示）
     */
    fun formatAddress(address: String): String {
        return if (address.length > 10) {
            "${address.take(6)}...${address.takeLast(4)}"
        } else {
            address
        }
    }
    
    /**
     * 獲取交易狀態顏色
     */
    fun getStatusColor(status: TransactionStatus): androidx.compose.ui.graphics.Color {
        return when (status) {
            TransactionStatus.CONFIRMED -> androidx.compose.ui.graphics.Color.Green
            TransactionStatus.PENDING -> androidx.compose.ui.graphics.Color.Yellow
            TransactionStatus.FAILED -> androidx.compose.ui.graphics.Color.Red
            TransactionStatus.DROPPED -> androidx.compose.ui.graphics.Color.Gray
            else -> androidx.compose.ui.graphics.Color.Gray
        }
    }
    
    /**
     * 獲取交易狀態文字
     */
    fun getStatusText(status: TransactionStatus): String {
        return when (status) {
            TransactionStatus.CONFIRMED -> "已確認"
            TransactionStatus.PENDING -> "待確認"
            TransactionStatus.FAILED -> "失敗"
            TransactionStatus.DROPPED -> "已取消"
            TransactionStatus.REPLACED -> "已替換"
            TransactionStatus.CANCELLED -> "已取消"
            TransactionStatus.SEND -> "發送中"
        }
    }
    
    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
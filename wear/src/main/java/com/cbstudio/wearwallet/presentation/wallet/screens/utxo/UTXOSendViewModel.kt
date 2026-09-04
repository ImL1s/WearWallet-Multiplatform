package com.cbstudio.wearwallet.presentation.wallet.screens.utxo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.*
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.utxo.SendUTXOTransactionUseCase
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * UTXO 鏈發送 ViewModel
 * 處理 Bitcoin, Litecoin, Dogecoin, Bitcoin Cash 的交易發送
 */
class UTXOSendViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val utxoApiClient: UTXOApiClient by inject()
    private val sendUTXOTransactionUseCase: SendUTXOTransactionUseCase by inject()
    private val transactionService = UTXOTransactionService()
    
    /**
     * 手續費等級
     */
    enum class FeeLevel(val displayName: String, val description: String, val blocks: Int) {
        SLOW("慢速", "約 6 小時", 24),
        NORMAL("正常", "約 1 小時", 6),
        FAST("快速", "約 10 分鐘", 2),
        URGENT("緊急", "下個區塊", 1)
    }
    
    /**
     * UI 狀態
     */
    data class UTXOSendState(
        val chainType: ChainType = ChainType.BITCOIN,
        val walletAddress: String = "",
        val balance: Long = 0L, // satoshis
        val recipientAddress: String = "",
        val amount: String = "",
        val estimatedFee: Long = 0L,
        val selectedFeeLevel: FeeLevel = FeeLevel.NORMAL,
        val isLoading: Boolean = false,
        val error: String? = null,
        val transactionHash: String? = null,
        val showAddressInput: Boolean = false,
        val showAmountInput: Boolean = false,
        val showFeeOptions: Boolean = false,
        val showConfirmation: Boolean = false,
        val isValid: Boolean = false
    )
    
    private val _uiState = MutableStateFlow(UTXOSendState())
    val uiState: StateFlow<UTXOSendState> = _uiState.asStateFlow()
    
    /**
     * 初始化鏈類型
     */
    fun initializeChain(chainType: ChainType) {
        _uiState.update { it.copy(chainType = chainType) }
        loadWalletInfo()
        estimateFee()
    }
    
    /**
     * 載入錢包資訊
     */
    private fun loadWalletInfo() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                // 獲取活動錢包
                val result = walletRepository.getActiveWallet()
                when (result) {
                    is Result.Success -> {
                        val wallet = result.data
                        if (wallet != null) {
                            _uiState.update { state ->
                                state.copy(
                                    walletAddress = wallet.address,
                                    isLoading = false
                                )
                            }
                            loadBalance(wallet.address)
                        } else {
                            _uiState.update { 
                                it.copy(
                                    error = "沒有找到活動錢包",
                                    isLoading = false
                                )
                            }
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                error = "載入錢包失敗: ${result.exception.message}",
                                isLoading = false
                            )
                        }
                    }
                    is Result.Loading -> {
                        // 保持載入狀態
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "載入錢包異常: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * 載入餘額 - 使用真實 API 查詢
     */
    private fun loadBalance(address: String) {
        viewModelScope.launch {
            try {
                // 使用 UTXOApiClient 獲取真實餘額
                val balance = utxoApiClient.getBalance(address, _uiState.value.chainType)
                Timber.d("UTXO 餘額查詢成功: ${_uiState.value.chainType} = $balance satoshis")
                
                _uiState.update { it.copy(balance = balance) }
                validateTransaction()
            } catch (e: Exception) {
                Timber.e(e, "載入餘額失敗")
                _uiState.update { it.copy(balance = 0L) }
            }
        }
    }
    
    /**
     * 估算手續費
     */
    private fun estimateFee() {
        viewModelScope.launch {
            try {
                // 根據鏈類型和手續費等級估算
                val baseFee = when (_uiState.value.chainType) {
                    ChainType.BITCOIN -> when (_uiState.value.selectedFeeLevel) {
                        FeeLevel.SLOW -> 5L
                        FeeLevel.NORMAL -> 10L
                        FeeLevel.FAST -> 20L
                        FeeLevel.URGENT -> 50L
                    }
                    ChainType.LITECOIN -> when (_uiState.value.selectedFeeLevel) {
                        FeeLevel.SLOW -> 1L
                        FeeLevel.NORMAL -> 2L
                        FeeLevel.FAST -> 5L
                        FeeLevel.URGENT -> 10L
                    }
                    ChainType.DOGECOIN -> when (_uiState.value.selectedFeeLevel) {
                        FeeLevel.SLOW -> 100L
                        FeeLevel.NORMAL -> 500L
                        FeeLevel.FAST -> 1000L
                        FeeLevel.URGENT -> 5000L
                    }
                    ChainType.BITCOIN_CASH -> when (_uiState.value.selectedFeeLevel) {
                        FeeLevel.SLOW -> 1L
                        FeeLevel.NORMAL -> 2L
                        FeeLevel.FAST -> 5L
                        FeeLevel.URGENT -> 10L
                    }
                    else -> 10L
                }
                
                _uiState.update { it.copy(estimatedFee = baseFee) }
            } catch (e: Exception) {
                Timber.e(e, "估算手續費失敗")
            }
        }
    }
    
    /**
     * 驗證交易
     */
    private fun validateTransaction() {
        val state = _uiState.value
        
        // 驗證地址
        val isValidAddress = state.recipientAddress.isNotEmpty() && when (state.chainType) {
            ChainType.BITCOIN -> state.recipientAddress.startsWith("1") || 
                                 state.recipientAddress.startsWith("3") || 
                                 state.recipientAddress.startsWith("bc1")
            ChainType.LITECOIN -> state.recipientAddress.startsWith("L") || 
                                  state.recipientAddress.startsWith("M") || 
                                  state.recipientAddress.startsWith("ltc1")
            ChainType.DOGECOIN -> state.recipientAddress.startsWith("D") || 
                                  state.recipientAddress.startsWith("9") || 
                                  state.recipientAddress.startsWith("A")
            ChainType.BITCOIN_CASH -> state.recipientAddress.startsWith("1") || 
                                      state.recipientAddress.startsWith("3") || 
                                      state.recipientAddress.startsWith("bitcoincash:")
            else -> false
        }
        
        // 驗證金額
        val amountSatoshi = try {
            (state.amount.toDouble() * 100_000_000).toLong()
        } catch (e: Exception) {
            0L
        }
        
        val isValidAmount = amountSatoshi > 0 && 
                           amountSatoshi <= state.balance - (state.estimatedFee * 250) // 預估交易大小 250 bytes
        
        _uiState.update { 
            it.copy(isValid = isValidAddress && isValidAmount)
        }
    }
    
    /**
     * 更新接收地址
     */
    fun updateRecipientAddress(address: String) {
        _uiState.update { it.copy(recipientAddress = address) }
        validateTransaction()
    }
    
    /**
     * 更新金額
     */
    fun updateAmount(amount: String) {
        _uiState.update { it.copy(amount = amount) }
        validateTransaction()
    }
    
    /**
     * 選擇手續費等級
     */
    fun selectFeeLevel(level: FeeLevel) {
        _uiState.update { it.copy(selectedFeeLevel = level) }
        estimateFee()
        validateTransaction()
    }
    
    /**
     * 顯示地址輸入
     */
    fun showAddressInput() {
        _uiState.update { it.copy(showAddressInput = true) }
    }
    
    /**
     * 隱藏地址輸入
     */
    fun hideAddressInput() {
        _uiState.update { it.copy(showAddressInput = false) }
    }
    
    /**
     * 顯示金額輸入
     */
    fun showAmountInput() {
        _uiState.update { it.copy(showAmountInput = true) }
    }
    
    /**
     * 隱藏金額輸入
     */
    fun hideAmountInput() {
        _uiState.update { it.copy(showAmountInput = false) }
    }
    
    /**
     * 顯示手續費選項
     */
    fun showFeeOptions() {
        _uiState.update { it.copy(showFeeOptions = true) }
    }
    
    /**
     * 隱藏手續費選項
     */
    fun hideFeeOptions() {
        _uiState.update { it.copy(showFeeOptions = false) }
    }
    
    /**
     * 確認交易
     */
    fun confirmTransaction() {
        _uiState.update { it.copy(showConfirmation = true) }
    }
    
    /**
     * 隱藏確認
     */
    fun hideConfirmation() {
        _uiState.update { it.copy(showConfirmation = false) }
    }
    
    /**
     * 發送交易。必須帶有效 SIGN 授權；密碼以 CharArray 傳入並在結束後清零。
     * 成功哈希不代表鏈上確認。
     */
    fun sendTransaction(password: CharArray, authContext: AuthenticationContext?) {
        viewModelScope.launch {
            try {
                val handle = authContext?.authHandle
                if (handle == null || !handle.isValid(expectedOperation = AuthOperation.SIGN)) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "需要有效授權才能發送",
                            showConfirmation = false
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(isLoading = true, error = null, showConfirmation = false) }
                
                val state = _uiState.value
                val amountSatoshi = (state.amount.toDouble() * 100_000_000).toLong()
                
                // 根據手續費等級獲取手續費率
                val feeRate = when (state.selectedFeeLevel) {
                    FeeLevel.SLOW -> 5L
                    FeeLevel.NORMAL -> 10L
                    FeeLevel.FAST -> 20L
                    FeeLevel.URGENT -> 50L
                }

                val passwordString = String(password)
                
                // 使用 UseCase 發送交易
                sendUTXOTransactionUseCase(
                    toAddress = state.recipientAddress,
                    amount = amountSatoshi,
                    chainType = state.chainType,
                    feeRate = feeRate,
                    password = passwordString
                ).collect { result ->
                    when (result) {
                        is Result.Loading -> {
                            _uiState.update { it.copy(isLoading = true) }
                        }
                        is Result.Success -> {
                            val txHash = result.data
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    transactionHash = txHash
                                )
                            }
                            Timber.d("交易已送出（未證明鏈上確認）: $txHash")
                            
                            // 交易成功後刷新餘額
                            loadBalance(state.walletAddress)
                        }
                        is Result.Failure -> {
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    error = "發送交易失敗: ${result.exception.message}"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "發送交易失敗: ${e.message}"
                    )
                }
            } finally {
                password.fill('\u0000')
            }
        }
    }
    
    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * 清除交易哈希
     */
    fun clearTransactionHash() {
        _uiState.update { it.copy(transactionHash = null) }
    }
    
    /**
     * 格式化餘額
     */
    fun formatBalance(): String {
        val balance = _uiState.value.balance
        val btc = balance.toDouble() / 100_000_000
        return when (_uiState.value.chainType) {
            ChainType.BITCOIN -> String.format("%.8f", btc)
            ChainType.LITECOIN -> String.format("%.8f", btc)
            ChainType.DOGECOIN -> String.format("%.2f", btc)
            ChainType.BITCOIN_CASH -> String.format("%.8f", btc)
            else -> "0"
        }
    }
}
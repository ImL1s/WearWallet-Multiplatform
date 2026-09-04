package com.cbstudio.wearwallet.presentation.wallet.screens.bitcoin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.usecase.bitcoin.SendBitcoinTransactionUseCase
import com.cbstudio.wearwallet.core.blockchain.adapter.BitcoinPlatformAdapter
import com.cbstudio.wearwallet.core.blockchain.api.BlockstreamApiClient
import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.domain.model.Network
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
// BigInteger replaced with Long for KMP compatibility

/**
 * Bitcoin 錢包 ViewModel
 * 處理 Bitcoin 相關的 UI 邏輯
 */
class BitcoinWalletViewModel : ViewModel(), KoinComponent {
    
    private val sendBitcoinTransactionUseCase: SendBitcoinTransactionUseCase by inject()
    private val bitcoinAdapter: BitcoinPlatformAdapter by inject()
    private val blockstreamApiClient: BlockstreamApiClient by inject()
    private val walletRepository: WalletRepository by inject()
    
    data class BitcoinWalletState(
        val isLoading: Boolean = false,
        val address: String = "",
        val balance: Long = 0L,
        val utxos: List<UTXO> = emptyList(),
        val estimatedFee: Long = 0L,
        val selectedNetwork: Network = Network.BITCOIN_MAINNET,
        val error: String? = null,
        val transactionHash: String? = null,
        val showSendDialog: Boolean = false,
        val recipientAddress: String = "",
        val sendAmount: String = ""
    )
    
    private val _uiState = MutableStateFlow(BitcoinWalletState())
    val uiState: StateFlow<BitcoinWalletState> = _uiState.asStateFlow()
    
    /**
     * 初始化 Bitcoin 錢包
     */
    fun initializeWallet(walletId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                // 獲取錢包資訊
                val walletsResult = walletRepository.getAllWallets()
                if (walletsResult is Result.Success) {
                    val wallet = walletsResult.data.find { it.id == walletId }
                    if (wallet != null) {
                        _uiState.update { it.copy(address = wallet.address) }
                        
                        // 查詢餘額和 UTXOs
                        refreshBalance()
                    } else {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "找不到錢包"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "初始化錢包失敗: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 刷新餘額和 UTXOs
     */
    fun refreshBalance() {
        viewModelScope.launch {
            try {
                val address = _uiState.value.address
                if (address.isEmpty()) return@launch
                
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                // 獲取餘額
                val balance = bitcoinAdapter.getBalance(address)
                
                // 獲取 UTXOs
                val utxos = blockstreamApiClient.getUtxos(address)
                
                // 獲取手續費估算
                val feeEstimates = blockstreamApiClient.getFeeEstimates()
                val mediumFee = feeEstimates["medium"] ?: 10.0
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        balance = balance,
                        utxos = utxos,
                        estimatedFee = (mediumFee * 250).toLong() // 預估 250 bytes 交易
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "刷新餘額失敗: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 切換網路（主網/測試網）
     */
    fun switchNetwork(isTestnet: Boolean) {
        val network = if (isTestnet) Network.BITCOIN_TESTNET else Network.BITCOIN_MAINNET
        _uiState.update { it.copy(selectedNetwork = network) }
        
        // 更新適配器網路設定
        bitcoinAdapter.currentNetwork = network
        
        // 刷新餘額
        refreshBalance()
    }
    
    /**
     * 顯示發送對話框
     */
    fun showSendDialog() {
        _uiState.update { it.copy(showSendDialog = true) }
    }
    
    /**
     * 隱藏發送對話框
     */
    fun hideSendDialog() {
        _uiState.update { 
            it.copy(
                showSendDialog = false,
                recipientAddress = "",
                sendAmount = "",
                error = null
            )
        }
    }
    
    /**
     * 更新接收地址
     */
    fun updateRecipientAddress(address: String) {
        _uiState.update { it.copy(recipientAddress = address) }
    }
    
    /**
     * 更新發送金額
     */
    fun updateSendAmount(amount: String) {
        _uiState.update { it.copy(sendAmount = amount) }
    }
    
    /**
     * 發送 Bitcoin 交易
     */
    fun sendTransaction(password: String) {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                
                // 驗證輸入
                if (!bitcoinAdapter.validateAddress(state.recipientAddress)) {
                    _uiState.update { it.copy(error = "無效的接收地址") }
                    return@launch
                }
                
                val amountSatoshi = try {
                    (state.sendAmount.toDouble() * 100_000_000).toLong()
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "無效的金額") }
                    return@launch
                }
                
                if (amountSatoshi <= 0) {
                    _uiState.update { it.copy(error = "金額必須大於0") }
                    return@launch
                }
                
                if (amountSatoshi > state.balance) {
                    _uiState.update { it.copy(error = "餘額不足") }
                    return@launch
                }
                
                _uiState.update { it.copy(isLoading = true, error = null) }
                
                // 獲取當前活動錢包
                val activeWalletResult = walletRepository.getActiveWallet()
                if (activeWalletResult !is Result.Success) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "無法獲取活動錢包"
                        )
                    }
                    return@launch
                }
                
                // 執行發送交易
                sendBitcoinTransactionUseCase.execute(
                    toAddress = state.recipientAddress,
                    amount = amountSatoshi,
                    network = state.selectedNetwork
                ).collect { result ->
                    when (result) {
                        is Result.Loading -> {
                            _uiState.update { it.copy(isLoading = true) }
                        }
                        is Result.Success -> {
                            val txResult = result.data
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    showSendDialog = false,
                                    transactionHash = txResult.txHash,
                                    recipientAddress = "",
                                    sendAmount = ""
                                )
                            }
                            // 交易成功後刷新餘額
                            refreshBalance()
                        }
                        is Result.Failure -> {
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    error = "交易失敗: ${result.exception.message}"
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
     * 格式化餘額為 BTC
     */
    fun formatBalanceAsBTC(): String {
        val balance = _uiState.value.balance
        val btc = balance.toDouble() / 100_000_000
        return String.format("%.8f BTC", btc)
    }
    
    /**
     * 格式化餘額為 USD（需要價格數據）
     */
    fun formatBalanceAsUSD(btcPrice: Double): String {
        val balance = _uiState.value.balance
        val btc = balance.toDouble() / 100_000_000
        val usd = btc * btcPrice
        return String.format("$%.2f USD", usd)
    }
}
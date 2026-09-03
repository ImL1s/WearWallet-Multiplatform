package com.cbstudio.wearwallet.presentation.wallet.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.*
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.usecase.token.ScanTokensUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.GetTransactionHistoryUseCase
import com.cbstudio.wearwallet.core.domain.usecase.price.GetTokenPriceUseCase
import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.math.BigDecimal

/**
 * WalletMainViewModel - 連接到 coreKmp 的真實實現
 */
class WalletMainViewModel : ViewModel(), KoinComponent {

    // 注入 repositories 和 use cases
    private val walletRepository: WalletRepository by inject()
    private val tokenRepository: TokenRepository by inject()
    private val scanTokensUseCase: ScanTokensUseCase by inject()
    private val getTransactionHistoryUseCase: GetTransactionHistoryUseCase by inject()
    private val getTokenPriceUseCase: GetTokenPriceUseCase by inject()
    
    // UTXO 鏈 API 客戶端
    private val utxoApiClient: UTXOApiClient by inject()

    // UI 狀態
    data class WalletUiState(
        val currentWallet: WalletAccount? = null,
        val walletCount: Int = 0,
        val currentChain: ChainType = ChainType.ETHEREUM,
        val currentMultiChain: MultiChainType = MultiChainType.ETHEREUM,
        val nativeBalance: Double = 0.0,
        val nativeBalanceUsd: String = "$0.00",
        val tokens: List<Token> = emptyList(),
        val tokenCount: Int = 0,
        val tokensTotalValue: BigDecimal = BigDecimal.ZERO,
        val transactions: List<Transaction> = emptyList(),
        val isLoading: Boolean = false,
        val isScanningTokens: Boolean = false,
        val error: String? = null,
        val currentTokenPrice: Double? = null  // 價格將從 API 獲取
    )

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        // 從全局狀態管理器獲取當前鏈（初始值，後續會被 wallet 的鏈過寫）
        val initialChain = ChainStateManager.getCurrentChain()
        _uiState.update { it.copy(currentChain = initialChain) }
        
        loadWallets()
        observeActiveWallet()
        observeChainChanges()
    }

    /**
     * 載入錢包列表
     */
    private fun loadWallets() {
        viewModelScope.launch {
            try {
                val result = walletRepository.getAllWallets()
                when (result) {
                    is Result.Success -> {
                        val wallets = result.data
                        _uiState.update { it.copy(walletCount = wallets.size) }
                        
                        // 如果沒有活躍錢包，設置第一個為活躍
                        if (wallets.isNotEmpty()) {
                            val activeResult = walletRepository.getActiveWallet()
                            if (activeResult is Result.Success && activeResult.data == null) {
                                walletRepository.setActiveWallet(wallets.first().id)
                            }
                        }
                    }
                    is Result.Failure -> {
                        Timber.e(result.exception, "載入錢包失敗")
                        _uiState.update { it.copy(error = "載入錢包失敗: ${result.exception.message}") }
                    }
                    is Result.Loading -> {
                        // 載入中狀態
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "載入錢包失敗")
                _uiState.update { it.copy(error = "載入錢包失敗: ${e.message}") }
            }
        }
    }

    /**
     * 觀察活躍錢包變化
     */
    private fun observeActiveWallet() {
        viewModelScope.launch {
            walletRepository.observeActiveWallet()
                .filterNotNull()
                .distinctUntilChanged()
                .collect { wallet ->
                    _uiState.update { it.copy(currentWallet = wallet) }
                    
                    // 從錢包讀取持久化的鏈設定（冷啟動時確保正確鏈）
                    val walletChain = wallet.chainType
                    val currentChain = _uiState.value.currentChain
                    if (walletChain != currentChain) {
                        Timber.d("從錢包同步鏈: ${walletChain.displayName} (原: ${currentChain.displayName})")
                        val multiChainType = chainTypeToMultiChainType(walletChain)
                        _uiState.update {
                            it.copy(
                                currentChain = walletChain,
                                currentMultiChain = multiChainType
                            )
                        }
                        ChainStateManager.setCurrentChain(walletChain)
                    }
                    
                    loadWalletData(wallet)
                }
        }
    }
    
    /**
     * 觀察 ChainStateManager 變化（運行時切換鏈）
     */
    private fun observeChainChanges() {
        viewModelScope.launch {
            ChainStateManager.currentChain
                .collect { chain ->
                    val currentChain = _uiState.value.currentChain
                    if (chain != currentChain) {
                        Timber.d("鏈變更動態更新: ${chain.displayName}")
                        switchChain(chain)
                    }
                }
        }
    }

    /**
     * 載入錢包數據
     */
    private fun loadWalletData(wallet: WalletAccount) {
        val currentChain = _uiState.value.currentChain
        val isUTXOChain = currentChain in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        loadBalance(wallet)
        
        // UTXO 鏈不需要載入代幣（只有原生幣）
        if (!isUTXOChain) {
            loadTokens(wallet)
        } else {
            // UTXO 鏈清空代幣列表
            _uiState.update { it.copy(tokens = emptyList()) }
        }
        
        loadTransactions(wallet)
    }

    /**
     * 載入餘額
     */
    private fun loadBalance(wallet: WalletAccount) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                kotlinx.coroutines.withTimeout(30000L) {
                    val currentChain = _uiState.value.currentChain
                    
                    // 檢查是否為 UTXO 鏈
                    val isUTXOChain = currentChain in listOf(
                        ChainType.BITCOIN,
                        ChainType.LITECOIN,
                        ChainType.DOGECOIN,
                        ChainType.BITCOIN_CASH
                    )
                    
                    // 獲取原生代幣餘額
                    var balance = if (isUTXOChain) {
                        // 使用 UTXOApiClient 獲取真實餘額
                        try {
                            val satoshis = utxoApiClient.getBalance(wallet.address, currentChain)
                            // 將 satoshis 轉換為主單位 (BTC/LTC/DOGE/BCH)
                            val divisor = when (currentChain) {
                                ChainType.BITCOIN, ChainType.LITECOIN, ChainType.BITCOIN_CASH -> 100_000_000.0 // 8 decimals
                                ChainType.DOGECOIN -> 100_000_000.0 // 8 decimals
                                else -> 100_000_000.0
                            }
                            Timber.d("UTXO 鏈 ${currentChain.displayName} 真實餘額: $satoshis satoshis = ${satoshis / divisor}")
                            satoshis / divisor
                        } catch (e: Exception) {
                            Timber.e(e, "獲取 UTXO 餘額失敗")
                            0.0
                        }
                    } else {
                        // EVM 鏈使用原有方法
                        walletRepository.getNativeBalance(
                            wallet.address,
                            currentChain
                        )
                    }
                    
                    // SAFETY CHECK: 如果餘額非常大（> 10^12），則假設它是 Wei 單位，強制轉換
                    // 這是為了防止 Repository 返回未縮放的 Wei 值
                    if (balance > 1_000_000_000_000.0 && !isUTXOChain) {
                        Timber.w("Detected potential raw Wei balance: $balance, scaling down by 10^18")
                        balance = balance / 1_000_000_000_000_000_000.0
                    }
                    
                    // 獲取真實代幣價格
                    val tokenSymbol = when (currentChain) {
                        ChainType.BITCOIN -> "BTC"
                        ChainType.LITECOIN -> "LTC"
                        ChainType.DOGECOIN -> "DOGE"
                        ChainType.BITCOIN_CASH -> "BCH"
                        ChainType.ETHEREUM -> "ETH"
                        ChainType.BSC -> "BNB"
                        ChainType.POLYGON -> "MATIC"
                        ChainType.AVALANCHE -> "AVAX"
                        ChainType.FANTOM -> "FTM"
                        ChainType.ARBITRUM -> "ETH"
                        ChainType.OPTIMISM -> "ETH"
                        else -> "ETH"
                    }
                    
                    
                    val tokenPrice = try {
                        val priceResult = getTokenPriceUseCase.getPrice(tokenSymbol)
                        when (priceResult) {
                            is Result.Success -> priceResult.data
                            else -> _uiState.value.currentTokenPrice ?: 0.0
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "獲取 $tokenSymbol 價格失敗")
                        _uiState.value.currentTokenPrice ?: 0.0
                    }
                    
                    val usdValue = balance * tokenPrice
                    
                    _uiState.update { 
                        it.copy(
                            nativeBalance = balance,
                            nativeBalanceUsd = "$%.2f".format(usdValue),
                            currentTokenPrice = tokenPrice,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "載入餘額失敗")
                _uiState.update { 
                    it.copy(
                        nativeBalance = 0.0,
                        nativeBalanceUsd = "$0.00",
                        isLoading = false,
                        error = "載入餘額失敗: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 載入代幣列表
     */
    private fun loadTokens(wallet: WalletAccount) {
        viewModelScope.launch {
            try {
                val tokens = tokenRepository.scanUserTokens(
                    wallet.address,
                    _uiState.value.currentChain
                )
                
                // 計算代幣總值 - 從價格 API 獲取真實價格
                var totalValue = BigDecimal.ZERO
                tokens.forEach { token ->
                    try {
                        val priceResult = getTokenPriceUseCase.getPrice(token.symbol)
                        val price = when (priceResult) {
                            is Result.Success -> BigDecimal(priceResult.data.toString())
                            else -> BigDecimal.ZERO
                        }
                        // Scale raw balance by token decimals (default 18 for ERC20)
                        val decimals = token.decimals ?: 18
                        val divisor = BigDecimal.TEN.pow(decimals)
                        val scaledBalance = BigDecimal(token.balance).divide(divisor, 18, java.math.RoundingMode.HALF_UP)
                        val tokenValue = scaledBalance.multiply(price)
                        totalValue = totalValue.add(tokenValue)
                    } catch (e: Exception) {
                        Timber.e(e, "獲取 ${token.symbol} 價格失敗")
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        tokens = tokens,
                        tokenCount = tokens.size,
                        tokensTotalValue = totalValue
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "載入代幣失敗")
                // 錯誤時清空列表，不使用假數據
                _uiState.update { 
                    it.copy(
                        tokens = emptyList(),
                        tokenCount = 0,
                        tokensTotalValue = BigDecimal.ZERO
                    )
                }
            }
        }
    }

    /**
     * 載入交易記錄
     */
    private fun loadTransactions(wallet: WalletAccount) {
        viewModelScope.launch {
            try {
                val currentChain = _uiState.value.currentChain
                val isUTXOChain = currentChain in listOf(
                    ChainType.BITCOIN,
                    ChainType.LITECOIN,
                    ChainType.DOGECOIN,
                    ChainType.BITCOIN_CASH
                )
                
                if (isUTXOChain) {
                    // UTXO 鏈暫時不載入交易記錄，避免不必要的 API 調用
                    Timber.d("UTXO 鏈 ${currentChain.displayName} 暫時不載入交易記錄")
                    _uiState.update { 
                        it.copy(
                            transactions = emptyList(),
                            error = null
                        )
                    }
                } else {
                    // 使用當前選擇的鏈載入交易歷史
                    getTransactionHistoryUseCase(
                        walletAddress = wallet.address,
                        chainType = currentChain,
                        limit = 10 // 首頁只顯示最近 10 筆
                    ).collect { result ->
                        when (result) {
                            is Result.Success -> {
                                _uiState.update { 
                                    it.copy(
                                        transactions = result.data ?: emptyList(),
                                        error = null
                                    )
                                }
                                Timber.d("載入了 ${result.data?.size ?: 0} 筆交易記錄")
                            }
                            is Result.Failure -> {
                                Timber.e(result.exception, "載入交易記錄失敗")
                                _uiState.update { 
                                    it.copy(
                                        transactions = emptyList(),
                                        error = "載入交易記錄失敗"
                                    )
                                }
                            }
                            is Result.Loading -> {
                                // 不更新 isLoading，避免影響其他載入狀態
                                Timber.d("正在載入交易記錄...")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "載入交易記錄異常")
                _uiState.update { 
                    it.copy(
                        transactions = emptyList(),
                        error = "載入交易記錄異常: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 刷新數據
     */
    fun refresh(force: Boolean = false) {
        _uiState.value.currentWallet?.let { wallet ->
            loadWalletData(wallet)
        }
    }

    /**
     * 掃描代幣
     */
    fun scanTokens() {
        viewModelScope.launch {
            _uiState.value.currentWallet?.let { wallet ->
                try {
                    _uiState.update { it.copy(isScanningTokens = true) }
                    
                    scanTokensUseCase(
                        wallet.address,
                        _uiState.value.currentChain
                    ).collect { result ->
                        when (result) {
                            is Result.Loading -> {
                                // 保持掃描狀態
                            }
                            is Result.Success -> {
                                _uiState.update { 
                                    it.copy(
                                        tokens = result.data,
                                        isScanningTokens = false
                                    )
                                }
                            }
                            is Result.Failure -> {
                                _uiState.update { 
                                    it.copy(
                                        isScanningTokens = false,
                                        error = "掃描代幣失敗: ${result.exception.message}"
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { 
                        it.copy(
                            isScanningTokens = false,
                            error = "掃描代幣異常: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * 切換鏈
     */
    fun switchChain(chainType: ChainType) {
        val multiChainType = chainTypeToMultiChainType(chainType)
        _uiState.update { 
            it.copy(
                currentChain = chainType,
                currentMultiChain = multiChainType
            )
        }
        // 同步到全局鏈狀態管理器
        ChainStateManager.setCurrentChain(chainType)
        _uiState.value.currentWallet?.let { wallet ->
            loadWalletData(wallet)
        }
    }
    
    /**
     * 將 ChainType 轉換為 MultiChainType
     */
    private fun chainTypeToMultiChainType(chainType: ChainType): MultiChainType {
        return when (chainType) {
            ChainType.ETHEREUM -> MultiChainType.ETHEREUM
            ChainType.BSC -> MultiChainType.BSC
            ChainType.POLYGON -> MultiChainType.POLYGON
            ChainType.ARBITRUM -> MultiChainType.ARBITRUM
            ChainType.OPTIMISM -> MultiChainType.OPTIMISM
            ChainType.AVALANCHE -> MultiChainType.AVALANCHE
            ChainType.CRONOS -> MultiChainType.CRONOS
            ChainType.FANTOM -> MultiChainType.FANTOM
            // ChainType.KLAYTN -> MultiChainType.KLAYTN  // Not in MultiChainType
            // ChainType.AURORA -> MultiChainType.AURORA  // Not in MultiChainType
            ChainType.BITCOIN -> MultiChainType.BITCOIN
            ChainType.LITECOIN -> MultiChainType.LITECOIN
            ChainType.DOGECOIN -> MultiChainType.DOGECOIN
            ChainType.BITCOIN_CASH -> MultiChainType.BITCOIN_CASH
            else -> MultiChainType.ETHEREUM // 默認值
        }
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
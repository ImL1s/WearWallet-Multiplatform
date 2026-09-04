package com.cbstudio.wearwallet.core.presentation.viewmodel

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * KMP 共享的錢包主畫面 ViewModel
 * 
 * 顯示"錢包功能遷移到 KMP 架構中，即將提供更強大的跨平台功能"消息
 */
class KmpWalletMainViewModel {
    
    companion object {
        private const val TAG = "KmpWalletMainViewModel"
        private const val MAINTENANCE_MESSAGE = "錢包功能遷移到 KMP 架構中\n即將提供更強大的跨平台功能"
    }
    
    // 簡化的UI狀態
    data class State(
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isScanningTokens: Boolean = false,
        val error: String? = null,
        val maintenanceMessage: String = MAINTENANCE_MESSAGE,
        val lastRefreshTime: Long = Clock.System.now().epochSeconds,
        val wallets: List<Wallet> = emptyList(),
        val selectedWallet: Wallet? = null,
        val activeWallet: Wallet? = null,
        val tokens: List<Token> = emptyList(),
        val tokenBalances: List<TokenBalance> = emptyList(),
        val selectedChain: ChainType = ChainType.ETHEREUM,
        val totalBalance: Balance = Balance("0.00"),
        val transactions: List<Transaction> = emptyList(),
        val recentTransactions: List<Transaction> = emptyList()
    )
    
    // 副作用（一次性事件）
    sealed class Effect {
        data class ShowError(val message: String) : Effect()
        data class ShowMaintenance(val message: String) : Effect()
        object RefreshCompleted : Effect()
    }
    
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    
    private val _effect = MutableSharedFlow<Effect>()
    val effect: SharedFlow<Effect> = _effect.asSharedFlow()
    
    init {
        // 顯示維護模式消息
        emitEffect(Effect.ShowMaintenance(MAINTENANCE_MESSAGE))
        // 創建示例數據
        loadSampleData()
    }
    
    /**
     * 刷新功能（維護模式）
     */
    fun refresh(force: Boolean = false) {
        _state.value = _state.value.copy(
            isRefreshing = force,
            isLoading = !force,
            lastRefreshTime = Clock.System.now().epochSeconds
        )
        
        // 模擬刷新延遲
        CoroutineScope(Dispatchers.Default).launch {
            delay(1000)
            _state.value = _state.value.copy(isLoading = false, isRefreshing = false)
            emitEffect(Effect.RefreshCompleted)
        }
    }
    
    /**
     * 錢包選擇（維護模式）
     */
    fun selectWallet(walletId: String) {
        val wallet = _state.value.wallets.find { it.id == walletId }
        if (wallet != null) {
            _state.value = _state.value.copy(selectedWallet = wallet)
        } else {
            emitEffect(Effect.ShowMaintenance("錢包選擇功能正在升級中"))
        }
    }
    
    /**
     * 代幣選擇（維護模式）
     */
    fun selectToken(token: Token) {
        emitEffect(Effect.ShowMaintenance("代幣選擇功能正在升級中"))
    }
    
    /**
     * 鏈選擇（維護模式）
     */
    fun selectChain(chainType: ChainType) {
        _state.value = _state.value.copy(selectedChain = chainType)
        emitEffect(Effect.ShowMaintenance("區塊鏈選擇功能正在升級中"))
    }
    
    /**
     * 發送效果事件
     */
    private fun emitEffect(effect: Effect) {
        CoroutineScope(Dispatchers.Default).launch {
            _effect.emit(effect)
        }
    }
    
    /**
     * 清除錯誤
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
    
    /**
     * 掃描代幣（維護模式）
     */
    fun scanTokens() {
        _state.value = _state.value.copy(isScanningTokens = true)
        CoroutineScope(Dispatchers.Default).launch {
            delay(2000)
            _state.value = _state.value.copy(isScanningTokens = false)
            emitEffect(Effect.ShowMaintenance("代幣掃描功能正在升級中"))
        }
    }
    
    /**
     * 切換錢包（維護模式）
     */
    fun switchWallet(walletId: String) {
        selectWallet(walletId)
    }
    
    /**
     * 導航到交易詳情（維護模式）
     */
    fun navigateToTransaction(transaction: Transaction) {
        emitEffect(Effect.ShowMaintenance("交易詳情功能正在升級中"))
    }
    
    /**
     * 載入示例數據
     */
    private fun loadSampleData() {
        val sampleWallets = listOf(
            Wallet(
                id = "1",
                name = "Main Wallet",
                address = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb0",
                publicKey = "0x04...",
                encryptedPrivateKey = "encrypted...",
                chainType = ChainType.ETHEREUM,
                createdAt = Clock.System.now()
            )
        )
        
        val sampleTokens = listOf(
            Token(
                id = "eth",
                address = "0x0000000000000000000000000000000000000000",
                symbol = "ETH",
                name = "Ethereum",
                balance = "1234000000000000000",
                usdPrice = 2000.0,
                decimals = 18,
                chainType = ChainType.ETHEREUM,
                logoUrl = null,
                isNative = true
            ),
            Token(
                id = "usdc",
                address = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                symbol = "USDC",
                name = "USD Coin",
                balance = "1000000000",
                usdPrice = 1.0,
                decimals = 6,
                chainType = ChainType.ETHEREUM,
                logoUrl = null,
                isNative = false
            )
        )
        
        val sampleTransactions = listOf(
            Transaction(
                id = "1",
                hash = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
                from = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb0",
                to = "0x5aAeb6053f3E94C9b9A09f33669435E7Ef1BeAed",
                value = "0.1",
                chainType = ChainType.ETHEREUM,
                status = TransactionStatus.CONFIRMED,
                type = TransactionType.TRANSFER,
                direction = TransactionDirection.OUTGOING,
                nonce = 1,
                timestamp = Clock.System.now()
            ),
            Transaction(
                id = "2",
                hash = "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                from = "0x5aAeb6053f3E94C9b9A09f33669435E7Ef1BeAed",
                to = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb0",
                value = "0.5",
                chainType = ChainType.ETHEREUM,
                status = TransactionStatus.CONFIRMED,
                type = TransactionType.TRANSFER,
                direction = TransactionDirection.INCOMING,
                nonce = 2,
                timestamp = Clock.System.now()
            )
        )
        
        val tokenBalances = sampleTokens.map { TokenBalance.fromToken(it) }
        
        _state.value = _state.value.copy(
            wallets = sampleWallets,
            selectedWallet = sampleWallets.firstOrNull(),
            activeWallet = sampleWallets.firstOrNull(),
            tokens = sampleTokens,
            tokenBalances = tokenBalances,
            totalBalance = Balance("3468.00"),
            transactions = sampleTransactions,
            recentTransactions = sampleTransactions
        )
    }
}
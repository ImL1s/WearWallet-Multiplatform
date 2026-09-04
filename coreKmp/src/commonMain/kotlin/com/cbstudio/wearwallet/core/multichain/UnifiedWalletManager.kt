package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.multichain.backend.*
import com.cbstudio.wearwallet.core.multichain.common.CoinType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate

/**
 * 統一的錢包管理器
 * 提供多鏈錢包的統一管理介面
 */
class UnifiedWalletManager(
    private val capabilityGate: CapabilityGate
) {
    
    // 後端管理
    private val backends = mutableMapOf<CoinType, WalletBackend>()
    private val backendMutex = Mutex()
    
    // 當前助記詞
    private var currentMnemonic: String? = null
    
    // 狀態管理
    private val _state = MutableStateFlow<WalletState>(WalletState.UNINITIALIZED)
    val state: StateFlow<WalletState> = _state.asStateFlow()
    
    // 支援的鏈（目前只實現 Monero）
    private val supportedChains = setOf(
        CoinType.MONERO
        // 未來可以添加更多鏈
    )
    
    /**
     * 初始化錢包管理器
     * @param mnemonic 助記詞
     * @param chains 要初始化的鏈（null 表示全部）
     */
    suspend fun initialize(
        mnemonic: String,
        chains: Set<CoinType>? = null
    ) = backendMutex.withLock {
        try {
            _state.value = WalletState.INITIALIZING
            currentMnemonic = mnemonic
            
            // 初始化指定的鏈
            val chainsToInit = chains ?: supportedChains
            chainsToInit.forEach { coinType ->
                initializeBackend(coinType, mnemonic)
            }
            
            _state.value = WalletState.READY
        } catch (e: Exception) {
            _state.value = WalletState.ERROR(e.message ?: "Initialization failed")
            throw e
        }
    }
    
    /**
     * 初始化特定後端
     */
    private suspend fun initializeBackend(coinType: CoinType, mnemonic: String) {
        val backend = when (coinType) {
            CoinType.MONERO -> {
                MoneroBackend().apply {
                    initialize(
                        mnemonic = mnemonic,
                        config = BackendConfig(
                            networkType = NetworkType.STAGENET,
                            nodeUrl = "54.153.251.193:38089",
                            autoSync = true
                        )
                    )
                }
            }
            
            else -> {
                // 其他鏈暫時跳過
                return
            }
        }
        
        backends[coinType] = backend
    }
    
    /**
     * 獲取指定鏈的後端
     */
    fun getBackend(coinType: CoinType): WalletBackend? {
        return backends[coinType]
    }
    
    /**
     * 獲取錢包地址
     */
    suspend fun getAddress(coinType: CoinType, index: Int = 0): String {
        val backend = getBackend(coinType) 
            ?: throw IllegalArgumentException("Backend not initialized for $coinType")
        return backend.getAddress(index)
    }
    
    /**
     * 獲取餘額
     */
    suspend fun getBalance(coinType: CoinType, address: String? = null): Balance {
        val backend = getBackend(coinType)
            ?: throw IllegalArgumentException("Backend not initialized for $coinType")
        return backend.getBalance(address)
    }
    
    /**
     * 同步錢包
     */
    suspend fun sync(coinType: CoinType, force: Boolean = false): SyncResult {
        val backend = getBackend(coinType)
            ?: throw IllegalArgumentException("Backend not initialized for $coinType")
        return backend.sync(force)
    }
    
    /**
     * 同步所有錢包
     */
    suspend fun syncAll(force: Boolean = false): Map<CoinType, SyncResult> {
        val results = mutableMapOf<CoinType, SyncResult>()
        backends.forEach { (coinType, backend) ->
            try {
                results[coinType] = backend.sync(force)
            } catch (e: Exception) {
                results[coinType] = SyncResult(
                    success = false,
                    blockHeight = 0,
                    syncedHeight = 0,
                    message = e.message
                )
            }
        }
        return results
    }
    
    /**
     * 創建交易
     */
    suspend fun createTransaction(
        coinType: CoinType,
        request: TransactionRequest
    ): UnsignedTransaction {
        val backend = getBackend(coinType)
            ?: throw IllegalArgumentException("Backend not initialized for $coinType")
        return backend.createTransaction(request)
    }
    
    /**
     * 簽名交易
     */
    suspend fun signTransaction(
        coinType: CoinType,
        transaction: UnsignedTransaction
    ): SignedTransaction {
        val backend = getBackend(coinType)
            ?: throw IllegalArgumentException("Backend not initialized for $coinType")
        return backend.signTransaction(transaction)
    }
    
    /**
     * 發送交易
     */
    suspend fun sendTransaction(
        coinType: CoinType,
        transaction: SignedTransaction
    ): String {
        val backend = getBackend(coinType)
            ?: throw IllegalArgumentException("Backend not initialized for $coinType")
        return backend.sendTransaction(transaction)
    }
    
    /**
     * 一步完成交易（創建、簽名、發送）
     */
    suspend fun transfer(
        coinType: CoinType,
        to: String,
        amount: String,
        priority: TransactionPriority = TransactionPriority.NORMAL
    ): String {
        val request = TransactionRequest(
            to = to,
            amount = amount,
            priority = priority
        )
        
        val unsigned = createTransaction(coinType, request)
        val signed = signTransaction(coinType, unsigned)
        return sendTransaction(coinType, signed)
    }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactionHistory(
        coinType: CoinType,
        limit: Int = 20,
        offset: Int = 0
    ): List<TransactionRecord> {
        val backend = getBackend(coinType)
            ?: throw IllegalArgumentException("Backend not initialized for $coinType")
        return backend.getTransactionHistory(limit, offset)
    }
    
    /**
     * 獲取交易詳情
     */
    suspend fun getTransactionDetail(
        coinType: CoinType,
        txHash: String
    ): TransactionDetail? {
        val backend = getBackend(coinType)
            ?: throw IllegalArgumentException("Backend not initialized for $coinType")
        return backend.getTransactionDetail(txHash)
    }
    
    /**
     * 估算手續費
     */
    suspend fun estimateFee(
        coinType: CoinType,
        request: TransactionRequest
    ): FeeEstimate {
        val backend = getBackend(coinType)
            ?: throw IllegalArgumentException("Backend not initialized for $coinType")
        return backend.estimateFee(request)
    }
    
    /**
     * 獲取所有已初始化的鏈
     */
    fun getInitializedChains(): Set<CoinType> {
        return backends.keys.toSet()
    }
    
    /**
     * 檢查鏈是否已初始化
     */
    fun isChainInitialized(coinType: CoinType): Boolean {
        return backends.containsKey(coinType)
    }
    
    /**
     * 獲取當前助記詞
     */
    fun getMnemonic(): String? {
        return currentMnemonic
    }
    
    /**
     * 釋放資源
     */
    suspend fun dispose() = backendMutex.withLock {
        backends.values.forEach { backend ->
            try {
                backend.dispose()
            } catch (e: Exception) {
                // Log error but continue disposing other backends
            }
        }
        backends.clear()
        currentMnemonic = null
        _state.value = WalletState.UNINITIALIZED
    }
    
    /**
     * 錢包狀態
     */
    sealed class WalletState {
        object UNINITIALIZED : WalletState()
        object INITIALIZING : WalletState()
        object READY : WalletState()
        data class ERROR(val message: String) : WalletState()
    }
}
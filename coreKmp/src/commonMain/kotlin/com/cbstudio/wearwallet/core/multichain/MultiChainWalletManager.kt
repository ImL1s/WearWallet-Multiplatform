package com.cbstudio.wearwallet.core.multichain

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.sdk.*
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import co.touchlab.kermit.Logger
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.Operation
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate

/**
 * 多鏈錢包管理器
 * 
 * 提供統一的介面管理所有區塊鏈錢包功能
 * 整合所有 SDK 適配器，提供簡化的 API
 */
class MultiChainWalletManager(
    private val capabilityGate: CapabilityGate
) {
    
    private val logger = Logger.withTag("MultiChainWalletManager")
    private val sdkManager = SDKAdapterFactory.createDefaultManager()
    
    // 錢包狀態
    private val _walletState = MutableStateFlow(WalletState())
    val walletState: StateFlow<WalletState> = _walletState.asStateFlow()
    
    // 支援的鏈配置
    private val chainConfigs = mutableMapOf<MultiChainType, ChainConfig>()
    
    /**
     * 錢包狀態數據類
     */
    data class WalletState(
        val isInitialized: Boolean = false,
        val activeChains: Set<MultiChainType> = emptySet(),
        val balances: Map<MultiChainType, Balance> = emptyMap(),
        val portfolioValue: PortfolioValue? = null,
        val lastUpdated: Long = 0
    )
    
    /**
     * 鏈配置
     */
    data class ChainConfig(
        val chainType: MultiChainType,
        val network: String = "mainnet",
        val rpcUrl: String? = null,
        val apiKey: String? = null,
        val enabled: Boolean = true
    )
    
    /**
     * 投資組合價值
     */
    data class PortfolioValue(
        val totalUsdValue: Double,
        val chainBreakdown: Map<MultiChainType, Double>,
        val change24h: Double = 0.0,
        val changePercentage24h: Double = 0.0
    )
    
    /**
     * 初始化錢包管理器
     */
    suspend fun initialize(configs: List<ChainConfig> = getDefaultConfigs()): Result<Unit> {
        return try {
            logger.i("Initializing MultiChainWalletManager with ${configs.size} chains")
            
            // 儲存配置
            configs.forEach { config ->
                chainConfigs[config.chainType] = config
            }
            
            // 初始化啟用的鏈
            val activeChains = mutableSetOf<MultiChainType>()
            
            coroutineScope {
                configs.filter { it.enabled }.map { config ->
                    async {
                        initializeChain(config)?.let { success ->
                            if (success) {
                                activeChains.add(config.chainType)
                            }
                        }
                    }
                }.awaitAll()
            }
            
            // 更新狀態
            _walletState.value = _walletState.value.copy(
                isInitialized = true,
                activeChains = activeChains,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )
            
            logger.i("Successfully initialized ${activeChains.size} chains")
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to initialize wallet manager", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 初始化單個鏈
     */
    private suspend fun initializeChain(config: ChainConfig): Boolean? {
        val adapter = sdkManager.getAdapter(config.chainType) ?: return null
        
        val sdkConfig = SDKConfig(
            network = config.network,
            rpcUrl = config.rpcUrl ?: getDefaultRpcUrl(config.chainType, config.network),
            apiKey = config.apiKey,
            timeout = 30000,
            retryCount = 3
        )
        
        return when (val result = adapter.initialize(sdkConfig)) {
            is Result.Success -> {
                logger.i("Successfully initialized ${config.chainType}")
                true
            }
            is Result.Failure -> {
                logger.w("Failed to initialize ${config.chainType}: ${result.exception}")
                false
            }
            is Result.Loading -> {
                logger.i("Initializing ${config.chainType}...")
                false
            }
        }
    }
    
    /**
     * 獲取所有鏈的餘額
     */
    suspend fun getAllBalances(addresses: Map<MultiChainType, String>): Result<Map<MultiChainType, Balance>> {
        if (!_walletState.value.isInitialized) {
            return Result.Failure(Exception("Wallet manager not initialized"))
        }
        
        return try {
            val balances = mutableMapOf<MultiChainType, Balance>()
            
            coroutineScope {
                addresses.map { (chainType, address) ->
                    async {
                        if (chainType in _walletState.value.activeChains) {
                            getBalance(chainType, address)?.let { balance ->
                                balances[chainType] = balance
                            }
                        }
                    }
                }.awaitAll()
            }
            
            // 更新狀態
            _walletState.value = _walletState.value.copy(
                balances = balances,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )
            
            // 計算投資組合價值
            updatePortfolioValue(balances)
            
            Result.Success(balances)
        } catch (e: Exception) {
            logger.e("Failed to get all balances", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取單個鏈的餘額
     */
    suspend fun getBalance(chainType: MultiChainType, address: String): Balance? {
        val adapter = sdkManager.getAdapter(chainType) ?: return null
        
        return when (val result = adapter.getAccountBalance(address)) {
            is Result.Success -> result.data
            is Result.Failure -> {
                logger.w("Failed to get balance for $chainType: ${result.exception}")
                null
            }
            is Result.Loading -> {
                logger.i("Loading balance for $chainType...")
                null
            }
        }
    }
    
    /**
     * 創建交易
     */
    suspend fun createTransaction(
        chainType: MultiChainType,
        request: TransactionRequest
    ): Result<UnsignedTransaction> {
        val adapter = sdkManager.getAdapter(chainType)
            ?: return Result.Failure(Exception("Chain $chainType not supported"))
        
        if (!adapter.isInitialized()) {
            return Result.Failure(Exception("Chain $chainType not initialized"))
        }
        
        return adapter.createTransaction(request)
    }
    
    /**
     * 簽名交易
     */
    suspend fun signTransaction(
        chainType: MultiChainType,
        unsignedTransaction: UnsignedTransaction,
        privateKey: String
    ): Result<SignedTransaction> {
        val adapter = sdkManager.getAdapter(chainType)
            ?: return Result.Failure(Exception("Chain $chainType not supported"))
        
        if (!adapter.isInitialized()) {
            return Result.Failure(Exception("Chain $chainType not initialized"))
        }
        
        return adapter.signTransaction(unsignedTransaction, privateKey)
    }

    /**
     * 廣播交易
     */
    suspend fun broadcastTransaction(
        chainType: MultiChainType,
        signedTransaction: SignedTransaction
    ): Result<TransactionResult> {
        val adapter = sdkManager.getAdapter(chainType)
            ?: return Result.Failure(Exception("Chain $chainType not supported"))
        
        if (!adapter.isInitialized()) {
            return Result.Failure(Exception("Chain $chainType not initialized"))
        }
        
        return adapter.broadcastTransaction(signedTransaction)
    }
    
    /**
     * 驗證地址
     */
    fun validateAddress(chainType: MultiChainType, address: String): Result<AddressValidation> {
        val adapter = sdkManager.getAdapter(chainType)
            ?: return Result.Failure(Exception("Chain $chainType not supported"))
        
        return adapter.validateAddress(address)
    }
    
    /**
     * 估算交易手續費
     */
    suspend fun estimateTransactionFee(
        chainType: MultiChainType,
        request: TransactionRequest
    ): Result<TransactionFee> {
        val adapter = sdkManager.getAdapter(chainType)
            ?: return Result.Failure(Exception("Chain $chainType not supported"))
        
        if (!adapter.isInitialized()) {
            return Result.Failure(Exception("Chain $chainType not initialized"))
        }
        
        return adapter.estimateTransactionFee(request)
    }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactionHistory(
        chainType: MultiChainType,
        address: String,
        limit: Int = 20
    ): Result<List<Transaction>> {
        val adapter = sdkManager.getAdapter(chainType)
            ?: return Result.Failure(Exception("Chain $chainType not supported"))
        
        if (!adapter.isInitialized()) {
            return Result.Failure(Exception("Chain $chainType not initialized"))
        }
        
        return adapter.getTransactionHistory(address, limit)
    }
    
    /**
     * 獲取網路狀態
     */
    suspend fun getNetworkStatus(chainType: MultiChainType): Result<NetworkStatus> {
        val adapter = sdkManager.getAdapter(chainType)
            ?: return Result.Failure(Exception("Chain $chainType not supported"))
        
        if (!adapter.isInitialized()) {
            return Result.Failure(Exception("Chain $chainType not initialized"))
        }
        
        return adapter.getNetworkStatus()
    }
    
    /**
     * 獲取所有網路狀態
     */
    suspend fun getAllNetworkStatus(): Map<MultiChainType, NetworkStatus> {
        val statuses = mutableMapOf<MultiChainType, NetworkStatus>()
        
        coroutineScope {
            _walletState.value.activeChains.map { chainType ->
                async {
                    when (val result = getNetworkStatus(chainType)) {
                        is Result.Success -> statuses[chainType] = result.data
                        is Result.Failure -> logger.w("Failed to get network status for $chainType")
                        is Result.Loading -> logger.i("Loading network status for $chainType...")
                    }
                }
            }.awaitAll()
        }
        
        return statuses
    }
    
    /**
     * 更新投資組合價值
     */
    private fun updatePortfolioValue(balances: Map<MultiChainType, Balance>) {
        val chainValues = mutableMapOf<MultiChainType, Double>()
        var totalValue = 0.0
        
        balances.forEach { (chainType, balance) ->
            val usdValue = balance.usdValue?.toDoubleOrNull() ?: 0.0
            chainValues[chainType] = usdValue
            totalValue += usdValue
        }
        
        val portfolioValue = PortfolioValue(
            totalUsdValue = totalValue,
            chainBreakdown = chainValues
        )
        
        _walletState.value = _walletState.value.copy(
            portfolioValue = portfolioValue
        )
    }
    
    /**
     * 獲取支援的鏈列表
     */
    fun getSupportedChains(): List<MultiChainType> {
        return sdkManager.getAllAdapters().map { it.chainType }.filter { chain ->
            capabilityGate.isChainSupported(chain)
        }
    }
    
    /**
     * 獲取支援特定功能的鏈
     */
    fun getChainsWithCapability(capability: SDKCapability): List<MultiChainType> {
        return sdkManager.getAdaptersByCapability(capability).map { it.chainType }
    }
    
    /**
     * 清理資源
     */
    suspend fun cleanup() {
        logger.i("Cleaning up MultiChainWalletManager")
        sdkManager.cleanup()
        _walletState.value = WalletState()
    }
    
    /**
     * 獲取預設配置
     */
    private fun getDefaultConfigs(): List<ChainConfig> {
        return listOf(
            ChainConfig(
                chainType = MultiChainType.SOLANA,
                network = "mainnet-beta",
                enabled = true
            ),
            ChainConfig(
                chainType = MultiChainType.POLKADOT,
                network = "mainnet",
                enabled = true
            ),
            ChainConfig(
                chainType = MultiChainType.TRON,
                network = "mainnet",
                enabled = true
            ),
            ChainConfig(
                chainType = MultiChainType.CARDANO,
                network = "mainnet",
                enabled = true
            ),
            ChainConfig(
                chainType = MultiChainType.MONERO,
                network = "mainnet",
                enabled = true
            )
        )
    }
    
    /**
     * 獲取預設 RPC URL
     */
    private fun getDefaultRpcUrl(chainType: MultiChainType, network: String): String {
        return when (chainType) {
            MultiChainType.SOLANA -> when (network) {
                "mainnet-beta" -> "https://api.mainnet-beta.solana.com"
                "testnet" -> "https://api.testnet.solana.com"
                "devnet" -> "https://api.devnet.solana.com"
                else -> "https://api.mainnet-beta.solana.com"
            }
            MultiChainType.POLKADOT -> when (network) {
                "mainnet" -> "wss://rpc.polkadot.io"
                "kusama" -> "wss://kusama-rpc.polkadot.io"
                "westend" -> "wss://westend-rpc.polkadot.io"
                else -> "wss://rpc.polkadot.io"
            }
            MultiChainType.TRON -> when (network) {
                "mainnet" -> "https://api.trongrid.io"
                "shasta" -> "https://api.shasta.trongrid.io"
                "nile" -> "https://nile.trongrid.io"
                else -> "https://api.trongrid.io"
            }
            MultiChainType.CARDANO -> when (network) {
                "mainnet" -> "wss://ogmios.mainnet.cardano.org"
                "preprod" -> "wss://ogmios.preprod.cardano.org"
                "preview" -> "wss://ogmios.preview.cardano.org"
                else -> "wss://ogmios.mainnet.cardano.org"
            }
            MultiChainType.MONERO -> when (network) {
                "mainnet" -> "https://node.moneroworld.com:18089"
                "stagenet" -> "https://stagenet.xmr-tw.org:38089"
                "testnet" -> "https://testnet.xmr-tw.org:28089"
                else -> "https://node.moneroworld.com:18089"
            }
            else -> ""
        }
    }
    
    companion object {
        /**
         * 創建預配置的錢包管理器實例
         */
        fun createDefault(capabilityGate: CapabilityGate): MultiChainWalletManager {
            return MultiChainWalletManager(capabilityGate)
        }
    }
}
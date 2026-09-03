package com.cbstudio.wearwallet.core.cache

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.*
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

/**
 * 資料預載器
 * 
 * 實現預測性資料載入，提升使用者體驗
 * 
 * Created: 2025-01-17
 */
class DataPreloader(
    private val walletRepository: WalletRepository,
    private val tokenRepository: TokenRepository,
    private val priceRepository: PriceRepository,
    private val transactionRepository: TransactionRepository,
    private val scope: CoroutineScope = GlobalScope
) {
    
    private val preloadJobs = mutableMapOf<String, Job>()
    private var isActive = true
    
    /**
     * 預載策略配置
     */
    data class PreloadStrategy(
        val preloadTokens: Boolean = true,
        val preloadPrices: Boolean = true,
        val preloadTransactions: Boolean = true,
        val preloadNFTs: Boolean = false,
        val maxConcurrency: Int = 3,
        val retryOnFailure: Boolean = true
    )
    
    private var strategy = PreloadStrategy()
    
    /**
     * 開始預載
     */
    fun startPreloading(strategy: PreloadStrategy = PreloadStrategy()) {
        this.strategy = strategy
        isActive = true
        
        Logger.d("DataPreloader", "Starting preloading with strategy: $strategy")
        
        // 監聽活動錢包變化
        preloadJobs["wallet_monitor"] = scope.launch {
            monitorActiveWallet()
        }
        
        // 定期更新價格
        if (strategy.preloadPrices) {
            preloadJobs["price_updater"] = scope.launch {
                periodicPriceUpdate()
            }
        }
    }
    
    /**
     * 停止預載
     */
    fun stopPreloading() {
        isActive = false
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
        Logger.d("DataPreloader", "Preloading stopped")
    }
    
    /**
     * 監聽活動錢包
     */
    private suspend fun monitorActiveWallet() {
        while (isActive) {
            try {
                val walletResult = walletRepository.getActiveWallet()
                
                if (walletResult is Result.Success) {
                    walletResult.data?.let { wallet ->
                        Logger.d("DataPreloader", 
                            "Active wallet detected: ${wallet.address}")
                        
                        // 並行預載相關資料
                        coroutineScope {
                            val jobs = mutableListOf<Deferred<*>>()
                            
                            if (strategy.preloadTokens) {
                                jobs.add(async { preloadTokens(wallet.address) })
                            }
                            
                            if (strategy.preloadTransactions) {
                                jobs.add(async { preloadTransactions(wallet.address) })
                            }
                            
                            // 限制並發數
                            jobs.chunked(strategy.maxConcurrency).forEach { chunk ->
                                chunk.awaitAll()
                            }
                        }
                    }
                }
                
                // 等待一段時間再檢查
                delay(30.seconds)
                
            } catch (e: Exception) {
                Logger.e("DataPreloader", "Error monitoring wallet", e)
                if (!strategy.retryOnFailure) break
                delay(10.seconds)
            }
        }
    }
    
    /**
     * 預載代幣
     */
    private suspend fun preloadTokens(walletAddress: String) {
        Logger.d("DataPreloader", "Preloading tokens for $walletAddress")
        
        val chains = listOf(
            ChainType.ETHEREUM,
            ChainType.BSC,
            ChainType.POLYGON
        )
        
        chains.forEach { chain ->
            try {
                val tokens = tokenRepository.scanUserTokens(walletAddress, chain)
                
                Logger.d("DataPreloader", 
                    "Preloaded ${tokens.size} tokens for $chain")
                
                // 預載這些代幣的價格
                if (strategy.preloadPrices && tokens.isNotEmpty()) {
                    val symbols = tokens.mapNotNull { token ->
                        if (token.symbol.isNotBlank()) token.symbol else null
                    }
                    if (symbols.isNotEmpty()) {
                        preloadTokenPrices(symbols)
                    }
                }
            } catch (e: Exception) {
                Logger.e("DataPreloader", "Failed to preload tokens for $chain", e)
            }
        }
    }
    
    /**
     * 預載交易歷史
     */
    private suspend fun preloadTransactions(walletAddress: String) {
        Logger.d("DataPreloader", "Preloading transactions for $walletAddress")
        
        try {
            // 預載最近的交易
            val chains = listOf(ChainType.ETHEREUM, ChainType.BSC)
            
            chains.forEach { chain ->
                val transactions = transactionRepository.getTransactionHistory(
                    walletAddress, chain
                )
                
                // 快取到 GlobalCacheManager
                val cacheKey = "${walletAddress}_${chain.name}_transactions"
                GlobalCacheManager.transactionCache.put(
                    cacheKey, 
                    transactions
                )
                
                Logger.d("DataPreloader", 
                    "Preloaded ${transactions.size} transactions for $chain")
            }
        } catch (e: Exception) {
            Logger.e("DataPreloader", "Failed to preload transactions", e)
        }
    }
    
    /**
     * 預載代幣價格
     */
    private suspend fun preloadTokenPrices(symbols: List<String>) {
        if (symbols.isEmpty()) return
        
        Logger.d("DataPreloader", "Preloading prices for ${symbols.size} tokens")
        
        try {
            val result = priceRepository.getBatchPrices(symbols, "USD")
            
            if (result is Result.Success) {
                Logger.d("DataPreloader", 
                    "Preloaded ${result.data.size} token prices")
            }
        } catch (e: Exception) {
            Logger.e("DataPreloader", "Failed to preload prices", e)
        }
    }
    
    /**
     * 定期更新價格
     */
    private suspend fun periodicPriceUpdate() {
        val commonTokens = listOf("ETH", "BTC", "BNB", "MATIC", "USDT", "USDC")
        
        while (isActive) {
            try {
                Logger.d("DataPreloader", "Updating common token prices")
                
                val result = priceRepository.getBatchPrices(commonTokens, "USD")
                
                if (result is Result.Success) {
                    Logger.d("DataPreloader", 
                        "Updated ${result.data.size} token prices")
                }
                
                // 每 30 秒更新一次
                delay(30.seconds)
                
            } catch (e: Exception) {
                Logger.e("DataPreloader", "Failed to update prices", e)
                delay(60.seconds) // 錯誤時等待更長時間
            }
        }
    }
    
    /**
     * 預載使用者可能需要的資料
     */
    suspend fun predictivePreload(userAction: UserAction) {
        Logger.d("DataPreloader", "Predictive preload for action: $userAction")
        
        scope.launch {
            when (userAction) {
                UserAction.OPEN_WALLET_LIST -> {
                    // 預載所有錢包
                    walletRepository.getAllWallets()
                }
                
                UserAction.PREPARE_TRANSACTION -> {
                    // 預載 Gas 價格和 Nonce
                    val wallet = walletRepository.getActiveWallet()
                    if (wallet is Result.Success) {
                        wallet.data?.let {
                            preloadGasPrice(it.address)
                        }
                    }
                }
                
                UserAction.VIEW_NFT -> {
                    // 預載 NFT 資料
                    preloadNFTData()
                }
                
                UserAction.CHECK_PRICES -> {
                    // 更新所有價格
                    refreshAllPrices()
                }
            }
        }
    }
    
    /**
     * 預載 Gas 價格
     */
    private suspend fun preloadGasPrice(walletAddress: String) {
        try {
            val chains = listOf(ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON)
            
            chains.forEach { chain ->
                val nonce = transactionRepository.getNonce(walletAddress, chain)
                Logger.d("DataPreloader", "Preloaded nonce for $chain: $nonce")
            }
        } catch (e: Exception) {
            Logger.e("DataPreloader", "Failed to preload gas price", e)
        }
    }
    
    /**
     * 預載 NFT 資料
     */
    private suspend fun preloadNFTData() {
        // TODO: 實現 NFT 預載邏輯
        Logger.d("DataPreloader", "NFT preloading not yet implemented")
    }
    
    /**
     * 刷新所有價格
     */
    private suspend fun refreshAllPrices() {
        try {
            // 獲取所有活躍代幣
            val walletResult = walletRepository.getActiveWallet()
            
            if (walletResult is Result.Success) {
                walletResult.data?.let { wallet ->
                    val tokens = tokenRepository.scanUserTokens(
                        wallet.address, 
                        ChainType.ETHEREUM
                    )
                    
                    val symbols = tokens.mapNotNull { token ->
                        if (token.symbol.isNotBlank()) token.symbol else null 
                    }
                    if (symbols.isNotEmpty()) {
                        priceRepository.getBatchPrices(symbols, "USD")
                        Logger.d("DataPreloader", 
                            "Refreshed prices for ${symbols.size} tokens")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("DataPreloader", "Failed to refresh prices", e)
        }
    }
    
    /**
     * 獲取預載統計
     */
    fun getStats(): PreloadStats {
        return PreloadStats(
            activeJobs = preloadJobs.size,
            isActive = isActive,
            cacheStats = GlobalCacheManager.run {
                mapOf(
                    "wallet" to walletCache.getStats(),
                    "price" to priceCache.getStats(),
                    "nft" to nftCache.getStats()
                )
            }
        )
    }
}

/**
 * 使用者行為枚舉
 */
enum class UserAction {
    OPEN_WALLET_LIST,
    PREPARE_TRANSACTION,
    VIEW_NFT,
    CHECK_PRICES
}

/**
 * 預載統計
 */
data class PreloadStats(
    val activeJobs: Int,
    val isActive: Boolean,
    val cacheStats: Map<String, CacheStats>
) {
    fun printStats() {
        println("""
            ╔════════════════════════════════════════╗
            ║         Preloader Statistics           ║
            ╚════════════════════════════════════════╝
            
            🔄 Active: $isActive
            📊 Active Jobs: $activeJobs
            
            Cache Performance:
            ${cacheStats.entries.joinToString("\n") { (name, stats) ->
                "  • $name: ${stats.size}/${stats.maxSize} (${(stats.hitRate * 100).toInt()}% hit rate)"
            }}
        """.trimIndent())
    }
}

/**
 * 智能預載管理器
 */
object SmartPreloadManager {
    private var preloader: DataPreloader? = null
    
    /**
     * 初始化預載器
     */
    fun initialize(
        walletRepository: WalletRepository,
        tokenRepository: TokenRepository,
        priceRepository: PriceRepository,
        transactionRepository: TransactionRepository,
        scope: CoroutineScope = GlobalScope
    ) {
        preloader = DataPreloader(
            walletRepository,
            tokenRepository,
            priceRepository,
            transactionRepository,
            scope
        )
        
        // 使用預設策略開始預載
        preloader?.startPreloading()
        
        Logger.d("SmartPreloadManager", "Initialized and started preloading")
    }
    
    /**
     * 預測使用者行為
     */
    fun predictUserAction(action: UserAction) {
        GlobalScope.launch {
            preloader?.predictivePreload(action)
        }
    }
    
    /**
     * 獲取統計
     */
    fun getStats(): PreloadStats? {
        return preloader?.getStats()
    }
    
    /**
     * 停止預載
     */
    fun stop() {
        preloader?.stopPreloading()
        preloader = null
    }
}
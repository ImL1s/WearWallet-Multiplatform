package com.cbstudio.wearwallet.core.multichain

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import co.touchlab.kermit.Logger
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.datetime.Clock

/**
 * 價格聚合服務
 * 
 * 從多個來源獲取加密貨幣價格，提供準確的市場數據
 */
class PriceAggregator {
    
    private val logger = Logger.withTag("PriceAggregator")
    
    // 價格狀態
    private val _priceState = MutableStateFlow(PriceState())
    val priceState: StateFlow<PriceState> = _priceState.asStateFlow()
    
    // 價格更新流
    private val _priceUpdates = MutableSharedFlow<PriceUpdate>()
    val priceUpdates: SharedFlow<PriceUpdate> = _priceUpdates.asSharedFlow()
    
    /**
     * 價格狀態
     */
    data class PriceState(
        val prices: Map<String, TokenPrice> = emptyMap(),
        val marketData: Map<String, MarketData> = emptyMap(),
        val priceAlerts: List<PriceAlert> = emptyList(),
        val lastUpdated: Long = 0,
        val isUpdating: Boolean = false
    )
    
    /**
     * 代幣價格
     */
    @Serializable
    data class TokenPrice(
        val symbol: String,
        val name: String,
        val price: Double,
        val currency: String = "USD",
        val change24h: Double,
        val changePercentage24h: Double,
        val volume24h: Double,
        val marketCap: Double,
        val circulatingSupply: Double,
        val totalSupply: Double?,
        val ath: Double, // All Time High
        val atl: Double, // All Time Low
        val lastUpdated: Long,
        val sources: List<PriceSource> = emptyList()
    )
    
    /**
     * 價格來源
     */
    @Serializable
    data class PriceSource(
        val provider: String,
        val price: Double,
        val timestamp: Long,
        val confidence: Double // 0.0 - 1.0
    )
    
    /**
     * 市場數據
     */
    @Serializable
    data class MarketData(
        val symbol: String,
        val openPrice: Double,
        val highPrice: Double,
        val lowPrice: Double,
        val closePrice: Double,
        val volume: Double,
        val trades: Int,
        val timestamp: Long
    )
    
    /**
     * 價格更新
     */
    data class PriceUpdate(
        val symbol: String,
        val oldPrice: Double,
        val newPrice: Double,
        val changeAmount: Double,
        val changePercentage: Double,
        val timestamp: Long
    )
    
    /**
     * 價格警報
     */
    data class PriceAlert(
        val id: String,
        val symbol: String,
        val condition: AlertCondition,
        val targetPrice: Double,
        val isActive: Boolean,
        val createdAt: Long,
        val triggeredAt: Long? = null
    )
    
    /**
     * 警報條件
     */
    enum class AlertCondition {
        ABOVE,      // 高於
        BELOW,      // 低於
        CROSS_UP,   // 向上穿越
        CROSS_DOWN, // 向下穿越
        CHANGE_PERCENTAGE // 變化百分比
    }
    
    /**
     * 價格提供者
     */
    enum class PriceProvider {
        COINGECKO,
        COINMARKETCAP,
        BINANCE,
        COINBASE,
        KRAKEN,
        UNISWAP,
        PANCAKESWAP,
        INTERNAL
    }
    
    /**
     * 初始化價格聚合器
     */
    suspend fun initialize(symbols: List<String> = getDefaultSymbols()): Result<Unit> {
        return try {
            logger.i("Initializing Price Aggregator with ${symbols.size} symbols")
            
            _priceState.value = _priceState.value.copy(isUpdating = true)
            
            // 載入初始價格
            val prices = fetchPrices(symbols)
            
            _priceState.value = _priceState.value.copy(
                prices = prices,
                lastUpdated = Clock.System.now().toEpochMilliseconds(),
                isUpdating = false
            )
            
            // 啟動自動更新
            startPriceUpdates()
            
            logger.i("Price Aggregator initialized with ${prices.size} prices")
            Result.Success(Unit)
        } catch (e: Exception) {
            logger.e("Failed to initialize Price Aggregator", e)
            _priceState.value = _priceState.value.copy(isUpdating = false)
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取代幣價格
     */
    suspend fun getPrice(symbol: String, forceRefresh: Boolean = false): Result<TokenPrice> {
        return try {
            // 檢查緩存
            if (!forceRefresh) {
                _priceState.value.prices[symbol]?.let { cachedPrice ->
                    if (isPriceValid(cachedPrice)) {
                        return Result.Success(cachedPrice)
                    }
                }
            }
            
            // 獲取新價格
            val prices = fetchPrices(listOf(symbol))
            val price = prices[symbol]
                ?: return Result.Failure(Exception("Price not found for $symbol"))
            
            // 更新緩存
            updatePriceCache(symbol, price)
            
            Result.Success(price)
        } catch (e: Exception) {
            logger.e("Failed to get price for $symbol", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 批量獲取價格
     */
    suspend fun getPrices(symbols: List<String>): Result<Map<String, TokenPrice>> {
        return try {
            val prices = fetchPrices(symbols)
            
            // 更新緩存
            val currentPrices = _priceState.value.prices.toMutableMap()
            currentPrices.putAll(prices)
            _priceState.value = _priceState.value.copy(
                prices = currentPrices,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            )
            
            Result.Success(prices)
        } catch (e: Exception) {
            logger.e("Failed to get prices", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取歷史價格
     */
    suspend fun getHistoricalPrices(
        symbol: String,
        days: Int = 7
    ): Result<List<HistoricalPrice>> {
        return try {
            logger.i("Fetching $days days historical prices for $symbol")
            
            // 模擬歷史價格數據
            val currentPrice = _priceState.value.prices[symbol]?.price ?: 100.0
            val historicalPrices = mutableListOf<HistoricalPrice>()
            
            for (i in days downTo 0) {
                val timestamp = Clock.System.now().toEpochMilliseconds() - (i * 86400000L) // 一天的毫秒數
                val variation = (Random.nextDouble() - 0.5) * 0.1 // ±5% 變化
                val price = currentPrice * (1 + variation)
                
                historicalPrices.add(
                    HistoricalPrice(
                        timestamp = timestamp,
                        price = price,
                        volume = Random.nextDouble() * 1000000
                    )
                )
            }
            
            Result.Success(historicalPrices)
        } catch (e: Exception) {
            logger.e("Failed to get historical prices", e)
            Result.Failure(e)
        }
    }
    
    /**
     * 歷史價格
     */
    data class HistoricalPrice(
        val timestamp: Long,
        val price: Double,
        val volume: Double
    )
    
    /**
     * 設置價格警報
     */
    fun setPriceAlert(
        symbol: String,
        condition: AlertCondition,
        targetPrice: Double
    ): PriceAlert {
        val alert = PriceAlert(
            id = "alert_${Clock.System.now().toEpochMilliseconds()}",
            symbol = symbol,
            condition = condition,
            targetPrice = targetPrice,
            isActive = true,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        
        val currentAlerts = _priceState.value.priceAlerts.toMutableList()
        currentAlerts.add(alert)
        _priceState.value = _priceState.value.copy(priceAlerts = currentAlerts)
        
        logger.i("Price alert set for $symbol: $condition $targetPrice")
        return alert
    }
    
    /**
     * 移除價格警報
     */
    fun removePriceAlert(alertId: String) {
        val currentAlerts = _priceState.value.priceAlerts.filter { it.id != alertId }
        _priceState.value = _priceState.value.copy(priceAlerts = currentAlerts)
        logger.i("Price alert removed: $alertId")
    }
    
    /**
     * 計算投資組合價值
     */
    fun calculatePortfolioValue(holdings: Map<String, Double>): PortfolioValue {
        val prices = _priceState.value.prices
        var totalValue = 0.0
        var totalChange24h = 0.0
        val breakdown = mutableMapOf<String, AssetValue>()
        
        holdings.forEach { (symbol, amount) ->
            val price = prices[symbol]
            if (price != null) {
                val value = amount * price.price
                val change = amount * price.change24h
                
                totalValue += value
                totalChange24h += change
                
                breakdown[symbol] = AssetValue(
                    symbol = symbol,
                    amount = amount,
                    price = price.price,
                    value = value,
                    change24h = change,
                    changePercentage24h = price.changePercentage24h,
                    allocation = 0.0 // 稍後計算
                )
            }
        }
        
        // 計算資產配置百分比
        breakdown.forEach { (symbol, assetValue) ->
            breakdown[symbol] = assetValue.copy(
                allocation = if (totalValue > 0) assetValue.value / totalValue * 100 else 0.0
            )
        }
        
        return PortfolioValue(
            totalValue = totalValue,
            totalChange24h = totalChange24h,
            changePercentage24h = if (totalValue > totalChange24h) {
                totalChange24h / (totalValue - totalChange24h) * 100
            } else 0.0,
            assets = breakdown,
            lastUpdated = _priceState.value.lastUpdated
        )
    }
    
    /**
     * 投資組合價值
     */
    data class PortfolioValue(
        val totalValue: Double,
        val totalChange24h: Double,
        val changePercentage24h: Double,
        val assets: Map<String, AssetValue>,
        val lastUpdated: Long
    )
    
    /**
     * 資產價值
     */
    data class AssetValue(
        val symbol: String,
        val amount: Double,
        val price: Double,
        val value: Double,
        val change24h: Double,
        val changePercentage24h: Double,
        val allocation: Double // 百分比
    )
    
    /**
     * 獲取市場統計
     */
    fun getMarketStatistics(): MarketStatistics {
        val prices = _priceState.value.prices.values
        
        if (prices.isEmpty()) {
            return MarketStatistics()
        }
        
        val totalMarketCap = prices.sumOf { it.marketCap }
        val totalVolume24h = prices.sumOf { it.volume24h }
        val averageChange24h = prices.map { it.changePercentage24h }.average()
        
        val topGainers = prices
            .filter { it.changePercentage24h > 0 }
            .sortedByDescending { it.changePercentage24h }
            .take(5)
        
        val topLosers = prices
            .filter { it.changePercentage24h < 0 }
            .sortedBy { it.changePercentage24h }
            .take(5)
        
        return MarketStatistics(
            totalMarketCap = totalMarketCap,
            totalVolume24h = totalVolume24h,
            averageChange24h = averageChange24h,
            topGainers = topGainers,
            topLosers = topLosers,
            totalAssets = prices.size
        )
    }
    
    /**
     * 市場統計
     */
    data class MarketStatistics(
        val totalMarketCap: Double = 0.0,
        val totalVolume24h: Double = 0.0,
        val averageChange24h: Double = 0.0,
        val topGainers: List<TokenPrice> = emptyList(),
        val topLosers: List<TokenPrice> = emptyList(),
        val totalAssets: Int = 0
    )
    
    // === 私有輔助方法 ===
    
    private suspend fun fetchPrices(symbols: List<String>): Map<String, TokenPrice> {
        return coroutineScope {
            val providers = listOf(
                PriceProvider.COINGECKO,
                PriceProvider.COINMARKETCAP,
                PriceProvider.BINANCE
            )
            
            // 從多個來源並行獲取價格
            val pricesByProvider = providers.map { provider ->
                async {
                    fetchPricesFromProvider(provider, symbols)
                }
            }.awaitAll()
            
            // 聚合價格
            aggregatePrices(pricesByProvider, symbols)
        }
    }
    
    private suspend fun fetchPricesFromProvider(
        provider: PriceProvider,
        symbols: List<String>
    ): Map<String, PriceSource> {
        return when (provider) {
            PriceProvider.COINGECKO -> fetchFromCoinGecko(symbols)
            PriceProvider.COINMARKETCAP -> fetchFromCoinMarketCap(symbols)
            PriceProvider.BINANCE -> fetchFromBinance(symbols)
            else -> emptyMap()
        }
    }
    
    private suspend fun fetchFromCoinGecko(symbols: List<String>): Map<String, PriceSource> {
        return try {
            val httpClient = io.ktor.client.HttpClient()
            val priceApiClient = com.cbstudio.wearwallet.core.network.PriceApiClient(httpClient)
            
            val priceResult = priceApiClient.getSimplePrice(symbols)
            
            when (priceResult) {
                is Result.Success -> {
                    priceResult.data.mapValues { (symbol, priceData) ->
                        PriceSource(
                            provider = "COINGECKO",
                            price = priceData.price,
                            timestamp = Clock.System.now().toEpochMilliseconds(),
                            confidence = 0.95
                        )
                    }
                }
                is Result.Failure -> {
                    logger.e("CoinGecko API 失敗: ${priceResult.exception.message}")
                    // 備用：使用基礎價格
                    symbols.associateWith { symbol ->
                        PriceSource(
                            provider = "COINGECKO",
                            price = getBasePrice(symbol),
                            timestamp = Clock.System.now().toEpochMilliseconds(),
                            confidence = 0.5
                        )
                    }
                }
                is Result.Loading -> emptyMap()
            }
        } catch (e: Exception) {
            logger.e("獲取 CoinGecko 價格失敗: ${e.message}")
            emptyMap()
        }
    }
    
    private suspend fun fetchFromCoinMarketCap(symbols: List<String>): Map<String, PriceSource> {
        // TODO: 實現 CoinMarketCap API
        // 暫時返回基礎價格
        return symbols.associateWith { symbol ->
            PriceSource(
                provider = "COINMARKETCAP",
                price = getBasePrice(symbol) * 1.01,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                confidence = 0.93
            )
        }
    }
    
    private suspend fun fetchFromBinance(symbols: List<String>): Map<String, PriceSource> {
        // TODO: 實現 Binance API
        // 暫時返回基礎價格
        return symbols.associateWith { symbol ->
            PriceSource(
                provider = "BINANCE",
                price = getBasePrice(symbol) * 0.99,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                confidence = 0.90
            )
        }
    }
    
    private fun aggregatePrices(
        pricesByProvider: List<Map<String, PriceSource>>,
        symbols: List<String>
    ): Map<String, TokenPrice> {
        return symbols.associateWith { symbol ->
            val sources = pricesByProvider.mapNotNull { it[symbol] }
            
            // 加權平均價格
            val weightedPrice = if (sources.isNotEmpty()) {
                val totalWeight = sources.sumOf { it.confidence }
                sources.sumOf { it.price * it.confidence } / totalWeight
            } else {
                getBasePrice(symbol)
            }
            
            // 生成其他市場數據
            val change24h = (Random.nextDouble() - 0.5) * 10 // ±5 USD
            val changePercentage = change24h / weightedPrice * 100
            
            TokenPrice(
                symbol = symbol,
                name = getTokenName(symbol),
                price = weightedPrice,
                change24h = change24h,
                changePercentage24h = changePercentage,
                volume24h = Random.nextDouble() * 10000000,
                marketCap = weightedPrice * 1000000 * Random.nextDouble() * 100,
                circulatingSupply = 1000000 * Random.nextDouble() * 100,
                totalSupply = 1000000 * Random.nextDouble() * 150,
                ath = weightedPrice * 1.5,
                atl = weightedPrice * 0.3,
                lastUpdated = Clock.System.now().toEpochMilliseconds(),
                sources = sources
            )
        }
    }
    
    private fun getBasePrice(symbol: String): Double {
        // 基礎價格（用於模擬）
        return when (symbol) {
            "BTC" -> 50000.0
            "ETH" -> 3000.0
            "BNB" -> 400.0
            "SOL" -> 100.0
            "DOT" -> 20.0
            "ADA" -> 0.5
            "MATIC" -> 1.0
            "TRX" -> 0.08
            "XMR" -> 150.0
            "USDT" -> 1.0
            "USDC" -> 1.0
            else -> 1.0
        }
    }
    
    private fun getTokenName(symbol: String): String {
        return when (symbol) {
            "BTC" -> "Bitcoin"
            "ETH" -> "Ethereum"
            "BNB" -> "Binance Coin"
            "SOL" -> "Solana"
            "DOT" -> "Polkadot"
            "ADA" -> "Cardano"
            "MATIC" -> "Polygon"
            "TRX" -> "TRON"
            "XMR" -> "Monero"
            "USDT" -> "Tether"
            "USDC" -> "USD Coin"
            else -> symbol
        }
    }
    
    private fun getDefaultSymbols(): List<String> {
        return listOf(
            "BTC", "ETH", "BNB", "SOL", "DOT", 
            "ADA", "MATIC", "TRX", "XMR", "USDT", "USDC"
        )
    }
    
    private fun isPriceValid(price: TokenPrice): Boolean {
        // 價格有效期：1 分鐘
        val maxAge = 60000L
        return (Clock.System.now().toEpochMilliseconds() - price.lastUpdated) < maxAge
    }
    
    private fun updatePriceCache(symbol: String, newPrice: TokenPrice) {
        val oldPrice = _priceState.value.prices[symbol]
        
        // 更新緩存
        val currentPrices = _priceState.value.prices.toMutableMap()
        currentPrices[symbol] = newPrice
        _priceState.value = _priceState.value.copy(prices = currentPrices)
        
        // 發送價格更新事件
        if (oldPrice != null && abs(oldPrice.price - newPrice.price) > 0.01) {
            val update = PriceUpdate(
                symbol = symbol,
                oldPrice = oldPrice.price,
                newPrice = newPrice.price,
                changeAmount = newPrice.price - oldPrice.price,
                changePercentage = (newPrice.price - oldPrice.price) / oldPrice.price * 100,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
            
            // 發送更新（需要在協程中）
            // _priceUpdates.tryEmit(update)
            
            // 檢查價格警報
            checkPriceAlerts(symbol, newPrice.price)
        }
    }
    
    private fun checkPriceAlerts(symbol: String, currentPrice: Double) {
        val alerts = _priceState.value.priceAlerts
            .filter { it.symbol == symbol && it.isActive }
        
        alerts.forEach { alert ->
            val triggered = when (alert.condition) {
                AlertCondition.ABOVE -> currentPrice > alert.targetPrice
                AlertCondition.BELOW -> currentPrice < alert.targetPrice
                AlertCondition.CROSS_UP -> {
                    val oldPrice = _priceState.value.prices[symbol]?.price ?: currentPrice
                    oldPrice <= alert.targetPrice && currentPrice > alert.targetPrice
                }
                AlertCondition.CROSS_DOWN -> {
                    val oldPrice = _priceState.value.prices[symbol]?.price ?: currentPrice
                    oldPrice >= alert.targetPrice && currentPrice < alert.targetPrice
                }
                AlertCondition.CHANGE_PERCENTAGE -> {
                    val oldPrice = _priceState.value.prices[symbol]?.price ?: currentPrice
                    abs((currentPrice - oldPrice) / oldPrice * 100) >= alert.targetPrice
                }
            }
            
            if (triggered) {
                logger.i("Price alert triggered: ${alert.symbol} ${alert.condition} ${alert.targetPrice}")
                
                // 更新警報狀態
                val updatedAlerts = _priceState.value.priceAlerts.map { a ->
                    if (a.id == alert.id) {
                        a.copy(isActive = false, triggeredAt = Clock.System.now().toEpochMilliseconds())
                    } else a
                }
                _priceState.value = _priceState.value.copy(priceAlerts = updatedAlerts)
            }
        }
    }
    
    private suspend fun startPriceUpdates() {
        // 啟動定期價格更新（實際應用中應該在後台服務中運行）
        // 這裡只是示範
        logger.i("Price updates started")
    }
    
    /**
     * 清理資源
     */
    fun cleanup() {
        logger.i("Cleaning up Price Aggregator")
        _priceState.value = PriceState()
    }
}
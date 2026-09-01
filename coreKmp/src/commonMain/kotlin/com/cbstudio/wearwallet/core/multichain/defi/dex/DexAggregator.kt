package com.cbstudio.wearwallet.core.multichain.defi.dex

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import co.touchlab.kermit.Logger

/**
 * DEX 聚合器
 * 整合多個去中心化交易所，提供最優價格路由
 */
class DexAggregator(
    private val dexes: List<DecentralizedExchange>,
    private val logger: Logger = Logger.withTag("DexAggregator")
) {
    
    /**
     * 取得最佳交換報價
     * 比較所有支援的 DEX，選擇最優價格
     */
    suspend fun getBestSwapQuote(
        request: SwapRequest,
        slippageTolerance: Double = 0.01
    ): AggregatedSwapQuote {
        logger.i("Getting best swap quote for ${request.fromToken} -> ${request.toToken} on ${request.chainType.symbol}")
        
        val supportedDexes = dexes.filter { 
            request.chainType in it.supportedChains 
        }
        
        if (supportedDexes.isEmpty()) {
            throw BlockchainException.UnsupportedOperationException(
                request.chainType,
                "No DEX supports ${request.chainType.fullName}"
            )
        }
        
        val quotes = mutableListOf<DexQuoteResult>()
        
        // 並行查詢所有 DEX 的報價
        supportedDexes.forEach { dex ->
            try {
                val quote = dex.getSwapQuote(request)
                val score = calculateQuoteScore(quote)
                
                quotes.add(
                    DexQuoteResult(
                        dex = dex,
                        quote = quote,
                        score = score
                    )
                )
                
                logger.d("${dex.dexName}: output=${quote.expectedOutput}, impact=${quote.priceImpact}, score=$score")
            } catch (e: Exception) {
                logger.w("Failed to get quote from ${dex.dexName}", e)
            }
        }
        
        if (quotes.isEmpty()) {
            throw BlockchainException.GenericException(
                request.chainType,
                "No DEX could provide quotes for this swap"
            )
        }
        
        // 按分數排序，選擇最佳報價
        val sortedQuotes = quotes.sortedByDescending { it.score }
        val best = sortedQuotes.first()
        val alternatives = sortedQuotes.drop(1)
        
        return AggregatedSwapQuote(
            request = request,
            bestQuote = best,
            alternativeQuotes = alternatives,
            priceComparison = generatePriceComparison(sortedQuotes),
            recommendation = generateRecommendation(best, alternatives)
        )
    }
    
    /**
     * 執行最佳路由交換
     */
    suspend fun executeSwap(
        request: SwapRequest,
        privateKey: String,
        slippageTolerance: Double = 0.01
    ): SwapResult {
        val aggregatedQuote = getBestSwapQuote(request, slippageTolerance)
        val bestDex = aggregatedQuote.bestQuote.dex
        
        logger.i("Executing swap using ${bestDex.dexName}")
        
        return try {
            val transactionData = bestDex.createSwapTransaction(request, slippageTolerance)
            
            SwapResult(
                success = true,
                transactionData = transactionData,
                dexUsed = bestDex.dexName,
                expectedOutput = aggregatedQuote.bestQuote.quote.expectedOutput,
                actualOutput = null, // 需要等待交易確認
                message = "Swap transaction created successfully"
            )
        } catch (e: Exception) {
            logger.e("Failed to execute swap", e)
            SwapResult(
                success = false,
                transactionData = null,
                dexUsed = bestDex.dexName,
                expectedOutput = aggregatedQuote.bestQuote.quote.expectedOutput,
                message = "Swap execution failed: ${e.message}",
                error = e
            )
        }
    }
    
    /**
     * 取得跨 DEX 價格比較
     */
    suspend fun getPriceComparison(
        tokenA: String,
        tokenB: String,
        chainType: MultiChainType
    ): List<DexPriceComparison> {
        logger.d("Getting price comparison for $tokenA/$tokenB on ${chainType.symbol}")
        
        val supportedDexes = dexes.filter { chainType in it.supportedChains }
        val comparisons = mutableListOf<DexPriceComparison>()
        
        supportedDexes.forEach { dex ->
            try {
                val price = dex.getPrice(tokenA, tokenB, chainType)
                val spread = calculateSpread(price.price.toDoubleOrNull() ?: 0.0, comparisons)
                
                comparisons.add(
                    DexPriceComparison(
                        dexName = dex.dexName,
                        price = price,
                        spreadPercentage = spread
                    )
                )
            } catch (e: Exception) {
                logger.w("Failed to get price from ${dex.dexName}", e)
            }
        }
        
        return comparisons.sortedByDescending { 
            it.price.price.toDoubleOrNull() ?: 0.0 
        }
    }
    
    /**
     * 取得流動性聚合資訊
     */
    suspend fun getAggregatedLiquidity(
        tokenA: String,
        tokenB: String,
        chainType: MultiChainType
    ): AggregatedLiquidity {
        logger.d("Getting aggregated liquidity for $tokenA/$tokenB")
        
        val supportedDexes = dexes.filter { chainType in it.supportedChains }
        val pools = mutableListOf<LiquidityPool>()
        var totalLiquidity = 0.0
        var totalVolume24h = 0.0
        
        supportedDexes.forEach { dex ->
            try {
                val pool = dex.getLiquidityPool(tokenA, tokenB, chainType)
                pools.add(pool)
                
                totalLiquidity += pool.totalLiquidity.toDoubleOrNull() ?: 0.0
                totalVolume24h += pool.volume24h.toDoubleOrNull() ?: 0.0
                
            } catch (e: Exception) {
                logger.w("Failed to get liquidity pool from ${dex.dexName}", e)
            }
        }
        
        return AggregatedLiquidity(
            tokenA = tokenA,
            tokenB = tokenB,
            chainType = chainType,
            totalLiquidity = totalLiquidity.toString(),
            totalVolume24h = totalVolume24h.toString(),
            pools = pools,
            dominantDex = pools.maxByOrNull { 
                it.totalLiquidity.toDoubleOrNull() ?: 0.0 
            }?.dexName
        )
    }
    
    /**
     * 取得跨鏈套利機會
     */
    suspend fun getArbitrageOpportunities(
        tokenSymbol: String,
        chains: List<MultiChainType>,
        minimumProfit: Double = 0.01 // 1% 最小利潤
    ): List<ArbitrageOpportunity> {
        logger.d("Scanning arbitrage opportunities for $tokenSymbol")
        
        val opportunities = mutableListOf<ArbitrageOpportunity>()
        
        // 取得所有鏈上的價格
        val chainPrices = mutableMapOf<MultiChainType, Double>()
        
        chains.forEach { chain ->
            try {
                val supportedDexes = dexes.filter { chain in it.supportedChains }
                if (supportedDexes.isNotEmpty()) {
                    // 使用第一個支援的 DEX 取得價格（可以改為最佳價格）
                    val dex = supportedDexes.first()
                    // 假設與穩定幣配對查價
                    val price = dex.getPrice(tokenSymbol, "USDC", chain)
                    chainPrices[chain] = price.price.toDoubleOrNull() ?: 0.0
                }
            } catch (e: Exception) {
                logger.w("Failed to get $tokenSymbol price on ${chain.symbol}", e)
            }
        }
        
        // 尋找套利機會
        chainPrices.forEach { (buyChain, buyPrice) ->
            chainPrices.forEach { (sellChain, sellPrice) ->
                if (buyChain != sellChain && buyPrice > 0 && sellPrice > 0) {
                    val profitPercentage = (sellPrice - buyPrice) / buyPrice
                    
                    if (profitPercentage > minimumProfit) {
                        opportunities.add(
                            ArbitrageOpportunity(
                                tokenSymbol = tokenSymbol,
                                buyChain = buyChain,
                                sellChain = sellChain,
                                buyPrice = buyPrice.toString(),
                                sellPrice = sellPrice.toString(),
                                profitPercentage = profitPercentage,
                                estimatedProfit = "0", // 需要計算實際數量
                                timestamp = Clock.System.now().toEpochMilliseconds()
                            )
                        )
                    }
                }
            }
        }
        
        return opportunities.sortedByDescending { it.profitPercentage }
    }
    
    // 私有輔助方法
    
    private fun calculateQuoteScore(quote: SwapQuote): Double {
        val outputValue = quote.expectedOutput.toDoubleOrNull() ?: 0.0
        val priceImpact = quote.priceImpact
        val gasValue = quote.estimatedGas.toDoubleOrNull() ?: 150000.0
        
        // 分數計算：輸出越高越好，價格影響和 Gas 越低越好
        return (outputValue * 0.6) - (priceImpact * 1000 * 0.3) - (gasValue / 10000 * 0.1)
    }
    
    private fun calculateSpread(currentPrice: Double, existingPrices: List<DexPriceComparison>): Double {
        if (existingPrices.isEmpty()) return 0.0
        
        val avgPrice = existingPrices.map { 
            it.price.price.toDoubleOrNull() ?: 0.0 
        }.average()
        
        return if (avgPrice > 0) {
            kotlin.math.abs(currentPrice - avgPrice) / avgPrice
        } else {
            0.0
        }
    }
    
    private fun generatePriceComparison(quotes: List<DexQuoteResult>): Map<String, String> {
        val comparison = mutableMapOf<String, String>()
        
        quotes.forEach { quote ->
            comparison[quote.dex.dexName] = quote.quote.expectedOutput
        }
        
        return comparison
    }
    
    private fun generateRecommendation(
        best: DexQuoteResult,
        alternatives: List<DexQuoteResult>
    ): String {
        val bestOutput = best.quote.expectedOutput.toDoubleOrNull() ?: 0.0
        
        return if (alternatives.isNotEmpty()) {
            val secondBest = alternatives.first()
            val secondBestOutput = secondBest.quote.expectedOutput.toDoubleOrNull() ?: 0.0
            val advantage = ((bestOutput - secondBestOutput) / secondBestOutput * 100)
            
            "${best.dex.dexName} offers ${advantage.toString().take(5)}% better output than ${secondBest.dex.dexName}"
        } else {
            "${best.dex.dexName} is the only available option"
        }
    }
}

/**
 * DEX 報價結果
 */
data class DexQuoteResult(
    val dex: DecentralizedExchange,
    val quote: SwapQuote,
    val score: Double
)

/**
 * 聚合交換報價
 */
data class AggregatedSwapQuote(
    val request: SwapRequest,
    val bestQuote: DexQuoteResult,
    val alternativeQuotes: List<DexQuoteResult>,
    val priceComparison: Map<String, String>, // DEX名稱 -> 輸出數量
    val recommendation: String
) {
    /**
     * 取得所有報價
     */
    val allQuotes: List<DexQuoteResult>
        get() = listOf(bestQuote) + alternativeQuotes
    
    /**
     * 最佳輸出數量
     */
    val bestOutput: String
        get() = bestQuote.quote.expectedOutput
    
    /**
     * 價格優勢百分比
     */
    fun getPriceAdvantage(): Double {
        if (alternativeQuotes.isEmpty()) return 0.0
        
        val bestOutput = bestQuote.quote.expectedOutput.toDoubleOrNull() ?: 0.0
        val secondBest = alternativeQuotes.first().quote.expectedOutput.toDoubleOrNull() ?: 0.0
        
        return if (secondBest > 0) {
            (bestOutput - secondBest) / secondBest * 100
        } else {
            0.0
        }
    }
}

/**
 * 交換執行結果
 */
data class SwapResult(
    val success: Boolean,
    val transactionData: String?,
    val dexUsed: String,
    val expectedOutput: String,
    val actualOutput: String? = null,
    val message: String,
    val error: Throwable? = null
)

/**
 * DEX 價格比較
 */
data class DexPriceComparison(
    val dexName: String,
    val price: DexPrice,
    val spreadPercentage: Double
)

/**
 * 聚合流動性資訊
 */
data class AggregatedLiquidity(
    val tokenA: String,
    val tokenB: String,
    val chainType: MultiChainType,
    val totalLiquidity: String,
    val totalVolume24h: String,
    val pools: List<LiquidityPool>,
    val dominantDex: String? // 流動性最大的 DEX
)

/**
 * 套利機會
 */
data class ArbitrageOpportunity(
    val tokenSymbol: String,
    val buyChain: MultiChainType,
    val sellChain: MultiChainType,
    val buyPrice: String,
    val sellPrice: String,
    val profitPercentage: Double,
    val estimatedProfit: String,
    val timestamp: Long
) {
    /**
     * 格式化利潤百分比
     */
    fun getFormattedProfitPercentage(): String {
        return "${(profitPercentage * 100).toString().take(5)}%"
    }
}

/**
 * DEX 聚合器工廠
 */
object DexAggregatorFactory {
    
    /**
     * 創建預設的 DEX 聚合器
     * 包含所有支援的 DEX
     */
    fun createDefaultAggregator(): DexAggregator {
        val dexes = listOf(
            UniswapV3(),
            SerumDex()
            // 可以添加更多 DEX：
            // SushiSwap(),
            // PancakeSwap(),
            // Curve(),
            // Balancer()
        )
        
        return DexAggregator(dexes)
    }
    
    /**
     * 創建特定鏈的 DEX 聚合器
     */
    fun createChainSpecificAggregator(chainType: MultiChainType): DexAggregator {
        val dexes = when (chainType) {
            MultiChainType.ETHEREUM -> listOf(
                UniswapV3()
                // SushiSwap(),
                // Curve(),
                // Balancer()
            )
            MultiChainType.SOLANA -> listOf(
                SerumDex()
                // Orca(),
                // Raydium()
            )
            // 其他鏈的 DEX
            else -> emptyList()
        }
        
        return DexAggregator(dexes)
    }
    
    /**
     * 創建自訂 DEX 組合的聚合器
     */
    fun createCustomAggregator(dexes: List<DecentralizedExchange>): DexAggregator {
        return DexAggregator(dexes)
    }
}
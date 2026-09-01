package com.cbstudio.wearwallet.core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import com.cbstudio.wearwallet.core.common.Result

/**
 * 價格 API 客戶端
 * 參考 sharedKmp 實現，提供加密貨幣價格查詢功能
 */
class PriceApiClient(
    private val httpClient: HttpClient
) {
    companion object {
        private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3"
        private const val RATE_LIMIT_DELAY_MS = 2000L // 確保不超過 30 calls/min
        
        // 常見加密貨幣 ID 映射
        private val COIN_ID_MAP = mapOf(
            "ETH" to "ethereum",
            "BTC" to "bitcoin",
            "BNB" to "binancecoin",
            "MATIC" to "matic-network",
            "USDC" to "usd-coin",
            "USDT" to "tether",
            "DAI" to "dai",
            "WBTC" to "wrapped-bitcoin",
            "AVAX" to "avalanche-2",
            "SOL" to "solana",
            "FTM" to "fantom",
            "CRO" to "crypto-com-chain",
            "ATOM" to "cosmos",
            "DOT" to "polkadot",
            "LINK" to "chainlink",
            "UNI" to "uniswap",
            "AAVE" to "aave",
            "SUSHI" to "sushi",
            "COMP" to "compound-governance-token",
            "MKR" to "maker",
            "TRX" to "tron",
            "ADA" to "cardano",
            "XMR" to "monero",
            "LTC" to "litecoin",
            "DOGE" to "dogecoin",
            "BCH" to "bitcoin-cash"
        )
    }
    
    /**
     * 獲取簡單價格數據
     */
    suspend fun getSimplePrice(
        symbols: List<String>,
        vsCurrency: String = "usd",
        include24hrChange: Boolean = true
    ): Result<Map<String, PriceData>> {
        return try {
            // 將符號轉換為 CoinGecko ID
            val coinIds = symbols.mapNotNull { symbol ->
                COIN_ID_MAP[symbol.uppercase()] ?: symbol.lowercase()
            }.joinToString(",")
            
            if (coinIds.isEmpty()) {
                return Result.Success(emptyMap())
            }
            
            val response: JsonObject = httpClient.get("$COINGECKO_BASE_URL/simple/price") {
                parameter("ids", coinIds)
                parameter("vs_currencies", vsCurrency)
                parameter("include_24hr_change", include24hrChange)
                parameter("include_market_cap", true)
                parameter("include_24hr_vol", true)
                parameter("precision", "full")
            }.body()
            
            val priceDataMap = mutableMapOf<String, PriceData>()
            
            response.forEach { (coinId, data) ->
                val priceObj = data as? JsonObject ?: return@forEach
                
                // 找回原始符號
                val symbol = COIN_ID_MAP.entries.find { it.value == coinId }?.key
                    ?: coinId.uppercase()
                
                val price = priceObj[vsCurrency]?.jsonPrimitive?.doubleOrNull
                val change24h = priceObj["${vsCurrency}_24h_change"]?.jsonPrimitive?.doubleOrNull
                val marketCap = priceObj["${vsCurrency}_market_cap"]?.jsonPrimitive?.doubleOrNull
                val volume24h = priceObj["${vsCurrency}_24h_vol"]?.jsonPrimitive?.doubleOrNull
                
                if (price != null) {
                    priceDataMap[symbol] = PriceData(
                        symbol = symbol,
                        price = price,
                        change24h = change24h,
                        changePercent24h = change24h,
                        marketCap = marketCap,
                        volume24h = volume24h,
                        currency = vsCurrency.uppercase()
                    )
                }
            }
            
            Result.Success(priceDataMap)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 根據合約地址獲取代幣價格
     */
    suspend fun getTokenPriceByContract(
        platform: String,
        contractAddress: String,
        vsCurrency: String = "usd"
    ): Result<PriceData> {
        return try {
            val response: JsonObject = httpClient.get("$COINGECKO_BASE_URL/simple/token_price/$platform") {
                parameter("contract_addresses", contractAddress)
                parameter("vs_currencies", vsCurrency)
                parameter("include_24hr_change", true)
                parameter("include_market_cap", true)
                parameter("include_24hr_vol", true)
            }.body()
            
            val tokenData = response[contractAddress.lowercase()] as? JsonObject
                ?: return Result.Failure(Exception("Token not found"))
            
            val price = tokenData[vsCurrency]?.jsonPrimitive?.doubleOrNull
                ?: return Result.Failure(Exception("Price not available"))
            
            val priceData = PriceData(
                symbol = contractAddress.take(6).uppercase(),
                price = price,
                change24h = tokenData["${vsCurrency}_24h_change"]?.jsonPrimitive?.doubleOrNull,
                changePercent24h = tokenData["${vsCurrency}_24h_change"]?.jsonPrimitive?.doubleOrNull,
                marketCap = tokenData["${vsCurrency}_market_cap"]?.jsonPrimitive?.doubleOrNull,
                volume24h = tokenData["${vsCurrency}_24h_vol"]?.jsonPrimitive?.doubleOrNull,
                currency = vsCurrency.uppercase()
            )
            
            Result.Success(priceData)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取市場數據（前 N 個加密貨幣）
     */
    suspend fun getMarketData(
        vsCurrency: String = "usd",
        perPage: Int = 100,
        page: Int = 1
    ): Result<List<MarketData>> {
        return try {
            val response: JsonArray = httpClient.get("$COINGECKO_BASE_URL/coins/markets") {
                parameter("vs_currency", vsCurrency)
                parameter("order", "market_cap_desc")
                parameter("per_page", perPage)
                parameter("page", page)
                parameter("sparkline", false)
            }.body()
            
            val marketDataList = response.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                
                MarketData(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    symbol = obj["symbol"]?.jsonPrimitive?.content?.uppercase() ?: "",
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    image = obj["image"]?.jsonPrimitive?.content,
                    currentPrice = obj["current_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    marketCap = obj["market_cap"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    marketCapRank = obj["market_cap_rank"]?.jsonPrimitive?.intOrNull ?: 0,
                    totalVolume = obj["total_volume"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    high24h = obj["high_24h"]?.jsonPrimitive?.doubleOrNull,
                    low24h = obj["low_24h"]?.jsonPrimitive?.doubleOrNull,
                    priceChange24h = obj["price_change_24h"]?.jsonPrimitive?.doubleOrNull,
                    priceChangePercentage24h = obj["price_change_percentage_24h"]?.jsonPrimitive?.doubleOrNull,
                    ath = obj["ath"]?.jsonPrimitive?.doubleOrNull,
                    athChangePercentage = obj["ath_change_percentage"]?.jsonPrimitive?.doubleOrNull,
                    atl = obj["atl"]?.jsonPrimitive?.doubleOrNull,
                    atlChangePercentage = obj["atl_change_percentage"]?.jsonPrimitive?.doubleOrNull
                )
            }
            
            Result.Success(marketDataList)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取歷史價格數據
     */
    suspend fun getHistoricalPrice(
        coinId: String,
        vsCurrency: String = "usd",
        days: Int = 7
    ): Result<List<PricePoint>> {
        return try {
            val response: JsonObject = httpClient.get("$COINGECKO_BASE_URL/coins/$coinId/market_chart") {
                parameter("vs_currency", vsCurrency)
                parameter("days", days)
            }.body()
            
            val prices = response["prices"] as? JsonArray
                ?: return Result.Failure(Exception("No price data"))
            
            val pricePoints = prices.mapNotNull { element ->
                val array = element as? JsonArray ?: return@mapNotNull null
                val timestamp = array.getOrNull(0)?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val price = array.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                
                PricePoint(timestamp, price)
            }
            
            Result.Success(pricePoints)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}

/**
 * 價格數據
 */
data class PriceData(
    val symbol: String,
    val price: Double,
    val change24h: Double? = null,
    val changePercent24h: Double? = null,
    val marketCap: Double? = null,
    val volume24h: Double? = null,
    val currency: String = "USD"
)

/**
 * 市場數據
 */
data class MarketData(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String? = null,
    val currentPrice: Double,
    val marketCap: Double,
    val marketCapRank: Int,
    val totalVolume: Double,
    val high24h: Double? = null,
    val low24h: Double? = null,
    val priceChange24h: Double? = null,
    val priceChangePercentage24h: Double? = null,
    val ath: Double? = null,
    val athChangePercentage: Double? = null,
    val atl: Double? = null,
    val atlChangePercentage: Double? = null
)

/**
 * 價格點（用於歷史數據）
 */
data class PricePoint(
    val timestamp: Long,
    val price: Double
)
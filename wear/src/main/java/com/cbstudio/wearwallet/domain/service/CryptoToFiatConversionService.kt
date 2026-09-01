package com.cbstudio.wearwallet.domain.service

import com.cbstudio.wearwallet.shared.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import javax.inject.Singleton

/**
 * 加密貨幣到法幣即時轉換服務
 * 提供實時匯率查詢和轉換功能
 */
@Singleton
class CryptoToFiatConversionService constructor() {
    
    companion object {
        private const val TAG = "CryptoToFiatConversionService"
        
        // 支援的法幣
        val SUPPORTED_FIAT = listOf("USD", "EUR", "GBP", "JPY", "CNY", "HKD", "TWD")
        
        // 支援的加密貨幣
        val SUPPORTED_CRYPTO = listOf(
            "BTC", "ETH", "BNB", "MATIC", "CRO", 
            "USDT", "USDC", "DAI", "BUSD"
        )
        
        // 穩定幣映射
        private val STABLECOIN_RATES = mapOf(
            "USDT" to "USD",
            "USDC" to "USD",
            "DAI" to "USD",
            "BUSD" to "USD",
            "EURS" to "EUR"
        )
    }
    
    // 匯率快取
    private val _exchangeRates = MutableStateFlow<Map<String, BigDecimal>>(emptyMap())
    val exchangeRates = _exchangeRates.asStateFlow()
    
    // 最後更新時間
    private var lastUpdateTime = 0L
    private val UPDATE_INTERVAL = 60_000L // 1分鐘更新一次
    
    /**
     * 轉換加密貨幣到法幣
     */
    suspend fun convert(
        amount: BigDecimal,
        fromCurrency: String,
        toCurrency: String
    ): BigDecimal = withContext(Dispatchers.IO) {
        try {
            // 檢查是否是穩定幣
            if (isStablecoin(fromCurrency, toCurrency)) {
                return@withContext convertStablecoin(amount, fromCurrency, toCurrency)
            }
            
            // 更新匯率（如果需要）
            updateRatesIfNeeded()
            
            // 獲取匯率
            val rate = getExchangeRate(fromCurrency, toCurrency)
            
            // 計算轉換金額
            val convertedAmount = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
            
            Logger.d(TAG, "轉換 $amount $fromCurrency = $convertedAmount $toCurrency (匯率: $rate)")
            
            convertedAmount
            
        } catch (e: Exception) {
            Logger.e(TAG, "轉換失敗", e)
            throw e
        }
    }
    
    /**
     * 批量轉換
     */
    suspend fun convertBatch(
        amounts: Map<String, BigDecimal>,
        toCurrency: String
    ): BigDecimal = withContext(Dispatchers.IO) {
        var total = BigDecimal.ZERO
        
        amounts.forEach { (currency, amount) ->
            val converted = convert(amount, currency, toCurrency)
            total = total.add(converted)
        }
        
        total
    }
    
    /**
     * 獲取特定交易對的匯率
     */
    suspend fun getRate(fromCurrency: String, toCurrency: String): BigDecimal {
        updateRatesIfNeeded()
        return getExchangeRate(fromCurrency, toCurrency)
    }
    
    /**
     * 獲取多個匯率
     */
    suspend fun getRates(
        baseCurrency: String,
        targetCurrencies: List<String>
    ): Map<String, BigDecimal> {
        updateRatesIfNeeded()
        
        return targetCurrencies.associateWith { target ->
            getExchangeRate(baseCurrency, target)
        }
    }
    
    /**
     * 計算手續費
     */
    fun calculateConversionFee(
        amount: BigDecimal,
        feePercentage: BigDecimal = BigDecimal("0.02") // 2% 預設
    ): ConversionFee {
        val fee = amount.multiply(feePercentage)
        val netAmount = amount.subtract(fee)
        
        return ConversionFee(
            grossAmount = amount,
            feeAmount = fee,
            feePercentage = feePercentage,
            netAmount = netAmount
        )
    }
    
    /**
     * 獲取歷史匯率（用於圖表）
     */
    suspend fun getHistoricalRates(
        fromCurrency: String,
        toCurrency: String,
        days: Int = 7
    ): List<HistoricalRate> {
        // TODO: 實作歷史匯率查詢
        // 暫時返回模擬數據
        val currentRate = getExchangeRate(fromCurrency, toCurrency)
        val rates = mutableListOf<HistoricalRate>()
        
        for (i in 0 until days) {
            val timestamp = System.currentTimeMillis() - (i * 24 * 60 * 60 * 1000)
            val variation = (0.95 + Math.random() * 0.1).toBigDecimal()
            val rate = currentRate.multiply(variation)
            
            rates.add(
                HistoricalRate(
                    timestamp = timestamp,
                    rate = rate,
                    volume = BigDecimal((1000000 * Math.random()).toLong())
                )
            )
        }
        
        return rates.reversed()
    }
    
    // 私有輔助方法
    
    private suspend fun updateRatesIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime > UPDATE_INTERVAL) {
            updateExchangeRates()
            lastUpdateTime = now
        }
    }
    
    private suspend fun updateExchangeRates() {
        try {
            // 使用 KMP 的 GetTokenPriceUseCase 獲取真實價格
            val priceApiClient: com.cbstudio.wearwallet.core.network.PriceApiClient = 
                org.koin.core.context.GlobalContext.get().get()
            
            val pricesResult = priceApiClient.getSimplePrice(SUPPORTED_CRYPTO)
            
            when (pricesResult) {
                is com.cbstudio.wearwallet.core.common.Result.Success -> {
                    val realRates = mutableMapOf<String, BigDecimal>()
                    
                    pricesResult.data.forEach { (symbol, priceData) ->
                        realRates["${symbol.uppercase()}_USD"] = BigDecimal(priceData.price.toString())
                    }
                    
                    // 法幣匯率 (可以從其他 API 獲取，暫時使用近似值)
                    // TODO: 整合真實法幣匯率 API
                    realRates["EUR_USD"] = BigDecimal("1.08")
                    realRates["GBP_USD"] = BigDecimal("1.26")
                    realRates["JPY_USD"] = BigDecimal("0.0067")
                    realRates["CNY_USD"] = BigDecimal("0.14")
                    realRates["HKD_USD"] = BigDecimal("0.128")
                    realRates["TWD_USD"] = BigDecimal("0.031")
                    
                    _exchangeRates.value = realRates
                    Logger.d(TAG, "真實匯率更新完成，共 ${realRates.size} 個交易對")
                }
                is com.cbstudio.wearwallet.core.common.Result.Failure -> {
                    Logger.e(TAG, "價格 API 請求失敗，使用緩存", pricesResult.exception)
                }
                else -> {
                    Logger.w(TAG, "價格 API 返回未知狀態")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "更新匯率異常", e)
        }
    }
    
    private fun getExchangeRate(from: String, to: String): BigDecimal {
        // 相同貨幣
        if (from == to) return BigDecimal.ONE
        
        val rates = _exchangeRates.value
        
        // 直接匯率
        val directRate = rates["${from}_$to"]
        if (directRate != null) return directRate
        
        // 反向匯率
        val reverseRate = rates["${to}_$from"]
        if (reverseRate != null) {
            return BigDecimal.ONE.divide(reverseRate, 8, RoundingMode.HALF_UP)
        }
        
        // 通過 USD 中轉
        val fromToUsd = rates["${from}_USD"] ?: rates["USD_$from"]?.let { 
            BigDecimal.ONE.divide(it, 8, RoundingMode.HALF_UP) 
        }
        
        val usdToTarget = rates["USD_$to"] ?: rates["${to}_USD"]?.let { 
            BigDecimal.ONE.divide(it, 8, RoundingMode.HALF_UP) 
        }
        
        if (fromToUsd != null && usdToTarget != null) {
            return fromToUsd.multiply(usdToTarget)
        }
        
        // 如果找不到匯率，拋出異常
        throw IllegalArgumentException("找不到 $from 到 $to 的匯率")
    }
    
    private fun isStablecoin(from: String, to: String): Boolean {
        val stablecoinFiat = STABLECOIN_RATES[from]
        return stablecoinFiat != null && stablecoinFiat == to
    }
    
    private fun convertStablecoin(
        amount: BigDecimal, 
        from: String, 
        to: String
    ): BigDecimal {
        // 穩定幣 1:1 轉換
        return if (STABLECOIN_RATES[from] == to) {
            amount
        } else {
            // 如果不是對應的法幣，使用正常匯率
            getExchangeRate(from, to).multiply(amount)
        }
    }
}

/**
 * 轉換手續費
 */
data class ConversionFee(
    val grossAmount: BigDecimal,
    val feeAmount: BigDecimal,
    val feePercentage: BigDecimal,
    val netAmount: BigDecimal
)

/**
 * 歷史匯率
 */
data class HistoricalRate(
    val timestamp: Long,
    val rate: BigDecimal,
    val volume: BigDecimal
)

/**
 * 匯率更新事件
 */
sealed class RateUpdateEvent {
    object Loading : RateUpdateEvent()
    data class Success(val rates: Map<String, BigDecimal>) : RateUpdateEvent()
    data class Error(val message: String) : RateUpdateEvent()
}

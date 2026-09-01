package com.cbstudio.wearwallet.core.domain.repository

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.flow.Flow

/**
 * 價格資料 Repository 介面
 * 
 * Created: 2025-01-17
 */
interface PriceRepository {
    
    /**
     * 獲取單個代幣價格
     */
    suspend fun getTokenPrice(
        symbol: String,
        currency: String = "USD"
    ): Result<Double>
    
    /**
     * 批量獲取代幣價格
     */
    suspend fun getBatchPrices(
        symbols: List<String>,
        currency: String = "USD"
    ): Result<Map<String, Double>>
    
    /**
     * 觀察價格變化
     */
    fun observePrices(
        symbols: List<String>,
        currency: String = "USD"
    ): Flow<Map<String, Double>>
    
    /**
     * 獲取歷史價格
     */
    suspend fun getHistoricalPrice(
        symbol: String,
        timestamp: Long,
        currency: String = "USD"
    ): Result<Double>
    
    /**
     * 獲取價格變化
     */
    suspend fun getPriceChange(
        symbol: String,
        period: PricePeriod,
        currency: String = "USD"
    ): Result<PriceChangeInfo>
}

/**
 * 價格週期
 */
enum class PricePeriod {
    HOUR_1,
    HOUR_24,
    DAY_7,
    DAY_30,
    YEAR_1
}

/**
 * 價格變化資訊
 */
data class PriceChangeInfo(
    val symbol: String,
    val currentPrice: Double,
    val previousPrice: Double,
    val changeAmount: Double,
    val changePercent: Double,
    val period: PricePeriod
)
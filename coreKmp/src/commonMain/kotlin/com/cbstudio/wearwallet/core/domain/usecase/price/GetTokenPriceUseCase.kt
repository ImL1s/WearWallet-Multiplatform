package com.cbstudio.wearwallet.core.domain.usecase.price

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.network.PriceApiClient
import com.cbstudio.wearwallet.core.network.PriceData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 獲取代幣價格 Use Case
 * 參考 sharedKmp 實現
 */
class GetTokenPriceUseCase(
    private val tokenRepository: TokenRepository,
    private val priceApiClient: PriceApiClient
) {
    /**
     * 獲取單個代幣價格
     */
    suspend fun getPrice(symbol: String): Result<Double> {
        val price = tokenRepository.getTokenPrice(symbol)
        return if (price != null) {
            Result.Success(price)
        } else {
            Result.Failure(Exception("Price not available for $symbol"))
        }
    }
    
    /**
     * 獲取多個代幣價格
     */
    suspend fun getPrices(symbols: List<String>): Result<Map<String, PriceData>> {
        return priceApiClient.getSimplePrice(symbols)
    }
    
    /**
     * 觀察代幣價格變化（定期更新）
     */
    fun observePrice(symbol: String, intervalMs: Long = 60000): Flow<Result<Double>> = flow {
        while (true) {
            emit(getPrice(symbol))
            kotlinx.coroutines.delay(intervalMs)
        }
    }
    
    /**
     * 計算錢包總價值
     */
    suspend fun calculatePortfolioValue(
        holdings: Map<String, Double>
    ): Result<Double> {
        val symbols = holdings.keys.toList()
        val pricesResult = getPrices(symbols)
        
        return when (pricesResult) {
            is Result.Success -> {
                var totalValue = 0.0
                holdings.forEach { (symbol, amount) ->
                    val price = pricesResult.data[symbol.uppercase()]?.price ?: 0.0
                    totalValue += amount * price
                }
                Result.Success(totalValue)
            }
            is Result.Failure -> Result.Failure(pricesResult.error)
            is Result.Loading -> Result.Loading()
        }
    }
}
package com.cbstudio.wearwallet.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal

class GetTokenPriceUseCase {
    fun getTokenPriceWithTrend(symbol: String): Flow<PriceInfo> = 
        flowOf(PriceInfo(BigDecimal.ZERO, BigDecimal.ZERO))
    fun getPortfolioPriceSummary(): Flow<PortfolioSummary> = 
        flowOf(PortfolioSummary(BigDecimal.ZERO, BigDecimal.ZERO))
    fun getPriceDescription(price: PriceInfo): String = ""
    fun getPortfolioDescription(summary: PortfolioSummary): String = ""
}

data class PriceInfo(val formattedPrice: BigDecimal, val change24h: BigDecimal)
data class PortfolioSummary(val totalValue: BigDecimal, val change24h: BigDecimal)

package com.cbstudio.wearwallet.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal

class GetWhaleTrackingUseCase {
    fun getWhaleActivitySummary(timeframe: Timeframe): Flow<WhaleActivitySummary> = 
        flowOf(WhaleActivitySummary(timeframe, "Neutral", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, emptyList(), emptyList()))
}

enum class Timeframe { HOUR, DAY, WEEK, MONTH }

data class WhaleActivitySummary(
    val timeframe: Timeframe,
    val sentiment: String,
    val totalVolume: BigDecimal,
    val buyVolume: BigDecimal,
    val sellVolume: BigDecimal,
    val hotTokens: List<HotToken>,
    val alerts: List<Alert>
)

data class HotToken(val symbol: String, val volume: BigDecimal)
data class Alert(val message: String, val severity: String)

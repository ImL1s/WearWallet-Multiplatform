package com.cbstudio.wearwallet.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal

class GetDeFiYieldUseCase {
    fun getBestYieldRecommendations(riskTolerance: RiskTolerance): Flow<YieldRecommendations> = 
        flowOf(YieldRecommendations(emptyList()))
}

enum class RiskTolerance { LOW, MEDIUM, HIGH }

data class YieldRecommendations(
    val topProtocols: List<YieldProtocol>
)

data class YieldProtocol(
    val protocol: String,
    val apr: BigDecimal,
    val risk: String
)

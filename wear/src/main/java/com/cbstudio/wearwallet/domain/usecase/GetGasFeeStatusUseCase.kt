package com.cbstudio.wearwallet.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal

class GetGasFeeStatusUseCase {
    fun getCurrentGasFeeStatus(): Flow<GasFeeStatus> = 
        flowOf(GasFeeStatus("Ethereum", BigDecimal.ZERO, NetworkStatus.NORMAL, 30))
    fun getMultiChainGasFeeComparison(): Flow<List<ChainGasFeeComparison>> = 
        flowOf(emptyList())
}

data class GasFeeStatus(
    val chain: String,
    val standardGasFee: BigDecimal,
    val networkStatus: NetworkStatus,
    val estimatedConfirmationTime: Int
)

enum class NetworkStatus { LOW, NORMAL, CONGESTED, VERY_CONGESTED, CRITICAL }

data class ChainGasFeeComparison(
    val chain: String,
    val standardGasFee: BigDecimal,
    val networkStatus: NetworkStatus,
    val estimatedConfirmationTime: Int
)

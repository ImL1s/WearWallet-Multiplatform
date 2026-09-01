package com.cbstudio.wearwallet.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal

class GetBalanceUseCase {
    fun observeBalance(): Flow<BalanceInfo> = flowOf(BalanceInfo(BigDecimal.ZERO, "ETH", "Ethereum"))
}

data class BalanceInfo(val amount: BigDecimal, val token: String, val chain: String)

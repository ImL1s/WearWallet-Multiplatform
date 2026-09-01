package com.cbstudio.wearwallet.core.domain.usecase.swap

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.rango.RangoRepository
import com.cbstudio.wearwallet.core.rango.model.RangoQuoteResponse
import com.cbstudio.wearwallet.core.rango.model.RangoTokenMeta

class GetSwapQuoteUseCase(
    private val rangoRepository: RangoRepository
) {
    suspend operator fun invoke(params: Params): Result<RangoQuoteResponse> {
        val result = rangoRepository.getSwapQuote(
            fromChain = params.fromToken.blockchain,
            fromTokenSymbol = if (params.fromToken.isNative) null else params.fromToken.address,
            toChain = params.toToken.blockchain,
            toTokenSymbol = if (params.toToken.isNative) null else params.toToken.address,
            amount = params.amountInWei,
            slippage = params.slippage
        )

        return result.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { Result.Failure(it as? Exception ?: Exception(it)) }
        )
    }

    data class Params(
        val fromToken: RangoTokenMeta,
        val toToken: RangoTokenMeta,
        val amountInWei: String,
        val slippage: Double = 1.0
    )
}

package com.cbstudio.wearwallet.core.zerox

import com.cbstudio.wearwallet.core.zerox.model.ZeroXPriceResponse
import com.cbstudio.wearwallet.core.zerox.model.ZeroXQuoteResponse

class ZeroXRepository(private val client: ZeroXClient) {

    suspend fun getSwapQuote(
        chainId: Int,
        sellTokenAddress: String,
        buyTokenAddress: String,
        sellAmount: String,
        takerAddress: String
    ): Result<ZeroXQuoteResponse> {
        return try {
            val response = client.getQuote(
                chainId = chainId,
                sellToken = sellTokenAddress,
                buyToken = buyTokenAddress,
                sellAmount = sellAmount,
                takerAddress = takerAddress
            )
            Result.success(response)
        } catch (e: Exception) {
            // Need better error parsing properly for 0x (they return JSON error)
            // For now, pass exception
            Result.failure(e)
        }
    }

    suspend fun getSwapPrice(
        chainId: Int,
        sellTokenAddress: String,
        buyTokenAddress: String,
        sellAmount: String
    ): Result<ZeroXPriceResponse> {
        return try {
            val response = client.getPrice(
                chainId = chainId,
                sellToken = sellTokenAddress,
                buyToken = buyTokenAddress,
                sellAmount = sellAmount
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.cbstudio.wearwallet.core.zerox

import com.cbstudio.wearwallet.core.network.ApiConfig
import com.cbstudio.wearwallet.core.zerox.model.ZeroXPriceResponse
import com.cbstudio.wearwallet.core.zerox.model.ZeroXQuoteResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ZeroXClient {
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    /**
     * Fetch a firm quote for a swap.
     * Uses /swap/permit2/quote if Permit2 is desired, or logic to fallback.
     * For simplicity, using /swap/v1/quote which redirects to v2 logic internally often, 
     * but standard v2 endpoint is clearer. documentation says /swap/permit2/quote OR /swap/allowance-holder/quote
     * Let's use the generic "client-side-router" approach or just standard swap endpoint if available.
     * 0x docs mention `https://api.0x.org/swap/permit2/quote`
     */
    suspend fun getQuote(
        chainId: Int,
        sellToken: String,
        buyToken: String,
        sellAmount: String,
        takerAddress: String,
        slippagePercentage: Double? = 0.01, // 1% default
        swapFeeRecipient: String? = ApiConfig.zeroXFeeRecipient,
        swapFeeBps: Int? = ApiConfig.zeroXFeeBps,
        skipValidation: Boolean = false
    ): ZeroXQuoteResponse {
        val endpoint = "swap/allowance-holder/quote" 
        val url = "${ApiConfig.ZEROX_BASE_URL}/$endpoint"
        
        return httpClient.get(url) {
            header("0x-api-key", ApiConfig.zeroXApiKey)
            header("0x-version", "v2")
            
            parameter("chainId", chainId)
            parameter("sellToken", sellToken)
            parameter("buyToken", buyToken)
            parameter("sellAmount", sellAmount)
            parameter("taker", takerAddress)
            
            if (slippagePercentage != null) {
                parameter("slippagePercentage", slippagePercentage)
            }
            
            if (skipValidation) {
                parameter("skipValidation", true)
            }
            
            if (!swapFeeRecipient.isNullOrEmpty() && swapFeeBps != null && swapFeeBps > 0) {
                parameter("swapFeeRecipient", swapFeeRecipient)
                parameter("swapFeeBps", swapFeeBps)
                parameter("swapFeeToken", sellToken)
            }
        }.body()
    }

    suspend fun getPrice(
        chainId: Int,
        sellToken: String,
        buyToken: String,
        sellAmount: String,
        takerAddress: String? = null // Optional for price
    ): ZeroXPriceResponse {
        val endpoint = "swap/allowance-holder/price"
        val url = "${ApiConfig.ZEROX_BASE_URL}/$endpoint"
        
        return httpClient.get(url) {
            header("0x-api-key", ApiConfig.zeroXApiKey)
            header("0x-version", "v2")
            
            parameter("chainId", chainId)
            parameter("sellToken", sellToken)
            parameter("buyToken", buyToken)
            parameter("sellAmount", sellAmount)
            if (takerAddress != null) {
                parameter("taker", takerAddress)
            }
        }.body()
    }
}

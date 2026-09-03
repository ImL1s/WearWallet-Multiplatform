package com.cbstudio.wearwallet.core.rango

import com.cbstudio.wearwallet.core.network.ApiConfig
import com.cbstudio.wearwallet.core.rango.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Rango Exchange API Client - Basic API (Single Step)
 * 
 * API Flow:
 * 1. /basic/quote - Get quote preview (optional, for preview only)
 * 2. /basic/swap - Get transaction data (REQUIRED for execution)
 * 3. Sign and broadcast the transaction
 * 4. /basic/status - Check transaction status periodically
 * 
 * Docs: https://docs.rango.exchange/api-integration/basic-api-single-step
 */
class RangoClient(
    private val httpClient: HttpClient
) {
    
    companion object {
        const val BASE_URL = "https://api.rango.exchange"
    }

    /**
     * Step 1: Get Quote (Preview)
     * 
     * Use this to show the user an estimated output before they commit.
     * This is READ-ONLY and does not create a transaction.
     * 
     * @param fromChain Source blockchain (e.g., "BSC", "POLYGON", "ARBITRUM")
     * @param fromToken Source token symbol or null for native (e.g., "BNB", null for native)
     * @param toChain Destination blockchain
     * @param toToken Destination token symbol or address
     * @param amount Amount in Wei/base units (machine readable, e.g., "1000000000000000000" for 1 token with 18 decimals)
     * @param slippage User's preferred slippage percentage (e.g., 1.0 = 1%)
     */
    suspend fun getQuote(
        fromChain: String,
        fromToken: String?,
        toChain: String,
        toToken: String?,
        amount: String,
        slippage: Double = 1.0,
        referrerCode: String? = null,
        referrerFee: Double? = null
    ): RangoQuoteResponse {
        val url = "$BASE_URL/basic/quote"
        
        val fromAsset = formatAsset(fromChain, fromToken)
        val toAsset = formatAsset(toChain, toToken)
        
        return httpClient.get(url) {
            parameter("apiKey", ApiConfig.rangoApiKey)
            parameter("from", fromAsset)
            parameter("to", toAsset)
            parameter("amount", amount)
            parameter("slippage", slippage)
            
            if (!referrerCode.isNullOrEmpty()) {
                parameter("referrerCode", referrerCode)
            }
            if (referrerFee != null && referrerFee > 0) {
                parameter("referrerFee", referrerFee)
            }
        }.body()
    }
    
    /**
     * Step 2: Create Swap Transaction
     * 
     * This is REQUIRED for execution. It returns the actual transaction data.
     * Must be called after getQuote when user is ready to execute.
     * 
     * @param fromChain Source blockchain
     * @param fromToken Source token symbol or null for native
     * @param toChain Destination blockchain
     * @param toToken Destination token symbol or address
     * @param amount Amount in Wei/base units
     * @param fromAddress User's wallet address on source chain
     * @param toAddress User's wallet address on destination chain (can be same)
     * @param slippage User's preferred slippage percentage
     * @param disableEstimate Set true to skip balance/fee validation (faster response)
     */
    suspend fun createSwapTransaction(
        fromChain: String,
        fromToken: String?,
        toChain: String,
        toToken: String?,
        amount: String,
        fromAddress: String,
        toAddress: String,
        slippage: Double = 1.0,
        disableEstimate: Boolean = true,
        referrerAddress: String? = null,
        referrerFee: Double? = null
    ): RangoSwapResponse {
        val url = "$BASE_URL/basic/swap"
        
        val fromAsset = formatAsset(fromChain, fromToken)
        val toAsset = formatAsset(toChain, toToken)
        
        return httpClient.get(url) {
            parameter("apiKey", ApiConfig.rangoApiKey)
            parameter("from", fromAsset)
            parameter("to", toAsset)
            parameter("amount", amount)
            parameter("slippage", slippage)
            parameter("fromAddress", fromAddress)
            parameter("toAddress", toAddress)
            parameter("disableEstimate", disableEstimate)
            
            if (!referrerAddress.isNullOrEmpty()) {
                parameter("referrerAddress", referrerAddress)
            }
            if (referrerFee != null && referrerFee > 0) {
                parameter("referrerFee", referrerFee)
            }
        }.body()
    }
    
    /**
     * Step 3: Check Transaction Status
     * 
     * Call this periodically after submitting the transaction to track progress.
     * For cross-chain swaps, this is essential to know when the destination tx completes.
     * 
     * @param requestId The requestId from swap response
     * @param txId The transaction hash returned by the wallet after signing
     */
    suspend fun checkStatus(
        requestId: String,
        txId: String
    ): RangoStatusResponse {
        val url = "$BASE_URL/basic/status"
        
        return httpClient.get(url) {
            parameter("apiKey", ApiConfig.rangoApiKey)
            parameter("requestId", requestId)
            parameter("txId", txId)
        }.body()
    }
    
    /**
     * Report Transaction Failure
     * 
     * Call this if the transaction fails to submit or is rejected.
     */
    suspend fun reportFailure(
        requestId: String,
        eventType: String = "TX_FAIL",
        reason: String? = null
    ): String {
        val url = "$BASE_URL/basic/report-failure"
        
        return httpClient.get(url) {
            parameter("apiKey", ApiConfig.rangoApiKey)
            parameter("requestId", requestId)
            parameter("eventType", eventType)
            if (!reason.isNullOrEmpty()) {
                parameter("reason", reason)
            }
        }.bodyAsText()
    }
    
    // ============================================
    // Metadata API
    // ============================================
    
    /**
     * Get Full Metadata
     * 
     * Returns all supported blockchains, tokens, and swappers.
     * 
     * @param blockchains Filter to specific blockchains (comma-separated)
     * @param excludeNonPopulars Only return popular tokens (native + stablecoins)
     * @param transactionTypes Filter by transaction type ("EVM", "COSMOS", "TRANSFER")
     */
    suspend fun getMetadata(
        blockchains: List<String>? = null,
        excludeNonPopulars: Boolean = false,
        transactionTypes: List<String>? = null
    ): RangoMetaResponse {
        val url = "$BASE_URL/basic/meta"
        
        return httpClient.get(url) {
            parameter("apiKey", ApiConfig.rangoApiKey)
            
            if (!blockchains.isNullOrEmpty()) {
                parameter("blockchains", blockchains.joinToString(","))
            }
            if (excludeNonPopulars) {
                parameter("excludeNonPopulars", true)
            }
            if (!transactionTypes.isNullOrEmpty()) {
                parameter("transactionTypes", transactionTypes.joinToString(","))
            }
        }.body()
    }
    
    /**
     * Get EVM chains metadata only
     */
    suspend fun getEvmChainsMetadata(
        excludeNonPopulars: Boolean = true
    ): RangoMetaResponse {
        return getMetadata(
            transactionTypes = listOf("EVM"),
            excludeNonPopulars = excludeNonPopulars
        )
    }
    
    /**
     * Get metadata for specific chains
     */
    suspend fun getMetadataForChains(
        chains: List<String>,
        excludeNonPopulars: Boolean = true
    ): RangoMetaResponse {
        return getMetadata(
            blockchains = chains,
            excludeNonPopulars = excludeNonPopulars
        )
    }
    
    /**
     * Get raw metadata response for debugging
     */
    suspend fun getMetadataRaw(
        blockchains: List<String>? = null
    ): String {
        val url = "$BASE_URL/basic/meta"
        return httpClient.get(url) {
            parameter("apiKey", ApiConfig.rangoApiKey)
            if (!blockchains.isNullOrEmpty()) {
                parameter("blockchains", blockchains.joinToString(","))
            }
        }.bodyAsText()
    }
    
    /**
     * Get raw quote response for debugging
     */
    suspend fun getQuoteRaw(
        fromChain: String,
        fromToken: String?,
        toChain: String,
        toToken: String?,
        amount: String
    ): String {
        val url = "$BASE_URL/basic/quote"
        val fromAsset = formatAsset(fromChain, fromToken)
        val toAsset = formatAsset(toChain, toToken)
        
        return httpClient.get(url) {
            parameter("apiKey", ApiConfig.rangoApiKey)
            parameter("from", fromAsset)
            parameter("to", toAsset)
            parameter("amount", amount)
            parameter("slippage", 1.0)
        }.bodyAsText()
    }
    
    /**
     * Format asset string for Rango API.
     * 
     * Native tokens: CHAIN.SYMBOL (e.g., "BSC.BNB", "POLYGON.MATIC")
     * ERC20 tokens: CHAIN--0xaddress (e.g., "BSC--0xe9e7CEA3...")
     * 
     * @param chain The blockchain name (e.g., "BSC", "POLYGON", "ARBITRUM")
     * @param token The token symbol or address. Null/empty for native token.
     */
    private fun formatAsset(chain: String, token: String?): String {
        return when {
            token.isNullOrEmpty() -> {
                // Native token - use chain name as both
                val nativeSymbol = getNativeSymbol(chain)
                "$chain.$nativeSymbol"
            }
            token.startsWith("0x") -> {
                // Token address format
                "$chain--$token"
            }
            else -> {
                // Token symbol format
                "$chain.$token"
            }
        }
    }
    
    /**
     * Get native token symbol for a blockchain
     */
    private fun getNativeSymbol(chain: String): String {
        return when (chain.uppercase()) {
            "BSC" -> "BNB"
            "POLYGON" -> "MATIC"
            "ETH", "ETHEREUM" -> "ETH"
            "ARBITRUM" -> "ETH"
            "OPTIMISM" -> "ETH"
            "AVAX_CCHAIN", "AVALANCHE" -> "AVAX"
            "FANTOM" -> "FTM"
            "BASE" -> "ETH"
            "LINEA" -> "ETH"
            "ZKSYNC" -> "ETH"
            else -> chain
        }
    }
}

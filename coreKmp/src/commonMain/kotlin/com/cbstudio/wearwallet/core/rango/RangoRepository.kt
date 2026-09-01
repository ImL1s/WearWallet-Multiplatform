package com.cbstudio.wearwallet.core.rango

import com.cbstudio.wearwallet.core.rango.model.RangoQuoteResponse
import com.cbstudio.wearwallet.core.rango.model.RangoStatusResponse
import com.cbstudio.wearwallet.core.rango.model.RangoSwapResponse

/**
 * Rango Repository - High-level interface for cross-chain swaps
 * 
 * Flow:
 * 1. getSwapQuote() - Preview the swap
 * 2. createSwapTransaction() - Get transaction data
 * 3. (Sign and broadcast externally)
 * 4. checkStatus() - Track transaction progress
 */
class RangoRepository(
    private val client: RangoClient
) {

    /**
     * Get a quote preview for a cross-chain swap
     */
    suspend fun getSwapQuote(
        fromChain: String,
        fromTokenSymbol: String?,
        toChain: String,
        toTokenSymbol: String?,
        amount: String,
        slippage: Double = 1.0
    ): Result<RangoQuoteResponse> {
        return try {
            val response = client.getQuote(
                fromChain = fromChain,
                fromToken = fromTokenSymbol,
                toChain = toChain,
                toToken = toTokenSymbol,
                amount = amount,
                slippage = slippage
            )
            
            if (response.error != null) {
                Result.failure(Exception(response.error))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a swap transaction with full transaction data
     */
    suspend fun createSwapTransaction(
        fromChain: String,
        fromTokenSymbol: String?,
        toChain: String,
        toTokenSymbol: String?,
        amount: String,
        fromAddress: String,
        toAddress: String,
        slippage: Double = 1.0,
        disableEstimate: Boolean = true
    ): Result<RangoSwapResponse> {
        return try {
            val response = client.createSwapTransaction(
                fromChain = fromChain,
                fromToken = fromTokenSymbol,
                toChain = toChain,
                toToken = toTokenSymbol,
                amount = amount,
                fromAddress = fromAddress,
                toAddress = toAddress,
                slippage = slippage,
                disableEstimate = disableEstimate
            )
            
            if (response.error != null && response.transaction == null) {
                Result.failure(Exception(response.error))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check the status of a submitted transaction
     */
    suspend fun checkStatus(
        requestId: String,
        txHash: String
    ): Result<RangoStatusResponse> {
        return try {
            val response = client.checkStatus(requestId, txHash)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Report a failed transaction
     */
    suspend fun reportFailure(
        requestId: String,
        reason: String? = null
    ): Result<String> {
        return try {
            val response = client.reportFailure(requestId, reason = reason)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

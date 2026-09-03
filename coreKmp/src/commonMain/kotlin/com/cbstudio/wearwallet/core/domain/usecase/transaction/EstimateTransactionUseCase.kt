package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.TransactionEstimate
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.TransactionRequest
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.utils.PerformanceMonitor.withPerformanceMonitoring
import com.cbstudio.wearwallet.core.utils.RetryPolicy
import com.cbstudio.wearwallet.core.utils.withRetryPolicy
import com.cbstudio.wearwallet.core.utils.Logger
import com.cbstudio.wearwallet.core.utils.NetworkException
import com.cbstudio.wearwallet.core.utils.ServiceUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

/**
 * 估算交易費用 Use Case
 * 
 * 提供智能的 Gas 估算和重試機制
 * 
 * Created: 2025-01-17
 */
class EstimateTransactionUseCase(
    private val transactionRepository: TransactionRepository
) {
    /**
     * 估算交易費用
     * 
     * @param fromAddress 發送地址
     * @param toAddress 接收地址
     * @param amount 金額（單位：ETH/BNB 等原生代幣）
     * @param chainType 鏈類型
     * @param data 交易數據（可選，用於智能合約調用）
     * @return 交易費用估算結果
     */
    suspend operator fun invoke(
        fromAddress: String,
        toAddress: String,
        amount: String,
        chainType: ChainType = ChainType.ETHEREUM,
        data: String? = null
    ): Flow<Result<TransactionEstimate>> = flow {
        try {
            // 驗證輸入
            if (!isValidAddress(fromAddress)) {
                emit(Result.Failure(Exception("Invalid from address")))
                return@flow
            }
            
            if (!isValidAddress(toAddress)) {
                emit(Result.Failure(Exception("Invalid to address")))
                return@flow
            }
            
            if (!isValidAmount(amount)) {
                emit(Result.Failure(Exception("Invalid amount")))
                return@flow
            }
            
            Logger.d("EstimateTransaction", 
                "Estimating transaction: $fromAddress -> $toAddress, amount: $amount $chainType")
            
            // 發送載入狀態
            emit(Result.Loading())
            
            // 從 Repository 獲取估算
            val gasEstimate = transactionRepository.estimateGas(
                TransactionRequest(
                    from = fromAddress,
                    to = toAddress,
                    value = amount,
                    chainType = chainType,
                    data = data,
                    gasPrice = null,
                    gasLimit = null,
                    tokenAddress = null
                )
            )
            
            // 創建估算結果
            val gasPriceHex = transactionRepository.getGasPrice(chainType)
            val gasPriceGwei = hexToGwei(gasPriceHex)
            val estimate = TransactionEstimate(
                gasLimit = gasEstimate,
                gasPrice = gasPriceGwei, // Real gas price from oracle
                estimatedFee = calculateFee(gasEstimate, gasPriceGwei),
                chainType = chainType,
                nonce = transactionRepository.getNonce(fromAddress, chainType)
            )
            
            // 驗證估算結果
            val validatedEstimate = validateEstimate(estimate, chainType)
            emit(Result.Success(validatedEstimate))
            
        } catch (e: Exception) {
            Logger.e("EstimateTransaction", "Failed to estimate transaction", e)
            emit(Result.Failure(e))
        }
    }.withRetryPolicy(
        policy = RetryPolicy.Exponential(
            maxAttempts = 3,
            initialDelay = 2.seconds,
            maxDelay = 10.seconds,
            factor = 1.5
        ),
        onRetry = { attempt, error ->
            Logger.d("EstimateTransaction", 
                "Retrying estimation (attempt $attempt): ${error.message}")
        }
    ).withPerformanceMonitoring(
        useCaseName = "EstimateTransactionUseCase",
        metadata = mapOf(
            "chainType" to chainType.name,
            "hasData" to (data != null)
        )
    )
    
    /**
     * 批量估算多筆交易
     */
    suspend fun estimateBatch(
        transactions: List<BatchTransactionRequest>
    ): Flow<Result<List<TransactionEstimate>>> = flow {
        try {
            emit(Result.Loading())
            
            val estimates = mutableListOf<TransactionEstimate>()
            var hasError = false
            
            transactions.forEach { request ->
                val estimateFlow = invoke(
                    fromAddress = request.from,
                    toAddress = request.to,
                    amount = request.value,
                    chainType = request.chainType,
                    data = request.data
                )
                
                estimateFlow.collect { result ->
                    when (result) {
                        is Result.Success -> estimates.add(result.data)
                        is Result.Failure -> {
                            Logger.e("EstimateTransaction", 
                                "Failed to estimate transaction in batch", result.exception)
                            hasError = true
                        }
                        is Result.Loading -> { /* Skip */ }
                    }
                }
            }
            
            if (hasError && estimates.isEmpty()) {
                emit(Result.Failure(Exception("All estimations failed")))
            } else if (hasError) {
                emit(Result.Success(estimates)) // Partial success
                Logger.w("EstimateTransaction", 
                    "Batch estimation partially successful: ${estimates.size}/${transactions.size}")
            } else {
                emit(Result.Success(estimates))
            }
            
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }.withPerformanceMonitoring(
        useCaseName = "EstimateTransactionUseCase.estimateBatch",
        metadata = mapOf("batchSize" to transactions.size)
    )
    
    /**
     * 驗證地址格式
     */
    private fun isValidAddress(address: String): Boolean {
        return address.isNotBlank() && 
               address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
    }
    
    /**
     * 驗證金額格式
     */
    private fun isValidAmount(amount: String): Boolean {
        return try {
            val value = amount.toDoubleOrNull()
            value != null && value >= 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 計算交易費用
     */
    private fun calculateFee(gasLimit: String, gasPrice: String): String {
        return try {
            val limit = gasLimit.toLongOrNull() ?: 21000L
            val price = gasPrice.toLongOrNull() ?: 20L
            val fee = limit * price
            // Convert from Wei to ETH (or native token)
            (fee / 1_000_000_000.0).toString()
        } catch (e: Exception) {
            "0.0"
        }
    }
    
    /**
     * 將十六進制 Wei 轉換為 Gwei 字串
     */
    private fun hexToGwei(hexWei: String): String {
        return try {
            val cleanHex = hexWei.removePrefix("0x")
            if (cleanHex.isEmpty() || cleanHex == "0") return "1"
            val wei = cleanHex.toLong(16)
            val gwei = wei / 1_000_000_000L // Wei to Gwei
            if (gwei < 1) "1" else gwei.toString()
        } catch (e: Exception) {
            "20" // fallback
        }
    }
    
    /**
     * 驗證估算結果
     */
    private fun validateEstimate(
        estimate: TransactionEstimate,
        chainType: ChainType
    ): TransactionEstimate {
        // 確保 Gas 價格在合理範圍內
        val maxGasPrice = when (chainType) {
            ChainType.ETHEREUM -> "500" // 500 Gwei
            ChainType.BSC -> "50"       // 50 Gwei
            ChainType.POLYGON -> "1000"  // 1000 Gwei
            else -> "100"
        }
        
        val estimatedGasPrice = estimate.gasPrice.toDoubleOrNull() ?: return estimate
        val maxPrice = maxGasPrice.toDoubleOrNull() ?: return estimate
        
        return if (estimatedGasPrice > maxPrice) {
            Logger.w("EstimateTransaction", 
                "Gas price exceeds maximum: $estimatedGasPrice > $maxPrice")
            estimate.copy(
                gasPrice = maxGasPrice,
                warning = "Gas price was capped at maximum: $maxGasPrice Gwei"
            )
        } else {
            estimate
        }
    }
    
    /**
     * 判斷是否應該重試估算
     */
    private fun shouldRetryEstimation(error: Throwable): Boolean {
        return when (error) {
            is NetworkException -> true
            is ServiceUnavailableException -> true
            else -> error.message?.contains("timeout", ignoreCase = true) == true ||
                    error.message?.contains("unavailable", ignoreCase = true) == true
        }
    }
    
    /**
     * 批量交易請求數據
     */
    data class BatchTransactionRequest(
        val from: String,
        val to: String,
        val value: String,
        val chainType: ChainType = ChainType.ETHEREUM,
        val data: String? = null
    )
}
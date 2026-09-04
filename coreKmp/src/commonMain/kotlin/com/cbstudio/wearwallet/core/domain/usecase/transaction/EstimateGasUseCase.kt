package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.TransactionRequest
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.quantities.GasLimit
import com.cbstudio.wearwallet.core.domain.model.quantities.Wei
import com.cbstudio.wearwallet.core.domain.model.quantities.BaseUnitAmount
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.pow
import kotlin.math.round

/**
 * 估算 Gas 費用 Use Case
 * 
 * P1-2 fix: ERC-20 gas estimation now builds actual transfer(address,uint256) calldata
 * and sends `to=tokenContract, value=0, data=calldata` to eth_estimateGas,
 * instead of estimating a native transfer to the recipient.
 */
class EstimateGasUseCase(
    private val transactionRepository: TransactionRepository
) {
    
    private fun formatDecimal(value: Double, decimals: Int): String {
        val multiplier = 10.0.pow(decimals)
        val rounded = round(value * multiplier) / multiplier
        return rounded.toString()
    }

    data class GasEstimation(
        val weiGasPrice: Wei,
        val gasLimitObj: GasLimit,
        val totalFee: String    // in ETH
    ) {
        val gasPrice: String get() = weiGasPrice.toGweiString()
        val gasLimit: String get() = gasLimitObj.toLong().toString()
    }
    
    /**
     * Estimate gas for a transaction.
     * 
     * @param from Sender address
     * @param to Recipient address (for native) or recipient address (for ERC-20)
     * @param value Human-readable amount string (e.g. "1.5", "100")
     * @param chainType Chain type
     * @param tokenAddress ERC-20 token contract address, null for native transfer
     * @param tokenDecimals Token decimals. REQUIRED when tokenAddress is non-null.
     *                      Do not default to 18 — USDC/USDT use 6 decimals.
     * @param executionContext Optional typed execution context for accurate testnet RPC routing
     */
    suspend operator fun invoke(
        from: String,
        to: String,
        value: String,
        chainType: ChainType,
        tokenAddress: String? = null,
        tokenDecimals: Int? = null,
        executionContext: ChainExecutionContext? = null
    ): Flow<Result<GasEstimation>> = flow {
        try {
            emit(Result.Loading())
            
            val isTokenTransfer = !tokenAddress.isNullOrBlank()
            
            // FAIL-CLOSED: ERC-20 requires explicit tokenDecimals
            if (isTokenTransfer && tokenDecimals == null) {
                throw IllegalArgumentException(
                    "tokenDecimals is required when tokenAddress is specified. " +
                    "Do not default to 18 — USDC/USDT use 6 decimals."
                )
            }
            
            // Build the actual transaction that will be estimated
            val (txTo, txValue, txData) = if (isTokenTransfer) {
                val decimals = tokenDecimals!!
                val baseUnitAmount = BaseUnitAmount.fromDecimalString(value, decimals)
                val cleanRecipient = to.removePrefix("0x")
                val cleanToken = tokenAddress!!.removePrefix("0x")
                
                if (!cleanRecipient.matches(Regex("^[0-9a-fA-F]{40}$"))) {
                    throw IllegalArgumentException("Invalid recipient address format")
                }
                if (!cleanToken.matches(Regex("^[0-9a-fA-F]{40}$"))) {
                    throw IllegalArgumentException("Invalid token contract address format")
                }
                
                // Build ERC-20 transfer(address,uint256) calldata
                val paddedRecipient = cleanRecipient.padStart(64, '0')
                val paddedAmount = baseUnitAmount.value.toString(16).padStart(64, '0')
                val erc20Data = "0xa9059cbb$paddedRecipient$paddedAmount"
                
                // ERC-20: to=contract, value=0, data=transfer calldata
                Triple(tokenAddress, "0x0", erc20Data)
            } else {
                // Native transfer: to=recipient, value=amount in Wei, data=empty
                val decimals = 18
                val baseUnitAmount = BaseUnitAmount.fromDecimalString(value, decimals)
                Triple(to, "0x" + baseUnitAmount.value.toString(16), "")
            }
            
            val request = TransactionRequest(
                from = from,
                to = txTo,
                value = txValue,
                chainType = chainType,
                tokenAddress = tokenAddress,
                data = txData,
                executionContext = executionContext
            )
            
            // 估算 Gas Limit - fail-closed on exception
            val estimatedGasLimitStr = transactionRepository.estimateGas(request)
            val gasLimitObj = GasLimit.fromDecimalString(estimatedGasLimitStr)

            // 獲取當前 Gas Price from live RPC - fail-closed on exception
            val gasPriceHex = if (executionContext != null) {
                transactionRepository.getGasPrice(executionContext)
            } else {
                transactionRepository.getGasPrice(chainType)
            }
            val weiGasPrice = if (gasPriceHex.startsWith("0x") || gasPriceHex.startsWith("0X")) {
                Wei.fromWeiHex(gasPriceHex)
            } else {
                Wei.fromWeiDecimal(gasPriceHex)
            }
            
            // 計算總費用 (weiGasPrice * gasLimit / 10^18) using arbitrary precision
            val totalFeeWei = Wei(weiGasPrice.value * BigInteger.fromLong(gasLimitObj.toLong()))
            val totalFeeEthDouble = totalFeeWei.value.toString(10).toDouble() / 1e18
            
            emit(Result.Success(
                GasEstimation(
                    weiGasPrice = weiGasPrice,
                    gasLimitObj = gasLimitObj,
                    totalFee = formatDecimal(totalFeeEthDouble, 6)
                )
            ))
            
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }
}
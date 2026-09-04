package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionRequest
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * watchOS TransactionRepository 實現
 * 使用 EthereumRpcClient 進行區塊鏈交互
 */
class WatchOSTransactionRepository(
    private val rpcClient: EthereumRpcClient
) : TransactionRepository {

    override suspend fun sendTransaction(
        signedTransaction: String,
        chainType: ChainType
    ): String {
        return when (val result = rpcClient.sendRawTransaction(signedTransaction, chainType)) {
            is Result.Success -> result.data
            is Result.Failure -> throw result.error
            else -> throw Exception("Unexpected state")
        }
    }

    override suspend fun getTransactionHistory(
        walletAddress: String,
        chainType: ChainType
    ): List<Transaction> {
        // watchOS: Transaction history requires block explorer API integration
        // For now, return empty list - history can be viewed on phone/web
        return emptyList()
    }

    override suspend fun getTransaction(
        hash: String,
        chainType: ChainType
    ): Transaction? {
        // watchOS: Individual transaction lookup not yet supported
        // Would require eth_getTransactionReceipt RPC call
        return null
    }

    override suspend fun estimateGas(request: TransactionRequest): String {
        return when (val result = rpcClient.estimateGas(
            from = request.from,
            to = request.to,
            value = request.value,
            data = request.data ?: "0x",
            chainType = request.chainType
        )) {
            is Result.Success -> result.data
            is Result.Failure -> "0x5208" // 21000 in hex, default for simple transfers
            else -> "0x5208"
        }
    }

    override suspend fun getNonce(walletAddress: String, chainType: ChainType): Long {
        return when (val result = rpcClient.getNonce(walletAddress, chainType)) {
            is Result.Success -> {
                parseHexToLong(result.data.toString())
            }
            is Result.Failure -> 0L
            else -> 0L
        }
    }
    
    private fun parseHexToLong(hex: String): Long {
        val cleanHex = hex.removePrefix("0x").removePrefix("0X")
        if (cleanHex.isEmpty()) return 0L
        return try {
            var result = 0L
            for (c in cleanHex) {
                val digit = when (c) {
                    in '0'..'9' -> c - '0'
                    in 'a'..'f' -> c - 'a' + 10
                    in 'A'..'F' -> c - 'A' + 10
                    else -> return 0L
                }
                result = result * 16 + digit
            }
            result
        } catch (e: Exception) {
            0L
        }
    }

    override suspend fun buildTransaction(request: TransactionRequest): String {
        val nonce = getNonce(request.from, request.chainType)
        val gasPrice = getGasPrice(request.chainType)
        val gasLimit = estimateGas(request)
        
        // Return JSON representation for signing layer
        return """{"nonce":$nonce,"gasPrice":"$gasPrice","gasLimit":"$gasLimit","to":"${request.to}","value":"${request.value}","data":"${request.data ?: "0x"}","chainId":${request.chainType.getChainId()}}"""
    }

    override fun observeTransactions(walletAddress: String): Flow<List<Transaction>> {
        return flow { emit(emptyList()) }
    }

    override suspend fun getGasPrice(chainType: ChainType): String {
        return when (val result = rpcClient.getGasPrice(chainType)) {
            is Result.Success -> result.data
            is Result.Failure -> "0x4a817c800" // 20 Gwei fallback
            else -> "0x4a817c800"
        }
    }
}

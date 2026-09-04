package com.cbstudio.wearwallet.core.domain.repository

import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionRequest
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    suspend fun sendTransaction(signedTransaction: String, chainType: ChainType): String
    suspend fun sendTransaction(signedTransaction: String, context: ChainExecutionContext): String = sendTransaction(signedTransaction, context.chain)
    suspend fun getTransactionHistory(walletAddress: String, chainType: ChainType): List<Transaction>
    suspend fun getTransaction(hash: String, chainType: ChainType): Transaction?
    suspend fun estimateGas(request: TransactionRequest): String
    suspend fun getNonce(walletAddress: String, chainType: ChainType): Long
    suspend fun getNonce(walletAddress: String, context: ChainExecutionContext): Long = getNonce(walletAddress, context.chain)
    suspend fun getGasPrice(chainType: ChainType): String
    suspend fun getGasPrice(context: ChainExecutionContext): String = getGasPrice(context.chain)
    suspend fun buildTransaction(request: TransactionRequest): String
    fun observeTransactions(walletAddress: String): Flow<List<Transaction>>
}
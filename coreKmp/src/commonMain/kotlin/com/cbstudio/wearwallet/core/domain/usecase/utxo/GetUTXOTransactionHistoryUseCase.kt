package com.cbstudio.wearwallet.core.domain.usecase.utxo

import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.blockchain.model.*
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/**
 * 獲取 UTXO 交易歷史 UseCase
 */
class GetUTXOTransactionHistoryUseCase(
    private val utxoApiClient: UTXOApiClient
) {
    
    /**
     * 獲取地址的交易歷史
     */
    operator fun invoke(
        address: String,
        chainType: ChainType,
        limit: Int = 50,
        offset: Int = 0
    ): Flow<Result<List<UTXOTransactionHistory>>> = flow {
        try {
            emit(Result.Loading())
            
            // 獲取交易歷史
            val transactions = utxoApiClient.getTransactionHistory(
                address = address,
                chainType = chainType,
                limit = limit,
                offset = offset
            )
            
            // 轉換為歷史記錄格式
            val history = transactions.map { tx ->
                convertToHistory(tx, address)
            }
            
            emit(Result.Success(history))
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }
    
    /**
     * 獲取交易詳情
     */
    suspend fun getTransactionDetail(
        txId: String,
        chainType: ChainType
    ): Result<UTXOTransaction> {
        return try {
            val transaction = utxoApiClient.getTransaction(txId, chainType)
            Result.Success(transaction)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取交易摘要統計
     */
    suspend fun getTransactionSummary(
        address: String,
        chainType: ChainType
    ): Result<UTXOTransactionSummary> {
        return try {
            val allTransactions = mutableListOf<UTXOTransactionHistory>()
            var offset = 0
            val batchSize = 100
            
            // 批量獲取所有交易
            while (true) {
                val batch = utxoApiClient.getTransactionHistory(
                    address = address,
                    chainType = chainType,
                    limit = batchSize,
                    offset = offset
                )
                
                if (batch.isEmpty()) break
                
                allTransactions.addAll(batch.map { convertToHistory(it, address) })
                
                if (batch.size < batchSize) break
                offset += batchSize
            }
            
            // 計算統計信息
            val summary = calculateSummary(allTransactions)
            Result.Success(summary)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 轉換為歷史記錄格式
     */
    private fun convertToHistory(
        tx: UTXOTransaction,
        userAddress: String
    ): UTXOTransactionHistory {
        // 判斷交易類型
        val isReceive = tx.outputs.any { it.address == userAddress }
        val isSend = tx.inputs.any { it.address == userAddress }
        
        val type = when {
            isReceive && isSend -> TransactionType.SELF
            isReceive -> TransactionType.RECEIVE
            else -> TransactionType.SEND
        }
        
        // 計算金額
        val amount = when (type) {
            TransactionType.RECEIVE -> {
                tx.outputs
                    .filter { it.address == userAddress }
                    .sumOf { it.value }
            }
            TransactionType.SEND -> {
                tx.inputs
                    .filter { it.address == userAddress }
                    .sumOf { it.value ?: 0 } -
                tx.outputs
                    .filter { it.address == userAddress }
                    .sumOf { it.value }
            }
            TransactionType.SELF -> {
                tx.fee
            }
        }
        
        // 獲取對方地址
        val fromAddress = tx.inputs.firstOrNull()?.address ?: "Unknown"
        val toAddress = tx.outputs.firstOrNull { it.address != userAddress }?.address
            ?: tx.outputs.firstOrNull()?.address
            ?: "Unknown"
        
        return UTXOTransactionHistory(
            txId = tx.txId,
            timestamp = tx.timestamp ?: Instant.DISTANT_PAST,
            type = type,
            amount = amount,
            fee = tx.fee,
            fromAddress = if (type == TransactionType.SEND) userAddress else fromAddress,
            toAddress = if (type == TransactionType.RECEIVE) userAddress else toAddress,
            confirmations = tx.confirmations,
            status = tx.status,
            chainType = tx.chainType
        )
    }
    
    /**
     * 計算交易摘要
     */
    private fun calculateSummary(
        transactions: List<UTXOTransactionHistory>
    ): UTXOTransactionSummary {
        val totalReceived = transactions
            .filter { it.type == TransactionType.RECEIVE }
            .sumOf { it.amount }
        
        val totalSent = transactions
            .filter { it.type == TransactionType.SEND }
            .sumOf { it.amount }
        
        val totalFees = transactions
            .filter { it.type == TransactionType.SEND || it.type == TransactionType.SELF }
            .sumOf { it.fee }
        
        val timestamps = transactions.mapNotNull { 
            if (it.timestamp != Instant.DISTANT_PAST) it.timestamp else null 
        }
        
        return UTXOTransactionSummary(
            totalReceived = totalReceived,
            totalSent = totalSent,
            totalFees = totalFees,
            transactionCount = transactions.size,
            firstTransaction = timestamps.minOrNull(),
            lastTransaction = timestamps.maxOrNull()
        )
    }
}
package com.cbstudio.wearwallet.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class GetTransactionHistoryUseCase {
    fun getTransactionStats(): Flow<TransactionStats> = flowOf(TransactionStats(0, mapOf()))
    fun getTransactionsByDirection(direction: String): Flow<List<TransactionInfo>> = flowOf(emptyList())
}

data class TransactionStats(val totalTransactions: Int, val byType: Map<String, Int>)
data class TransactionInfo(val hash: String, val amount: String, val timestamp: Long)

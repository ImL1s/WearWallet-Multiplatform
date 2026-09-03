package com.cbstudio.wearwallet.core.domain.usecase.transaction

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 獲取交易歷史 UseCase
 */
class GetTransactionHistoryUseCase(
    private val transactionRepository: TransactionRepository
) {
    suspend operator fun invoke(
        walletAddress: String,
        chainType: ChainType,
        limit: Int = 50
    ): Flow<Result<List<Transaction>>> = flow {
        try {
            emit(Result.Loading())
            
            // 獲取交易歷史
            val transactions = transactionRepository.getTransactionHistory(
                walletAddress = walletAddress,
                chainType = chainType
            ).take(limit)
            
            emit(Result.Success(transactions))
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }
    
    /**
     * 觀察交易變化
     */
    fun observeTransactions(walletAddress: String): Flow<List<Transaction>> {
        return transactionRepository.observeTransactions(walletAddress)
    }
}
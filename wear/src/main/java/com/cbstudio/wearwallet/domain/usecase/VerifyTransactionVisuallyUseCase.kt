package com.cbstudio.wearwallet.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class VerifyTransactionVisuallyUseCase {
    fun verifyTransaction(transactionData: ByteArray): Flow<TransactionVerification> = 
        flowOf(TransactionVerification(true, 0.95f, ""))
}

data class TransactionVerification(
    val isValid: Boolean,
    val confidence: Float,
    val details: String
)

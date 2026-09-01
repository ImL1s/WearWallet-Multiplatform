package com.cbstudio.wearwallet.core.domain.usecase.token

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 掃描代幣 Use Case
 * 掃描錢包地址擁有的所有代幣
 */
class ScanTokensUseCase(
    private val tokenRepository: TokenRepository
) {
    suspend operator fun invoke(
        address: String,
        chainType: ChainType
    ): Flow<Result<List<Token>>> = flow {
        try {
            emit(Result.Loading())
            
            // 掃描代幣
            val tokens = tokenRepository.scanTokens(address, chainType)
            
            // 過濾餘額大於 0 的代幣
            val activeTokens = tokens.filter { 
                val balanceValue = it.balance?.toDoubleOrNull() ?: 0.0
                balanceValue > 0
            }
            
            emit(Result.Success(activeTokens))
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }
}
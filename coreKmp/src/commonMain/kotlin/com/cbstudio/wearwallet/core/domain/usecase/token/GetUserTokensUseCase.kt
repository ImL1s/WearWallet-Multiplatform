package com.cbstudio.wearwallet.core.domain.usecase.token

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 獲取用戶代幣 UseCase
 */
class GetUserTokensUseCase(
    private val tokenRepository: TokenRepository
) {
    suspend operator fun invoke(
        walletAddress: String,
        chainType: ChainType
    ): Flow<Result<List<Token>>> = flow {
        try {
            emit(Result.Loading())
            
            // 獲取用戶代幣
            val tokens = tokenRepository.scanUserTokens(walletAddress, chainType)
            
            // 獲取原生代幣餘額
            val nativeBalance = tokenRepository.getNativeBalance(walletAddress, chainType)
            
            // 創建原生代幣
            val nativeToken = Token(
                address = "",
                name = chainType.nativeToken,
                symbol = chainType.nativeToken,
                decimals = 18,
                chainType = chainType,
                balance = nativeBalance,
                isNative = true
            )
            
            // 合併原生代幣和其他代幣
            val allTokens = listOf(nativeToken) + tokens
            
            emit(Result.Success(allTokens))
        } catch (e: Exception) {
            emit(Result.Failure(e))
        }
    }
    
    /**
     * 觀察用戶代幣變化
     */
    fun observeUserTokens(walletAddress: String): Flow<List<Token>> {
        return tokenRepository.observeUserTokens(walletAddress)
    }
}
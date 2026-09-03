package com.cbstudio.wearwallet.core.domain.repository

import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.coroutines.flow.Flow

interface TokenRepository {
    // 基本查詢
    suspend fun getTokenBalance(walletAddress: String, tokenAddress: String, chainType: ChainType): String
    suspend fun getNativeBalance(walletAddress: String, chainType: ChainType): String
    suspend fun getTokenInfo(tokenAddress: String, chainType: ChainType): Token?
    suspend fun getTokenPrice(tokenSymbol: String): Double?
    
    // 掃描和管理
    suspend fun scanTokens(address: String, chainType: ChainType): List<Token>
    suspend fun scanUserTokens(walletAddress: String, chainType: ChainType): List<Token>
    suspend fun saveUserToken(walletAddress: String, token: Token)
    suspend fun removeUserToken(walletAddress: String, tokenAddress: String)
    
    // 批量操作
    suspend fun getTokenBalances(walletAddress: String, chainType: ChainType): Map<String, Double>
    
    // 觀察
    fun observeUserTokens(walletAddress: String): Flow<List<Token>>
}
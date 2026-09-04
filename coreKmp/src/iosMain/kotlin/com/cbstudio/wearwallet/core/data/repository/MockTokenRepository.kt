package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Mock implementation of TokenRepository for iOS/watchOS
 * TODO: Replace with real implementation using SQLDelight
 */
class MockTokenRepository : TokenRepository {
    
    private val userTokens = mutableMapOf<String, MutableList<Token>>()
    
    override suspend fun getTokenBalance(walletAddress: String, tokenAddress: String, chainType: ChainType): String {
        return "0"
    }
    
    override suspend fun getNativeBalance(walletAddress: String, chainType: ChainType): String {
        return "0"
    }
    
    override suspend fun getTokenInfo(tokenAddress: String, chainType: ChainType): Token? {
        return Token(
            address = tokenAddress,
            name = "Mock Token",
            symbol = "MCK",
            decimals = 18,
            chainType = chainType,
            balance = "0"
        )
    }
    
    override suspend fun getTokenPrice(tokenSymbol: String): Double? {
        return when (tokenSymbol.uppercase()) {
            "ETH" -> 2000.0
            "BTC" -> 30000.0
            "USDT", "USDC" -> 1.0
            else -> null
        }
    }
    
    override suspend fun scanTokens(address: String, chainType: ChainType): List<Token> {
        // Return some mock tokens
        return listOf(
            Token(
                address = "0x" + "a".repeat(40),
                name = "Tether USD",
                symbol = "USDT",
                decimals = 6,
                chainType = chainType,
                balance = "100000000", // 100 USDT
                usdPrice = 1.0
            ),
            Token(
                address = "0x" + "b".repeat(40),
                name = "USD Coin",
                symbol = "USDC",
                decimals = 6,
                chainType = chainType,
                balance = "50000000", // 50 USDC
                usdPrice = 1.0
            )
        )
    }
    
    override suspend fun scanUserTokens(walletAddress: String, chainType: ChainType): List<Token> {
        return userTokens[walletAddress]?.filter { it.chainType == chainType } ?: emptyList()
    }
    
    override suspend fun saveUserToken(walletAddress: String, token: Token) {
        userTokens.getOrPut(walletAddress) { mutableListOf() }.add(token)
    }
    
    override suspend fun removeUserToken(walletAddress: String, tokenAddress: String) {
        userTokens[walletAddress]?.removeAll { it.address == tokenAddress }
    }
    
    override suspend fun getTokenBalances(walletAddress: String, chainType: ChainType): Map<String, Double> {
        val tokens = scanUserTokens(walletAddress, chainType)
        return tokens.associate { token ->
            token.address to (token.displayBalance.toDoubleOrNull() ?: 0.0)
        }
    }
    
    override fun observeUserTokens(walletAddress: String): Flow<List<Token>> {
        return flowOf(userTokens[walletAddress] ?: emptyList())
    }
}
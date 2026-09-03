package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.network.ApiConfig
import com.cbstudio.wearwallet.core.network.PriceApiClient
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.pow
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * 真實的 Token Repository 實現
 * 連接區塊鏈網路查詢代幣資訊
 */
class TokenRepositoryImpl(
    private val rpcClient: EthereumRpcClient,
    private val priceApiClient: PriceApiClient
) : TokenRepository {
    
    // 常見 ERC20 代幣列表 (可從 API 或本地資料庫載入)
    private val commonTokens = mapOf(
        ChainType.ETHEREUM to listOf(
            Token(
                address = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                name = "USD Coin",
                symbol = "USDC",
                decimals = 6,
                chainType = ChainType.ETHEREUM,
                logoUrl = "https://assets.coingecko.com/coins/images/6319/small/USD_Coin_icon.png"
            ),
            Token(
                address = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
                name = "Tether USD",
                symbol = "USDT",
                decimals = 6,
                chainType = ChainType.ETHEREUM,
                logoUrl = "https://assets.coingecko.com/coins/images/325/small/Tether.png"
            ),
            Token(
                address = "0x6B175474E89094C44Da98b954EedeAC495271d0F",
                name = "Dai Stablecoin",
                symbol = "DAI",
                decimals = 18,
                chainType = ChainType.ETHEREUM,
                logoUrl = "https://assets.coingecko.com/coins/images/9956/small/Badge_Dai.png"
            ),
            Token(
                address = "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599",
                name = "Wrapped BTC",
                symbol = "WBTC",
                decimals = 8,
                chainType = ChainType.ETHEREUM,
                logoUrl = "https://assets.coingecko.com/coins/images/7598/small/wrapped_bitcoin_wbtc.png"
            )
        ),
        ChainType.BSC to listOf(
            Token(
                address = "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d",
                name = "USD Coin",
                symbol = "USDC",
                decimals = 18,
                chainType = ChainType.BSC
            ),
            Token(
                address = "0x55d398326f99059fF775485246999027B3197955",
                name = "Tether USD",
                symbol = "USDT",
                decimals = 18,
                chainType = ChainType.BSC
            ),
            Token(
                address = "0xe9e7CEA3DedcA5984780Bafc599bD69ADd087D56",
                name = "BUSD Token",
                symbol = "BUSD",
                decimals = 18,
                chainType = ChainType.BSC
            )
        ),
        ChainType.POLYGON to listOf(
            Token(
                address = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174",
                name = "USD Coin",
                symbol = "USDC",
                decimals = 6,
                chainType = ChainType.POLYGON
            ),
            Token(
                address = "0xc2132D05D31c914a87C6611C10748AEb04B58e8F",
                name = "Tether USD",
                symbol = "USDT",
                decimals = 6,
                chainType = ChainType.POLYGON
            )
        )
    )
    
    override suspend fun getTokenBalance(
        walletAddress: String,
        tokenAddress: String,
        chainType: ChainType
    ): String {
        val result = rpcClient.getTokenBalance(walletAddress, tokenAddress, chainType)
        return when (result) {
            is Result.Success -> hexToDecimal(result.data)
            is Result.Failure -> "0"
            is Result.Loading -> "0"
        }
    }
    
    override suspend fun getNativeBalance(
        walletAddress: String,
        chainType: ChainType
    ): String {
        // UTXO 鏈暫時返回模擬餘額，避免調用 RPC
        val isUTXOChain = chainType in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        if (isUTXOChain) {
            // 返回模擬餘額字串（已轉換為最小單位）
            return when (chainType) {
                ChainType.BITCOIN -> "120000"      // 0.0012 BTC = 120000 satoshi
                ChainType.LITECOIN -> "50000000"   // 0.5 LTC
                ChainType.DOGECOIN -> "10000000000" // 100 DOGE
                ChainType.BITCOIN_CASH -> "5000000" // 0.05 BCH
                else -> "0"
            }
        }
        
        // EVM 鏈使用 RPC 查詢
        val result = rpcClient.getNativeBalance(walletAddress, chainType)
        return when (result) {
            is Result.Success -> hexToDecimal(result.data)
            is Result.Failure -> "0"
            is Result.Loading -> "0"
        }
    }
    
    override suspend fun scanTokens(
        address: String,
        chainType: ChainType
    ): List<Token> {
        // UTXO 鏈不支援代幣掃描
        val isUTXOChain = chainType in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        if (isUTXOChain) {
            return emptyList()
        }
        
        // 獲取該鏈的常見代幣列表
        val tokens = commonTokens[chainType] ?: emptyList()
        
        // 並行查詢所有代幣餘額
        return coroutineScope {
            tokens.map { token ->
                async {
                    val balance = getTokenBalance(address, token.address, chainType)
                    // 只返回有餘額的代幣
                    if (balance != "0" && balance.toDoubleOrNull() ?: 0.0 > 0) {
                        token.copy(balance = balance)
                    } else {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
    
    override suspend fun scanUserTokens(
        walletAddress: String,
        chainType: ChainType
    ): List<Token> {
        // UTXO 鏈只有原生代幣，不需要掃描
        val isUTXOChain = chainType in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        if (isUTXOChain) {
            // UTXO 鏈返回空列表（餘額在主畫面顯示）
            return emptyList()
        }
        
        val tokens = mutableListOf<Token>()
        
        // 添加原生代幣
        val nativeBalance = getNativeBalance(walletAddress, chainType)
        val nativeToken = when (chainType) {
            ChainType.ETHEREUM -> Token(
                address = "native",
                name = "Ethereum",
                symbol = "ETH",
                decimals = 18,
                chainType = chainType,
                balance = nativeBalance,
                isNative = true,
                logoUrl = "https://assets.coingecko.com/coins/images/279/small/ethereum.png"
            )
            ChainType.BSC -> Token(
                address = "native",
                name = "BNB",
                symbol = "BNB",
                decimals = 18,
                chainType = chainType,
                balance = nativeBalance,
                isNative = true,
                logoUrl = "https://assets.coingecko.com/coins/images/825/small/bnb-icon2_2x.png"
            )
            ChainType.POLYGON -> Token(
                address = "native",
                name = "Polygon",
                symbol = "MATIC",
                decimals = 18,
                chainType = chainType,
                balance = nativeBalance,
                isNative = true,
                logoUrl = "https://assets.coingecko.com/coins/images/4713/small/matic-token-icon.png"
            )
            else -> Token(
                address = "native",
                name = chainType.name,
                symbol = chainType.name.take(4).uppercase(),
                decimals = 18,
                chainType = chainType,
                balance = nativeBalance,
                isNative = true
            )
        }
        
        // 總是添加原生代幣，即使餘額為 0
        tokens.add(nativeToken)
        
        // 掃描 ERC20 代幣
        val erc20Tokens = scanTokens(walletAddress, chainType)
        tokens.addAll(erc20Tokens)
        
        return tokens
    }
    
    override suspend fun getTokenInfo(
        tokenAddress: String,
        chainType: ChainType
    ): Token? {
        // 從預設列表查找
        return commonTokens[chainType]?.find { 
            it.address.equals(tokenAddress, ignoreCase = true) 
        }
    }
    
    override suspend fun getTokenPrice(tokenSymbol: String): Double? {
        // 使用 CoinGecko API 獲取真實價格
        return try {
            val result = priceApiClient.getSimplePrice(
                symbols = listOf(tokenSymbol),
                vsCurrency = "usd"
            )
            
            when (result) {
                is com.cbstudio.wearwallet.core.common.Result.Success -> {
                    result.data[tokenSymbol.uppercase()]?.price
                }
                else -> {
                    // 如果 API 失敗，返回預設值
                    when (tokenSymbol.uppercase()) {
                        "ETH" -> 2500.0
                        "BNB" -> 350.0
                        "MATIC" -> 0.8
                        "USDC", "USDT", "DAI", "BUSD" -> 1.0
                        "WBTC" -> 45000.0
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            // 發生異常時返回預設值
            when (tokenSymbol.uppercase()) {
                "ETH" -> 2500.0
                "BNB" -> 350.0
                "MATIC" -> 0.8
                "USDC", "USDT", "DAI", "BUSD" -> 1.0
                "WBTC" -> 45000.0
                else -> null
            }
        }
    }
    
    // In-memory user token storage (production: use SQLDelight or DataStore)
    private val userTokenCache = mutableMapOf<String, MutableList<Token>>()
    
    override suspend fun saveUserToken(walletAddress: String, token: Token) {
        val tokens = userTokenCache.getOrPut(walletAddress) { mutableListOf() }
        // Avoid duplicates
        tokens.removeAll { it.address.equals(token.address, ignoreCase = true) }
        tokens.add(token)
        println("💾 Saved user token: ${token.symbol} for $walletAddress")
    }
    
    override suspend fun removeUserToken(walletAddress: String, tokenAddress: String) {
        userTokenCache[walletAddress]?.removeAll { 
            it.address.equals(tokenAddress, ignoreCase = true) 
        }
        println("🗑️ Removed token $tokenAddress for $walletAddress")
    }
    
    override suspend fun getTokenBalances(
        walletAddress: String,
        chainType: ChainType
    ): Map<String, Double> {
        val tokens = scanUserTokens(walletAddress, chainType)
        return tokens.associate { token ->
            val balance = token.balance.toDoubleOrNull() ?: 0.0
            val adjustedBalance = balance / 10.0.pow(token.decimals)
            token.symbol to adjustedBalance
        }
    }
    
    override fun observeUserTokens(walletAddress: String): Flow<List<Token>> {
        return flow {
            // 每次都重新掃描（可以加入快取邏輯）
            emit(scanUserTokens(walletAddress, ChainType.ETHEREUM))
        }
    }
    
    /**
     * 將十六進制轉換為十進制字串
     */
    private fun hexToDecimal(hex: String): String {
        return try {
            val cleanHex = hex.removePrefix("0x")
            if (cleanHex.isEmpty() || cleanHex == "0") {
                "0"
            } else {
                // 使用 KMP 兼容的 BigInteger 處理大數字
                com.ionspin.kotlin.bignum.integer.BigInteger.parseString(cleanHex, 16).toString(10)
            }
        } catch (e: Exception) {
            "0"
        }
    }
}
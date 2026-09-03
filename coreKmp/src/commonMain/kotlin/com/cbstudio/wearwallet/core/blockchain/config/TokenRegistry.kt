package com.cbstudio.wearwallet.core.blockchain.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 動態代幣註冊表
 * 取代硬編碼的代幣地址，支援多網路配置
 */
object TokenRegistry {
    
    private val tokenMap = mutableMapOf<String, MutableMap<String, TokenInfo>>()
    
    init {
        // 初始化常用代幣
        registerDefaultTokens()
    }
    
    /**
     * 註冊預設代幣
     * 實際應用中應從配置文件或 API 加載
     */
    private fun registerDefaultTokens() {
        // Solana Mainnet
        registerToken("solana", "mainnet", TokenInfo(
            symbol = "USDC",
            name = "USD Coin",
            address = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            decimals = 6,
            chainId = "solana-mainnet"
        ))
        
        registerToken("solana", "mainnet", TokenInfo(
            symbol = "USDT",
            name = "Tether USD",
            address = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
            decimals = 6,
            chainId = "solana-mainnet"
        ))
        
        // Solana Devnet
        registerToken("solana", "devnet", TokenInfo(
            symbol = "USDC",
            name = "USD Coin (Devnet)",
            address = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU",
            decimals = 6,
            chainId = "solana-devnet"
        ))
        
        // Ethereum Mainnet
        registerToken("ethereum", "mainnet", TokenInfo(
            symbol = "USDC",
            name = "USD Coin",
            address = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            decimals = 6,
            chainId = "1"
        ))
        
        registerToken("ethereum", "mainnet", TokenInfo(
            symbol = "USDT",
            name = "Tether USD",
            address = "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            decimals = 6,
            chainId = "1"
        ))
        
        registerToken("ethereum", "mainnet", TokenInfo(
            symbol = "DAI",
            name = "Dai Stablecoin",
            address = "0x6B175474E89094C44Da98b954EedeAC495271d0F",
            decimals = 18,
            chainId = "1"
        ))
        
        // Ethereum Sepolia Testnet
        registerToken("ethereum", "sepolia", TokenInfo(
            symbol = "USDC",
            name = "USD Coin (Sepolia)",
            address = "0x07865c6E87B9F70255377e024ace6630C1Eaa37F",
            decimals = 6,
            chainId = "11155111"
        ))
        
        registerToken("ethereum", "sepolia", TokenInfo(
            symbol = "USDT",
            name = "Tether USD (Sepolia)",
            address = "0x7169D38820dfd117C3FA1f22a697dBA58d90BA06",
            decimals = 6,
            chainId = "11155111"
        ))
        
        // BSC Mainnet
        registerToken("bsc", "mainnet", TokenInfo(
            symbol = "USDC",
            name = "USD Coin",
            address = "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d",
            decimals = 18,
            chainId = "56"
        ))
        
        registerToken("bsc", "mainnet", TokenInfo(
            symbol = "USDT",
            name = "Tether USD",
            address = "0x55d398326f99059fF775485246999027B3197955",
            decimals = 18,
            chainId = "56"
        ))
        
        // Polygon Mainnet
        registerToken("polygon", "mainnet", TokenInfo(
            symbol = "USDC",
            name = "USD Coin",
            address = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174",
            decimals = 6,
            chainId = "137"
        ))
        
        registerToken("polygon", "mainnet", TokenInfo(
            symbol = "USDT",
            name = "Tether USD",
            address = "0xc2132D05D31c914a87C6611C10748AEb04B58e8F",
            decimals = 6,
            chainId = "137"
        ))
    }
    
    /**
     * 註冊代幣
     */
    fun registerToken(chain: String, network: String, token: TokenInfo) {
        val chainKey = "$chain-$network".lowercase()
        if (!tokenMap.containsKey(chainKey)) {
            tokenMap[chainKey] = mutableMapOf()
        }
        tokenMap[chainKey]!![token.symbol] = token
    }
    
    /**
     * 獲取代幣資訊
     */
    fun getToken(chain: String, network: String, symbol: String): TokenInfo? {
        val chainKey = "$chain-$network".lowercase()
        return tokenMap[chainKey]?.get(symbol)
    }
    
    /**
     * 獲取代幣地址
     */
    fun getTokenAddress(chain: String, network: String, symbol: String): String? {
        return getToken(chain, network, symbol)?.address
    }
    
    /**
     * 獲取鏈上所有代幣
     */
    fun getTokensForChain(chain: String, network: String): List<TokenInfo> {
        val chainKey = "$chain-$network".lowercase()
        return tokenMap[chainKey]?.values?.toList() ?: emptyList()
    }
    
    /**
     * 從 JSON 文件加載代幣配置
     */
    suspend fun loadFromJson(jsonContent: String) {
        try {
            val config = Json.decodeFromString<TokenConfig>(jsonContent)
            config.tokens.forEach { token ->
                registerToken(token.chain, token.network, token)
            }
        } catch (e: Exception) {
            println("❌ 加載代幣配置失敗: ${e.message}")
        }
    }
    
    /**
     * 從遠端 API 更新代幣列表
     */
    suspend fun updateFromRemote(apiUrl: String) {
        // TODO: 實現從 CoinGecko、CoinMarketCap 等 API 更新
        // 這裡可以定期更新代幣地址和資訊
    }
    
    /**
     * 清除所有代幣
     */
    fun clear() {
        tokenMap.clear()
    }
    
    /**
     * 驗證代幣地址格式
     */
    fun validateTokenAddress(chain: String, address: String): Boolean {
        return when (chain.lowercase()) {
            "ethereum", "bsc", "polygon" -> {
                // EVM 鏈地址格式
                address.startsWith("0x") && address.length == 42 &&
                address.substring(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            }
            "solana" -> {
                // Solana 地址格式（Base58）
                address.length in 32..44 && 
                address.all { it.isLetterOrDigit() } &&
                !address.contains('0') && !address.contains('O') &&
                !address.contains('I') && !address.contains('l')
            }
            else -> false
        }
    }
}

/**
 * 代幣資訊
 */
@Serializable
data class TokenInfo(
    val symbol: String,
    val name: String,
    val address: String,
    val decimals: Int,
    val chainId: String,
    val chain: String = "",
    val network: String = "",
    val logoUrl: String? = null,
    val website: String? = null,
    val coingeckoId: String? = null,
    val isStablecoin: Boolean = false,
    val isNative: Boolean = false
)

/**
 * 代幣配置
 */
@Serializable
data class TokenConfig(
    val version: String,
    val updatedAt: String,
    val tokens: List<TokenInfo>
)

/**
 * 網路配置
 */
@Serializable
data class NetworkConfig(
    val chain: String,
    val network: String,
    val chainId: String,
    val rpcUrl: String,
    val explorerUrl: String,
    val nativeCurrency: NativeCurrency
)

/**
 * 原生貨幣
 */
@Serializable
data class NativeCurrency(
    val name: String,
    val symbol: String,
    val decimals: Int
)
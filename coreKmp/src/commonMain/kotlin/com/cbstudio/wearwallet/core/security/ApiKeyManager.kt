package com.cbstudio.wearwallet.core.security

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * API Key 管理器
 * 提供安全的 API Key 存取和管理
 */
object ApiKeyManager {
    
    private val mutex = Mutex()
    private val apiKeys = mutableMapOf<String, String>()
    private val keyProviders = mutableListOf<ApiKeyProvider>()
    
    // API Key 類型常量
    const val KEY_INFURA = "INFURA_API_KEY"
    const val KEY_ALCHEMY = "ALCHEMY_API_KEY"
    const val KEY_MORALIS = "MORALIS_API_KEY"
    const val KEY_ETHERSCAN = "ETHERSCAN_API_KEY"
    const val KEY_BSCSCAN = "BSCSCAN_API_KEY"
    const val KEY_POLYGONSCAN = "POLYGONSCAN_API_KEY"
    const val KEY_BLOCKSTREAM = "BLOCKSTREAM_API_KEY"
    const val KEY_BLOCKCYPHER = "BLOCKCYPHER_API_KEY"
    const val KEY_SOLANA_RPC = "SOLANA_RPC_API_KEY"
    const val KEY_TRON_GRID = "TRON_GRID_API_KEY"
    
    /**
     * 初始化 API Key 管理器
     * 應該在應用啟動時調用
     */
    suspend fun initialize(provider: ApiKeyProvider) = mutex.withLock {
        keyProviders.add(provider)
        loadKeysFromProvider(provider)
    }
    
    /**
     * 從提供者加載 API Keys
     */
    private suspend fun loadKeysFromProvider(provider: ApiKeyProvider) {
        provider.getAllKeys().forEach { (key, value) ->
            if (value.isNotBlank()) {
                apiKeys[key] = value
            }
        }
    }
    
    /**
     * 獲取 API Key
     * @param keyName API Key 名稱
     * @return API Key 值，如果不存在返回 null
     */
    suspend fun getApiKey(keyName: String): String? = mutex.withLock {
        // 首先檢查緩存
        apiKeys[keyName]?.let { return@withLock it }
        
        // 嘗試從所有提供者獲取
        keyProviders.forEach { provider ->
            provider.getKey(keyName)?.let { key ->
                if (key.isNotBlank()) {
                    apiKeys[keyName] = key
                    return@withLock key
                }
            }
        }
        
        return@withLock null
    }
    
    /**
     * 設置 API Key（僅用於測試）
     */
    suspend fun setApiKey(keyName: String, value: String) = mutex.withLock {
        apiKeys[keyName] = value
    }
    
    /**
     * 清除所有緩存的 API Keys
     */
    suspend fun clear() = mutex.withLock {
        apiKeys.clear()
    }
    
    /**
     * 獲取 RPC URL，包含 API Key
     */
    suspend fun getRpcUrl(chain: String, network: String): String? {
        return when (chain.lowercase()) {
            "ethereum" -> getEthereumRpcUrl(network)
            "solana" -> getSolanaRpcUrl(network)
            "bsc" -> getBscRpcUrl(network)
            "polygon" -> getPolygonRpcUrl(network)
            "bitcoin" -> getBitcoinRpcUrl(network)
            else -> null
        }
    }
    
    private suspend fun getEthereumRpcUrl(network: String): String? {
        val infuraKey = getApiKey(KEY_INFURA)
        val alchemyKey = getApiKey(KEY_ALCHEMY)
        
        return when {
            infuraKey != null -> when (network) {
                "mainnet" -> "https://mainnet.infura.io/v3/$infuraKey"
                "goerli" -> "https://goerli.infura.io/v3/$infuraKey"
                "sepolia" -> "https://sepolia.infura.io/v3/$infuraKey"
                else -> null
            }
            alchemyKey != null -> when (network) {
                "mainnet" -> "https://eth-mainnet.g.alchemy.com/v2/$alchemyKey"
                "goerli" -> "https://eth-goerli.g.alchemy.com/v2/$alchemyKey"
                "sepolia" -> "https://eth-sepolia.g.alchemy.com/v2/$alchemyKey"
                else -> null
            }
            else -> {
                // 公共備用節點（速率限制較嚴格）
                when (network) {
                    "mainnet" -> "https://cloudflare-eth.com"
                    else -> null
                }
            }
        }
    }
    
    private suspend fun getSolanaRpcUrl(network: String): String? {
        val apiKey = getApiKey(KEY_SOLANA_RPC)
        
        return when (network) {
            "mainnet", "mainnet-beta" -> {
                if (apiKey != null) {
                    "https://solana-mainnet.g.alchemy.com/v2/$apiKey"
                } else {
                    "https://api.mainnet-beta.solana.com"
                }
            }
            "devnet" -> "https://api.devnet.solana.com"
            "testnet" -> "https://api.testnet.solana.com"
            else -> null
        }
    }
    
    private suspend fun getBscRpcUrl(network: String): String? {
        return when (network) {
            "mainnet" -> "https://bsc-dataseed.binance.org"
            "testnet" -> "https://data-seed-prebsc-1-s1.binance.org:8545"
            else -> null
        }
    }
    
    private suspend fun getPolygonRpcUrl(network: String): String? {
        val alchemyKey = getApiKey(KEY_ALCHEMY)
        
        return when {
            alchemyKey != null -> when (network) {
                "mainnet" -> "https://polygon-mainnet.g.alchemy.com/v2/$alchemyKey"
                "mumbai" -> "https://polygon-mumbai.g.alchemy.com/v2/$alchemyKey"
                else -> null
            }
            else -> when (network) {
                "mainnet" -> "https://polygon-rpc.com"
                "mumbai" -> "https://rpc-mumbai.maticvigil.com"
                else -> null
            }
        }
    }
    
    private suspend fun getBitcoinRpcUrl(network: String): String? {
        val blockcypherKey = getApiKey(KEY_BLOCKCYPHER)
        
        return when (network) {
            "mainnet" -> {
                if (blockcypherKey != null) {
                    "https://api.blockcypher.com/v1/btc/main?token=$blockcypherKey"
                } else {
                    "https://blockstream.info/api"
                }
            }
            "testnet" -> {
                if (blockcypherKey != null) {
                    "https://api.blockcypher.com/v1/btc/test3?token=$blockcypherKey"
                } else {
                    "https://blockstream.info/testnet/api"
                }
            }
            else -> null
        }
    }
    
    /**
     * 驗證必要的 API Keys 是否存在
     */
    suspend fun validateRequiredKeys(): ApiKeyValidation = mutex.withLock {
        val missingKeys = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // 檢查關鍵 API Keys
        val criticalKeys = listOf(KEY_INFURA, KEY_ALCHEMY)
        val hasAnyCritical = criticalKeys.any { apiKeys.containsKey(it) }
        
        if (!hasAnyCritical) {
            missingKeys.add("需要至少一個 Ethereum RPC 提供者 (Infura 或 Alchemy)")
        }
        
        // 檢查可選但推薦的 Keys
        val recommendedKeys = mapOf(
            KEY_ETHERSCAN to "Etherscan (用於交易歷史)",
            KEY_MORALIS to "Moralis (用於 NFT 數據)"
        )
        
        recommendedKeys.forEach { (key, description) ->
            if (!apiKeys.containsKey(key)) {
                warnings.add("缺少 $description API Key")
            }
        }
        
        return@withLock ApiKeyValidation(
            isValid = missingKeys.isEmpty(),
            missingKeys = missingKeys,
            warnings = warnings
        )
    }
}

/**
 * API Key 提供者介面
 */
interface ApiKeyProvider {
    /**
     * 獲取單個 API Key
     */
    suspend fun getKey(keyName: String): String?
    
    /**
     * 獲取所有 API Keys
     */
    suspend fun getAllKeys(): Map<String, String>
}

/**
 * 環境變量 API Key 提供者
 * KMP 中使用 expect/actual 模式處理平台特定實現
 */
expect class EnvironmentApiKeyProvider : ApiKeyProvider {
    override suspend fun getKey(keyName: String): String?
    override suspend fun getAllKeys(): Map<String, String>
}

/**
 * BuildConfig API Key 提供者（Android 專用）
 */
expect class BuildConfigApiKeyProvider : ApiKeyProvider {
    override suspend fun getKey(keyName: String): String?
    override suspend fun getAllKeys(): Map<String, String>
}

/**
 * 安全存儲 API Key 提供者
 */
expect class SecureStorageApiKeyProvider : ApiKeyProvider {
    override suspend fun getKey(keyName: String): String?
    override suspend fun getAllKeys(): Map<String, String>
}

/**
 * API Key 驗證結果
 */
data class ApiKeyValidation(
    val isValid: Boolean,
    val missingKeys: List<String>,
    val warnings: List<String>
)

/**
 * RPC 節點配置
 */
data class RpcNodeConfig(
    val url: String,
    val apiKey: String? = null,
    val isPrimary: Boolean = true,
    val rateLimit: Int? = null // 每秒請求數限制
)
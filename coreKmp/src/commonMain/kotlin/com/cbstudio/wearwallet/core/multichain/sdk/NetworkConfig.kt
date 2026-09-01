package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.multichain.MultiChainType

/**
 * 網路配置管理
 * 包含主網和測試網的 RPC 端點
 */
object NetworkConfig {
    
    /**
     * 獲取網路配置
     */
    fun getConfig(chain: MultiChainType, useTestnet: Boolean = true): SDKConfig {
        return when (chain) {
            MultiChainType.SOLANA -> getSolanaConfig(useTestnet)
            MultiChainType.ETHEREUM -> getEthereumConfig(useTestnet)
            MultiChainType.TRON -> getTronConfig(useTestnet)
            else -> getDefaultConfig(chain)
        }
    }
    
    private fun getSolanaConfig(useTestnet: Boolean): SDKConfig {
        return if (useTestnet) {
            SDKConfig(
                network = "devnet",
                rpcUrl = "https://api.devnet.solana.com",
                customParams = mapOf(
                    "commitment" to "confirmed",
                    "encoding" to "base64",
                    "maxRetries" to 3
                )
            )
        } else {
            SDKConfig(
                network = "mainnet-beta",
                rpcUrl = "https://api.mainnet-beta.solana.com",
                customParams = mapOf(
                    "commitment" to "finalized",
                    "encoding" to "base64",
                    "maxRetries" to 5
                )
            )
        }
    }
    
    private fun getEthereumConfig(useTestnet: Boolean): SDKConfig {
        return if (useTestnet) {
            SDKConfig(
                network = "sepolia",
                rpcUrl = "https://sepolia.infura.io/v3/9aa3d95b3bc440fa88ea12eaa4456161", // 公開測試 API
                customParams = mapOf(
                    "chainId" to 11155111,
                    "blockTime" to 12,
                    "confirmations" to 2
                )
            )
        } else {
            SDKConfig(
                network = "mainnet",
                rpcUrl = "https://mainnet.infura.io/v3/9aa3d95b3bc440fa88ea12eaa4456161", // 需要替換為真實 API key
                customParams = mapOf(
                    "chainId" to 1,
                    "blockTime" to 12,
                    "confirmations" to 6
                )
            )
        }
    }
    
    private fun getTronConfig(useTestnet: Boolean): SDKConfig {
        return if (useTestnet) {
            SDKConfig(
                network = "shasta",
                rpcUrl = "https://api.shasta.trongrid.io",
                customParams = mapOf(
                    "apiKey" to "test-api-key", // Shasta 不需要 API key
                    "fullNodeUrl" to "https://api.shasta.trongrid.io",
                    "solidityNodeUrl" to "https://api.shasta.trongrid.io",
                    "eventServerUrl" to "https://api.shasta.trongrid.io"
                )
            )
        } else {
            SDKConfig(
                network = "mainnet",
                rpcUrl = "https://api.trongrid.io",
                customParams = mapOf(
                    "apiKey" to "your-trongrid-api-key", // 需要替換為真實 API key
                    "fullNodeUrl" to "https://api.trongrid.io",
                    "solidityNodeUrl" to "https://api.trongrid.io",
                    "eventServerUrl" to "https://api.trongrid.io"
                )
            )
        }
    }
    
    private fun getDefaultConfig(chain: MultiChainType): SDKConfig {
        return SDKConfig(
            network = "testnet",
            rpcUrl = "",
            customParams = mapOf(
                "chain" to chain.name,
                "useTestnet" to true
            )
        )
    }
    
    /**
     * 測試網代幣合約地址
     */
    object TestnetTokens {
        val SOLANA = mapOf(
            "USDC" to "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU", // Devnet USDC
            "USDT" to "EJwZgeZrdC8TXTQbQBoL6bfuAnFUUy1PVCMB4DYPzVaS"  // Devnet USDT
        )
        
        val ETHEREUM = mapOf(
            "USDC" to "0x07865c6E87B9F70255377e024ace6630C1Eaa37F", // Sepolia USDC
            "USDT" to "0x7169D38820dfd117C3FA1f22a697dBA58d90BA06", // Sepolia USDT
            "DAI" to "0x68194a729C2450ad26072b3D33ADaCbcef39D574"  // Sepolia DAI
        )
        
        val TRON = mapOf(
            "USDT" to "TG3XXyExBkPp9nzdajDZsozEu4BkaSJozs", // Shasta USDT
            "USDC" to "TWYdfHUjjfQFTf7XUvf8rNpbLrCtA43qGU"  // Shasta USDC
        )
    }
    
    /**
     * 主網代幣合約地址
     */
    object MainnetTokens {
        val SOLANA = mapOf(
            "USDC" to "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
            "USDT" to "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
        )
        
        val ETHEREUM = mapOf(
            "USDC" to "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            "USDT" to "0xdAC17F958D2ee523a2206206994597C13D831ec7",
            "DAI" to "0x6B175474E89094C44Da98b954EedeAC495271d0F"
        )
        
        val TRON = mapOf(
            "USDT" to "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
            "USDC" to "TEkxiTehnzSmSe2XqrBj4w32RUN966rdz8"
        )
    }
    
    /**
     * 區塊瀏覽器 URL
     */
    fun getExplorerUrl(chain: MultiChainType, txHash: String, useTestnet: Boolean = true): String {
        return when (chain) {
            MultiChainType.SOLANA -> {
                val network = if (useTestnet) "?cluster=devnet" else ""
                "https://solscan.io/tx/$txHash$network"
            }
            MultiChainType.ETHEREUM -> {
                val subdomain = if (useTestnet) "sepolia." else ""
                "https://${subdomain}etherscan.io/tx/$txHash"
            }
            MultiChainType.TRON -> {
                val subdomain = if (useTestnet) "shasta." else ""
                "https://${subdomain}tronscan.org/#/transaction/$txHash"
            }
            else -> ""
        }
    }
    
    /**
     * 水龍頭 URL (測試網)
     */
    fun getFaucetUrl(chain: MultiChainType): String? {
        return when (chain) {
            MultiChainType.SOLANA -> "https://solfaucet.com"
            MultiChainType.ETHEREUM -> "https://sepoliafaucet.com"
            MultiChainType.TRON -> "https://shasta.tronscan.org/#/faucet"
            else -> null
        }
    }
    
    /**
     * 驗證 RPC 端點是否可用
     */
    suspend fun validateRpcEndpoint(url: String): Boolean {
        return try {
            // 這裡應該實作真實的 RPC 健康檢查
            // 例如發送一個簡單的請求來檢查連接
            url.isNotEmpty() && url.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 獲取推薦的 Gas 價格
     */
    fun getRecommendedGasPrice(chain: MultiChainType, priority: GasPriority = GasPriority.NORMAL): String {
        return when (chain) {
            MultiChainType.ETHEREUM -> when (priority) {
                GasPriority.SLOW -> "20" // Gwei
                GasPriority.NORMAL -> "30"
                GasPriority.FAST -> "50"
            }
            MultiChainType.TRON -> when (priority) {
                GasPriority.SLOW -> "10" // Sun
                GasPriority.NORMAL -> "15"
                GasPriority.FAST -> "30"
            }
            else -> "1"
        }
    }
    
    enum class GasPriority {
        SLOW, NORMAL, FAST
    }
}
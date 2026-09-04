package com.cbstudio.wearwallet.core.caip

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result

/**
 * CAIP (Chain Agnostic Improvement Proposals) 標準化實作
 * 
 * 實作業界標準的跨鏈標識符，提供統一的地址和資產表示方法
 * 
 * 標準參考:
 * - CAIP-2: Blockchain ID Specification
 * - CAIP-10: Account ID Specification  
 * - CAIP-19: Asset Type and Asset ID Specification
 * - CAIP-196: Chain Agnostic Asset ID Specification
 */

/**
 * CAIP-2 區塊鏈 ID 標準
 * 格式: namespace:reference
 * 
 * 範例:
 * - eip155:1 (Ethereum mainnet)
 * - cosmos:cosmoshub-4 (Cosmos Hub)
 * - bip122:000000000019d6689c085ae165831e93 (Bitcoin mainnet)
 */
data class CAIPChainID(
    val namespace: String,    // 命名空間 (eip155, cosmos, bip122, etc.)
    val reference: String     // 鏈參考 ID
) {
    /**
     * 轉換為 CAIP-2 字符串格式
     */
    fun toCAIPString(): String = "$namespace:$reference"
    
    /**
     * 取得完整的鏈識別描述
     */
    fun getDescription(): String {
        return when (namespace) {
            "eip155" -> when (reference) {
                "1" -> "Ethereum Mainnet"
                "137" -> "Polygon Mainnet"
                "56" -> "BSC Mainnet"
                "25" -> "Cronos Mainnet"
                else -> "EVM Chain $reference"
            }
            "cosmos" -> "Cosmos Chain $reference"
            "bip122" -> "Bitcoin Chain $reference"
            "solana" -> "Solana $reference"
            "polkadot" -> "Polkadot $reference"
            else -> "Chain $namespace:$reference"
        }
    }
    
    companion object {
        /**
         * 從 CAIP-2 字符串解析
         */
        fun parse(caipString: String): Result<CAIPChainID> {
            val parts = caipString.split(":")
            return if (parts.size == 2) {
                Result.Success(CAIPChainID(parts[0], parts[1]))
            } else {
                Result.Failure(IllegalArgumentException("Invalid CAIP-2 format: $caipString"))
            }
        }
        
        /**
         * 從 MultiChainType 創建 CAIP 鏈 ID
         */
        fun fromMultiChainType(chainType: MultiChainType, network: String = "mainnet"): CAIPChainID {
            return when (chainType) {
                MultiChainType.ETHEREUM -> CAIPChainID("eip155", "1")
                MultiChainType.SOLANA -> CAIPChainID("solana", when (network) {
                    "mainnet" -> "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"
                    "testnet" -> "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3z"
                    "devnet" -> "EtWTRABZaYq6iMfeYKouRu166VU2xqa1"
                    else -> "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"
                })
                MultiChainType.BITCOIN -> CAIPChainID("bip122", "000000000019d6689c085ae165831e93")
                MultiChainType.TRON -> CAIPChainID("tron", "0x2b6653dc")
                MultiChainType.POLKADOT -> CAIPChainID("polkadot", "91b171bb158e2d3848fa23a9f1c25182")
                MultiChainType.CARDANO -> CAIPChainID("cardano", "764824073")
                MultiChainType.MONERO -> CAIPChainID("monero", "mainnet")
                else -> CAIPChainID("unknown", chainType.symbol.lowercase())
            }
        }
    }
}

/**
 * CAIP-10 帳戶 ID 標準  
 * 格式: namespace:chain_id:account_address
 * 
 * 範例:
 * - eip155:1:0xab16a96d359ec26a11e2c2b3d8f8b8942d5bfcdb (Ethereum)
 * - cosmos:cosmoshub-4:cosmos1t2uflqwqe0fsj0shcfkrvpukewcw40yjj6hdc0 (Cosmos)
 * - solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp:4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F
 */
data class CAIPAddress(
    val chainId: CAIPChainID,      // 區塊鏈 ID
    val address: String            // 帳戶地址
) {
    /**
     * 轉換為 CAIP-10 字符串格式
     */
    fun toCAIPString(): String = "${chainId.toCAIPString()}:$address"
    
    /**
     * 取得簡化的顯示地址（前6後4字符）
     */
    fun getDisplayAddress(): String {
        return if (address.length > 10) {
            "${address.take(6)}...${address.takeLast(4)}"
        } else {
            address
        }
    }
    
    /**
     * 驗證地址格式是否符合對應鏈的要求
     */
    fun validate(): Result<Boolean> {
        return try {
            when (chainId.namespace) {
                "eip155" -> validateEthereumAddress(address)
                "cosmos" -> validateCosmosAddress(address)
                "solana" -> validateSolanaAddress(address)
                "bip122" -> validateBitcoinAddress(address)
                "polkadot" -> validatePolkadotAddress(address)
                else -> Result.Success(true) // 未知鏈暫時通過
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    companion object {
        /**
         * 從 CAIP-10 字符串解析
         */
        fun parse(caipString: String): Result<CAIPAddress> {
            val parts = caipString.split(":")
            return if (parts.size == 3) {
                val chainId = CAIPChainID(parts[0], parts[1])
                Result.Success(CAIPAddress(chainId, parts[2]))
            } else {
                Result.Failure(IllegalArgumentException("Invalid CAIP-10 format: $caipString"))
            }
        }
        
        /**
         * 從傳統地址創建 CAIP 地址
         */
        fun fromLegacyAddress(
            address: String,
            chainType: MultiChainType,
            network: String = "mainnet"
        ): CAIPAddress {
            val chainId = CAIPChainID.fromMultiChainType(chainType, network)
            return CAIPAddress(chainId, address)
        }
    }
    
    private fun validateEthereumAddress(address: String): Result<Boolean> {
        val isValid = address.startsWith("0x") && address.length == 42 && 
                     address.drop(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        return Result.Success(isValid)
    }
    
    private fun validateCosmosAddress(address: String): Result<Boolean> {
        val isValid = address.startsWith("cosmos") && address.length == 45
        return Result.Success(isValid)
    }
    
    private fun validateSolanaAddress(address: String): Result<Boolean> {
        val isValid = address.length == 44 && address.matches(Regex("[1-9A-HJ-NP-Za-km-z]+"))
        return Result.Success(isValid)
    }
    
    private fun validateBitcoinAddress(address: String): Result<Boolean> {
        val isValid = (address.startsWith("1") || address.startsWith("3") || 
                      address.startsWith("bc1")) && address.length in 26..62
        return Result.Success(isValid)
    }
    
    private fun validatePolkadotAddress(address: String): Result<Boolean> {
        val isValid = address.length >= 47
        return Result.Success(isValid)
    }
}

/**
 * CAIP-19 資產類型和資產 ID 標準
 * 格式: namespace:chain_id/asset_namespace:asset_reference
 * 
 * 範例:
 * - eip155:1/slip44:60 (ETH on Ethereum)
 * - eip155:1/erc20:0xa0b86a33e6776bb5b4e8a8e7b4a9b23ef4b50c6b (ERC20 token)  
 * - eip155:1/erc721:0x06012c8cf97bead5deae237070f9587f8e7a266d/771769 (CryptoKitty)
 * - cosmos:cosmoshub-4/slip44:118 (ATOM)
 */
data class CAIPAsset(
    val chainId: CAIPChainID,          // 區塊鏈 ID
    val assetNamespace: String,        // 資產命名空間 (slip44, erc20, erc721, etc.)
    val assetReference: String         // 資產參考 (合約地址或代幣 ID)
) {
    /**
     * 轉換為 CAIP-19 字符串格式
     */
    fun toCAIPString(): String = "${chainId.toCAIPString()}/$assetNamespace:$assetReference"
    
    /**
     * 檢查是否為原生代幣
     */
    fun isNativeToken(): Boolean = assetNamespace == "slip44"
    
    /**
     * 檢查是否為 ERC20 代幣
     */
    fun isERC20Token(): Boolean = assetNamespace == "erc20"
    
    /**
     * 檢查是否為 NFT
     */
    fun isNFT(): Boolean = assetNamespace in listOf("erc721", "erc1155", "spl-token")
    
    /**
     * 取得資產符號（需要額外資料來源支援）
     */
    fun getSymbol(): String {
        return when {
            isNativeToken() -> when (chainId.namespace) {
                "eip155" -> when (chainId.reference) {
                    "1" -> "ETH"
                    "137" -> "MATIC" 
                    "56" -> "BNB"
                    "25" -> "CRO"
                    else -> "ETH"
                }
                "cosmos" -> "ATOM"
                "solana" -> "SOL"
                "bip122" -> "BTC"
                else -> "UNKNOWN"
            }
            else -> extractTokenSymbol() ?: "TOKEN"
        }
    }
    
    private fun extractTokenSymbol(): String? {
        // 這裡需要查詢代幣合約來取得符號
        // 暫時返回簡化的標識
        return when (assetNamespace) {
            "erc20" -> "ERC20"
            "erc721" -> "NFT"
            "erc1155" -> "NFT" 
            else -> null
        }
    }
    
    companion object {
        /**
         * 從 CAIP-19 字符串解析
         */
        fun parse(caipString: String): Result<CAIPAsset> {
            val mainParts = caipString.split("/")
            if (mainParts.size != 2) {
                return Result.Failure(IllegalArgumentException("Invalid CAIP-19 format: $caipString"))
            }
            
            val chainIdResult = CAIPChainID.parse(mainParts[0])
            if (chainIdResult.isFailure()) {
                return chainIdResult as Result.Failure
            }
            
            val assetParts = mainParts[1].split(":")
            if (assetParts.size != 2) {
                return Result.Failure(IllegalArgumentException("Invalid asset format in CAIP-19: $caipString"))
            }
            
            return Result.Success(
                CAIPAsset(
                    chainId = chainIdResult.getOrThrow(),
                    assetNamespace = assetParts[0],
                    assetReference = assetParts[1]
                )
            )
        }
        
        /**
         * 創建原生代幣資產
         */
        fun createNativeAsset(chainType: MultiChainType, network: String = "mainnet"): CAIPAsset {
            val chainId = CAIPChainID.fromMultiChainType(chainType, network)
            val slip44Id = when (chainType) {
                MultiChainType.ETHEREUM -> "60"
                MultiChainType.BITCOIN -> "0"
                MultiChainType.ETHEREUM -> "60" // ETH 也使用相同的 slip44
                MultiChainType.BITCOIN -> "0"
                MultiChainType.BITCOIN_CASH -> "145"
                MultiChainType.LITECOIN -> "2"
                MultiChainType.DOGECOIN -> "3"
                MultiChainType.SOLANA -> "501"
                MultiChainType.POLKADOT -> "354"
                MultiChainType.CARDANO -> "1815"
                MultiChainType.TRON -> "195"
                MultiChainType.MONERO -> "128"
                else -> "0"
            }
            
            return CAIPAsset(
                chainId = chainId,
                assetNamespace = "slip44",
                assetReference = slip44Id
            )
        }
        
        /**
         * 創建 ERC20 代幣資產
         */
        fun createERC20Asset(
            contractAddress: String,
            chainType: MultiChainType,
            network: String = "mainnet"
        ): CAIPAsset {
            val chainId = CAIPChainID.fromMultiChainType(chainType, network)
            return CAIPAsset(
                chainId = chainId,
                assetNamespace = "erc20",
                assetReference = contractAddress.lowercase()
            )
        }
        
        /**
         * 創建 NFT 資產
         */
        fun createNFTAsset(
            contractAddress: String,
            tokenId: String,
            chainType: MultiChainType,
            standard: String = "erc721",
            network: String = "mainnet"
        ): CAIPAsset {
            val chainId = CAIPChainID.fromMultiChainType(chainType, network)
            return CAIPAsset(
                chainId = chainId,
                assetNamespace = standard,
                assetReference = "${contractAddress.lowercase()}/$tokenId"
            )
        }
    }
}

/**
 * CAIP 交易請求標準化
 */
data class CAIPTransactionRequest(
    val fromAddress: CAIPAddress,       // 發送方 CAIP 地址
    val toAddress: CAIPAddress,         // 接收方 CAIP 地址  
    val asset: CAIPAsset,              // 轉移的資產
    val amount: String,                 // 轉移金額
    val memo: String? = null,           // 交易備註
    val gasLimit: String? = null,       // Gas 限制
    val gasPrice: String? = null,       // Gas 價格
    val metadata: Map<String, Any> = emptyMap()  // 附加元數據
) {
    /**
     * 驗證交易請求
     */
    fun validate(): Result<Boolean> {
        return try {
            // 驗證地址
            val fromValidation = fromAddress.validate()
            if (fromValidation.isFailure()) {
                return fromValidation as Result.Failure
            }
            
            val toValidation = toAddress.validate()
            if (toValidation.isFailure()) {
                return toValidation as Result.Failure
            }
            
            // 驗證同鏈交易
            if (fromAddress.chainId != toAddress.chainId) {
                return Result.Failure(IllegalArgumentException("Cross-chain transfers not supported in single transaction"))
            }
            
            // 驗證資產屬於同一鏈
            if (asset.chainId != fromAddress.chainId) {
                return Result.Failure(IllegalArgumentException("Asset chain does not match transaction chain"))
            }
            
            // 驗證金額格式
            if (amount.toDoubleOrNull() == null || amount.toDouble() <= 0) {
                return Result.Failure(IllegalArgumentException("Invalid amount: $amount"))
            }
            
            Result.Success(true)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 轉換為舊版交易請求格式（向後兼容）
     */
    fun toLegacyTransactionRequest(): com.cbstudio.wearwallet.core.multichain.sdk.TransactionRequest {
        return com.cbstudio.wearwallet.core.multichain.sdk.TransactionRequest(
            fromAddress = fromAddress.address,
            toAddress = toAddress.address,
            amount = amount,
            tokenAddress = if (asset.isERC20Token()) asset.assetReference else null,
            memo = memo,
            customGasLimit = gasLimit,
            customGasPrice = gasPrice
        )
    }
}

/**
 * CAIP 交易結果
 */
data class CAIPTransactionResult(
    val transactionHash: String,        // 交易雜湊
    val status: CAIPTransactionStatus,  // 交易狀態
    val chainId: CAIPChainID,          // 執行鏈
    val blockNumber: Long? = null,      // 區塊號
    val gasUsed: String? = null,       // 實際使用 Gas
    val fee: String? = null,           // 實際手續費
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val explorerUrl: String? = null    // 區塊瀏覽器 URL
)

/**
 * CAIP 交易狀態
 */
enum class CAIPTransactionStatus {
    PENDING,     // 待處理
    CONFIRMED,   // 已確認
    FAILED,      // 失敗
    CANCELLED    // 已取消
}

/**
 * CAIP 服務 - 提供標準化操作介面
 */
class CAIPService {
    
    /**
     * 解析任意 CAIP 字符串
     */
    fun parseCAIPString(caipString: String): Result<Any> {
        return when {
            caipString.count { it == ':' } == 1 -> {
                // CAIP-2 格式 (namespace:reference)
                CAIPChainID.parse(caipString)
            }
            caipString.count { it == ':' } == 2 -> {
                // CAIP-10 格式 (namespace:chain_id:account_address)  
                CAIPAddress.parse(caipString)
            }
            caipString.contains('/') -> {
                // CAIP-19 格式 (namespace:chain_id/asset_namespace:asset_reference)
                CAIPAsset.parse(caipString)
            }
            else -> {
                Result.Failure(IllegalArgumentException("Unknown CAIP format: $caipString"))
            }
        }
    }
    
    /**
     * 驗證 CAIP 格式字符串
     */
    fun validateCAIPString(caipString: String): Result<Boolean> {
        return when (val result = parseCAIPString(caipString)) {
            is Result.Success -> Result.Success(true)
            is Result.Failure -> Result.Success(false)
            is Result.Loading -> Result.Success(false)
        }
    }
    
    /**
     * 將舊版地址轉換為 CAIP 格式
     */
    fun convertLegacyAddress(
        address: String, 
        chainType: MultiChainType, 
        network: String = "mainnet"
    ): CAIPAddress {
        return CAIPAddress.fromLegacyAddress(address, chainType, network)
    }
    
    /**
     * 取得支援的命名空間列表
     */
    fun getSupportedNamespaces(): List<String> {
        return listOf(
            "eip155",    // Ethereum 兼容鏈
            "cosmos",    // Cosmos 生態
            "solana",    // Solana
            "bip122",    // Bitcoin 兼容鏈
            "polkadot",  // Polkadot
            "cardano",   // Cardano
            "tron",      // TRON
            "monero"     // Monero
        )
    }
    
    /**
     * 取得支援的資產命名空間
     */
    fun getSupportedAssetNamespaces(): List<String> {
        return listOf(
            "slip44",    // 原生代幣 (SLIP-44 coin types)
            "erc20",     // ERC-20 代幣
            "erc721",    // ERC-721 NFT
            "erc1155",   // ERC-1155 多代幣
            "spl-token", // Solana SPL 代幣
            "cw20",      // CosmWasm CW-20 代幣
            "native"     // 鏈原生資產
        )
    }
    
    /**
     * 根據鏈類型推薦區塊瀏覽器 URL
     */
    fun getExplorerUrl(chainId: CAIPChainID, transactionHash: String): String? {
        return when (chainId.namespace) {
            "eip155" -> when (chainId.reference) {
                "1" -> "https://etherscan.io/tx/$transactionHash"
                "137" -> "https://polygonscan.com/tx/$transactionHash"
                "56" -> "https://bscscan.com/tx/$transactionHash"
                "25" -> "https://cronoscan.com/tx/$transactionHash"
                else -> null
            }
            "solana" -> "https://solscan.io/tx/$transactionHash"
            "cosmos" -> "https://www.mintscan.io/cosmos/txs/$transactionHash"
            "bip122" -> "https://blockstream.info/tx/$transactionHash"
            "polkadot" -> "https://polkadot.subscan.io/extrinsic/$transactionHash"
            else -> null
        }
    }
}
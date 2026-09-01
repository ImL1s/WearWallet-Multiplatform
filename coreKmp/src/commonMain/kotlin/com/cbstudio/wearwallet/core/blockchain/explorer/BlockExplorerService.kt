package com.cbstudio.wearwallet.core.blockchain.explorer

import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.security.ApiKeyManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * 區塊鏈瀏覽器服務
 * 整合 Etherscan、BscScan、PolygonScan 等 API
 * 提供交易歷史、代幣交易、NFT 轉移等查詢功能
 */
class BlockExplorerService {
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
        }
    }
    
    /**
     * 獲取交易歷史
     * @param address 錢包地址
     * @param chainId 鏈 ID
     * @param page 頁碼（從 1 開始）
     * @param pageSize 每頁數量（最多 10000）
     * @param startBlock 起始區塊（0 表示從創世區塊開始）
     * @param endBlock 結束區塊（99999999 表示到最新區塊）
     * @param sort 排序方式（"asc" 或 "desc"）
     */
    suspend fun getTransactionHistory(
        address: String,
        chainId: String,
        page: Int = 1,
        pageSize: Int = 50,
        startBlock: Long = 0,
        endBlock: Long = 99999999,
        sort: String = "desc"
    ): Result<List<Transaction>> = withContext(Dispatchers.Default) {
        try {
            val apiKey = getExplorerApiKey(chainId)
            if (apiKey == null) {
                // 沒有 API Key，返回空列表而非錯誤（避免阻塞 UI）
                // 這是因為 Etherscan 等服務需要 API Key，但用戶可能尚未配置
                return@withContext Result.Success(emptyList())
            }

            val explorerUrl = getExplorerApiUrl(chainId)
            val url = buildString {
                append(explorerUrl)
                append("?chainid=$chainId")  // V2 API requires chainid
                append("&module=account")
                append("&action=txlist")
                append("&address=$address")
                append("&startblock=$startBlock")
                append("&endblock=$endBlock")
                append("&page=$page")
                append("&offset=$pageSize")
                append("&sort=$sort")
                append("&apikey=$apiKey")
            }

            val response: HttpResponse = client.get(url)
            val responseText = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val jsonElement = json.parseToJsonElement(responseText)
            val jsonObject = jsonElement.jsonObject

            val status = jsonObject["status"]?.jsonPrimitive?.content ?: "0"
            val message = jsonObject["message"]?.jsonPrimitive?.content ?: ""
            val resultElement = jsonObject["result"]

            // 處理 Etherscan API 的兩種回應格式：
            // 1. 有交易時：result 是陣列
            // 2. 無交易時：result 是字串 "No transactions found"
            if (status == "1" && resultElement is JsonArray) {
                val transactions = resultElement.mapNotNull { txElement ->
                    try {
                        val tx = json.decodeFromJsonElement<EtherscanTransaction>(txElement)
                        // 解析 ERC-20 代幣轉帳
                        val isTokenTransfer = tx.input.startsWith("0xa9059cbb") // transfer(address,uint256)
                        val tokenInfo = if (isTokenTransfer && tx.value == "0") {
                            decodeErc20Transfer(tx.to, tx.input)
                        } else {
                            null
                        }
                        
                        Transaction(
                            hash = tx.hash,
                            from = tx.from,
                            to = tokenInfo?.recipient ?: tx.to,
                            value = tokenInfo?.amount ?: tx.value,
                            gasPrice = tx.gasPrice,
                            gasUsed = tx.gasUsed?.toLongOrNull(),
                            blockNumber = tx.blockNumber.toLongOrNull(),
                            timestamp = Instant.fromEpochMilliseconds((tx.timeStamp.toLongOrNull() ?: 0L) * 1000),
                            status = if (tx.isError == "0") TransactionStatus.CONFIRMED else TransactionStatus.FAILED,
                            chainType = when(chainId) {
                                "1" -> ChainType.ETHEREUM
                                "56" -> ChainType.BSC
                                "137" -> ChainType.POLYGON
                                "42161" -> ChainType.ARBITRUM
                                "10" -> ChainType.OPTIMISM
                                "43114" -> ChainType.AVALANCHE
                                "250" -> ChainType.FANTOM
                                "8453" -> ChainType.BASE
                                else -> ChainType.ETHEREUM
                            },
                            nonce = tx.nonce.toLongOrNull() ?: 0L,
                            data = tx.input,
                            tokenAddress = if (isTokenTransfer) tx.to else tx.contractAddress.takeIf { it.isNotEmpty() },
                            tokenName = tokenInfo?.name,
                            tokenSymbol = tokenInfo?.symbol,
                            tokenDecimals = tokenInfo?.decimals
                        )
                    } catch (e: Exception) {
                        println("⚠️ 解析交易失敗: ${e.message}")
                        null
                    }
                }

                // Log success (println不顯示在Android logcat)
                Result.Success(transactions)
            } else if (status == "0" && (message.contains("No transactions found", ignoreCase = true) ||
                                          resultElement is JsonPrimitive)) {
                // 無交易的情況 - 返回空列表而非錯誤
                // 此地址沒有交易記錄
                Result.Success(emptyList())
            } else {
                // API 返回錯誤: $message
                Result.Failure(Exception(message))
            }
        } catch (e: Exception) {
            // 獲取交易歷史失敗: ${e.message}
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取 ERC20 代幣交易歷史
     */
    suspend fun getTokenTransactionHistory(
        address: String,
        tokenContract: String? = null,
        chainId: String,
        page: Int = 1,
        pageSize: Int = 50,
        startBlock: Long = 0,
        endBlock: Long = 99999999,
        sort: String = "desc"
    ): Result<List<Transaction>> = withContext(Dispatchers.Default) {
        try {
            val apiKey = getExplorerApiKey(chainId)
            if (apiKey == null) {
                return@withContext Result.Failure(Exception("No API key configured"))
            }
            
            val explorerUrl = getExplorerApiUrl(chainId)
            val url = buildString {
                append(explorerUrl)
                append("?module=account")
                append("&action=tokentx")
                append("&address=$address")
                if (tokenContract != null) {
                    append("&contractaddress=$tokenContract")
                }
                append("&startblock=$startBlock")
                append("&endblock=$endBlock")
                append("&page=$page")
                append("&offset=$pageSize")
                append("&sort=$sort")
                append("&apikey=$apiKey")
            }
            
            val response: HttpResponse = client.get(url)
            val responseData = response.body<EtherscanTokenResponse>()
            
            if (responseData.status == "1" && responseData.message == "OK") {
                val transactions = responseData.result.map { tx ->
                    Transaction(
                        hash = tx.hash,
                        from = tx.from,
                        to = tx.to,
                        value = tx.value,
                        gasPrice = tx.gasPrice,
                        gasUsed = tx.gasUsed?.toLongOrNull(),
                        blockNumber = tx.blockNumber.toLongOrNull(),
                        timestamp = Instant.fromEpochMilliseconds((tx.timeStamp.toLongOrNull() ?: 0L) * 1000),
                        status = TransactionStatus.CONFIRMED,
                        chainType = when(chainId) {
                            "1" -> ChainType.ETHEREUM
                            "56" -> ChainType.BSC
                            "137" -> ChainType.POLYGON
                            else -> ChainType.ETHEREUM
                        },
                        nonce = tx.nonce.toLongOrNull() ?: 0L,
                        data = tx.input,
                        tokenAddress = tx.contractAddress,
                        tokenName = tx.tokenName,
                        tokenSymbol = tx.tokenSymbol,
                        tokenDecimals = tx.tokenDecimal.toIntOrNull()
                    )
                }
                
                println("✅ 成功獲取 ${transactions.size} 筆代幣交易記錄")
                Result.Success(transactions)
            } else {
                Result.Failure(Exception(responseData.message))
            }
        } catch (e: Exception) {
            println("❌ 獲取代幣交易歷史失敗: ${e.message}")
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取 NFT 轉移歷史
     */
    suspend fun getNFTTransferHistory(
        address: String,
        contractAddress: String? = null,
        chainId: String,
        page: Int = 1,
        pageSize: Int = 50
    ): Result<List<NFTTransfer>> = withContext(Dispatchers.Default) {
        try {
            val apiKey = getExplorerApiKey(chainId)
            if (apiKey == null) {
                return@withContext Result.Failure(Exception("No API key configured"))
            }
            
            val explorerUrl = getExplorerApiUrl(chainId)
            val url = buildString {
                append(explorerUrl)
                append("?module=account")
                append("&action=tokennfttx")
                append("&address=$address")
                if (contractAddress != null) {
                    append("&contractaddress=$contractAddress")
                }
                append("&page=$page")
                append("&offset=$pageSize")
                append("&sort=desc")
                append("&apikey=$apiKey")
            }
            
            val response: HttpResponse = client.get(url)
            val responseData = response.body<EtherscanNFTResponse>()
            
            if (responseData.status == "1" && responseData.message == "OK") {
                val transfers = responseData.result.map { nft ->
                    NFTTransfer(
                        hash = nft.hash,
                        from = nft.from,
                        to = nft.to,
                        tokenId = nft.tokenID,
                        tokenName = nft.tokenName,
                        tokenSymbol = nft.tokenSymbol,
                        contractAddress = nft.contractAddress,
                        blockNumber = nft.blockNumber.toLongOrNull() ?: 0L,
                        timestamp = nft.timeStamp.toLongOrNull() ?: 0L
                    )
                }
                
                println("✅ 成功獲取 ${transfers.size} 筆 NFT 轉移記錄")
                Result.Success(transfers)
            } else {
                Result.Failure(Exception(responseData.message))
            }
        } catch (e: Exception) {
            println("❌ 獲取 NFT 轉移歷史失敗: ${e.message}")
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取交易收據（確認狀態）
     */
    suspend fun getTransactionReceipt(
        txHash: String,
        chainId: String
    ): Result<TransactionReceipt> = withContext(Dispatchers.Default) {
        try {
            val apiKey = getExplorerApiKey(chainId)
            if (apiKey == null) {
                return@withContext Result.Failure(Exception("No API key configured"))
            }
            
            val explorerUrl = getExplorerApiUrl(chainId)
            val url = buildString {
                append(explorerUrl)
                append("?module=transaction")
                append("&action=gettxreceiptstatus")
                append("&txhash=$txHash")
                append("&apikey=$apiKey")
            }
            
            val response: HttpResponse = client.get(url)
            val responseData = response.body<EtherscanReceiptResponse>()
            
            if (responseData.status == "1") {
                val receipt = TransactionReceipt(
                    status = responseData.result.status == "1",
                    blockNumber = 0L, // 需要額外查詢
                    gasUsed = 0L, // 需要額外查詢
                    effectiveGasPrice = "0"
                )
                Result.Success(receipt)
            } else {
                Result.Failure(Exception(responseData.message))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取地址餘額（包含多個地址）
     */
    suspend fun getBalances(
        addresses: List<String>,
        chainId: String
    ): Result<Map<String, String>> = withContext(Dispatchers.Default) {
        try {
            val apiKey = getExplorerApiKey(chainId)
            if (apiKey == null) {
                return@withContext Result.Failure(Exception("No API key configured"))
            }
            
            val explorerUrl = getExplorerApiUrl(chainId)
            val addressList = addresses.joinToString(",")
            val url = buildString {
                append(explorerUrl)
                append("?module=account")
                append("&action=balancemulti")
                append("&address=$addressList")
                append("&tag=latest")
                append("&apikey=$apiKey")
            }
            
            val response: HttpResponse = client.get(url)
            val responseData = response.body<EtherscanBalanceResponse>()
            
            if (responseData.status == "1") {
                val balances = responseData.result.associate { 
                    it.account to it.balance 
                }
                Result.Success(balances)
            } else {
                Result.Failure(Exception(responseData.message))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    // === 輔助方法 ===
    
    private suspend fun getExplorerApiKey(chainId: String): String? {
        // V2 API uses a single Etherscan API key for all supported chains
        return ApiKeyManager.getApiKey(ApiKeyManager.KEY_ETHERSCAN)
    }
    
    private fun getExplorerApiUrl(chainId: String): String {
        // All chains now use the unified Etherscan V2 API
        return "https://api.etherscan.io/v2/api"
    }
    
    private fun getChainName(chainId: String): String {
        return when (chainId) {
            "1" -> "Ethereum"
            "56" -> "BSC"
            "137" -> "Polygon"
            "42161" -> "Arbitrum"
            "10" -> "Optimism"
            "43114" -> "Avalanche"
            else -> "Chain $chainId"
        }
    }
    
    fun close() {
        client.close()
    }
}

// === Response Models ===

@Serializable
data class EtherscanResponse(
    val status: String,
    val message: String,
    val result: List<EtherscanTransaction>
)

@Serializable
data class EtherscanTransaction(
    val blockNumber: String,
    val timeStamp: String,
    val hash: String,
    val nonce: String,
    val blockHash: String,
    val transactionIndex: String,
    val from: String,
    val to: String,
    val value: String,
    val gas: String,
    val gasPrice: String,
    val isError: String,
    val txreceipt_status: String = "",
    val input: String,
    val contractAddress: String,
    val cumulativeGasUsed: String,
    val gasUsed: String,
    val confirmations: String,
    val methodId: String = "",
    val functionName: String = ""
)

/**
 * ERC-20 代幣轉帳信息
 */
data class Erc20TransferInfo(
    val recipient: String,
    val amount: String,  // 已轉換為代幣單位
    val symbol: String,
    val name: String,
    val decimals: Int
)

/**
 * 常見代幣合約地址映射 (Ethereum Mainnet)
 */
private val KNOWN_TOKENS = mapOf(
    "0xdac17f958d2ee523a2206206994597c13d831ec7" to Triple("USDT", "Tether USD", 6),
    "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48" to Triple("USDC", "USD Coin", 6),
    "0x6b175474e89094c44da98b954eedeac495271d0f" to Triple("DAI", "Dai Stablecoin", 18),
    "0x2260fac5e5542a773aa44fbcfedf7c193bc2c599" to Triple("WBTC", "Wrapped BTC", 8),
    "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2" to Triple("WETH", "Wrapped Ether", 18),
    "0x514910771af9ca656af840dff83e8264ecf986ca" to Triple("LINK", "Chainlink Token", 18),
    "0x1f9840a85d5af5bf1d1762f925bdaddc4201f984" to Triple("UNI", "Uniswap", 18)
)

/**
 * 解析 ERC-20 transfer(address,uint256) 函數調用
 */
private fun decodeErc20Transfer(contractAddress: String, input: String): Erc20TransferInfo? {
    return try {
        // transfer(address _to, uint256 _value) 的方法簽名是 0xa9059cbb
        if (!input.startsWith("0xa9059cbb") || input.length < 138) {
            return null
        }
        
        // 解析 recipient (address) - bytes 4-36 (去掉 0x 和方法ID後的前 64 個字符)
        val recipientHex = input.substring(10, 74)
        val recipient = "0x" + recipientHex.takeLast(40)
        
        // 解析 amount (uint256) - bytes 36-68
        val amountHex = input.substring(74, 138)
        val amountBigInt = try {
            com.ionspin.kotlin.bignum.integer.BigInteger.parseString(amountHex, 16)
        } catch (e: Exception) {
            return null
        }
        
        // 查找代幣信息
        val tokenKey = contractAddress.lowercase()
        val tokenInfo = KNOWN_TOKENS[tokenKey]
        
        val symbol = tokenInfo?.first ?: "TOKEN"
        val name = tokenInfo?.second ?: "Unknown Token"
        val decimals = tokenInfo?.third ?: 18
        
        // 將 amount 轉換為代幣單位
        val divisor = com.ionspin.kotlin.bignum.integer.BigInteger.TEN.pow(decimals)
        val amountDecimal = com.ionspin.kotlin.bignum.decimal.BigDecimal.fromBigInteger(amountBigInt)
        val divisorDecimal = com.ionspin.kotlin.bignum.decimal.BigDecimal.fromBigInteger(divisor)
        val scaledAmount = amountDecimal.divide(
            divisorDecimal,
            com.ionspin.kotlin.bignum.decimal.DecimalMode(decimalPrecision = 8, roundingMode = com.ionspin.kotlin.bignum.decimal.RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
        )
        
        Erc20TransferInfo(
            recipient = recipient,
            amount = scaledAmount.toPlainString(),
            symbol = symbol,
            name = name,
            decimals = decimals
        )
    } catch (e: Exception) {
        null
    }
}

@Serializable
data class EtherscanTokenResponse(
    val status: String,
    val message: String,
    val result: List<EtherscanTokenTransaction>
)

@Serializable
data class EtherscanTokenTransaction(
    val blockNumber: String,
    val timeStamp: String,
    val hash: String,
    val nonce: String,
    val blockHash: String,
    val from: String,
    val contractAddress: String,
    val to: String,
    val value: String,
    val tokenName: String,
    val tokenSymbol: String,
    val tokenDecimal: String,
    val transactionIndex: String,
    val gas: String,
    val gasPrice: String,
    val gasUsed: String,
    val cumulativeGasUsed: String,
    val input: String,
    val confirmations: String
)

@Serializable
data class EtherscanNFTResponse(
    val status: String,
    val message: String,
    val result: List<EtherscanNFTTransaction>
)

@Serializable
data class EtherscanNFTTransaction(
    val blockNumber: String,
    val timeStamp: String,
    val hash: String,
    val nonce: String,
    val blockHash: String,
    val from: String,
    val contractAddress: String,
    val to: String,
    val tokenID: String,
    val tokenName: String,
    val tokenSymbol: String,
    val tokenDecimal: String = "0",
    val transactionIndex: String,
    val gas: String,
    val gasPrice: String,
    val gasUsed: String,
    val cumulativeGasUsed: String,
    val input: String,
    val confirmations: String
)

@Serializable
data class EtherscanReceiptResponse(
    val status: String,
    val message: String,
    val result: EtherscanReceiptResult
)

@Serializable
data class EtherscanReceiptResult(
    val status: String
)

@Serializable
data class EtherscanBalanceResponse(
    val status: String,
    val message: String,
    val result: List<EtherscanBalanceResult>
)

@Serializable
data class EtherscanBalanceResult(
    val account: String,
    val balance: String
)

// === Domain Models ===

data class NFTTransfer(
    val hash: String,
    val from: String,
    val to: String,
    val tokenId: String,
    val tokenName: String,
    val tokenSymbol: String,
    val contractAddress: String,
    val blockNumber: Long,
    val timestamp: Long
)

data class TransactionReceipt(
    val status: Boolean,
    val blockNumber: Long,
    val gasUsed: Long,
    val effectiveGasPrice: String
)
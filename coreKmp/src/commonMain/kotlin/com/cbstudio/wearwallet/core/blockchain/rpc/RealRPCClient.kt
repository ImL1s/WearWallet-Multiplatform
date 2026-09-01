package com.cbstudio.wearwallet.core.blockchain.rpc

// 移除對 sharedKmp 的依賴，coreKmp 不應該依賴 sharedKmp
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * 真實的 RPC 客戶端實現
 * 支援 Solana 和 Ethereum 的真實 RPC 調用
 * 
 * 注意：這個類不應該直接使用 ApiKeyProvider，而應該在上層注入具體的 API key
 */
class RealRPCClient(
    private val rpcUrl: String,
    private val apiKey: String? = null
) {
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
    
    // ============= Solana RPC Methods =============
    
    /**
     * 獲取 Solana 帳戶餘額（真實 RPC 調用）
     * @param address Solana 地址
     * @return 餘額（SOL）
     */
    suspend fun getSolanaBalance(address: String): Double = withContext(Dispatchers.Default) {
        try {
            val response: HttpResponse = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "getBalance",
                    params = listOf(address)
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            val lamports = result.result?.jsonObject?.get("value")?.jsonPrimitive?.long ?: 0L
            
            // 轉換 lamports 到 SOL (1 SOL = 10^9 lamports)
            lamports / 1_000_000_000.0
        } catch (e: Exception) {
            println("❌ 獲取 Solana 餘額失敗: ${e.message}")
            0.0
        }
    }
    
    /**
     * 獲取 SPL Token 餘額
     */
    suspend fun getSPLTokenBalance(tokenAccountAddress: String): Double = withContext(Dispatchers.Default) {
        try {
            val response: HttpResponse = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "getTokenAccountBalance",
                    params = listOf(tokenAccountAddress)
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            val value = result.result?.jsonObject?.get("value")?.jsonObject
            val amountStr = value?.get("amount")?.jsonPrimitive?.content ?: "0"
            val decimals = value?.get("decimals")?.jsonPrimitive?.int ?: 0
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            
            amount / 10.0.pow(decimals.toDouble())
        } catch (e: Exception) {
            println("❌ 獲取 SPL Token 餘額失敗: ${e.message}")
            0.0
        }
    }
    
    /**
     * 發送 Solana 交易
     */
    suspend fun sendSolanaTransaction(signedTransaction: String): String? = withContext(Dispatchers.Default) {
        try {
            val response: HttpResponse = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "sendTransaction",
                    params = listOf(
                        signedTransaction,
                        mapOf("encoding" to "base64")
                    )
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            result.result?.jsonPrimitive?.content
        } catch (e: Exception) {
            println("❌ 發送 Solana 交易失敗: ${e.message}")
            null
        }
    }
    
    suspend fun getSolanaTransaction(
        signature: String,
        encoding: String = "base64"
    ): Map<String, Any>? = withContext(Dispatchers.Default) {
        try {
            val response: HttpResponse = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "getTransaction",
                    params = listOf(
                        signature,
                        mapOf(
                            "encoding" to encoding,
                            "commitment" to "confirmed",
                            "maxSupportedTransactionVersion" to 0
                        )
                    )
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            return@withContext result.result?.let { jsonElement ->
                val jsonObject = jsonElement.jsonObject
                mapOf<String, Any>(
                    "signature" to signature,
                    "slot" to (jsonObject["slot"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "blockTime" to (jsonObject["blockTime"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "meta" to (jsonObject["meta"]?.toString() ?: ""),
                    "transaction" to (jsonObject["transaction"]?.toString() ?: "")
                )
            }
        } catch (e: Exception) {
            println("❌ 獲取 Solana 交易失敗: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * 獲取 Solana 地址的交易歷史（真實 RPC 調用）
     * @param address 錢包地址
     * @param limit 返回數量限制
     * @return 交易簽名列表
     */
    suspend fun getSolanaSignaturesForAddress(
        address: String,
        limit: Int = 10
    ): List<String> = withContext(Dispatchers.Default) {
        try {
            val response: HttpResponse = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "getSignaturesForAddress",
                    params = listOf(
                        address,
                        mapOf(
                            "limit" to limit,
                            "commitment" to "confirmed"
                        )
                    )
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            result.result?.jsonArray?.mapNotNull { element ->
                element.jsonObject["signature"]?.jsonPrimitive?.content
            } ?: emptyList()
        } catch (e: Exception) {
            println("❌ 獲取 Solana 交易歷史失敗: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 獲取最近的區塊哈希（用於交易構建）
     * @return 區塊哈希和最後有效區塊高度
     */
    suspend fun getSolanaRecentBlockhash(): Pair<String, Long>? = withContext(Dispatchers.Default) {
        try {
            val response: HttpResponse = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "getLatestBlockhash",
                    params = listOf(
                        mapOf("commitment" to "confirmed")
                    )
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            result.result?.jsonObject?.get("value")?.jsonObject?.let { value ->
                val blockhash = value["blockhash"]?.jsonPrimitive?.content
                val lastValidBlockHeight = value["lastValidBlockHeight"]?.jsonPrimitive?.longOrNull
                
                if (blockhash != null && lastValidBlockHeight != null) {
                    blockhash to lastValidBlockHeight
                } else null
            }
        } catch (e: Exception) {
            println("❌ 獲取 Solana 區塊哈希失敗: ${e.message}")
            null
        }
    }
    
    // ============= TRON TronGrid API Methods =============
    
    /**
     * 獲取 TRON 交易歷史（使用 TronGrid API）
     * @param address TRON 地址
     * @param limit 返回數量限制
     * @param onlyConfirmed 只返回已確認交易
     * @return 交易列表
     */
    suspend fun getTronTransactions(
        address: String,
        limit: Int = 20,
        onlyConfirmed: Boolean = true
    ): List<Map<String, Any>> = withContext(Dispatchers.Default) {
        try {
            val trongridUrl = "https://api.trongrid.io/v1/accounts/$address/transactions"
            
            val response: HttpResponse = client.get(trongridUrl) {
                parameter("limit", limit)
                parameter("only_confirmed", onlyConfirmed)
                parameter("order_by", "block_timestamp,desc")
            }
            
            val jsonResponse = response.body<JsonObject>()
            val data = jsonResponse["data"]?.jsonArray ?: return@withContext emptyList()
            
            data.mapNotNull { element ->
                val tx = element.jsonObject
                mapOf(
                    "txID" to (tx["txID"]?.jsonPrimitive?.content ?: ""),
                    "blockNumber" to (tx["blockNumber"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "blockTimeStamp" to (tx["block_timestamp"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "energy_fee" to (tx["energy_fee"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "net_fee" to (tx["net_fee"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "ret" to (tx["ret"]?.jsonArray?.firstOrNull()?.jsonObject?.get("contractRet")?.jsonPrimitive?.content ?: "UNKNOWN")
                )
            }
        } catch (e: Exception) {
            println("❌ 獲取 TRON 交易歷史失敗: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 獲取 TRC20 代幣交易歷史
     * @param address 錢包地址
     * @param contractAddress 代幣合約地址
     * @param limit 返回數量限制
     * @return TRC20 交易列表
     */
    suspend fun getTRC20Transactions(
        address: String,
        contractAddress: String,
        limit: Int = 20
    ): List<Map<String, Any>> = withContext(Dispatchers.Default) {
        try {
            val trongridUrl = "https://api.trongrid.io/v1/accounts/$address/transactions/trc20"
            
            val response: HttpResponse = client.get(trongridUrl) {
                parameter("contract_address", contractAddress)
                parameter("limit", limit)
                parameter("only_confirmed", true)
                parameter("order_by", "block_timestamp,desc")
            }
            
            val jsonResponse = response.body<JsonObject>()
            val data = jsonResponse["data"]?.jsonArray ?: return@withContext emptyList()
            
            data.mapNotNull { element ->
                val tx = element.jsonObject
                mapOf(
                    "transaction_id" to (tx["transaction_id"]?.jsonPrimitive?.content ?: ""),
                    "token_info" to (tx["token_info"]?.jsonObject?.toString() ?: "{}"),
                    "block_timestamp" to (tx["block_timestamp"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "from" to (tx["from"]?.jsonPrimitive?.content ?: ""),
                    "to" to (tx["to"]?.jsonPrimitive?.content ?: ""),
                    "value" to (tx["value"]?.jsonPrimitive?.content ?: "0"),
                    "type" to (tx["type"]?.jsonPrimitive?.content ?: "Transfer")
                )
            }
        } catch (e: Exception) {
            println("❌ 獲取 TRC20 交易歷史失敗: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 獲取 TRON 賬戶信息
     * @param address TRON 地址
     * @return 賬戶信息
     */
    suspend fun getTronAccountInfo(address: String): Map<String, Any>? = withContext(Dispatchers.Default) {
        try {
            val trongridUrl = "https://api.trongrid.io/v1/accounts/$address"
            
            val response: HttpResponse = client.get(trongridUrl)
            val jsonResponse = response.body<JsonObject>()
            
            jsonResponse["data"]?.jsonArray?.firstOrNull()?.jsonObject?.let { account ->
                mapOf(
                    "address" to (account["address"]?.jsonPrimitive?.content ?: address),
                    "balance" to (account["balance"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "create_time" to (account["create_time"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "latest_operation_time" to (account["latest_operation_time"]?.jsonPrimitive?.longOrNull ?: 0L),
                    "bandwidth" to mapOf(
                        "free_net_used" to (account["free_net_used"]?.jsonPrimitive?.longOrNull ?: 0L),
                        "free_net_limit" to (account["free_net_limit"]?.jsonPrimitive?.longOrNull ?: 0L)
                    ),
                    "energy" to mapOf(
                        "energy_used" to (account["account_resource"]?.jsonObject?.get("energy_used")?.jsonPrimitive?.longOrNull ?: 0L),
                        "energy_limit" to (account["account_resource"]?.jsonObject?.get("energy_limit")?.jsonPrimitive?.longOrNull ?: 0L)
                    )
                )
            }
        } catch (e: Exception) {
            println("❌ 獲取 TRON 賬戶信息失敗: ${e.message}")
            null
        }
    }
    
    // ============= Ethereum RPC Methods =============
    
    /**
     * 獲取 Ethereum 帳戶餘額（真實 RPC 調用）
     * @param address Ethereum 地址
     * @return 餘額（ETH）
     */
    suspend fun getEthereumBalance(address: String): Double = withContext(Dispatchers.Default) {
        try {
            val finalUrl = if (apiKey != null) "$rpcUrl/$apiKey" else rpcUrl
            
            val response: HttpResponse = client.post(finalUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "eth_getBalance",
                    params = listOf(address, "latest")
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            val weiHex = result.result?.jsonPrimitive?.content ?: "0x0"
            val wei = weiHex.removePrefix("0x").toLongOrNull(16) ?: 0L
            
            // 轉換 Wei 到 ETH (1 ETH = 10^18 Wei)
            wei / 10.0.pow(18)
        } catch (e: Exception) {
            println("❌ 獲取 Ethereum 餘額失敗: ${e.message}")
            0.0
        }
    }
    
    /**
     * 獲取 ERC20 Token 餘額
     */
    suspend fun getERC20Balance(
        walletAddress: String,
        tokenContract: String
    ): Double = withContext(Dispatchers.Default) {
        try {
            // 構建 balanceOf(address) 調用數據
            val methodId = "0x70a08231" // balanceOf 方法 ID
            val paddedAddress = walletAddress.removePrefix("0x").padStart(64, '0')
            val data = methodId + paddedAddress
            
            val finalUrl = if (apiKey != null) "$rpcUrl/$apiKey" else rpcUrl
            
            val response: HttpResponse = client.post(finalUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "eth_call",
                    params = listOf(
                        mapOf(
                            "to" to tokenContract,
                            "data" to data
                        ),
                        "latest"
                    )
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            val balanceHex = result.result?.jsonPrimitive?.content ?: "0x0"
            val balance = balanceHex.removePrefix("0x").toLongOrNull(16) ?: 0L
            
            // 大多數 ERC20 代幣使用 18 位小數
            // 實際應用中應該查詢代幣的 decimals()
            balance / 10.0.pow(18)
        } catch (e: Exception) {
            println("❌ 獲取 ERC20 餘額失敗: ${e.message}")
            0.0
        }
    }
    
    /**
     * 獲取當前 Gas 價格
     */
    suspend fun getGasPrice(): Long? = withContext(Dispatchers.Default) {
        try {
            val finalUrl = if (apiKey != null) "$rpcUrl/$apiKey" else rpcUrl
            
            val response: HttpResponse = client.post(finalUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "eth_gasPrice",
                    params = emptyList<String>()
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            val gasPriceHex = result.result?.jsonPrimitive?.content ?: return@withContext null
            gasPriceHex.removePrefix("0x").toLongOrNull(16)
        } catch (e: Exception) {
            println("❌ 獲取 Gas 價格失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 獲取交易數量（nonce）
     */
    suspend fun getTransactionCount(address: String): Long? = withContext(Dispatchers.Default) {
        try {
            val finalUrl = if (apiKey != null) "$rpcUrl/$apiKey" else rpcUrl
            
            val response: HttpResponse = client.post(finalUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "eth_getTransactionCount",
                    params = listOf(address, "latest")
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            val nonceHex = result.result?.jsonPrimitive?.content ?: return@withContext null
            nonceHex.removePrefix("0x").toLongOrNull(16) ?: 0L
        } catch (e: Exception) {
            println("❌ 獲取 Nonce 失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 發送 Ethereum 交易
     */
    suspend fun sendEthereumTransaction(signedTransaction: String): String? = withContext(Dispatchers.Default) {
        try {
            val finalUrl = if (apiKey != null) "$rpcUrl/$apiKey" else rpcUrl
            
            val response: HttpResponse = client.post(finalUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "eth_sendRawTransaction",
                    params = listOf(signedTransaction)
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            result.result?.jsonPrimitive?.content
        } catch (e: Exception) {
            println("❌ 發送 Ethereum 交易失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 獲取區塊高度
     */
    suspend fun getBlockNumber(): Long? = withContext(Dispatchers.Default) {
        try {
            val finalUrl = if (apiKey != null) "$rpcUrl/$apiKey" else rpcUrl
            
            val response: HttpResponse = client.post(finalUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "eth_blockNumber",
                    params = emptyList<String>()
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            val blockHex = result.result?.jsonPrimitive?.content ?: return@withContext null
            blockHex.removePrefix("0x").toLongOrNull(16) ?: 0L
        } catch (e: Exception) {
            println("❌ 獲取區塊高度失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 獲取 Solana 交易簽名列表
     */
    suspend fun getSolanaTransactionSignatures(
        address: String,
        limit: Int = 10
    ): List<String> = getSolanaSignaturesForAddress(address, limit)
    
    /**
     * 獲取 Solana 當前 Slot
     */
    suspend fun getSolanaSlot(): Long = withContext(Dispatchers.Default) {
        try {
            val response: HttpResponse = client.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(JsonRpcRequest(
                    method = "getSlot",
                    params = emptyList<String>()
                ))
            }
            
            val result = response.body<JsonRpcResponse>()
            result.result?.jsonPrimitive?.longOrNull ?: 0L
        } catch (e: Exception) {
            println("❌ 獲取 Solana Slot 失敗: ${e.message}")
            0L
        }
    }
    
    /**
     * TRON 專用: POST JSON-RPC 請求（不使用 id 和 jsonrpc 字段）
     * @param url API endpoint URL
     * @param body JSON 請求體
     * @return JSON 響應
     */
    suspend fun postJsonRpc(url: String, body: JsonObject): JsonObject = withContext(Dispatchers.Default) {
        try {
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
                // 添加 TRON-PRO-API-KEY header (如果有)
                apiKey?.let { header("TRON-PRO-API-KEY", it) }
            }

            response.body<JsonObject>()
        } catch (e: Exception) {
            println("❌ POST JSON-RPC 請求失敗: ${e.message}")
            buildJsonObject {
                put("error", buildJsonObject {
                    put("message", e.message ?: "Unknown error")
                })
            }
        }
    }

    fun close() {
        client.close()
    }

    // 移除工廠方法，避免對 sharedKmp 的依賴
    // 工廠方法應該在上層服務中實作
}

/**
 * JSON-RPC 請求格式
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int = 1,
    val method: String,
    val params: List<@Contextual Any>
)

/**
 * JSON-RPC 響應格式
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String,
    val id: Int,
    val result: JsonElement? = null,
    val error: JsonElement? = null
)
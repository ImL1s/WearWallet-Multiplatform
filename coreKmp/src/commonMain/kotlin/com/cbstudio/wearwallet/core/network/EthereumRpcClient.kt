package com.cbstudio.wearwallet.core.network

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

import com.cbstudio.wearwallet.core.security.SideEffectTracker
import com.cbstudio.wearwallet.core.security.GlobalSideEffectTracker

/**
 * Ethereum JSON-RPC 客戶端
 */
class EthereumRpcClient(
    private val httpClient: HttpClient,
    private val sideEffectTracker: SideEffectTracker = GlobalSideEffectTracker.instance
) {
    
    // Note: not thread-safe, but acceptable since Ktor dispatchers are single-threaded per client
    private var requestIdCounter = 1
    
    /**
     * 獲取原生代幣餘額 (with ChainExecutionContext)
     */
    suspend fun getNativeBalance(
        address: String,
        context: ChainExecutionContext
    ): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(context)
            println("[Debug] getNativeBalance - Address: $address, Context: $context, RPC: $rpcUrl")
            
            val request = JsonRpcRequest(
                method = "eth_getBalance",
                params = buildJsonArray {
                    add(JsonPrimitive(address))
                    add(JsonPrimitive("latest"))
                },
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val responseBody = response.bodyAsText()
            val jsonResponse = try {
                 Json { ignoreUnknownKeys = true }.decodeFromString<JsonRpcResponse>(responseBody)
            } catch (e: Exception) {
                return Result.Failure(e)
            }
            
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val balance = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            Result.Success(balance)
            
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * 獲取原生代幣餘額
     */
    suspend fun getNativeBalance(
        address: String,
        chainType: ChainType
    ): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(chainType)
            println("[Debug] getNativeBalance - Address: $address, Chain: $chainType, RPC: $rpcUrl")
            
            val request = JsonRpcRequest(
                method = "eth_getBalance",
                params = buildJsonArray {
                    add(JsonPrimitive(address))
                    add(JsonPrimitive("latest"))
                },
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            println("[Debug] RPC Response Status: ${response.status}")
            
            if (!response.status.isSuccess()) {
                println("[Debug] RPC Error: HTTP ${response.status.value}")
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val responseBody = response.bodyAsText()
            println("[Debug] RPC Raw Response: $responseBody")
            
            val jsonResponse = try {
                 Json { ignoreUnknownKeys = true }.decodeFromString<JsonRpcResponse>(responseBody)
            } catch (e: Exception) {
                println("[Debug] RPC Serialization Error: ${e.message}")
                return Result.Failure(e)
            }
            
            if (jsonResponse.error != null) {
                println("[Debug] RPC JSON Error: ${jsonResponse.error.message}")
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val balance = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            println("[Debug] RPC Success - Balance Hex: $balance")
            Result.Success(balance)
            
        } catch (e: Exception) {
            println("[Debug] RPC Exception: ${e.message}")
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取 ERC20 代幣餘額 (with ChainExecutionContext)
     */
    suspend fun getTokenBalance(
        walletAddress: String,
        tokenAddress: String,
        context: ChainExecutionContext
    ): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(context)
            val methodId = "0x70a08231" // balanceOf(address)
            val paddedAddress = walletAddress.removePrefix("0x").padStart(64, '0')
            val data = methodId + paddedAddress
            
            val request = JsonRpcRequest(
                method = "eth_call",
                params = buildJsonArray {
                    add(buildJsonObject {
                        put("to", JsonPrimitive(tokenAddress))
                        put("data", JsonPrimitive(data))
                    })
                    add(JsonPrimitive("latest"))
                },
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val balance = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            Result.Success(balance)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * 獲取 ERC20 代幣餘額
     */
    suspend fun getTokenBalance(
        walletAddress: String,
        tokenAddress: String,
        chainType: ChainType
    ): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(chainType)
            
            // ERC20 balanceOf function signature
            val methodId = "0x70a08231" // balanceOf(address)
            val paddedAddress = walletAddress.removePrefix("0x").padStart(64, '0')
            val data = methodId + paddedAddress
            
            val request = JsonRpcRequest(
                method = "eth_call",
                params = buildJsonArray {
                    add(buildJsonObject {
                        put("to", JsonPrimitive(tokenAddress))
                        put("data", JsonPrimitive(data))
                    })
                    add(JsonPrimitive("latest"))
                },
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val balance = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            Result.Success(balance)
            
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取 ERC20 代幣授權額度 (allowance) (with ChainExecutionContext)
     */
    suspend fun getAllowance(
        ownerAddress: String,
        spenderAddress: String,
        tokenAddress: String,
        context: ChainExecutionContext
    ): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(context)
            val methodId = "0xdd62ed3e" // allowance(address,address)
            val paddedOwner = ownerAddress.removePrefix("0x").lowercase().padStart(64, '0')
            val paddedSpender = spenderAddress.removePrefix("0x").lowercase().padStart(64, '0')
            val data = methodId + paddedOwner + paddedSpender
            
            val request = JsonRpcRequest(
                method = "eth_call",
                params = buildJsonArray {
                    add(buildJsonObject {
                        put("to", JsonPrimitive(tokenAddress))
                        put("data", JsonPrimitive(data))
                    })
                    add(JsonPrimitive("latest"))
                },
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val allowance = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            Result.Success(allowance)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * 獲取 ERC20 代幣授權額度 (allowance)
     */
    suspend fun getAllowance(
        ownerAddress: String,
        spenderAddress: String,
        tokenAddress: String,
        chainType: ChainType
    ): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(chainType)
            
            // ERC20 allowance function signature
            val methodId = "0xdd62ed3e" // allowance(address,address)
            val paddedOwner = ownerAddress.removePrefix("0x").lowercase().padStart(64, '0')
            val paddedSpender = spenderAddress.removePrefix("0x").lowercase().padStart(64, '0')
            val data = methodId + paddedOwner + paddedSpender
            
            val request = JsonRpcRequest(
                method = "eth_call",
                params = buildJsonArray {
                    add(buildJsonObject {
                        put("to", JsonPrimitive(tokenAddress))
                        put("data", JsonPrimitive(data))
                    })
                    add(JsonPrimitive("latest"))
                },
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val allowance = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            Result.Success(allowance)
            
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 發送交易 (with ChainExecutionContext)
     */
    suspend fun sendRawTransaction(
        signedTransaction: String,
        context: ChainExecutionContext
    ): Result<String> {
        sideEffectTracker.onBroadcast()
        sideEffectTracker.onNetworkSend()
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(context)
            val request = JsonRpcRequest(
                method = "eth_sendRawTransaction",
                params = buildJsonArray {
                    add(JsonPrimitive(signedTransaction))
                },
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val txHash = jsonResponse.result?.jsonPrimitive?.content ?: ""
            Result.Success(txHash)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * 發送交易
     */
    suspend fun sendRawTransaction(
        signedTransaction: String,
        chainType: ChainType
    ): Result<String> {
        sideEffectTracker.onBroadcast()
        sideEffectTracker.onNetworkSend()
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(chainType)
            val request = JsonRpcRequest(
                method = "eth_sendRawTransaction",
                params = buildJsonArray {
                    add(JsonPrimitive(signedTransaction))
                },
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val txHash = jsonResponse.result?.jsonPrimitive?.content ?: ""
            Result.Success(txHash)
            
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取 Gas Price (with ChainExecutionContext)
     */
    suspend fun getGasPrice(context: ChainExecutionContext): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(context)
            val request = JsonRpcRequest(
                method = "eth_gasPrice",
                params = buildJsonArray {},
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val gasPrice = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            Result.Success(gasPrice)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * 獲取 Gas Price
     */
    suspend fun getGasPrice(chainType: ChainType): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(chainType)
            val request = JsonRpcRequest(
                method = "eth_gasPrice",
                params = buildJsonArray {},
                id = requestIdCounter++
            )
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }
            
            val gasPrice = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            Result.Success(gasPrice)
            
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取 Nonce (with ChainExecutionContext)
     */
    suspend fun getNonce(address: String, context: ChainExecutionContext): Result<Long> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(context)
            val request = JsonRpcRequest(
                method = "eth_getTransactionCount",
                params = buildJsonArray {
                    add(JsonPrimitive(address))
                    add(JsonPrimitive("pending"))
                },
                id = requestIdCounter++
            )

            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }

            val jsonResponse = response.body<JsonRpcResponse>()
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }

            val nonceHex = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            val nonce = nonceHex.removePrefix("0x").toLongOrNull(16) ?: 0L
            Result.Success(nonce)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * 獲取 Nonce
     */
    suspend fun getNonce(address: String, chainType: ChainType): Result<Long> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(chainType)
            val request = JsonRpcRequest(
                method = "eth_getTransactionCount",
                params = buildJsonArray {
                    add(JsonPrimitive(address))
                    add(JsonPrimitive("pending"))
                },
                id = requestIdCounter++
            )

            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }

            val jsonResponse = response.body<JsonRpcResponse>()

            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }

            val nonceHex = jsonResponse.result?.jsonPrimitive?.content ?: "0x0"
            val nonce = nonceHex.removePrefix("0x").toLongOrNull(16) ?: 0L
            Result.Success(nonce)

        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * 估算交易 Gas（eth_estimateGas） (with ChainExecutionContext)
     */
    suspend fun estimateGas(
        from: String,
        to: String,
        value: String = "0x0",
        data: String = "0x",
        context: ChainExecutionContext
    ): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(context)

            val transactionObject = buildJsonObject {
                put("from", JsonPrimitive(from))
                put("to", JsonPrimitive(to))
                put("value", JsonPrimitive(value))
                if (data != "0x" && data.isNotEmpty()) {
                    put("data", JsonPrimitive(data))
                }
            }

            val request = JsonRpcRequest(
                method = "eth_estimateGas",
                params = buildJsonArray {
                    add(transactionObject)
                    add(JsonPrimitive("latest"))
                },
                id = requestIdCounter++
            )

            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }

            val jsonResponse = response.body<JsonRpcResponse>()
            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }

            val gasEstimate = jsonResponse.result?.jsonPrimitive?.content ?: "0x5208"
            Result.Success(gasEstimate)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    /**
     * 估算交易 Gas（eth_estimateGas）
     *
     * @param from 發送方地址
     * @param to 接收方地址
     * @param value 轉帳金額（Wei，十六進制字符串）
     * @param data 交易數據（十六進制字符串）
     * @param chainType 鏈類型
     * @return Result<String> Gas 估算值（十六進制）
     */
    suspend fun estimateGas(
        from: String,
        to: String,
        value: String = "0x0",
        data: String = "0x",
        chainType: ChainType
    ): Result<String> {
        return try {
            val rpcUrl = ApiConfig.getRpcUrl(chainType)

            // 構建交易對象
            val transactionObject = buildJsonObject {
                put("from", JsonPrimitive(from))
                put("to", JsonPrimitive(to))
                put("value", JsonPrimitive(value))
                if (data != "0x" && data.isNotEmpty()) {
                    put("data", JsonPrimitive(data))
                }
            }

            val request = JsonRpcRequest(
                method = "eth_estimateGas",
                params = buildJsonArray {
                    add(transactionObject)
                    add(JsonPrimitive("latest"))
                },
                id = requestIdCounter++
            )

            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (!response.status.isSuccess()) {
                return Result.Failure(Exception("HTTP ${response.status.value}"))
            }

            val jsonResponse = response.body<JsonRpcResponse>()

            if (jsonResponse.error != null) {
                return Result.Failure(Exception(jsonResponse.error.message))
            }

            val gasEstimate = jsonResponse.result?.jsonPrimitive?.content ?: "0x5208"
            Result.Success(gasEstimate)

        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}

/**
 * JSON-RPC 請求
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonArray,
    val id: Int
)

/**
 * JSON-RPC 回應
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

/**
 * JSON-RPC 錯誤
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)
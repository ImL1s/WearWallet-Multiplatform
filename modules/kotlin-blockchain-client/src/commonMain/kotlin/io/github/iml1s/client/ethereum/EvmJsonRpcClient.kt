package io.github.iml1s.client.ethereum

import io.github.iml1s.client.BlockchainClient
import io.github.iml1s.client.ChainTransaction
import io.github.iml1s.client.ChainUTXO
import io.github.iml1s.client.FeeRates
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Simple Ethereum JSON-RPC Client
 */
class EvmJsonRpcClient(
    private val httpClient: HttpClient,
    private val rpcUrl: String,
    private val chainId: Long = 1
) : BlockchainClient {

    @Serializable
    private data class JsonRpcRequest(
        val jsonrpc: String = "2.0",
        val method: String,
        val params: List<JsonElement>,
        val id: Int = 1
    )

    @Serializable
    private data class JsonRpcResponse<T>(
        val jsonrpc: String,
        val result: T? = null,
        val error: JsonRpcError? = null,
        val id: Int
    )

    @Serializable
    private data class JsonRpcError(
        val code: Int,
        val message: String
    )

    private suspend inline fun <reified T> rpcCall(method: String, params: List<JsonElement>): T {
        val request = JsonRpcRequest(method = method, params = params)
        val response = httpClient.post(rpcUrl) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<JsonRpcResponse<T>>()

        if (response.error != null) {
            throw Exception("RPC Error: ${response.error.message} (${response.error.code})")
        }
        return response.result ?: throw Exception("Empty result")
    }

    override suspend fun getBalance(address: String): Long {
        val hexBalance = rpcCall<String>(
            "eth_getBalance", 
            listOf(JsonPrimitive(address), JsonPrimitive("latest"))
        )
        // Remove 0x and parse hex
        return hexBalance.removePrefix("0x").toLong(16)
    }

    override suspend fun getUTXOs(address: String): List<ChainUTXO> {
        // Ethereum is account-based, no UTXOs. Return empty or dummy.
        return emptyList()
    }
    
    // Helper to get Nonce
    suspend fun getNonce(address: String): Long {
        val hexNonce = rpcCall<String>(
            "eth_getTransactionCount",
            listOf(JsonPrimitive(address), JsonPrimitive("pending")) // pending for next tx
        )
        return hexNonce.removePrefix("0x").toLong(16)
    }

    override suspend fun getTransactions(address: String): List<ChainTransaction> {
        // Standard JSON-RPC eth_getLogs or similar is complex.
        // Etherscan API is usually better for tx history.
        // For standard node, we can't easily get full history without scanning blocks.
        // Returning empty to respect simple RPC scope for now.
        return emptyList()
    }

    override suspend fun broadcastTransaction(rawTxHex: String): String {
        return rpcCall<String>(
            "eth_sendRawTransaction",
            listOf(JsonPrimitive(rawTxHex))
        )
    }

    override suspend fun getFeeRates(): FeeRates {
        val hexGasPrice = rpcCall<String>("eth_gasPrice", emptyList())
        val gasPrice = hexGasPrice.removePrefix("0x").toLong(16)
        
        // EIP-1559 priority fee suggestion
        // eth_maxPriorityFeePerGas
        val hexPriority = try {
            rpcCall<String>("eth_maxPriorityFeePerGas", emptyList())
        } catch (e: Exception) {
            "0x0" // Fallback
        }
        val priority = hexPriority.removePrefix("0x").toLongOrNull(16) ?: 0L
        
        // Simple heuristic
        return FeeRates(
            fast = gasPrice + priority,
            average = gasPrice,
            slow = gasPrice - (gasPrice / 10) // 90%
        )
    }
}

package com.cbstudio.wearwallet.core.multichain.monero.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.coroutines.withContext

/**
 * Monero Wallet RPC 客戶端
 * 
 * 提供完整的錢包功能，包括：
 * - 餘額查詢
 * - 交易歷史
 * - 交易創建和簽名
 * - UTXO 管理
 * 
 * 需要運行 monero-wallet-rpc 服務：
 * ```
 * ./monero-wallet-rpc --stagenet --rpc-bind-port 38083 \
 *   --disable-rpc-login --wallet-dir ./wallets \
 *   --daemon-address 54.153.251.193:38089
 * ```
 */
class MoneroWalletRPC(
    private val rpcUrl: String = "http://127.0.0.1:38083/json_rpc",
    private val httpClient: HttpClient = HttpClient()
) {
    
    /**
     * 餘額信息
     */
    @Serializable
    data class BalanceInfo(
        val balance: Long,
        val unlockedBalance: Long
    )
    
    /**
     * 錢包信息
     */
    @Serializable
    data class WalletInfo(
        val address: String,
        val balance: String,
        val unlockedBalance: String,
        val height: Long
    )
    
    /**
     * 交易信息
     */
    @Serializable
    data class TransactionInfo(
        val txHash: String,
        val paymentId: String? = null,
        val height: Int? = null,
        val timestamp: Long,
        val amount: Long,
        val fee: Long,
        val confirmations: Int,
        val type: String,  // "in" or "out"
        val destinations: List<DestinationInfo> = emptyList()
    )
    
    @Serializable
    data class DestinationInfo(
        val address: String,
        val amount: Long
    )
    
    /**
     * 打開錢包
     */
    suspend fun openWallet(
        filename: String,
        password: String = ""
    ): Result<Boolean> =
        try {
            val params = buildJsonObject {
                put("filename", filename)
                if (password.isNotEmpty()) {
                    put("password", password)
                }
            }
            
            val result = makeRpcCall("open_wallet", params)
            Result.Success(result != null)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 創建錢包
     */
    suspend fun createWallet(
        filename: String,
        password: String = "",
        language: String = "English"
    ): Result<String> = 
        try {
            val params = buildJsonObject {
                put("filename", filename)
                put("password", password)
                put("language", language)
            }
            
            val result = makeRpcCall("create_wallet", params)
            val address = result?.get("address")?.jsonPrimitive?.content ?: ""
            Result.Success(address)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 從助記詞恢復錢包
     */
    suspend fun restoreWallet(
        filename: String,
        mnemonic: String,
        password: String = "",
        restoreHeight: Long = 0
    ): Result<String> = 
        try {
            val params = buildJsonObject {
                put("filename", filename)
                put("seed", mnemonic)
                put("password", password)
                put("restore_height", restoreHeight)
            }
            
            val result = makeRpcCall("restore_deterministic_wallet", params)
            val address = result?.get("address")?.jsonPrimitive?.content ?: ""
            Result.Success(address)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 獲取餘額
     */
    suspend fun getBalance(accountIndex: Int = 0): Result<BalanceInfo> = 
        try {
            val params = buildJsonObject {
                put("account_index", accountIndex)
            }
            
            val result = makeRpcCall("get_balance", params)
            if (result != null) {
                val balance = result["balance"]?.jsonPrimitive?.long ?: 0L
                val unlockedBalance = result["unlocked_balance"]?.jsonPrimitive?.long ?: 0L
                
                val info = BalanceInfo(
                    balance = balance,
                    unlockedBalance = unlockedBalance
                )
                Result.Success(info)
            } else {
                Result.Failure(Exception("無法獲取餘額"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    // 保留舊的方法以兼容
    suspend fun getWalletInfo(accountIndex: Int = 0): Result<WalletInfo> = 
        try {
            val balanceResult = getBalance(accountIndex)
            if (balanceResult is Result.Success) {
                val balance = balanceResult.data
                
                // 獲取地址
                val addressResult = makeRpcCall("get_address", buildJsonObject {
                    put("account_index", accountIndex)
                })
                val address = addressResult?.get("address")?.jsonPrimitive?.content ?: ""
                
                // 獲取高度
                val heightResult = makeRpcCall("get_height", buildJsonObject {})
                val height = heightResult?.get("height")?.jsonPrimitive?.long ?: 0L
                
                val info = WalletInfo(
                    address = address,
                    balance = (balance.balance.toDouble() / 1e12).toString(),
                    unlockedBalance = (balance.unlockedBalance.toDouble() / 1e12).toString(),
                    height = height
                )
                Result.Success(info)
            } else {
                Result.Failure(Exception("無法獲取錢包信息"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactions(
        filterType: String = "all",
        accountIndex: Int = 0,
        limit: Int = 100,
        pending: Boolean = false,
        failed: Boolean = false,
        pool: Boolean = false
    ): Result<List<TransactionInfo>> = 
        try {
            val params = buildJsonObject {
                put("account_index", accountIndex)
                put("pending", pending)
                put("failed", failed)
                put("pool", pool)
            }
            
            val result = makeRpcCall("get_transfers", params)
            val transactions = mutableListOf<TransactionInfo>()
            
            // 處理輸入交易
            result?.get("in")?.jsonArray?.forEach { txElement ->
                val tx = txElement.jsonObject
                transactions.add(parseTransaction(tx, "in"))
            }
            
            // 處理輸出交易
            result?.get("out")?.jsonArray?.forEach { txElement ->
                val tx = txElement.jsonObject
                transactions.add(parseTransaction(tx, "out"))
            }
            
            // 按時間排序
            transactions.sortByDescending { it.timestamp }
            
            Result.Success(transactions)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 創建交易
     */
    suspend fun createTransaction(
        destinations: List<DestinationInfo>,
        priority: Int = 1,
        mixin: Int = 11,
        getTransactionHex: Boolean = true
    ): Result<String> = 
        try {
            val params = buildJsonObject {
                putJsonArray("destinations") {
                    destinations.forEach { dest ->
                        add(buildJsonObject {
                            put("address", dest.address)
                            put("amount", dest.amount)
                        })
                    }
                }
                put("priority", priority)
                put("ring_size", mixin)
                put("get_tx_hex", getTransactionHex)
            }
            
            val result = makeRpcCall("transfer", params)
            val txHash = result?.get("tx_hash")?.jsonPrimitive?.content ?: ""
            Result.Success(txHash)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 掃碼支付 URI
     */
    suspend fun makeUri(
        address: String,
        amount: String? = null,
        paymentId: String? = null,
        recipientName: String? = null,
        description: String? = null
    ): Result<String> = 
        try {
            val params = buildJsonObject {
                put("address", address)
                amount?.let {
                    put("amount", (BigDecimal.parseString(it) * BigDecimal.parseString("1000000000000")).longValue())
                }
                paymentId?.let { put("payment_id", it) }
                recipientName?.let { put("recipient_name", it) }
                description?.let { put("tx_description", it) }
            }
            
            val result = makeRpcCall("make_uri", params)
            val uri = result?.get("uri")?.jsonPrimitive?.content ?: ""
            Result.Success(uri)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 解析支付 URI
     */
    suspend fun parseUri(uri: String): Result<Map<String, String>> = 
        try {
            val params = buildJsonObject {
                put("uri", uri)
            }
            
            val result = makeRpcCall("parse_uri", params)
            if (result != null && result["uri"]?.jsonObject != null) {
                val uriData = result["uri"]!!.jsonObject
                val parsedData = mutableMapOf<String, String>()
                
                uriData["address"]?.jsonPrimitive?.content?.let {
                    parsedData["address"] = it
                }
                uriData["amount"]?.jsonPrimitive?.long?.let {
                    parsedData["amount"] = (it.toDouble() / 1e12).toString()
                }
                uriData["payment_id"]?.jsonPrimitive?.content?.let {
                    parsedData["payment_id"] = it
                }
                uriData["recipient_name"]?.jsonPrimitive?.content?.let {
                    parsedData["recipient_name"] = it
                }
                uriData["tx_description"]?.jsonPrimitive?.content?.let {
                    parsedData["description"] = it
                }
                
                Result.Success(parsedData)
            } else {
                Result.Failure(Exception("無法解析 URI"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 驗證地址
     */
    suspend fun validateAddress(address: String): Result<Boolean> = 
        try {
            val params = buildJsonObject {
                put("address", address)
            }
            
            val result = makeRpcCall("validate_address", params)
            val valid = result?.get("valid")?.jsonPrimitive?.boolean ?: false
            Result.Success(valid)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 同步錢包
     */
    suspend fun refresh(): Result<Boolean> = 
        try {
            val result = makeRpcCall("refresh", buildJsonObject {})
            Result.Success(result != null)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 關閉錢包
     */
    suspend fun closeWallet(): Result<Boolean> = 
        try {
            val result = makeRpcCall("close_wallet", buildJsonObject {})
            Result.Success(result != null)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 停止 RPC 服務
     */
    suspend fun stop(): Result<Boolean> = 
        try {
            val result = makeRpcCall("stop_wallet", buildJsonObject {})
            Result.Success(result != null)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    /**
     * 廣播交易（中繼交易）
     */
    suspend fun relayTransaction(
        txHex: String
    ): Result<String> = 
        try {
            val params = buildJsonObject {
                put("hex", txHex)
            }
            
            val result = makeRpcCall("relay_tx", params)
            if (result != null) {
                // 返回交易雜湊或成功標誌
                val txHash = result["tx_hash"]?.jsonPrimitive?.content
                if (txHash != null) {
                    Result.Success(txHash)
                } else {
                    // 如果沒有返回 tx_hash，表示交易已在池中
                    Result.Success("Transaction relayed successfully")
                }
            } else {
                Result.Failure(Exception("無法廣播交易"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    
    // 私有輔助函數
    
    private suspend fun makeRpcCall(
        method: String,
        params: JsonObject
    ): JsonObject? {
        return try {
            val requestBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", "0")
                put("method", method)
                put("params", params)
            }
            
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }
            
            if (response.status == HttpStatusCode.OK) {
                val responseText = response.bodyAsText()
                val jsonResponse = Json.parseToJsonElement(responseText).jsonObject
                
                // 檢查錯誤
                if (jsonResponse.containsKey("error")) {
                    val error = jsonResponse["error"]?.jsonObject
                    val errorMessage = error?.get("message")?.jsonPrimitive?.content ?: "未知錯誤"
                    println("RPC 錯誤: $errorMessage")
                    null
                } else {
                    jsonResponse["result"]?.jsonObject
                }
            } else {
                println("HTTP 錯誤: ${response.status}")
                null
            }
        } catch (e: Exception) {
            println("RPC 調用失敗: ${e.message}")
            null
        }
    }
    
    private fun parseTransaction(tx: JsonObject, type: String): TransactionInfo {
        return TransactionInfo(
            txHash = tx["txid"]?.jsonPrimitive?.content ?: "",
            paymentId = tx["payment_id"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() },
            height = tx["height"]?.jsonPrimitive?.int,
            timestamp = tx["timestamp"]?.jsonPrimitive?.long ?: 0L,
            amount = tx["amount"]?.jsonPrimitive?.long ?: 0L,
            fee = tx["fee"]?.jsonPrimitive?.long ?: 0L,
            confirmations = tx["confirmations"]?.jsonPrimitive?.int ?: 0,
            type = type,
            destinations = if (type == "out") {
                tx["destinations"]?.jsonArray?.map { dest ->
                    val destObj = dest.jsonObject
                    DestinationInfo(
                        address = destObj["address"]?.jsonPrimitive?.content ?: "",
                        amount = destObj["amount"]?.jsonPrimitive?.long ?: 0L
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        )
    }
}

/**
 * Monero Wallet RPC 工廠
 */
object MoneroWalletRPCFactory {
    
    /**
     * 創建連接到本地 Wallet RPC 的客戶端
     */
    fun createLocal(port: Int = 38083): MoneroWalletRPC {
        return MoneroWalletRPC("http://127.0.0.1:$port/json_rpc")
    }
    
    /**
     * 創建連接到遠端 Wallet RPC 的客戶端
     */
    fun createRemote(host: String, port: Int = 38083): MoneroWalletRPC {
        return MoneroWalletRPC("http://$host:$port/json_rpc")
    }
}
package com.cbstudio.wearwallet.core.blockchain.api

import com.cbstudio.wearwallet.core.blockchain.model.BitcoinTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.domain.model.Network
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Dogecoin API 客戶端
 * 支援 BlockCypher 主要 API 和 SoChain 備用 API
 */
class DogecoinApiClient(
    private val network: Network
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(this@DogecoinApiClient.json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }
    
    // BlockCypher API (主要)
    private val blockCypherUrl: String = when (network) {
        Network.DOGECOIN_MAINNET -> "https://api.blockcypher.com/v1/doge/main"
        Network.DOGECOIN_TESTNET -> "https://api.blockcypher.com/v1/doge/test3"
        else -> throw IllegalArgumentException("Unsupported network: $network")
    }
    
    // SoChain API (備用)
    private val soChainUrl = "https://sochain.com/api/v3"
    private val soChainNetwork = when (network) {
        Network.DOGECOIN_MAINNET -> "DOGE"
        Network.DOGECOIN_TESTNET -> "DOGETEST"
        else -> throw IllegalArgumentException("Unsupported network: $network")
    }
    
    /**
     * 獲取地址餘額
     */
    suspend fun getBalance(address: String): Long {
        return try {
            // 優先使用 BlockCypher
            getBalanceFromBlockCypher(address)
        } catch (e: Exception) {
            println("BlockCypher API failed, trying SoChain: ${e.message}")
            // 備用 SoChain
            getBalanceFromSoChain(address)
        }
    }
    
    /**
     * 從 BlockCypher 獲取餘額
     */
    private suspend fun getBalanceFromBlockCypher(address: String): Long {
        val response: HttpResponse = httpClient.get("$blockCypherUrl/addrs/$address/balance")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            
            // BlockCypher 返回 satoshi 單位
            val balance = jsonObject["balance"]?.jsonPrimitive?.long ?: 0L
            val unconfirmedBalance = jsonObject["unconfirmed_balance"]?.jsonPrimitive?.long ?: 0L
            
            return balance + unconfirmedBalance
        } else {
            throw Exception("Failed to get balance from BlockCypher: ${response.status}")
        }
    }
    
    /**
     * 從 SoChain 獲取餘額
     */
    private suspend fun getBalanceFromSoChain(address: String): Long {
        val response: HttpResponse = httpClient.get("$soChainUrl/balance/$soChainNetwork/$address")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val data = jsonObject["data"]?.jsonObject ?: throw Exception("Invalid SoChain response")
            
            // SoChain 返回 DOGE 單位，需要轉換為 satoshi
            val confirmedStr = data["confirmed"]?.jsonPrimitive?.content ?: "0"
            val unconfirmedStr = data["unconfirmed"]?.jsonPrimitive?.content ?: "0"
            
            val confirmed = (confirmedStr.toDouble() * 100_000_000).toLong()
            val unconfirmed = (unconfirmedStr.toDouble() * 100_000_000).toLong()
            
            return confirmed + unconfirmed
        } else {
            throw Exception("Failed to get balance from SoChain: ${response.status}")
        }
    }
    
    /**
     * 獲取 UTXOs
     */
    suspend fun getUtxos(address: String): List<UTXO> {
        return try {
            getUtxosFromBlockCypher(address)
        } catch (e: Exception) {
            println("BlockCypher API failed, trying SoChain: ${e.message}")
            getUtxosFromSoChain(address)
        }
    }
    
    /**
     * 從 BlockCypher 獲取 UTXOs
     */
    private suspend fun getUtxosFromBlockCypher(address: String): List<UTXO> {
        val response: HttpResponse = httpClient.get("$blockCypherUrl/addrs/$address?unspentOnly=true&includeScript=true")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val txrefs = jsonObject["txrefs"]?.jsonArray ?: return emptyList()
            
            return txrefs.mapNotNull { element ->
                val txref = element.jsonObject
                try {
                    UTXO(
                        txid = txref["tx_hash"]?.jsonPrimitive?.content ?: "",
                        vout = txref["tx_output_n"]?.jsonPrimitive?.int ?: 0,
                        value = txref["value"]?.jsonPrimitive?.long ?: 0L,
                        confirmed = (txref["confirmations"]?.jsonPrimitive?.int ?: 0) > 0,
                        blockHeight = txref["block_height"]?.jsonPrimitive?.long ?: 0L,
                        scriptPubKey = txref["script"]?.jsonPrimitive?.content ?: "",
                        address = address
                    )
                } catch (e: Exception) {
                    println("Error parsing UTXO: ${e.message}")
                    null
                }
            }
        } else {
            throw Exception("Failed to get UTXOs from BlockCypher: ${response.status}")
        }
    }
    
    /**
     * 從 SoChain 獲取 UTXOs
     */
    private suspend fun getUtxosFromSoChain(address: String): List<UTXO> {
        val response: HttpResponse = httpClient.get("$soChainUrl/unspent_outputs/$soChainNetwork/$address")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val data = jsonObject["data"]?.jsonObject ?: throw Exception("Invalid SoChain response")
            val outputs = data["outputs"]?.jsonArray ?: return emptyList()
            
            return outputs.mapNotNull { element ->
                val output = element.jsonObject
                try {
                    val valueStr = output["value"]?.jsonPrimitive?.content ?: "0"
                    val value = (valueStr.toDouble() * 100_000_000).toLong()
                    
                    UTXO(
                        txid = output["hash"]?.jsonPrimitive?.content ?: "",
                        vout = output["index"]?.jsonPrimitive?.int ?: 0,
                        value = value,
                        confirmed = (output["confirmations"]?.jsonPrimitive?.int ?: 0) > 0,
                        blockHeight = output["block_no"]?.jsonPrimitive?.long ?: 0L,
                        scriptPubKey = output["script"]?.jsonPrimitive?.content ?: "",
                        address = address
                    )
                } catch (e: Exception) {
                    println("Error parsing UTXO: ${e.message}")
                    null
                }
            }
        } else {
            throw Exception("Failed to get UTXOs from SoChain: ${response.status}")
        }
    }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactions(address: String, limit: Int = 50): List<BitcoinTransaction> {
        return try {
            getTransactionsFromBlockCypher(address, limit)
        } catch (e: Exception) {
            println("BlockCypher API failed, trying SoChain: ${e.message}")
            getTransactionsFromSoChain(address, limit)
        }
    }
    
    /**
     * 從 BlockCypher 獲取交易歷史
     */
    private suspend fun getTransactionsFromBlockCypher(address: String, limit: Int): List<BitcoinTransaction> {
        val response: HttpResponse = httpClient.get("$blockCypherUrl/addrs/$address/full?limit=$limit")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val txs = jsonObject["txs"]?.jsonArray ?: return emptyList()
            
            return txs.mapNotNull { element ->
                val tx = element.jsonObject
                try {
                    BitcoinTransaction(
                        hash = tx["hash"]?.jsonPrimitive?.content ?: "",
                        from = extractAddresses(tx["inputs"]?.jsonArray).firstOrNull() ?: "",
                        to = extractAddresses(tx["outputs"]?.jsonArray).firstOrNull() ?: "",
                        value = tx["total"]?.jsonPrimitive?.long?.toString() ?: "0",
                        fee = tx["fees"]?.jsonPrimitive?.long?.toString() ?: "0",
                        blockNumber = tx["block_height"]?.jsonPrimitive?.int?.toString(),
                        timestamp = null,
                        confirmations = tx["confirmations"]?.jsonPrimitive?.int ?: 0
                    )
                } catch (e: Exception) {
                    println("Error parsing transaction: ${e.message}")
                    null
                }
            }
        } else {
            throw Exception("Failed to get transactions from BlockCypher: ${response.status}")
        }
    }
    
    /**
     * 從 SoChain 獲取交易歷史
     */
    private suspend fun getTransactionsFromSoChain(address: String, limit: Int): List<BitcoinTransaction> {
        val response: HttpResponse = httpClient.get("$soChainUrl/transactions/$soChainNetwork/$address/1")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val data = jsonObject["data"]?.jsonObject ?: throw Exception("Invalid SoChain response")
            val txs = data["transactions"]?.jsonArray ?: return emptyList()
            
            return txs.take(limit).mapNotNull { element ->
                val tx = element.jsonObject
                try {
                    val valueStr = tx["value"]?.jsonPrimitive?.content ?: "0"
                    val value = (valueStr.toDouble() * 100_000_000).toLong()
                    
                    BitcoinTransaction(
                        hash = tx["hash"]?.jsonPrimitive?.content ?: "",
                        from = "", // SoChain 不直接提供輸入地址
                        to = address, // 簡化處理
                        value = value.toString(),
                        fee = "0", // SoChain 不直接提供手續費
                        blockNumber = tx["block_no"]?.jsonPrimitive?.int?.toString(),
                        timestamp = null,
                        confirmations = tx["confirmations"]?.jsonPrimitive?.int ?: 0
                    )
                } catch (e: Exception) {
                    println("Error parsing transaction: ${e.message}")
                    null
                }
            }
        } else {
            throw Exception("Failed to get transactions from SoChain: ${response.status}")
        }
    }
    
    /**
     * 獲取手續費估算
     */
    suspend fun getFeeEstimates(): Map<String, Double> {
        // Dogecoin 手續費相對固定，通常是 1 DOGE
        // 這裡返回簡化的估算值
        return mapOf(
            "1" to 1.0,    // 1 DOGE per KB
            "6" to 1.0,    // 1 DOGE per KB
            "144" to 1.0   // 1 DOGE per KB
        )
    }
    
    /**
     * 廣播交易
     */
    suspend fun broadcastTransaction(signedTx: String): String {
        return try {
            broadcastViaBlockCypher(signedTx)
        } catch (e: Exception) {
            println("BlockCypher broadcast failed, trying SoChain: ${e.message}")
            broadcastViaSoChain(signedTx)
        }
    }
    
    /**
     * 通過 BlockCypher 廣播交易
     */
    private suspend fun broadcastViaBlockCypher(signedTx: String): String {
        val response: HttpResponse = httpClient.post("$blockCypherUrl/txs/push") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("tx" to signedTx))
        }
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            return jsonObject["tx"]?.jsonObject?.get("hash")?.jsonPrimitive?.content
                ?: throw Exception("No transaction hash in response")
        } else {
            val error = response.bodyAsText()
            throw Exception("BlockCypher broadcast failed: $error")
        }
    }
    
    /**
     * 通過 SoChain 廣播交易
     */
    private suspend fun broadcastViaSoChain(signedTx: String): String {
        val response: HttpResponse = httpClient.post("$soChainUrl/send_tx/$soChainNetwork") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("tx_hex" to signedTx))
        }
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val data = jsonObject["data"]?.jsonObject ?: throw Exception("Invalid response")
            return data["hash"]?.jsonPrimitive?.content
                ?: throw Exception("No transaction hash in response")
        } else {
            val error = response.bodyAsText()
            throw Exception("SoChain broadcast failed: $error")
        }
    }
    
    /**
     * 提取地址列表
     */
    private fun extractAddresses(array: JsonArray?): List<String> {
        if (array == null) return emptyList()
        
        return array.mapNotNull { element ->
            val obj = element.jsonObject
            obj["addresses"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
        }
    }
}
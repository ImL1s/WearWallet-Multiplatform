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
import kotlinx.serialization.json.*

/**
 * Bitcoin Cash API 客戶端
 * 支援多個 API 提供商：
 * 1. Blockchair API (主要)
 * 2. Bitcoin.com API (備用)
 * 3. Fullstack.cash API (備用)
 */
class BitcoinCashApiClient(
    private val network: Network
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(this@BitcoinCashApiClient.json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }
    
    // API URLs
    private val blockchairUrl = when (network) {
        Network.BITCOIN_CASH_MAINNET -> "https://api.blockchair.com/bitcoin-cash"
        Network.BITCOIN_CASH_TESTNET -> "https://api.blockchair.com/bitcoin-cash/testnet"
        else -> "https://api.blockchair.com/bitcoin-cash"
    }
    
    // Bitcoin.com REST API (備用)
    private val bitcoinComUrl = when (network) {
        Network.BITCOIN_CASH_MAINNET -> "https://rest.bitcoin.com/v2"
        Network.BITCOIN_CASH_TESTNET -> "https://trest.bitcoin.com/v2"
        else -> "https://rest.bitcoin.com/v2"
    }
    
    /**
     * 獲取地址餘額
     */
    suspend fun getBalance(address: String): Long {
        return try {
            // 優先使用 Blockchair
            getBalanceFromBlockchair(address)
        } catch (e: Exception) {
            println("Blockchair API failed, trying Bitcoin.com: ${e.message}")
            // 備用 Bitcoin.com
            getBalanceFromBitcoinCom(address)
        }
    }
    
    /**
     * 從 Blockchair 獲取餘額
     */
    private suspend fun getBalanceFromBlockchair(address: String): Long {
        val response: HttpResponse = httpClient.get("$blockchairUrl/dashboards/address/$address")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val data = jsonObject["data"]?.jsonObject ?: throw Exception("Invalid response")
            val addressData = data[address]?.jsonObject ?: throw Exception("Address not found")
            val addressInfo = addressData["address"]?.jsonObject ?: throw Exception("No address info")
            
            val balance = addressInfo["balance"]?.jsonPrimitive?.long ?: 0L
            val unconfirmedBalance = addressInfo["balance_usd"]?.jsonPrimitive?.long ?: 0L
            
            return balance
        } else {
            throw Exception("Failed to get balance from Blockchair: ${response.status}")
        }
    }
    
    /**
     * 從 Bitcoin.com 獲取餘額
     */
    private suspend fun getBalanceFromBitcoinCom(address: String): Long {
        val response: HttpResponse = httpClient.get("$bitcoinComUrl/address/details/$address")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            
            val balance = jsonObject["balance"]?.jsonPrimitive?.double ?: 0.0
            val unconfirmedBalance = jsonObject["unconfirmedBalance"]?.jsonPrimitive?.double ?: 0.0
            
            // Bitcoin.com 返回 BCH 單位，需要轉換為 satoshis
            return ((balance + unconfirmedBalance) * 100_000_000).toLong()
        } else {
            throw Exception("Failed to get balance from Bitcoin.com: ${response.status}")
        }
    }
    
    /**
     * 獲取 UTXOs
     */
    suspend fun getUtxos(address: String): List<UTXO> {
        return try {
            getUtxosFromBlockchair(address)
        } catch (e: Exception) {
            println("Blockchair API failed, trying Bitcoin.com: ${e.message}")
            getUtxosFromBitcoinCom(address)
        }
    }
    
    /**
     * 從 Blockchair 獲取 UTXOs
     */
    private suspend fun getUtxosFromBlockchair(address: String): List<UTXO> {
        val response: HttpResponse = httpClient.get("$blockchairUrl/dashboards/address/$address")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val data = jsonObject["data"]?.jsonObject ?: throw Exception("Invalid response")
            val addressData = data[address]?.jsonObject ?: throw Exception("Address not found")
            val utxos = addressData["utxo"]?.jsonArray ?: return emptyList()
            
            return utxos.mapNotNull { element ->
                val utxo = element.jsonObject
                try {
                    UTXO(
                        txid = utxo["transaction_hash"]?.jsonPrimitive?.content ?: "",
                        vout = utxo["index"]?.jsonPrimitive?.int ?: 0,
                        value = utxo["value"]?.jsonPrimitive?.long ?: 0L,
                        confirmed = true,
                        blockHeight = utxo["block_id"]?.jsonPrimitive?.long ?: 0L,
                        scriptPubKey = null,
                        address = address
                    )
                } catch (e: Exception) {
                    println("Error parsing UTXO: ${e.message}")
                    null
                }
            }
        } else {
            throw Exception("Failed to get UTXOs from Blockchair: ${response.status}")
        }
    }
    
    /**
     * 從 Bitcoin.com 獲取 UTXOs
     */
    private suspend fun getUtxosFromBitcoinCom(address: String): List<UTXO> {
        val response: HttpResponse = httpClient.get("$bitcoinComUrl/address/utxo/$address")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val utxos = jsonObject["utxos"]?.jsonArray ?: return emptyList()
            
            return utxos.mapNotNull { element ->
                val utxo = element.jsonObject
                try {
                    val cashAddress = utxo["cashAddress"]?.jsonObject
                    UTXO(
                        txid = utxo["txid"]?.jsonPrimitive?.content ?: "",
                        vout = utxo["vout"]?.jsonPrimitive?.int ?: 0,
                        value = utxo["satoshis"]?.jsonPrimitive?.long ?: 0L,
                        confirmed = utxo["confirmations"]?.jsonPrimitive?.int?.let { it > 0 } ?: false,
                        blockHeight = utxo["height"]?.jsonPrimitive?.long ?: 0L,
                        scriptPubKey = utxo["scriptPubKey"]?.jsonPrimitive?.content,
                        address = cashAddress?.get("cashAddress")?.jsonPrimitive?.content ?: address
                    )
                } catch (e: Exception) {
                    println("Error parsing UTXO: ${e.message}")
                    null
                }
            }
        } else {
            throw Exception("Failed to get UTXOs from Bitcoin.com: ${response.status}")
        }
    }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactions(address: String, limit: Int = 50): List<BitcoinTransaction> {
        return try {
            getTransactionsFromBlockchair(address, limit)
        } catch (e: Exception) {
            println("Blockchair API failed, trying Bitcoin.com: ${e.message}")
            getTransactionsFromBitcoinCom(address, limit)
        }
    }
    
    /**
     * 從 Blockchair 獲取交易歷史
     */
    private suspend fun getTransactionsFromBlockchair(address: String, limit: Int): List<BitcoinTransaction> {
        val response: HttpResponse = httpClient.get("$blockchairUrl/dashboards/address/$address?limit=$limit")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val data = jsonObject["data"]?.jsonObject ?: throw Exception("Invalid response")
            val addressData = data[address]?.jsonObject ?: throw Exception("Address not found")
            val transactions = addressData["transactions"]?.jsonArray ?: return emptyList()
            
            return transactions.take(limit).mapNotNull { element ->
                try {
                    val hash = element.jsonPrimitive.content
                    // 簡化處理 - 只返回交易 hash
                    BitcoinTransaction(
                        hash = hash,
                        from = address,
                        to = "",
                        value = "0",
                        fee = "0",
                        blockNumber = null,
                        timestamp = null,
                        confirmations = 0
                    )
                } catch (e: Exception) {
                    println("Error parsing transaction: ${e.message}")
                    null
                }
            }
        } else {
            throw Exception("Failed to get transactions from Blockchair: ${response.status}")
        }
    }
    
    /**
     * 從 Bitcoin.com 獲取交易歷史
     */
    private suspend fun getTransactionsFromBitcoinCom(address: String, limit: Int): List<BitcoinTransaction> {
        val response: HttpResponse = httpClient.get("$bitcoinComUrl/address/transactions/$address")
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val txs = jsonObject["txs"]?.jsonArray ?: return emptyList()
            
            return txs.take(limit).mapNotNull { element ->
                val tx = element.jsonObject
                try {
                    BitcoinTransaction(
                        hash = tx["txid"]?.jsonPrimitive?.content ?: "",
                        from = "", // 需要額外查詢
                        to = "", // 需要額外查詢
                        value = (tx["valueOut"]?.jsonPrimitive?.double?.let { it * 100_000_000 }?.toLong() ?: 0L).toString(),
                        fee = tx["fees"]?.jsonPrimitive?.double?.let { (it * 100_000_000).toLong() }?.toString() ?: "0",
                        blockNumber = tx["blockheight"]?.jsonPrimitive?.int?.toString(),
                        timestamp = null,
                        confirmations = tx["confirmations"]?.jsonPrimitive?.int ?: 0
                    )
                } catch (e: Exception) {
                    println("Error parsing transaction: ${e.message}")
                    null
                }
            }
        } else {
            throw Exception("Failed to get transactions from Bitcoin.com: ${response.status}")
        }
    }
    
    /**
     * 獲取手續費估算
     */
    suspend fun getFeeEstimate(): Long? {
        return try {
            // Blockchair 提供手續費建議
            val response: HttpResponse = httpClient.get("$blockchairUrl/stats")
            
            if (response.status.isSuccess()) {
                val json = response.bodyAsText()
                val jsonObject = Json.parseToJsonElement(json).jsonObject
                val data = jsonObject["data"]?.jsonObject ?: return null
                
                // 獲取建議手續費（satoshis per byte）
                val suggestedFee = data["suggested_transaction_fee_per_byte_sat"]?.jsonPrimitive?.long
                return suggestedFee ?: 2L // 預設 2 sat/byte
            } else {
                null
            }
        } catch (e: Exception) {
            println("Failed to get fee estimate: ${e.message}")
            null
        }
    }
    
    /**
     * 廣播交易
     */
    suspend fun broadcastTransaction(signedTx: String): String {
        return try {
            broadcastViaBlockchair(signedTx)
        } catch (e: Exception) {
            println("Blockchair broadcast failed, trying Bitcoin.com: ${e.message}")
            broadcastViaBitcoinCom(signedTx)
        }
    }
    
    /**
     * 通過 Blockchair 廣播交易
     */
    private suspend fun broadcastViaBlockchair(signedTx: String): String {
        val response: HttpResponse = httpClient.post("$blockchairUrl/push/transaction") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("data" to signedTx))
        }
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            val jsonObject = Json.parseToJsonElement(json).jsonObject
            val data = jsonObject["data"]?.jsonObject ?: throw Exception("Invalid response")
            return data["transaction_hash"]?.jsonPrimitive?.content
                ?: throw Exception("No transaction hash in response")
        } else {
            val error = response.bodyAsText()
            throw Exception("Blockchair broadcast failed: $error")
        }
    }
    
    /**
     * 通過 Bitcoin.com 廣播交易
     */
    private suspend fun broadcastViaBitcoinCom(signedTx: String): String {
        val response: HttpResponse = httpClient.post("$bitcoinComUrl/rawtransactions/sendRawTransaction") {
            contentType(ContentType.Application.Json)
            setBody(listOf(signedTx)) // Bitcoin.com API 需要數組格式
        }
        
        if (response.status.isSuccess()) {
            val json = response.bodyAsText()
            // Bitcoin.com 直接返回交易 hash 字串或數組
            return if (json.startsWith("[")) {
                Json.parseToJsonElement(json).jsonArray.firstOrNull()?.jsonPrimitive?.content
                    ?: throw Exception("No transaction hash in response")
            } else {
                json.trim('"')
            }
        } else {
            val error = response.bodyAsText()
            throw Exception("Bitcoin.com broadcast failed: $error")
        }
    }
    
    /**
     * 獲取網路狀態
     */
    suspend fun getNetworkStatus(): Map<String, Any> {
        return try {
            val response: HttpResponse = httpClient.get("$blockchairUrl/stats")
            
            if (response.status.isSuccess()) {
                val json = response.bodyAsText()
                val jsonObject = Json.parseToJsonElement(json).jsonObject
                val data = jsonObject["data"]?.jsonObject ?: return emptyMap()
                
                mapOf(
                    "blocks" to (data["blocks"]?.jsonPrimitive?.int ?: 0),
                    "difficulty" to (data["difficulty"]?.jsonPrimitive?.double ?: 0.0),
                    "hashrate" to (data["hashrate_24h"]?.jsonPrimitive?.content ?: "0"),
                    "medianTime" to (data["median_time"]?.jsonPrimitive?.int ?: 0),
                    "feeRate" to (data["suggested_transaction_fee_per_byte_sat"]?.jsonPrimitive?.long ?: 2L)
                )
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            println("Failed to get network status: ${e.message}")
            emptyMap()
        }
    }
}
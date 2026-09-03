package com.cbstudio.wearwallet.core.blockchain.api

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.BitcoinTransaction
import com.cbstudio.wearwallet.core.domain.model.Network
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Litecoin API 客戶端
 * 支援多個 API 提供者以確保可靠性
 */
class LitecoinApiClient(
    private val network: Network
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
    
    // 根據網路選擇 API endpoint
    private val baseUrl: String = when (network) {
        Network.LITECOIN_MAINNET -> "https://api.blockcypher.com/v1/ltc/main"
        Network.LITECOIN_TESTNET -> "https://api.blockcypher.com/v1/ltc/test3"
        else -> throw IllegalArgumentException("Unsupported network: $network")
    }
    
    // 備用 API (SoChain)
    private val soChainUrl: String = when (network) {
        Network.LITECOIN_MAINNET -> "https://sochain.com/api/v2"
        Network.LITECOIN_TESTNET -> "https://sochain.com/api/v2"
        else -> throw IllegalArgumentException("Unsupported network: $network")
    }
    
    private val soChainNetwork = when (network) {
        Network.LITECOIN_MAINNET -> "LTC"
        Network.LITECOIN_TESTNET -> "LTCTEST"
        else -> ""
    }
    
    /**
     * 獲取地址的 UTXOs
     */
    suspend fun getUtxos(address: String): List<UTXO> {
        return try {
            // 嘗試 BlockCypher API
            getUtxosFromBlockCypher(address)
        } catch (e: Exception) {
            // 降級到 SoChain API
            getUtxosFromSoChain(address)
        }
    }
    
    private suspend fun getUtxosFromBlockCypher(address: String): List<UTXO> {
        val response: BlockCypherAddressResponse = client.get("$baseUrl/addrs/$address") {
            parameter("unspentOnly", true)
            parameter("includeScript", false)
        }.body()
        
        return response.txrefs?.map { txref ->
            UTXO(
                txid = txref.tx_hash,
                vout = txref.tx_output_n,
                value = txref.value,
                confirmed = txref.confirmations > 0,
                blockHeight = txref.block_height?.toLong() ?: 0L
            )
        } ?: emptyList()
    }
    
    private suspend fun getUtxosFromSoChain(address: String): List<UTXO> {
        val response: SoChainUtxoResponse = client.get("$soChainUrl/get_tx_unspent/$soChainNetwork/$address").body()
        
        return response.data?.txs?.map { utxo ->
            UTXO(
                txid = utxo.txid,
                vout = utxo.output_no,
                value = (utxo.value.toDouble() * 100_000_000).toLong(), // Convert LTC to litoshi
                confirmed = utxo.confirmations > 0,
                blockHeight = 0 // SoChain doesn't provide block height in this endpoint
            )
        } ?: emptyList()
    }
    
    /**
     * 獲取手續費估算
     */
    suspend fun getFeeEstimates(): Map<String, Double> {
        return try {
            // Litecoin 通常使用較低的手續費
            mapOf(
                "1" to 5.0,   // 高優先級
                "6" to 3.0,   // 中優先級
                "144" to 1.0  // 低優先級
            )
        } catch (e: Exception) {
            // 默認手續費率
            mapOf(
                "1" to 5.0,
                "6" to 3.0,
                "144" to 1.0
            )
        }
    }
    
    /**
     * 廣播交易
     */
    suspend fun broadcastTransaction(txHex: String): String? {
        return try {
            val response: BlockCypherTxResponse = client.post("$baseUrl/txs/push") {
                contentType(ContentType.Application.Json)
                setBody(BlockCypherPushTx(tx = txHex))
            }.body()
            
            response.tx?.hash
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactionHistory(address: String, limit: Int = 50): List<BitcoinTransaction> {
        return try {
            val response: BlockCypherAddressResponse = client.get("$baseUrl/addrs/$address/full") {
                parameter("limit", limit)
            }.body()
            
            response.txs?.map { tx ->
                BitcoinTransaction(
                    hash = tx.hash,
                    from = tx.inputs?.firstOrNull()?.addresses?.firstOrNull() ?: "",
                    to = tx.outputs?.firstOrNull()?.addresses?.firstOrNull() ?: "",
                    value = tx.total.toString(),
                    fee = tx.fees.toString(),
                    confirmations = tx.confirmations,
                    timestamp = Instant.parse(tx.received ?: "1970-01-01T00:00:00Z")
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// BlockCypher API 響應模型
@Serializable
data class BlockCypherAddressResponse(
    val address: String? = null,
    val total_received: Long? = null,
    val total_sent: Long? = null,
    val balance: Long? = null,
    val unconfirmed_balance: Long? = null,
    val n_tx: Int? = null,
    val txrefs: List<BlockCypherTxRef>? = null,
    val txs: List<BlockCypherTransaction>? = null
)

@Serializable
data class BlockCypherTxRef(
    val tx_hash: String,
    val block_height: Int? = null,
    val tx_input_n: Int = -1,
    val tx_output_n: Int = 0,
    val value: Long,
    val confirmations: Int = 0,
    val double_spend: Boolean = false
)

@Serializable
data class BlockCypherTransaction(
    val hash: String,
    val total: Long,
    val fees: Long,
    val size: Int? = null,
    val confirmations: Int = 0,
    val block_height: Int? = null,
    val received: String? = null,
    val inputs: List<BlockCypherInput>? = null,
    val outputs: List<BlockCypherOutput>? = null
)

@Serializable
data class BlockCypherInput(
    val addresses: List<String>? = null,
    val output_value: Long? = null
)

@Serializable
data class BlockCypherOutput(
    val addresses: List<String>? = null,
    val value: Long? = null
)

@Serializable
data class BlockCypherPushTx(
    val tx: String
)

@Serializable
data class BlockCypherTxResponse(
    val tx: BlockCypherTransaction? = null
)

// SoChain API 響應模型
@Serializable
data class SoChainUtxoResponse(
    val status: String? = null,
    val data: SoChainUtxoData? = null
)

@Serializable
data class SoChainUtxoData(
    val network: String? = null,
    val address: String? = null,
    val txs: List<SoChainUtxo>? = null
)

@Serializable
data class SoChainUtxo(
    val txid: String,
    val output_no: Int,
    val script_asm: String? = null,
    val script_hex: String? = null,
    val value: String,
    val confirmations: Int,
    val time: Long? = null
)
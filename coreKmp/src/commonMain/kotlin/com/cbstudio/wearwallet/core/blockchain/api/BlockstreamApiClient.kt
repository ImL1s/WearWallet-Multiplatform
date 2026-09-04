package com.cbstudio.wearwallet.core.blockchain.api

import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.BitcoinTransaction
import com.cbstudio.wearwallet.core.domain.model.Network
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Blockstream API 客戶端
 * 用於與 Blockstream.info API 交互
 */
class BlockstreamApiClient(private val network: Network) {
    
    private val baseUrl = when (network) {
        Network.BITCOIN_MAINNET -> "https://blockstream.info/api"
        Network.BITCOIN_TESTNET -> "https://blockstream.info/testnet/api"
        else -> throw IllegalArgumentException("Unsupported network: $network")
    }
    
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
    }
    
    /**
     * 獲取地址的 UTXO 列表
     */
    suspend fun getUtxos(address: String): List<UTXO> {
        return try {
            val response: List<BlockstreamUTXO> = httpClient.get("$baseUrl/address/$address/utxo").body()
            response.map { it.toUTXO() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 獲取地址餘額
     */
    suspend fun getBalance(address: String): Long {
        return try {
            val stats: AddressStats = httpClient.get("$baseUrl/address/$address").body()
            stats.chain_stats.funded_txo_sum - stats.chain_stats.spent_txo_sum
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 廣播交易
     */
    suspend fun broadcastTransaction(txHex: String): String? {
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/tx") {
                setBody(txHex)
            }
            response.bodyAsText()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 獲取手續費估算
     */
    suspend fun getFeeEstimates(): Map<String, Double> {
        return try {
            httpClient.get("$baseUrl/fee-estimates").body()
        } catch (e: Exception) {
            // 返回默認費率
            mapOf(
                "2" to 50.0,   // 高優先級 ~20分鐘
                "6" to 20.0,   // 中優先級 ~1小時
                "144" to 5.0   // 低優先級 ~24小時
            )
        }
    }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactionHistory(address: String, limit: Int = 50): List<BitcoinTransaction> {
        return try {
            val txs: List<BlockstreamTransaction> = httpClient.get("$baseUrl/address/$address/txs").body()
            txs.take(limit).map { it.toBitcoinTransaction(address) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 獲取當前區塊高度
     */
    suspend fun getCurrentBlockHeight(): Long {
        return try {
            val response: String = httpClient.get("$baseUrl/blocks/tip/height").body()
            response.toLong()
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 獲取交易詳情
     */
    suspend fun getTransaction(txid: String): BitcoinTransaction? {
        return try {
            val tx: BlockstreamTransaction = httpClient.get("$baseUrl/tx/$txid").body()
            tx.toBitcoinTransaction("")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 檢查交易狀態
     */
    suspend fun getTransactionStatus(txid: String): TransactionStatus {
        return try {
            val tx: BlockstreamTransaction = httpClient.get("$baseUrl/tx/$txid").body()
            when {
                tx.status.confirmed -> TransactionStatus.CONFIRMED
                else -> TransactionStatus.PENDING
            }
        } catch (e: Exception) {
            TransactionStatus.FAILED
        }
    }
}

// Blockstream API 數據模型

@Serializable
data class BlockstreamUTXO(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: TxStatus
) {
    fun toUTXO() = UTXO(
        txid = txid,
        vout = vout,
        value = value,
        confirmed = status.confirmed,
        blockHeight = status.block_height ?: 0
    )
}

@Serializable
data class TxStatus(
    val confirmed: Boolean,
    val block_height: Long? = null,
    val block_hash: String? = null,
    val block_time: Long? = null
)

@Serializable
data class AddressStats(
    val address: String,
    val chain_stats: ChainStats,
    val mempool_stats: MempoolStats
)

@Serializable
data class ChainStats(
    val funded_txo_count: Int,
    val funded_txo_sum: Long,
    val spent_txo_count: Int,
    val spent_txo_sum: Long,
    val tx_count: Int
)

@Serializable
data class MempoolStats(
    val funded_txo_count: Int,
    val funded_txo_sum: Long,
    val spent_txo_count: Int,
    val spent_txo_sum: Long,
    val tx_count: Int
)

@Serializable
data class BlockstreamTransaction(
    val txid: String,
    val version: Int,
    val locktime: Long,
    val size: Int,
    val weight: Int,
    val fee: Long,
    val status: TxStatus,
    val vin: List<TransactionInput>,
    val vout: List<TransactionOutput>
) {
    fun toBitcoinTransaction(userAddress: String): BitcoinTransaction {
        // 判斷交易方向
        val isIncoming = vout.any { output ->
            output.scriptpubkey_address == userAddress
        }
        
        val from = if (isIncoming) {
            vin.firstOrNull()?.prevout?.scriptpubkey_address ?: "Unknown"
        } else {
            userAddress
        }
        
        val to = if (isIncoming) {
            userAddress
        } else {
            vout.firstOrNull { it.scriptpubkey_address != userAddress }?.scriptpubkey_address ?: "Unknown"
        }
        
        val value = if (isIncoming) {
            vout.filter { it.scriptpubkey_address == userAddress }.sumOf { it.value }
        } else {
            vout.filter { it.scriptpubkey_address != userAddress }.sumOf { it.value }
        }
        
        return BitcoinTransaction(
            hash = txid,
            from = from,
            to = to,
            value = value.toString(),
            fee = fee.toString(),
            blockNumber = status.block_height?.toString(),
            timestamp = status.block_time?.let { Instant.fromEpochSeconds(it) },
            status = if (status.confirmed) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
            confirmations = if (status.confirmed && status.block_height != null) {
                // 需要獲取當前區塊高度來計算確認數
                1
            } else {
                0
            }
        )
    }
}

@Serializable
data class TransactionInput(
    val txid: String,
    val vout: Int,
    val prevout: TransactionOutput? = null,
    val scriptsig: String,
    val scriptsig_asm: String,
    val witness: List<String>? = null,
    val is_coinbase: Boolean,
    val sequence: Long
)

@Serializable
data class TransactionOutput(
    val value: Long,
    val scriptpubkey: String,
    val scriptpubkey_asm: String,
    val scriptpubkey_type: String,
    val scriptpubkey_address: String? = null
)
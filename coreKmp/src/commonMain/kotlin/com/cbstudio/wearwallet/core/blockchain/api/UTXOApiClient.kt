package com.cbstudio.wearwallet.core.blockchain.api

import com.cbstudio.wearwallet.core.blockchain.model.*
import com.cbstudio.wearwallet.core.domain.model.ChainType
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * UTXO 區塊鏈 API 客戶端
 * 支援多個 API 提供者：BlockCypher, Blockchair, Blockstream
 */
class UTXOApiClient {
    
    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    /**
     * 獲取地址餘額
     */
    suspend fun getBalance(address: String, chainType: ChainType): Long {
        return try {
            when (chainType) {
                ChainType.BITCOIN -> getBlockstreamBalance(address, "bitcoin")
                ChainType.LITECOIN -> getBlockCypherBalance(address, "ltc")
                ChainType.DOGECOIN -> getBlockCypherBalance(address, "doge")
                ChainType.BITCOIN_CASH -> getBlockchairBalance(address, "bitcoin-cash")
                else -> 0L
            }
        } catch (e: Exception) {
            println("獲取餘額失敗: ${e.message}")
            0L
        }
    }
    
    /**
     * 獲取 UTXOs
     */
    suspend fun getUTXOs(address: String, chainType: ChainType): List<UTXO> {
        return try {
            when (chainType) {
                ChainType.BITCOIN -> getBlockstreamUTXOs(address, "bitcoin")
                ChainType.LITECOIN -> getBlockCypherUTXOs(address, "ltc")
                ChainType.DOGECOIN -> getBlockCypherUTXOs(address, "doge")
                ChainType.BITCOIN_CASH -> getBlockchairUTXOs(address, "bitcoin-cash")
                else -> emptyList()
            }
        } catch (e: Exception) {
            println("獲取 UTXOs 失敗: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 獲取手續費估算 (sat/vB)
     */
    suspend fun getFeeEstimate(chainType: ChainType, priority: FeePriority): Long {
        return try {
            when (chainType) {
                ChainType.BITCOIN -> getBlockstreamFeeEstimate(priority)
                ChainType.LITECOIN -> getLitecoinFeeEstimate(priority)
                ChainType.DOGECOIN -> getDogecoinFeeEstimate(priority)
                ChainType.BITCOIN_CASH -> getBitcoinCashFeeEstimate(priority)
                else -> 10L
            }
        } catch (e: Exception) {
            println("獲取手續費估算失敗: ${e.message}")
            // 返回默認值
            when (chainType) {
                ChainType.BITCOIN -> when (priority) {
                    FeePriority.SLOW -> 5L
                    FeePriority.NORMAL -> 10L
                    FeePriority.FAST -> 20L
                    FeePriority.URGENT -> 50L
                }
                ChainType.LITECOIN -> when (priority) {
                    FeePriority.SLOW -> 1L
                    FeePriority.NORMAL -> 2L
                    FeePriority.FAST -> 5L
                    FeePriority.URGENT -> 10L
                }
                ChainType.DOGECOIN -> when (priority) {
                    FeePriority.SLOW -> 100L
                    FeePriority.NORMAL -> 500L
                    FeePriority.FAST -> 1000L
                    FeePriority.URGENT -> 5000L
                }
                ChainType.BITCOIN_CASH -> when (priority) {
                    FeePriority.SLOW -> 1L
                    FeePriority.NORMAL -> 2L
                    FeePriority.FAST -> 5L
                    FeePriority.URGENT -> 10L
                }
                else -> 10L
            }
        }
    }
    
    /**
     * 廣播交易
     */
    suspend fun broadcastTransaction(rawTx: String, chainType: ChainType): String {
        return try {
            when (chainType) {
                ChainType.BITCOIN -> broadcastBlockstream(rawTx, "bitcoin")
                ChainType.LITECOIN -> broadcastBlockCypher(rawTx, "ltc")
                ChainType.DOGECOIN -> broadcastBlockCypher(rawTx, "doge")
                ChainType.BITCOIN_CASH -> broadcastBlockchair(rawTx, "bitcoin-cash")
                else -> throw IllegalArgumentException("不支援的鏈類型")
            }
        } catch (e: Exception) {
            throw Exception("廣播交易失敗: ${e.message}")
        }
    }
    
    /**
     * 獲取交易歷史
     */
    suspend fun getTransactionHistory(
        address: String,
        chainType: ChainType,
        limit: Int = 50,
        offset: Int = 0
    ): List<UTXOTransaction> {
        return try {
            when (chainType) {
                ChainType.BITCOIN -> getBlockstreamTransactionHistory(address, "bitcoin", limit, offset)
                ChainType.LITECOIN -> getBlockCypherTransactionHistory(address, "ltc", limit, offset)
                ChainType.DOGECOIN -> getBlockCypherTransactionHistory(address, "doge", limit, offset)
                ChainType.BITCOIN_CASH -> getBlockchairTransactionHistory(address, "bitcoin-cash", limit, offset)
                else -> emptyList()
            }
        } catch (e: Exception) {
            println("獲取交易歷史失敗: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 獲取單筆交易詳情
     */
    suspend fun getTransaction(txId: String, chainType: ChainType): UTXOTransaction {
        return when (chainType) {
            ChainType.BITCOIN -> getBlockstreamTransaction(txId, "bitcoin")
            ChainType.LITECOIN -> getBlockCypherTransaction(txId, "ltc")
            ChainType.DOGECOIN -> getBlockCypherTransaction(txId, "doge")
            ChainType.BITCOIN_CASH -> getBlockchairTransaction(txId, "bitcoin-cash")
            else -> throw IllegalArgumentException("不支援的鏈類型")
        }
    }
    
    /**
     * 檢查地址是否有交易活動
     */
    suspend fun hasTransactionActivity(address: String, chainType: ChainType): Boolean {
        return try {
            val history = getTransactionHistory(address, chainType, limit = 1)
            history.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    // ===== Blockstream API (Bitcoin) =====
    
    private suspend fun getBlockstreamBalance(address: String, network: String): Long {
        val baseUrl = if (network == "bitcoin") {
            "https://blockstream.info/api"
        } else {
            "https://blockstream.info/testnet/api"
        }
        
        val response = httpClient.get("$baseUrl/address/$address")
        val data: UTXOBlockstreamAddress = response.body()
        
        return data.chainStats.fundedTxoSum - data.chainStats.spentTxoSum
    }
    
    private suspend fun getBlockstreamUTXOs(address: String, network: String): List<UTXO> {
        val baseUrl = if (network == "bitcoin") {
            "https://blockstream.info/api"
        } else {
            "https://blockstream.info/testnet/api"
        }
        
        val response = httpClient.get("$baseUrl/address/$address/utxo")
        val utxos: List<UTXOBlockstreamUTXO> = response.body()
        
        return utxos.map { utxo ->
            UTXO(
                txid = utxo.txid,
                vout = utxo.vout,
                value = utxo.value,
                address = address,
                scriptPubKey = "", // Blockstream 不直接返回 script
                confirmed = utxo.status.confirmed
            )
        }
    }
    
    private suspend fun getBlockstreamFeeEstimate(priority: FeePriority): Long {
        val response = httpClient.get("https://mempool.space/api/v1/fees/recommended")
        val fees: MempoolFees = response.body()
        
        return when (priority) {
            FeePriority.SLOW -> fees.hourFee.toLong()
            FeePriority.NORMAL -> fees.halfHourFee.toLong()
            FeePriority.FAST -> fees.fastestFee.toLong()
            FeePriority.URGENT -> (fees.fastestFee * 1.5).toLong()
        }
    }
    
    private suspend fun broadcastBlockstream(rawTx: String, network: String): String {
        val baseUrl = if (network == "bitcoin") {
            "https://blockstream.info/api"
        } else {
            "https://blockstream.info/testnet/api"
        }
        
        val response = httpClient.post("$baseUrl/tx") {
            setBody(rawTx)
        }
        
        return response.body()
    }
    
    private suspend fun getBlockstreamTransactionHistory(
        address: String,
        network: String,
        limit: Int,
        offset: Int
    ): List<UTXOTransaction> {
        val baseUrl = if (network == "bitcoin") {
            "https://blockstream.info/api"
        } else {
            "https://blockstream.info/testnet/api"
        }
        
        val response = httpClient.get("$baseUrl/address/$address/txs")
        val transactions: List<UTXOBlockstreamTransaction> = response.body()
        
        return transactions
            .drop(offset)
            .take(limit)
            .map { tx ->
                UTXOTransaction(
                    txId = tx.txid,
                    blockHeight = tx.status.blockHeight?.toLong(),
                    timestamp = tx.status.blockTime?.let { Instant.fromEpochSeconds(it) },
                    inputs = tx.vin.map { input ->
                        UTXOInput(
                            txId = input.txid ?: "",
                            vout = input.vout ?: 0,
                            scriptSig = input.scriptsig ?: "",
                            sequence = input.sequence ?: 0,
                            address = input.prevout?.scriptpubkeyAddress,
                            value = input.prevout?.value
                        )
                    },
                    outputs = tx.vout.mapIndexed { index, output ->
                        UTXOOutput(
                            index = index,
                            value = output.value,
                            scriptPubKey = output.scriptpubkey ?: "",
                            address = output.scriptpubkeyAddress,
                            spent = false // Will need additional query
                        )
                    },
                    fee = tx.fee ?: 0L,
                    size = tx.size,
                    weight = tx.weight,
                    confirmations = if (tx.status.confirmed) {
                        val currentHeight = getBlockHeight(network)
                        (currentHeight - (tx.status.blockHeight?.toLong() ?: currentHeight)).toInt()
                    } else 0,
                    status = if (tx.status.confirmed) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
                    chainType = ChainType.BITCOIN
                )
            }
    }
    
    private suspend fun getBlockstreamTransaction(txId: String, network: String): UTXOTransaction {
        val baseUrl = if (network == "bitcoin") {
            "https://blockstream.info/api"
        } else {
            "https://blockstream.info/testnet/api"
        }
        
        val response = httpClient.get("$baseUrl/tx/$txId")
        val tx: UTXOBlockstreamTransaction = response.body()
        
        return UTXOTransaction(
            txId = tx.txid,
            blockHeight = tx.status.blockHeight?.toLong(),
            timestamp = tx.status.blockTime?.let { Instant.fromEpochSeconds(it) },
            inputs = tx.vin.map { input ->
                UTXOInput(
                    txId = input.txid ?: "",
                    vout = input.vout ?: 0,
                    scriptSig = input.scriptsig ?: "",
                    sequence = input.sequence ?: 0,
                    address = input.prevout?.scriptpubkeyAddress,
                    value = input.prevout?.value
                )
            },
            outputs = tx.vout.mapIndexed { index, output ->
                UTXOOutput(
                    index = index,
                    value = output.value,
                    scriptPubKey = output.scriptpubkey ?: "",
                    address = output.scriptpubkeyAddress,
                    spent = false
                )
            },
            fee = tx.fee ?: 0L,
            size = tx.size,
            weight = tx.weight,
            confirmations = if (tx.status.confirmed) {
                val currentHeight = getBlockHeight(network)
                (currentHeight - (tx.status.blockHeight?.toLong() ?: currentHeight)).toInt()
            } else 0,
            status = if (tx.status.confirmed) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
            chainType = ChainType.BITCOIN
        )
    }
    
    private suspend fun getBlockHeight(network: String): Long {
        val baseUrl = if (network == "bitcoin") {
            "https://blockstream.info/api"
        } else {
            "https://blockstream.info/testnet/api"
        }
        
        return try {
            val response = httpClient.get("$baseUrl/blocks/tip/height")
            response.body<String>().toLong()
        } catch (e: Exception) {
            0L
        }
    }
    
    // ===== BlockCypher API (Litecoin, Dogecoin) =====
    
    private suspend fun getBlockCypherBalance(address: String, coin: String): Long {
        val response = httpClient.get("https://api.blockcypher.com/v1/$coin/main/addrs/$address/balance")
        val data: UTXOBlockCypherBalance = response.body()
        return data.balance
    }
    
    private suspend fun getBlockCypherUTXOs(address: String, coin: String): List<UTXO> {
        val response = httpClient.get("https://api.blockcypher.com/v1/$coin/main/addrs/$address?unspentOnly=true")
        val data: UTXOBlockCypherAddress = response.body()
        
        return data.txrefs?.map { ref ->
            UTXO(
                txid = ref.txHash,
                vout = ref.txOutputN,
                value = ref.value,
                address = address,
                scriptPubKey = ref.script ?: "",
                confirmed = ref.confirmations > 0
            )
        } ?: emptyList()
    }
    
    private suspend fun broadcastBlockCypher(rawTx: String, coin: String): String {
        val response = httpClient.post("https://api.blockcypher.com/v1/$coin/main/txs/push") {
            setBody(UTXOBlockCypherPushTx(tx = rawTx))
        }
        
        val result: UTXOBlockCypherTxResponse = response.body()
        return result.tx.hash
    }
    
    private suspend fun getBlockCypherTransactionHistory(
        address: String,
        coin: String,
        limit: Int,
        offset: Int
    ): List<UTXOTransaction> {
        val response = httpClient.get("https://api.blockcypher.com/v1/$coin/main/addrs/$address/full?limit=$limit&after=$offset")
        val data: UTXOBlockCypherFullAddress = response.body()
        
        return data.txs.map { tx ->
            UTXOTransaction(
                txId = tx.hash,
                blockHeight = tx.blockHeight?.toLong(),
                timestamp = tx.received?.let { Instant.parse(it) },
                inputs = tx.inputs.map { input ->
                    UTXOInput(
                        txId = input.prevHash ?: "",
                        vout = input.outputIndex ?: 0,
                        scriptSig = input.script ?: "",
                        sequence = input.sequence ?: 0,
                        address = input.addresses?.firstOrNull(),
                        value = input.outputValue
                    )
                },
                outputs = tx.outputs.mapIndexed { index, output ->
                    UTXOOutput(
                        index = index,
                        value = output.value,
                        scriptPubKey = output.script ?: "",
                        address = output.addresses?.firstOrNull(),
                        spent = output.spentBy != null
                    )
                },
                fee = tx.fees ?: 0L,
                size = tx.size,
                weight = null,
                confirmations = tx.confirmations,
                status = if (tx.confirmations > 0) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
                chainType = when (coin) {
                    "ltc" -> ChainType.LITECOIN
                    "doge" -> ChainType.DOGECOIN
                    else -> ChainType.BITCOIN
                }
            )
        }
    }
    
    private suspend fun getBlockCypherTransaction(txId: String, coin: String): UTXOTransaction {
        val response = httpClient.get("https://api.blockcypher.com/v1/$coin/main/txs/$txId")
        val tx: UTXOBlockCypherTransaction = response.body()
        
        return UTXOTransaction(
            txId = tx.hash,
            blockHeight = tx.blockHeight?.toLong(),
            timestamp = tx.received?.let { Instant.parse(it) },
            inputs = tx.inputs.map { input ->
                UTXOInput(
                    txId = input.prevHash ?: "",
                    vout = input.outputIndex ?: 0,
                    scriptSig = input.script ?: "",
                    sequence = input.sequence ?: 0,
                    address = input.addresses?.firstOrNull(),
                    value = input.outputValue
                )
            },
            outputs = tx.outputs.mapIndexed { index, output ->
                UTXOOutput(
                    index = index,
                    value = output.value,
                    scriptPubKey = output.script ?: "",
                    address = output.addresses?.firstOrNull(),
                    spent = output.spentBy != null
                )
            },
            fee = tx.fees ?: 0L,
            size = tx.size,
            weight = null,
            confirmations = tx.confirmations,
            status = if (tx.confirmations > 0) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
            chainType = when (coin) {
                "ltc" -> ChainType.LITECOIN
                "doge" -> ChainType.DOGECOIN
                else -> ChainType.BITCOIN
            }
        )
    }
    
    // ===== Blockchair API (Bitcoin Cash) =====
    
    private suspend fun getBlockchairBalance(address: String, chain: String): Long {
        val response = httpClient.get("https://api.blockchair.com/$chain/dashboards/address/$address")
        val data: BlockchairResponse = response.body()
        return data.data[address]?.address?.balance ?: 0L
    }
    
    private suspend fun getBlockchairUTXOs(address: String, chain: String): List<UTXO> {
        val response = httpClient.get("https://api.blockchair.com/$chain/outputs?q=recipient($address),is_spent(false)")
        val data: BlockchairUTXOResponse = response.body()
        
        return data.data.map { utxo ->
            UTXO(
                txid = utxo.transactionHash,
                vout = utxo.index,
                value = utxo.value,
                address = address,
                scriptPubKey = utxo.scriptHex ?: "",
                confirmed = utxo.blockId != null
            )
        }
    }
    
    private suspend fun broadcastBlockchair(rawTx: String, chain: String): String {
        val response = httpClient.post("https://api.blockchair.com/$chain/push/transaction") {
            setBody(mapOf("data" to rawTx))
        }
        
        val result: BlockchairPushResponse = response.body()
        return result.data.transactionHash
    }
    
    private suspend fun getBlockchairTransactionHistory(
        address: String,
        chain: String,
        limit: Int,
        offset: Int
    ): List<UTXOTransaction> {
        val response = httpClient.get("https://api.blockchair.com/$chain/dashboards/address/$address?limit=$limit&offset=$offset")
        val data: BlockchairAddressResponse = response.body()
        
        return data.data[address]?.transactions?.map { txHash ->
            // Fetch each transaction detail
            getBlockchairTransaction(txHash, chain)
        } ?: emptyList()
    }
    
    private suspend fun getBlockchairTransaction(txId: String, chain: String): UTXOTransaction {
        val response = httpClient.get("https://api.blockchair.com/$chain/dashboards/transaction/$txId")
        val data: BlockchairTransactionResponse = response.body()
        
        val tx = data.data[txId]?.transaction ?: throw Exception("Transaction not found")
        
        return UTXOTransaction(
            txId = tx.hash,
            blockHeight = tx.blockId?.toLong(),
            timestamp = tx.time?.let { Instant.parse(it) },
            inputs = data.data[txId]?.inputs?.map { input ->
                UTXOInput(
                    txId = input.transactionHash ?: "",
                    vout = input.index ?: 0,
                    scriptSig = input.scriptHex ?: "",
                    sequence = input.sequence ?: 0,
                    address = input.recipient,
                    value = input.value
                )
            } ?: emptyList(),
            outputs = data.data[txId]?.outputs?.mapIndexed { index, output ->
                UTXOOutput(
                    index = index,
                    value = output.value,
                    scriptPubKey = output.scriptHex ?: "",
                    address = output.recipient,
                    spent = output.isSpent ?: false
                )
            } ?: emptyList(),
            fee = tx.fee ?: 0L,
            size = tx.size ?: 0,
            weight = tx.weight,
            confirmations = if (tx.blockId != null) {
                val currentHeight = getBlockchairHeight(chain)
                (currentHeight - tx.blockId).toInt()
            } else 0,
            status = if (tx.blockId != null) TransactionStatus.CONFIRMED else TransactionStatus.PENDING,
            chainType = ChainType.BITCOIN_CASH
        )
    }
    
    private suspend fun getBlockchairHeight(chain: String): Long {
        return try {
            val response = httpClient.get("https://api.blockchair.com/$chain/stats")
            val stats: BlockchairStats = response.body()
            stats.data.blocks?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    // ===== Chain-specific fee estimates =====
    
    private suspend fun getLitecoinFeeEstimate(priority: FeePriority): Long {
        // Litecoin 通常使用較低的手續費
        return when (priority) {
            FeePriority.SLOW -> 1L
            FeePriority.NORMAL -> 2L
            FeePriority.FAST -> 5L
            FeePriority.URGENT -> 10L
        }
    }
    
    private suspend fun getDogecoinFeeEstimate(priority: FeePriority): Long {
        // Dogecoin 手續費以 DOGE 計算，通常較高的數值
        return when (priority) {
            FeePriority.SLOW -> 100L
            FeePriority.NORMAL -> 500L
            FeePriority.FAST -> 1000L
            FeePriority.URGENT -> 5000L
        }
    }
    
    private suspend fun getBitcoinCashFeeEstimate(priority: FeePriority): Long {
        // Bitcoin Cash 手續費極低
        return when (priority) {
            FeePriority.SLOW -> 1L
            FeePriority.NORMAL -> 2L
            FeePriority.FAST -> 5L
            FeePriority.URGENT -> 10L
        }
    }
    
    enum class FeePriority {
        SLOW,    // ~6 hours
        NORMAL,  // ~1 hour
        FAST,    // ~10 minutes
        URGENT   // Next block
    }
}

// ===== API Response Models =====

@Serializable
data class UTXOBlockstreamAddress(
    @SerialName("chain_stats") val chainStats: UTXOChainStats,
    @SerialName("mempool_stats") val mempoolStats: UTXOChainStats
)

@Serializable
data class UTXOChainStats(
    @SerialName("funded_txo_sum") val fundedTxoSum: Long,
    @SerialName("spent_txo_sum") val spentTxoSum: Long
)

@Serializable
data class UTXOBlockstreamUTXO(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: UTXOBlockstreamStatus
)

@Serializable
data class UTXOBlockstreamStatus(
    val confirmed: Boolean,
    @SerialName("block_height") val blockHeight: Int? = null
)

@Serializable
data class MempoolFees(
    val fastestFee: Int,
    val halfHourFee: Int,
    val hourFee: Int,
    val economyFee: Int,
    val minimumFee: Int
)

@Serializable
data class UTXOBlockCypherBalance(
    val balance: Long,
    @SerialName("unconfirmed_balance") val unconfirmedBalance: Long
)

@Serializable
data class UTXOBlockCypherAddress(
    val balance: Long,
    val txrefs: List<UTXOBlockCypherTxRef>? = null
)

@Serializable
data class UTXOBlockCypherTxRef(
    @SerialName("tx_hash") val txHash: String,
    @SerialName("tx_output_n") val txOutputN: Int,
    val value: Long,
    val script: String? = null,
    val confirmations: Int
)

@Serializable
data class UTXOBlockCypherPushTx(
    val tx: String
)

@Serializable
data class UTXOBlockCypherTxResponse(
    val tx: UTXOBlockCypherTx
)

@Serializable
data class UTXOBlockCypherTx(
    val hash: String
)

@Serializable
data class BlockchairResponse(
    val data: Map<String, BlockchairAddressData>
)

@Serializable
data class BlockchairAddressData(
    val address: BlockchairAddressInfo
)

@Serializable
data class BlockchairAddressInfo(
    val balance: Long
)

@Serializable
data class BlockchairUTXOResponse(
    val data: List<BlockchairUTXO>
)

@Serializable
data class BlockchairUTXO(
    @SerialName("transaction_hash") val transactionHash: String,
    val index: Int,
    val value: Long,
    @SerialName("script_hex") val scriptHex: String? = null,
    @SerialName("block_id") val blockId: Int? = null
)

@Serializable
data class BlockchairPushResponse(
    val data: BlockchairPushData
)

@Serializable
data class BlockchairPushData(
    @SerialName("transaction_hash") val transactionHash: String
)

// ===== Blockstream Transaction Models =====

@Serializable
data class UTXOBlockstreamTransaction(
    val txid: String,
    val version: Int,
    val locktime: Long,
    val vin: List<UTXOBlockstreamInput>,
    val vout: List<UTXOBlockstreamOutput>,
    val size: Int,
    val weight: Int? = null,
    val fee: Long? = null,
    val status: UTXOBlockstreamTransactionStatus
)

@Serializable
data class UTXOBlockstreamInput(
    val txid: String? = null,
    val vout: Int? = null,
    val scriptsig: String? = null,
    val sequence: Long? = null,
    val prevout: UTXOBlockstreamPrevout? = null,
    @SerialName("is_coinbase") val isCoinbase: Boolean? = null
)

@Serializable
data class UTXOBlockstreamPrevout(
    val value: Long? = null,
    val scriptpubkey: String? = null,
    @SerialName("scriptpubkey_address") val scriptpubkeyAddress: String? = null
)

@Serializable
data class UTXOBlockstreamOutput(
    val value: Long,
    val scriptpubkey: String? = null,
    @SerialName("scriptpubkey_address") val scriptpubkeyAddress: String? = null
)

@Serializable
data class UTXOBlockstreamTransactionStatus(
    val confirmed: Boolean,
    @SerialName("block_height") val blockHeight: Int? = null,
    @SerialName("block_time") val blockTime: Long? = null
)

// ===== BlockCypher Transaction Models =====

@Serializable
data class UTXOBlockCypherFullAddress(
    val address: String,
    val balance: Long,
    @SerialName("unconfirmed_balance") val unconfirmedBalance: Long,
    val txs: List<UTXOBlockCypherTransaction>
)

@Serializable
data class UTXOBlockCypherTransaction(
    val hash: String,
    val total: Long,
    val fees: Long? = null,
    val size: Int,
    val vsize: Int? = null,
    val preference: String? = null,
    val received: String? = null,
    val confirmed: String? = null,
    val confirmations: Int,
    @SerialName("block_height") val blockHeight: Int? = null,
    val inputs: List<UTXOBlockCypherInput>,
    val outputs: List<UTXOBlockCypherOutput>
)

@Serializable
data class UTXOBlockCypherInput(
    @SerialName("prev_hash") val prevHash: String? = null,
    @SerialName("output_index") val outputIndex: Int? = null,
    val script: String? = null,
    @SerialName("output_value") val outputValue: Long? = null,
    val sequence: Long? = null,
    val addresses: List<String>? = null
)

@Serializable
data class UTXOBlockCypherOutput(
    val value: Long,
    val script: String? = null,
    val addresses: List<String>? = null,
    @SerialName("spent_by") val spentBy: String? = null
)

// ===== Blockchair Extended Models =====

@Serializable
data class BlockchairAddressResponse(
    val data: Map<String, BlockchairAddressDetailData>
)

@Serializable
data class BlockchairAddressDetailData(
    val address: BlockchairAddressInfo,
    val transactions: List<String>? = null
)

@Serializable
data class BlockchairTransactionResponse(
    val data: Map<String, BlockchairTransactionData>
)

@Serializable
data class BlockchairTransactionData(
    val transaction: BlockchairTransactionDetail,
    val inputs: List<BlockchairTransactionIO>? = null,
    val outputs: List<BlockchairTransactionIO>? = null
)

@Serializable
data class BlockchairTransactionDetail(
    val hash: String,
    @SerialName("block_id") val blockId: Int? = null,
    val time: String? = null,
    val size: Int? = null,
    val weight: Int? = null,
    val fee: Long? = null
)

@Serializable
data class BlockchairTransactionIO(
    @SerialName("transaction_hash") val transactionHash: String? = null,
    val index: Int? = null,
    val value: Long,
    val recipient: String? = null,
    @SerialName("script_hex") val scriptHex: String? = null,
    @SerialName("is_spent") val isSpent: Boolean? = null,
    val sequence: Long? = null
)

@Serializable
data class BlockchairStats(
    val data: BlockchairStatsData
)

@Serializable
data class BlockchairStatsData(
    val blocks: Int? = null
)
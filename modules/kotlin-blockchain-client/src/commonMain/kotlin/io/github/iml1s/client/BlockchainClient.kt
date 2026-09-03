package io.github.iml1s.client

import kotlinx.serialization.Serializable

/**
 * Standardized blockhain transaction details
 */
@Serializable
data class ChainTransaction(
    val txid: String,
    val blockHeight: Long?,
    val timestamp: Long?,
    val fee: Long?,
    val confirmed: Boolean
)

/**
 * Standardized Unspent Transaction Output
 */
@Serializable
data class ChainUTXO(
    val txid: String,
    val vout: Int,
    val value: Long,
    val scriptPubKey: String? = null
)

/**
 * Recommended Fee Rates (in sat/vB or wei/gas)
 */
@Serializable
data class FeeRates(
    val fast: Long,
    val average: Long,
    val slow: Long
)

interface BlockchainClient {
    suspend fun getBalance(address: String): Long
    suspend fun getUTXOs(address: String): List<ChainUTXO>
    suspend fun getTransactions(address: String): List<ChainTransaction>
    suspend fun broadcastTransaction(rawTxHex: String): String
    suspend fun getFeeRates(): FeeRates
}

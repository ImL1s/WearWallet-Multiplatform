package io.github.iml1s.client.bitcoin

import io.github.iml1s.client.BlockchainClient
import io.github.iml1s.client.ChainTransaction
import io.github.iml1s.client.ChainUTXO
import io.github.iml1s.client.FeeRates
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bitcoin Client Implementation using Mempool.space REST API
 */
class MempoolSpaceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://mempool.space/api"
) : BlockchainClient {

    @Serializable
    private data class MempoolUTXO(
        val txid: String,
        val vout: Int,
        val value: Long,
        val status: Status
    )

    @Serializable
    private data class Status(
        val confirmed: Boolean,
        val block_height: Long? = null,
        val block_time: Long? = null
    )
    
    @Serializable
    private data class MempoolTx(
        val txid: String,
        val status: Status,
        val fee: Long? = null
    )
    
    @Serializable
    private data class RecommendedFees(
        val fastestFee: Long,
        val halfHourFee: Long,
        val hourFee: Long,
        val economyFee: Long,
        val minimumFee: Long
    )

    override suspend fun getBalance(address: String): Long {
        val utxos = getUTXOs(address)
        return utxos.sumOf { it.value }
    }

    override suspend fun getUTXOs(address: String): List<ChainUTXO> {
        val response = httpClient.get("$baseUrl/address/$address/utxo").body<List<MempoolUTXO>>()
        return response.map { 
            ChainUTXO(
                txid = it.txid,
                vout = it.vout,
                value = it.value
            )
        }
    }

    override suspend fun getTransactions(address: String): List<ChainTransaction> {
        val response = httpClient.get("$baseUrl/address/$address/txs").body<List<MempoolTx>>()
        return response.map {
            ChainTransaction(
                txid = it.txid,
                blockHeight = it.status.block_height,
                timestamp = it.status.block_time,
                fee = it.fee,
                confirmed = it.status.confirmed
            )
        }
    }

    override suspend fun broadcastTransaction(rawTxHex: String): String {
        // Mempool.space returns txid string on success
        return httpClient.post("$baseUrl/tx") {
            setBody(rawTxHex)
        }.body()
    }

    override suspend fun getFeeRates(): FeeRates {
        val fees = httpClient.get("$baseUrl/v1/fees/recommended").body<RecommendedFees>()
        return FeeRates(
            fast = fees.fastestFee,
            average = fees.halfHourFee,
            slow = fees.hourFee
        )
    }
}

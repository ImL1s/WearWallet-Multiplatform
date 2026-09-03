package com.cbstudio.wearwallet.core.data.repository

import com.cbstudio.wearwallet.core.domain.model.Transaction
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import com.cbstudio.wearwallet.core.domain.model.TransactionRequest
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.network.ApiConfig
import com.cbstudio.wearwallet.core.blockchain.explorer.BlockExplorerService
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.security.CryptoProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.blockchain.model.UTXOTransaction
import com.cbstudio.wearwallet.core.blockchain.model.TransactionStatus as UTXOTransactionStatus

import com.cbstudio.wearwallet.core.security.SideEffectTracker
import com.cbstudio.wearwallet.core.security.GlobalSideEffectTracker

/**
 * Common implementation of TransactionRepository
 * Handles tracking, sending, and history of transactions for both EVM and UTXO chains.
 *
 * Integrates BlockExplorerService for real transaction history.
 */
class TransactionRepositoryImpl(
    private val cryptoProvider: CryptoProvider,
    private val rpcClient: EthereumRpcClient,
    private val httpClient: HttpClient,
    private val utxoApiClient: UTXOApiClient,
    private val sideEffectTracker: SideEffectTracker = GlobalSideEffectTracker.instance
) : TransactionRepository {

    private val blockExplorerService = BlockExplorerService()

    // Local cache (5 min expiry)
    private val transactionCache = mutableMapOf<String, CachedTransactions>()
    private val cacheExpiryMs = 5 * 60 * 1000L // 5 minutes

    private data class CachedTransactions(
        val transactions: List<Transaction>,
        val timestamp: Long
    )
    
    override suspend fun sendTransaction(
        signedTransaction: String,
        chainType: ChainType
    ): String {
        sideEffectTracker.onBroadcast()
        val result = rpcClient.sendRawTransaction(signedTransaction, chainType)
        return when (result) {
            is Result.Success -> result.data
            is Result.Failure -> throw result.error
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }

    override suspend fun sendTransaction(
        signedTransaction: String,
        context: ChainExecutionContext
    ): String {
        sideEffectTracker.onBroadcast()
        val result = rpcClient.sendRawTransaction(signedTransaction, context)
        return when (result) {
            is Result.Success -> result.data
            is Result.Failure -> throw result.error
            is Result.Loading -> throw Exception("Unexpected loading state")
        }
    }
    
    override suspend fun getTransactionHistory(
        walletAddress: String,
        chainType: ChainType
    ): List<Transaction> {
        val isUTXOChain = chainType in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )

        if (isUTXOChain) {
            println("ℹ️ UTXO Chain ${chainType.name} history via UTXOApiClient")
            try {
                val history = utxoApiClient.getTransactionHistory(walletAddress, chainType)
                println("✅ UTXO history success: ${history.size} txs (${chainType.name})")
                
                return history.map { it.toTransaction(walletAddress) }
            } catch (e: Exception) {
                println("❌ UTXO history failed: ${e.message}")
                e.printStackTrace()
                return emptyList()
            }
        }

        val cacheKey = "$walletAddress-${chainType.name}"
        val cached = transactionCache[cacheKey]
        val now = Clock.System.now().toEpochMilliseconds()

        if (cached != null && (now - cached.timestamp) < cacheExpiryMs) {
            println("📦 Using cached transactions: ${cached.transactions.size}")
            return cached.transactions
        }

        return try {
            val chainId = getChainId(chainType)
            println("🔍 Fetching history from ${chainType.name} explorer...")

            val result = blockExplorerService.getTransactionHistory(
                address = walletAddress,
                chainId = chainId,
                page = 1,
                pageSize = 50,
                sort = "desc"
            )

            when (result) {
                is Result.Success -> {
                    val transactions = result.data
                    transactionCache[cacheKey] = CachedTransactions(transactions, now)
                    println("✅ Successfully fetched ${transactions.size} txs")
                    transactions
                }
                is Result.Failure -> {
                    println("❌ Failed to fetch history: ${result.error.message}")
                    cached?.transactions ?: emptyList()
                }
                is Result.Loading -> {
                    cached?.transactions ?: emptyList()
                }
            }
        } catch (e: Exception) {
            println("❌ History fetch exception: ${e.message}")
            cached?.transactions ?: emptyList()
        }
    }

    private fun getChainId(chainType: ChainType): String {
        return when (chainType) {
            ChainType.ETHEREUM -> "1"
            ChainType.BSC -> "56"
            ChainType.POLYGON -> "137"
            ChainType.ARBITRUM -> "42161"
            ChainType.OPTIMISM -> "10"
            ChainType.AVALANCHE -> "43114"
            ChainType.FANTOM -> "250"
            ChainType.CRONOS -> "25"
            ChainType.BASE -> "8453"
            ChainType.GNOSIS -> "100"
            ChainType.MOONBEAM -> "1284"
            ChainType.CELO -> "42220"
            ChainType.LINEA -> "59144"
            ChainType.ZKSYNC -> "324"
            else -> "1"
        }
    }
    
    override suspend fun getTransaction(
        hash: String,
        chainType: ChainType
    ): Transaction? {
        // Simplified implementation
        return Transaction(
            hash = hash,
            from = "0x...",
            to = "0x...",
            value = "0",
            chainType = chainType,
            status = TransactionStatus.PENDING,
            timestamp = Clock.System.now(),
            nonce = 0L
        )
    }
    
    override suspend fun estimateGas(request: TransactionRequest): String {
        val isUTXOChain = request.chainType in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        if (isUTXOChain) {
            return "0"
        }
        
        val rpcUrl = if (request.executionContext != null) {
            ApiConfig.getRpcUrl(request.executionContext)
        } else {
            ApiConfig.getRpcUrl(request.chainType)
        }
        val jsonRequest = JsonRpcRequest(
            method = "eth_estimateGas",
            params = buildJsonArray {
                add(buildJsonObject {
                    put("from", JsonPrimitive(request.from))
                    put("to", JsonPrimitive(request.to))
                    put("value", JsonPrimitive(amountToHex(request.value)))
                    request.data?.let {
                        put("data", JsonPrimitive(it))
                    }
                })
            },
            id = 1
        )
        
        return try {
            val response = httpClient.post(rpcUrl) {
                contentType(ContentType.Application.Json)
                setBody(jsonRequest)
            }
            
            val jsonResponse = response.body<JsonRpcResponse>()
            if (jsonResponse.error != null) {
                throw IllegalStateException("eth_estimateGas RPC error: ${jsonResponse.error.message}")
            }
            val gasHex = jsonResponse.result?.jsonPrimitive?.content
                ?: throw IllegalStateException("eth_estimateGas RPC returned empty result")
            hexToDecimal(gasHex)
        } catch (e: Exception) {
            throw IllegalStateException("eth_estimateGas RPC failed: ${e.message}", e)
        }
    }
    
    override suspend fun getNonce(
        walletAddress: String,
        chainType: ChainType
    ): Long {
        val isUTXOChain = chainType in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        if (isUTXOChain) {
            return 0L
        }
        
        val result = rpcClient.getNonce(walletAddress, chainType)
        return when (result) {
            is Result.Success -> result.data
            is Result.Failure -> throw IllegalStateException("eth_getTransactionCount (getNonce) RPC failed: ${result.exception.message}", result.exception)
            is Result.Loading -> throw IllegalStateException("eth_getTransactionCount (getNonce) RPC loading timed out")
        }
    }

    override suspend fun getNonce(
        walletAddress: String,
        context: ChainExecutionContext
    ): Long {
        val isUTXOChain = context.chain in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        if (isUTXOChain) {
            return 0L
        }
        
        val result = rpcClient.getNonce(walletAddress, context)
        return when (result) {
            is Result.Success -> result.data
            is Result.Failure -> throw IllegalStateException("eth_getTransactionCount (getNonce) RPC failed: ${result.exception.message}", result.exception)
            is Result.Loading -> throw IllegalStateException("eth_getTransactionCount (getNonce) RPC loading timed out")
        }
    }
    
    override suspend fun buildTransaction(request: TransactionRequest): String {
        val nonce = request.nonce ?: getNonce(request.from, request.chainType)
        val gasPrice = request.gasPrice ?: getGasPrice(request.chainType)
        val gasLimit = request.gasLimit ?: estimateGas(request)
        
        val nonceObj = Nonce.fromLong(nonce)
        val gasPriceObj = if (gasPrice.startsWith("0x", ignoreCase = true)) {
            com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromWeiHex(gasPrice)
        } else {
            com.cbstudio.wearwallet.core.domain.model.quantities.Wei.fromWeiDecimal(gasPrice)
        }
        val gasLimitObj = if (gasLimit.startsWith("0x", ignoreCase = true)) {
            GasLimit.fromHex(gasLimit)
        } else {
            GasLimit.fromDecimalString(gasLimit)
        }
        val valueHex = amountToHex(request.value)
        val dataHex = request.data?.let { d ->
            val clean = if (d.startsWith("0x", ignoreCase = true)) d else "0x$d"
            require(clean.removePrefix("0x").length % 2 == 0) { "Transaction data must have even hex length" }
            clean
        } ?: "0x"

        return buildJsonObject {
            put("from", JsonPrimitive(request.from))
            put("to", JsonPrimitive(request.to))
            put("value", JsonPrimitive(valueHex))
            put("nonce", JsonPrimitive(nonceObj.toHex()))
            put("gasPrice", JsonPrimitive(gasPriceObj.toHex()))
            put("gasLimit", JsonPrimitive(gasLimitObj.toHex()))
            put("chainId", JsonPrimitive(ApiConfig.getChainId(request.chainType)))
            put("data", JsonPrimitive(dataHex))
        }.toString()
    }
    
    override fun observeTransactions(walletAddress: String): Flow<List<Transaction>> {
        return flow {
            val transactions = getTransactionHistory(walletAddress, ChainType.ETHEREUM)
            emit(transactions)
        }
    }

    override suspend fun getGasPrice(context: ChainExecutionContext): String {
        val isUTXOChain = context.chain in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        if (isUTXOChain) {
            return "0x0"
        }
        
        val result = rpcClient.getGasPrice(context)
        return when (result) {
            is Result.Success -> result.data
            is Result.Failure -> throw IllegalStateException("eth_gasPrice RPC failed: ${result.exception.message}", result.exception)
            is Result.Loading -> throw IllegalStateException("eth_gasPrice RPC loading timed out")
        }
    }
    
    override suspend fun getGasPrice(chainType: ChainType): String {
        val isUTXOChain = chainType in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        if (isUTXOChain) {
            return "0x0"
        }
        
        val result = rpcClient.getGasPrice(chainType)
        return when (result) {
            is Result.Success -> result.data
            is Result.Failure -> throw IllegalStateException("eth_gasPrice RPC failed: ${result.exception.message}", result.exception)
            is Result.Loading -> throw IllegalStateException("eth_gasPrice RPC loading timed out")
        }
    }
    
    private fun amountToHex(amount: String): String {
        if (amount.startsWith("0x", ignoreCase = true)) {
            return amount
        }
        return BaseUnitAmount.fromDecimalString(amount, 18).toHex()
    }
    
    private fun hexToDecimal(hex: String): String {
        return try {
            val cleanHex = hex.removePrefix("0x")
            if (cleanHex.isEmpty() || cleanHex == "0") {
                "0"
            } else {
                cleanHex.toLong(16).toString()
            }
        } catch (e: Exception) {
            "0"
        }
    }
}

// JSON-RPC Models
@kotlinx.serialization.Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonArray,
    val id: Int
)

@kotlinx.serialization.Serializable
data class JsonRpcResponse(
    val jsonrpc: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null
)

@kotlinx.serialization.Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

private fun UTXOTransaction.toTransaction(walletAddress: String): Transaction {
    val isReceive = this.outputs.any { it.address == walletAddress }
    val isSend = this.inputs.any { it.address == walletAddress }
    
    val direction = when {
        isReceive && isSend -> {
            val hasExternalOutput = this.outputs.any { it.address != walletAddress }
            if (hasExternalOutput) {
                com.cbstudio.wearwallet.core.domain.model.TransactionDirection.OUTGOING
            } else {
                com.cbstudio.wearwallet.core.domain.model.TransactionDirection.SELF
            }
        }
        isReceive -> com.cbstudio.wearwallet.core.domain.model.TransactionDirection.INCOMING
        else -> com.cbstudio.wearwallet.core.domain.model.TransactionDirection.OUTGOING
    }

    val type = com.cbstudio.wearwallet.core.domain.model.TransactionType.TRANSFER

    val amount = when (direction) {
        com.cbstudio.wearwallet.core.domain.model.TransactionDirection.INCOMING -> {
            this.outputs
                .filter { it.address == walletAddress }
                .sumOf { it.value }
        }
        com.cbstudio.wearwallet.core.domain.model.TransactionDirection.OUTGOING -> {
            val inputSum = this.inputs
                .filter { it.address == walletAddress }
                .sumOf { it.value ?: 0L }
            val outputSum = this.outputs
                .filter { it.address == walletAddress }
                .sumOf { it.value }
            
            if (inputSum > 0) {
                inputSum - outputSum - this.fee
            } else {
                this.outputs
                    .filter { it.address != walletAddress }
                    .sumOf { it.value }
            }
        }
        com.cbstudio.wearwallet.core.domain.model.TransactionDirection.SELF -> {
            this.fee
        }
        else -> 0L
    }

    val otherAddress = if (direction == com.cbstudio.wearwallet.core.domain.model.TransactionDirection.INCOMING) {
        this.inputs.firstOrNull()?.address ?: "Unknown"
    } else {
        this.outputs.firstOrNull { it.address != walletAddress }?.address
            ?: this.outputs.firstOrNull()?.address
            ?: "Unknown"
    }

    val displayFrom = if (direction == com.cbstudio.wearwallet.core.domain.model.TransactionDirection.INCOMING) otherAddress else walletAddress
    val displayTo = if (direction == com.cbstudio.wearwallet.core.domain.model.TransactionDirection.OUTGOING) otherAddress else walletAddress

    val txStatus = when (this.status) {
        UTXOTransactionStatus.PENDING -> TransactionStatus.PENDING
        UTXOTransactionStatus.CONFIRMED -> TransactionStatus.CONFIRMED
        UTXOTransactionStatus.FAILED -> TransactionStatus.FAILED
        UTXOTransactionStatus.REPLACED -> TransactionStatus.REPLACED
    }

    val txTimestamp = this.timestamp ?: Clock.System.now()

    return Transaction(
        hash = this.txId,
        from = displayFrom,
        to = displayTo,
        value = amount.toString(),
        chainType = this.chainType,
        status = txStatus,
        type = type,
        direction = direction,
        timestamp = txTimestamp,
        createdAt = txTimestamp,
        confirmedAt = if (txStatus == TransactionStatus.CONFIRMED) txTimestamp else null,
        confirmations = this.confirmations,
        networkFee = this.fee.toString(),
        gasPrice = null,
        gasLimit = null,
        gasUsed = null,
        nonce = 0,
        tokenDecimals = when (this.chainType) {
            ChainType.BITCOIN, ChainType.BITCOIN_CASH -> 8
            ChainType.LITECOIN -> 8
            ChainType.DOGECOIN -> 8
            else -> 18
        },
        tokenSymbol = this.chainType.nativeToken
    )
}

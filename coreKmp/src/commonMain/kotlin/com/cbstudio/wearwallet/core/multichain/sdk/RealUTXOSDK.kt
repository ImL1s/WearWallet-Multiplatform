package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UTXOInput
import com.cbstudio.wearwallet.core.blockchain.signer.BitcoinSigner
import com.cbstudio.wearwallet.core.blockchain.signer.LitecoinSigner
import com.cbstudio.wearwallet.core.blockchain.signer.DogecoinSigner
import com.cbstudio.wearwallet.core.blockchain.signer.BitcoinCashSigner
import com.cbstudio.wearwallet.core.blockchain.signer.SignedTransaction as SignerSignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction as BlockchainUnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.utxo.UTXOSelector
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.datetime.Clock

/**
 * UTXO 區塊鏈 SDK 實作基礎類別
 * 支援 Bitcoin, Litecoin, Dogecoin, Bitcoin Cash
 */
abstract class BaseUTXOSDK(
    protected val utxoChainType: ChainType,
    override val chainType: MultiChainType
) : BlockchainSDKAdapter {
    
    override val sdkVersion: String = "1.0.0"
    override val capabilities: Set<SDKCapability> = setOf(
        SDKCapability.BALANCE_QUERY,
        SDKCapability.TRANSACTION_HISTORY,
        SDKCapability.TRANSACTION_CREATION,
        SDKCapability.TRANSACTION_SIGNING,
        SDKCapability.TRANSACTION_BROADCAST,
        SDKCapability.ADDRESS_VALIDATION,
        SDKCapability.OFFLINE_SIGNING
    )
    
    protected val apiClient = UTXOApiClient()
    protected var config: SDKConfig? = null
    protected var _isInitialized = false
    
    override suspend fun initialize(config: SDKConfig): Result<Unit> {
        return try {
            this.config = config
            _isInitialized = true
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override fun isInitialized(): Boolean = _isInitialized
    
    override suspend fun getAccountBalance(address: String): Result<Balance> {
        return try {
            val balance = apiClient.getBalance(address, utxoChainType)
            Result.Success(
                Balance(
                    amount = satoshiToString(balance),
                    decimals = 8,
                    symbol = getSymbol(),
                    usdValue = null
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun createTransaction(request: TransactionRequest): Result<UnsignedTransaction> {
        return try {
            // 獲取 UTXOs
            val utxos = apiClient.getUTXOs(request.fromAddress, utxoChainType)
            if (utxos.isEmpty()) {
                return Result.Failure(Exception("沒有可用的 UTXO"))
            }
            
            // 轉換金額為 satoshi
            val amountSatoshi = stringToSatoshi(request.amount)
            
            // 估算手續費
            val feePerByte = apiClient.getFeeEstimate(
                utxoChainType, 
                UTXOApiClient.FeePriority.NORMAL
            )
            
            // 選擇 UTXOs（簡單選擇策略）
            val selectedUtxos = selectUTXOs(utxos, amountSatoshi, feePerByte)
            
            // 計算總輸入和找零
            val totalInput = selectedUtxos.sumOf { it.value }
            val estimatedFee = estimateTransactionSize(selectedUtxos.size, 2) * feePerByte
            val change = totalInput - amountSatoshi - estimatedFee
            
            if (change < 0) {
                return Result.Failure(Exception("餘額不足"))
            }
            
            // 創建未簽名交易
            val unsignedTx = UnsignedTransaction(
                rawData = createRawTransaction(
                    selectedUtxos,
                    request.toAddress,
                    amountSatoshi,
                    request.fromAddress,
                    change
                ),
                chainType = chainType,
                estimatedFee = TransactionFee(
                    gasLimit = estimatedFee.toString(),
                    gasPrice = feePerByte.toString(),
                    estimatedCost = satoshiToString(estimatedFee),
                    usdValue = null,
                    priority = request.priority
                ),
                expirationTime = null,
                metadata = mapOf(
                    "utxos" to selectedUtxos,
                    "fee" to estimatedFee,
                    "change" to change,
                    "fromAddress" to request.fromAddress,
                    "toAddress" to request.toAddress,
                    "amount" to amountSatoshi
                )
            )
            
            Result.Success(unsignedTx)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun signTransaction(
        unsignedTransaction: UnsignedTransaction,
        privateKey: String
    ): Result<SignedTransaction> {
        return try {
            // 現在直接使用具體的簽名器，由子類實現
            val signedTx = signTransactionInternal(unsignedTransaction, privateKey)
            Result.Success(signedTx)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun broadcastTransaction(
        signedTx: SignedTransaction
    ): Result<TransactionResult> {
        return try {
            val txHash = apiClient.broadcastTransaction(
                signedTx.rawData,
                utxoChainType
            )
            
            Result.Success(
                TransactionResult(
                    hash = txHash,
                    status = TransactionStatus.PENDING,
                    blockNumber = null,
                    gasUsed = null,
                    message = "Transaction broadcasted successfully"
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getTransactionHistory(
        address: String,
        limit: Int,
        offset: Int
    ): Result<List<Transaction>> {
        return try {
            val txList = apiClient.getTransactionHistory(address, utxoChainType, limit, offset)
            val transactions = txList.map { tx ->
                // Calculate total amount from outputs for this address
                val totalAmount = tx.outputs
                    .filter { it.address == address }
                    .sumOf { it.value }
                
                Transaction(
                    hash = tx.txId,
                    fromAddress = address, // UTXO model doesn't have simple from/to
                    toAddress = tx.outputs.firstOrNull()?.address ?: address,
                    amount = satoshiToString(totalAmount),
                    fee = satoshiToString(tx.fee),
                    timestamp = tx.timestamp?.toEpochMilliseconds() ?: 0L,
                    blockNumber = tx.blockHeight,
                    status = when (tx.status) {
                        com.cbstudio.wearwallet.core.blockchain.model.TransactionStatus.CONFIRMED -> TransactionStatus.CONFIRMED
                        com.cbstudio.wearwallet.core.blockchain.model.TransactionStatus.PENDING -> TransactionStatus.PENDING
                        com.cbstudio.wearwallet.core.blockchain.model.TransactionStatus.FAILED -> TransactionStatus.FAILED
                        com.cbstudio.wearwallet.core.blockchain.model.TransactionStatus.REPLACED -> TransactionStatus.CANCELLED
                    },
                    memo = null
                )
            }
            Result.Success(transactions)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override fun validateAddress(address: String): Result<AddressValidation> {
        return try {
            val isValid = when (utxoChainType) {
                ChainType.BITCOIN -> validateBitcoinAddress(address)
                ChainType.LITECOIN -> validateLitecoinAddress(address)
                ChainType.DOGECOIN -> validateDogecoinAddress(address)
                ChainType.BITCOIN_CASH -> validateBitcoinCashAddress(address)
                else -> false
            }
            
            Result.Success(
                AddressValidation(
                    isValid = isValid,
                    addressType = if (isValid) AddressType.LEGACY else null,
                    networkMatches = true,
                    message = if (isValid) "有效地址" else "無效地址格式"
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun estimateTransactionFee(
        request: TransactionRequest
    ): Result<TransactionFee> {
        return try {
            val feePerByte = apiClient.getFeeEstimate(
                utxoChainType,
                UTXOApiClient.FeePriority.NORMAL
            )
            
            // 估算交易大小（1 輸入 + 2 輸出的標準交易）
            val estimatedSize = estimateTransactionSize(1, 2)
            val estimatedFee = estimatedSize * feePerByte
            
            Result.Success(
                TransactionFee(
                    estimatedCost = satoshiToString(estimatedFee),
                    gasLimit = estimatedSize.toString(),
                    gasPrice = feePerByte.toString(),
                    priority = TransactionPriority.NORMAL
                )
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    override suspend fun getNetworkStatus(): Result<NetworkStatus> {
        return try {
            // For now, return a simple status
            Result.Success(
                NetworkStatus(
                    isConnected = true,
                    blockHeight = 800000, // Example block height
                    networkId = utxoChainType.name
                )
            )
        } catch (e: Exception) {
            Result.Success(
                NetworkStatus(
                    isConnected = false,
                    blockHeight = 0,
                    networkId = utxoChainType.name
                )
            )
        }
    }
    
    // ===== 抽象方法 =====
    
    abstract suspend fun signTransactionInternal(
        unsignedTx: UnsignedTransaction,
        privateKey: String
    ): SignedTransaction
    abstract fun getSymbol(): String
    
    // ===== 輔助方法 =====
    
    protected fun satoshiToString(satoshi: Long): String {
        val btc = satoshi / 100_000_000.0
        return btc.toString()
    }
    
    protected fun stringToSatoshi(amount: String): Long {
        return (amount.toDouble() * 100_000_000).toLong()
    }
    
    protected fun String.hexToByteArray(): ByteArray {
        val cleanHex = this.removePrefix("0x")
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    protected fun selectUTXOs(
        utxos: List<UTXO>,
        targetAmount: Long,
        feePerByte: Long
    ): List<UTXO> {
        // 簡單的 UTXO 選擇策略
        val sortedUtxos = utxos.sortedByDescending { it.value }
        val selected = mutableListOf<UTXO>()
        var totalValue = 0L
        
        for (utxo in sortedUtxos) {
            selected.add(utxo)
            totalValue += utxo.value
            
            // 估算當前選擇的手續費
            val estimatedFee = estimateTransactionSize(selected.size, 2) * feePerByte
            
            if (totalValue >= targetAmount + estimatedFee) {
                break
            }
        }
        
        return selected
    }
    
    protected fun estimateTransactionSize(inputCount: Int, outputCount: Int): Long {
        // P2PKH: ~148 bytes per input, ~34 bytes per output, ~10 bytes overhead
        // P2WPKH: ~68 bytes per input, ~31 bytes per output
        return when (utxoChainType) {
            ChainType.BITCOIN -> (10 + (inputCount * 68) + (outputCount * 31)).toLong()
            else -> (10 + (inputCount * 148) + (outputCount * 34)).toLong()
        }
    }
    
    protected fun createRawTransaction(
        utxos: List<UTXO>,
        toAddress: String,
        amount: Long,
        changeAddress: String,
        changeAmount: Long
    ): String {
        // 建構交易資料結構
        val txData = buildMap {
            put("version", 2)
            put("inputs", utxos.map { utxo ->
                mapOf(
                    "txid" to utxo.txid,
                    "vout" to utxo.vout,
                    "value" to utxo.value,
                    "scriptPubKey" to (utxo.scriptPubKey ?: "")
                )
            })
            put("outputs", listOf(
                mapOf(
                    "address" to toAddress,
                    "value" to amount
                ),
                if (changeAmount > 0) {
                    mapOf(
                        "address" to changeAddress,
                        "value" to changeAmount
                    )
                } else null
            ).filterNotNull())
            put("lockTime", 0)
        }
        
        // 將交易資料序列化為 JSON 字符串（實際應用中需要使用二進制格式）
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                txData.forEach { (key, value) ->
                    put(key, kotlinx.serialization.json.Json.encodeToJsonElement(
                        kotlinx.serialization.serializer<Any?>(),
                        value
                    ))
                }
            }
        )
    }
    
    protected fun validateBitcoinAddress(address: String): Boolean {
        return when {
            // P2PKH addresses start with 1
            address.startsWith("1") && address.length in 26..35 -> true
            // P2SH addresses start with 3
            address.startsWith("3") && address.length in 26..35 -> true
            // Bech32 addresses start with bc1
            address.startsWith("bc1") && address.length in 42..62 -> true
            // Testnet addresses
            address.startsWith("m") || address.startsWith("n") || 
            address.startsWith("2") || address.startsWith("tb1") -> true
            else -> false
        }
    }
    
    protected fun validateLitecoinAddress(address: String): Boolean {
        return when {
            // P2PKH addresses start with L
            address.startsWith("L") && address.length in 26..35 -> true
            // P2SH addresses start with M or 3
            (address.startsWith("M") || address.startsWith("3")) && address.length in 26..35 -> true
            // Bech32 addresses start with ltc1
            address.startsWith("ltc1") && address.length in 42..62 -> true
            else -> false
        }
    }
    
    protected fun validateDogecoinAddress(address: String): Boolean {
        return when {
            // P2PKH addresses start with D
            address.startsWith("D") && address.length in 26..35 -> true
            // P2SH addresses start with A or 9
            (address.startsWith("A") || address.startsWith("9")) && address.length in 26..35 -> true
            else -> false
        }
    }
    
    protected fun validateBitcoinCashAddress(address: String): Boolean {
        return when {
            // CashAddr format
            address.startsWith("bitcoincash:") -> true
            // Legacy format (same as Bitcoin)
            address.startsWith("1") || address.startsWith("3") -> true
            // Simple format without prefix
            address.startsWith("q") || address.startsWith("p") -> true
            else -> false
        }
    }
}

/**
 * Bitcoin SDK 實作
 */
class RealBitcoinSDK : BaseUTXOSDK(ChainType.BITCOIN, MultiChainType.BITCOIN) {
    private val signer = BitcoinSigner()
    
    override suspend fun signTransactionInternal(
        unsignedTx: UnsignedTransaction,
        privateKey: String
    ): SignedTransaction {
        // Convert SDK UnsignedTransaction to blockchain model UnsignedTransaction
        val fromAddress = unsignedTx.metadata["fromAddress"] as? String ?: ""
        val toAddress = unsignedTx.metadata["toAddress"] as? String ?: ""
        val amount = (unsignedTx.metadata["amount"] as? Long ?: 0L).toString()
        val fee = (unsignedTx.metadata["fee"] as? Long ?: 0L).toString()
        
        val blockchainUnsignedTx = BlockchainUnsignedTransaction(
            fromAddress = fromAddress,
            toAddress = toAddress,
            amount = amount,
            fee = fee,
            metadata = unsignedTx.metadata
        )
        
        val signedTx = signer.signTransaction(
            blockchainUnsignedTx,
            privateKey.hexToByteArray()
        )
        
        return SignedTransaction(
            rawData = signedTx.rawTransaction,
            signature = "", // UTXO transactions have signatures embedded in rawTransaction
            chainType = unsignedTx.chainType,
            hash = signedTx.hash
        )
    }
    
    override fun getSymbol(): String = "BTC"
    
    override suspend fun cleanup() {
        // Cleanup resources if needed
    }
}

/**
 * Litecoin SDK 實作
 */
class RealLitecoinSDK : BaseUTXOSDK(ChainType.LITECOIN, MultiChainType.LITECOIN) {
    private val signer = LitecoinSigner()
    
    override suspend fun signTransactionInternal(
        unsignedTx: UnsignedTransaction,
        privateKey: String
    ): SignedTransaction {
        // Convert SDK UnsignedTransaction to blockchain model UnsignedTransaction
        val fromAddress = unsignedTx.metadata["fromAddress"] as? String ?: ""
        val toAddress = unsignedTx.metadata["toAddress"] as? String ?: ""
        val amount = (unsignedTx.metadata["amount"] as? Long ?: 0L).toString()
        val fee = (unsignedTx.metadata["fee"] as? Long ?: 0L).toString()
        
        val blockchainUnsignedTx = BlockchainUnsignedTransaction(
            fromAddress = fromAddress,
            toAddress = toAddress,
            amount = amount,
            fee = fee,
            metadata = unsignedTx.metadata
        )
        
        val signedTx = signer.signTransaction(
            blockchainUnsignedTx,
            privateKey.hexToByteArray()
        )
        
        return SignedTransaction(
            rawData = signedTx.rawTransaction,
            signature = "", // UTXO transactions have signatures embedded in rawTransaction
            chainType = unsignedTx.chainType,
            hash = signedTx.hash
        )
    }
    
    override fun getSymbol(): String = "LTC"
    
    override suspend fun cleanup() {
        // Cleanup resources if needed
    }
}

/**
 * Dogecoin SDK 實作
 */
class RealDogecoinSDK : BaseUTXOSDK(ChainType.DOGECOIN, MultiChainType.DOGECOIN) {
    private val signer = DogecoinSigner()
    
    override suspend fun signTransactionInternal(
        unsignedTx: UnsignedTransaction,
        privateKey: String
    ): SignedTransaction {
        // Convert SDK UnsignedTransaction to blockchain model UnsignedTransaction
        val fromAddress = unsignedTx.metadata["fromAddress"] as? String ?: ""
        val toAddress = unsignedTx.metadata["toAddress"] as? String ?: ""
        val amount = (unsignedTx.metadata["amount"] as? Long ?: 0L).toString()
        val fee = (unsignedTx.metadata["fee"] as? Long ?: 0L).toString()
        
        val blockchainUnsignedTx = BlockchainUnsignedTransaction(
            fromAddress = fromAddress,
            toAddress = toAddress,
            amount = amount,
            fee = fee,
            metadata = unsignedTx.metadata
        )
        
        val signedTx = signer.signTransaction(
            blockchainUnsignedTx,
            privateKey.hexToByteArray()
        )
        
        return SignedTransaction(
            rawData = signedTx.rawTransaction,
            signature = "", // UTXO transactions have signatures embedded in rawTransaction
            chainType = unsignedTx.chainType,
            hash = signedTx.hash
        )
    }
    
    override fun getSymbol(): String = "DOGE"
    
    override suspend fun cleanup() {
        // Cleanup resources if needed
    }
}

/**
 * Bitcoin Cash SDK 實作
 */
class RealBitcoinCashSDK : BaseUTXOSDK(ChainType.BITCOIN_CASH, MultiChainType.BITCOIN_CASH) {
    private val signer = BitcoinCashSigner()
    
    override suspend fun signTransactionInternal(
        unsignedTx: UnsignedTransaction,
        privateKey: String
    ): SignedTransaction {
        // Convert SDK UnsignedTransaction to blockchain model UnsignedTransaction
        val fromAddress = unsignedTx.metadata["fromAddress"] as? String ?: ""
        val toAddress = unsignedTx.metadata["toAddress"] as? String ?: ""
        val amount = (unsignedTx.metadata["amount"] as? Long ?: 0L).toString()
        val fee = (unsignedTx.metadata["fee"] as? Long ?: 0L).toString()
        
        val blockchainUnsignedTx = BlockchainUnsignedTransaction(
            fromAddress = fromAddress,
            toAddress = toAddress,
            amount = amount,
            fee = fee,
            metadata = unsignedTx.metadata
        )
        
        val signedTx = signer.signTransaction(
            blockchainUnsignedTx,
            privateKey.hexToByteArray()
        )
        
        return SignedTransaction(
            rawData = signedTx.rawTransaction,
            signature = "", // UTXO transactions have signatures embedded in rawTransaction
            chainType = unsignedTx.chainType,
            hash = signedTx.hash
        )
    }
    
    override fun getSymbol(): String = "BCH"
    
    override suspend fun cleanup() {
        // Cleanup resources if needed
    }
}
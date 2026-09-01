package com.cbstudio.wearwallet.core.blockchain.adapter

import com.cbstudio.wearwallet.core.blockchain.api.BitcoinCashApiClient
import com.cbstudio.wearwallet.core.blockchain.model.BitcoinTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.model.SignedTransaction
import com.cbstudio.wearwallet.core.blockchain.utxo.UTXOSelector
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Network
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Bitcoin Cash 平台適配器
 * 基於 Bitcoin 架構，但有以下差異：
 * 1. 使用 CashAddr 地址格式
 * 2. 支援 32MB 區塊
 * 3. 使用 SIGHASH_FORKID 簽名
 * 4. CoinType = 145
 */
class BitcoinCashPlatformAdapter(
    override var currentNetwork: Network = Network.BITCOIN_CASH_MAINNET
) : ChainAdapter {
    
    private val apiClient by lazy {
        BitcoinCashApiClient(currentNetwork)
    }
    
    private val utxoSelector = UTXOSelector()
    
    override val chainType: ChainType = ChainType.BITCOIN_CASH
    
    override val supportedNetworks: List<Network> = listOf(
        Network.BITCOIN_CASH_MAINNET,
        Network.BITCOIN_CASH_TESTNET
    )
    
    companion object {
        // Bitcoin Cash 特定參數
        const val DUST_LIMIT = 546L  // satoshis
        const val MIN_FEE_RATE = 1L  // sat/byte
        const val DEFAULT_FEE_RATE = 2L  // sat/byte
        const val MAX_BLOCK_SIZE = 32_000_000  // 32MB
        
        // 派生路徑
        const val DERIVATION_PATH_MAINNET = "m/44'/145'/0'/0/0"  // BCH CoinType = 145
        const val DERIVATION_PATH_TESTNET = "m/44'/1'/0'/0/0"   // 測試網使用 Bitcoin testnet 路徑
        
        // 地址前綴
        const val CASHADDR_PREFIX_MAINNET = "bitcoincash"
        const val CASHADDR_PREFIX_TESTNET = "bchtest"
        const val CASHADDR_PREFIX_REGTEST = "bchreg"
    }
    
    /**
     * 生成 Bitcoin Cash 地址
     * 使用 CashAddr 格式
     */
    override suspend fun generateAddress(
        seed: ByteArray,
        derivationPath: String
    ): Address {
        // 簡化實現 - 實際需要使用 TrustWallet Core
        val prefix = when (currentNetwork) {
            Network.BITCOIN_CASH_MAINNET -> CASHADDR_PREFIX_MAINNET
            Network.BITCOIN_CASH_TESTNET -> CASHADDR_PREFIX_TESTNET
            else -> CASHADDR_PREFIX_MAINNET
        }
        
        // 模擬生成 CashAddr 格式地址
        val randomPart = (1..40).map { 
            "qpzry9x8gf2tvdw0s3jn54khce6mua7l"[kotlin.random.Random.nextInt(32)]
        }.joinToString("")
        
        return Address(
            value = "$prefix:q$randomPart",
            type = AddressType.CASHADDR
        )
    }
    
    /**
     * 獲取地址餘額
     */
    override suspend fun getBalance(address: String): Long {
        return try {
            // 將 CashAddr 轉換為 Legacy 格式（如果需要）
            val normalizedAddress = normalizeCashAddress(address)
            apiClient.getBalance(normalizedAddress)
        } catch (e: Exception) {
            println("Error getting Bitcoin Cash balance: ${e.message}")
            0L
        }
    }
    
    /**
     * 獲取交易歷史
     */
    override suspend fun getTransactionHistory(
        address: String,
        limit: Int
    ): List<BitcoinTransaction> {
        return try {
            val normalizedAddress = normalizeCashAddress(address)
            apiClient.getTransactions(normalizedAddress, limit)
        } catch (e: Exception) {
            println("Error getting Bitcoin Cash transactions: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 創建 Bitcoin Cash 交易
     */
    override suspend fun createTransaction(
        from: String,
        to: String,
        amount: Long,
        data: String? // BCH 不使用 data 欄位
    ): UnsignedTransaction {
        try {
            // 正規化地址
            val fromAddress = normalizeCashAddress(from)
            val toAddress = normalizeCashAddress(to)
            
            // 獲取 UTXOs
            val utxos = apiClient.getUtxos(fromAddress)
            if (utxos.isEmpty()) {
                throw Exception("No UTXOs available for address: $fromAddress")
            }
            
            // 獲取手續費估算
            val feeRate = apiClient.getFeeEstimate() ?: DEFAULT_FEE_RATE
            
            // 估算交易大小
            val estimatedSize = (utxos.size * 148) + (2 * 34) + 10
            val estimatedFee = maxOf(
                feeRate * estimatedSize,
                MIN_FEE_RATE * estimatedSize
            )
            
            // 選擇 UTXOs
            val requiredAmount = amount + estimatedFee
            val selection = utxoSelector.selectOptimal(
                utxos = utxos,
                targetAmount = amount,
                feeRate = feeRate
            )
            
            val totalInput = selection.selectedUTXOs.sumOf { it.value }
            if (totalInput < requiredAmount) {
                throw Exception("Insufficient balance. Required: $requiredAmount, Available: $totalInput")
            }
            
            // 計算找零
            val change = selection.change
            
            return UnsignedTransaction(
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount.toString(),
                gasPrice = feeRate.toString(),
                gasLimit = estimatedSize.toString(),
                nonce = "",
                data = buildBitcoinCashTransactionData(
                    selection.selectedUTXOs,
                    toAddress,
                    amount,
                    change,
                    fromAddress
                ),
                chainId = when (currentNetwork) {
                    Network.BITCOIN_CASH_MAINNET -> "bch"
                    Network.BITCOIN_CASH_TESTNET -> "bchtest"
                    else -> "bch"
                },
                fee = estimatedFee.toString()
            )
        } catch (e: Exception) {
            throw Exception("Failed to create Bitcoin Cash transaction: ${e.message}")
        }
    }
    
    /**
     * 簽名交易（平台特定實現）
     */
    suspend fun signTransaction(
        unsignedTx: UnsignedTransaction,
        privateKey: ByteArray
    ): SignedTransaction {
        // 簽名應該在平台特定代碼中實現
        // Bitcoin Cash 需要使用 SIGHASH_FORKID
        throw NotImplementedError("Signing should be implemented in platform-specific code")
    }
    
    /**
     * 廣播交易 (SignedTransaction)
     */
    suspend fun broadcastTransaction(
        signedTx: SignedTransaction
    ): String? {
        return try {
            apiClient.broadcastTransaction(signedTx.rawTransaction)
        } catch (e: Exception) {
            println("Error broadcasting Bitcoin Cash transaction: ${e.message}")
            null
        }
    }
    
    /**
     * 廣播交易（字串格式）
     */
    override suspend fun broadcastTransaction(signedTx: String): String? {
        return try {
            apiClient.broadcastTransaction(signedTx)
        } catch (e: Exception) {
            println("Error broadcasting Bitcoin Cash transaction: ${e.message}")
            null
        }
    }
    
    /**
     * 驗證地址格式
     */
    override fun validateAddress(address: String): Boolean {
        return when {
            // CashAddr 格式驗證
            address.startsWith("$CASHADDR_PREFIX_MAINNET:") && currentNetwork == Network.BITCOIN_CASH_MAINNET -> {
                validateCashAddress(address)
            }
            address.startsWith("$CASHADDR_PREFIX_TESTNET:") && currentNetwork == Network.BITCOIN_CASH_TESTNET -> {
                validateCashAddress(address)
            }
            // Legacy 格式（向後相容）
            address.startsWith("1") && currentNetwork == Network.BITCOIN_CASH_MAINNET -> true
            address.startsWith("m") || address.startsWith("n") && currentNetwork == Network.BITCOIN_CASH_TESTNET -> true
            else -> false
        }
    }
    
    /**
     * 估算交易手續費
     */
    override suspend fun estimateFee(
        from: String,
        to: String,
        amount: Long
    ): Long {
        return try {
            val utxos = apiClient.getUtxos(normalizeCashAddress(from))
            val feeRate = apiClient.getFeeEstimate() ?: DEFAULT_FEE_RATE
            
            // 估算需要的 UTXO 數量
            var totalValue = 0L
            var inputCount = 0
            for (utxo in utxos.sortedByDescending { it.value }) {
                totalValue += utxo.value
                inputCount++
                if (totalValue >= amount) break
            }
            
            // 估算交易大小：輸入 × 148 + 輸出 × 34 + 10
            val estimatedSize = (inputCount * 148) + (2 * 34) + 10
            maxOf(estimatedSize * feeRate, estimatedSize * MIN_FEE_RATE)
        } catch (e: Exception) {
            // 預設手續費
            1000L
        }
    }
    
    /**
     * 監聽新交易
     */
    override fun observeTransactions(address: String): Flow<BitcoinTransaction> {
        return flow {
            // Bitcoin Cash 尚未實現 WebSocket，使用輪詢
            // 這裡先返回空 Flow
        }
    }
    
    /**
     * 構建 Bitcoin Cash 交易數據
     */
    private fun buildBitcoinCashTransactionData(
        utxos: List<UTXO>,
        to: String,
        amount: Long,
        change: Long,
        changeAddress: String
    ): String {
        // 將交易資訊序列化為 JSON 格式，供簽名使用
        val inputs = utxos.map { utxo ->
            mapOf(
                "txid" to utxo.txid,
                "vout" to utxo.vout,
                "value" to utxo.value,
                "scriptPubKey" to (utxo.scriptPubKey ?: "")
            )
        }
        
        val outputs = mutableListOf(
            mapOf(
                "address" to to,
                "value" to amount
            )
        )
        
        if (change > DUST_LIMIT) {
            outputs.add(
                mapOf(
                    "address" to changeAddress,
                    "value" to change
                )
            )
        }
        
        return """
        {
            "inputs": ${inputs.toString()},
            "outputs": ${outputs.toString()},
            "useForkId": true
        }
        """.trimIndent()
    }
    
    /**
     * 正規化 CashAddr 地址
     * 移除前綴或添加前綴
     */
    private fun normalizeCashAddress(address: String): String {
        return when {
            address.contains(":") -> address.substringAfter(":")
            address.startsWith("q") || address.startsWith("p") -> {
                val prefix = when (currentNetwork) {
                    Network.BITCOIN_CASH_MAINNET -> CASHADDR_PREFIX_MAINNET
                    Network.BITCOIN_CASH_TESTNET -> CASHADDR_PREFIX_TESTNET
                    else -> CASHADDR_PREFIX_MAINNET
                }
                "$prefix:$address"
            }
            else -> address
        }
    }
    
    /**
     * 驗證 CashAddr 格式
     */
    private fun validateCashAddress(address: String): Boolean {
        // 簡化驗證 - 實際應該使用完整的 CashAddr 驗證
        val parts = address.split(":")
        if (parts.size != 2) return false
        
        val prefix = parts[0]
        val payload = parts[1]
        
        // 檢查前綴
        if (prefix !in listOf(CASHADDR_PREFIX_MAINNET, CASHADDR_PREFIX_TESTNET, CASHADDR_PREFIX_REGTEST)) {
            return false
        }
        
        // 檢查有效字符（base32）
        val validChars = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
        return payload.all { it in validChars }
    }
    
    /**
     * 獲取區塊瀏覽器 URL
     */
    fun getExplorerUrl(txHash: String): String {
        return when (currentNetwork) {
            Network.BITCOIN_CASH_MAINNET -> "https://blockchair.com/bitcoin-cash/transaction/$txHash"
            Network.BITCOIN_CASH_TESTNET -> "https://www.blockchain.com/explorer/transactions/bch-testnet/$txHash"
            else -> "https://blockchair.com/bitcoin-cash/transaction/$txHash"
        }
    }
    
    /**
     * 獲取 Bitcoin Cash 網路資訊
     */
    suspend fun getNetworkInfo(): Map<String, Any> {
        return mapOf(
            "network" to currentNetwork.name,
            "blockTime" to "10 minutes",
            "blockSize" to "32 MB",
            "coinType" to 145,
            "symbol" to "BCH",
            "decimals" to 8,
            "algorithm" to "SHA-256",
            "maxSupply" to "21,000,000",
            "currentFeeRate" to (apiClient.getFeeEstimate() ?: DEFAULT_FEE_RATE),
            "addressFormat" to "CashAddr"
        )
    }
}

// AddressType already defined in BitcoinPlatformAdapter.kt
// Using the existing AddressType enum
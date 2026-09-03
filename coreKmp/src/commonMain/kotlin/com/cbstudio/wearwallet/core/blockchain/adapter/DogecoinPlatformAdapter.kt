package com.cbstudio.wearwallet.core.blockchain.adapter

import com.cbstudio.wearwallet.core.blockchain.api.DogecoinApiClient
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
 * Dogecoin 平台適配器
 * 基於 Litecoin 架構，調整 Dogecoin 特定參數
 */
class DogecoinPlatformAdapter(
    override var currentNetwork: Network = Network.DOGECOIN_MAINNET
) : ChainAdapter {
    
    private val apiClient by lazy {
        DogecoinApiClient(currentNetwork)
    }
    
    private val utxoSelector = UTXOSelector()
    
    override val chainType: ChainType = ChainType.DOGECOIN
    
    override val supportedNetworks: List<Network> = listOf(
        Network.DOGECOIN_MAINNET,
        Network.DOGECOIN_TESTNET
    )
    
    companion object {
        // Dogecoin 特定參數
        const val DUST_LIMIT = 100_000L  // 0.001 DOGE
        const val MIN_FEE = 100_000_000L  // 1 DOGE 最低手續費
        const val DEFAULT_FEE_RATE = 100_000_000L  // 1 DOGE per KB
        
        // 派生路徑
        const val DERIVATION_PATH_MAINNET = "m/44'/3'/0'/0/0"  // Dogecoin CoinType = 3
        const val DERIVATION_PATH_TESTNET = "m/44'/1'/0'/0/0"  // 測試網路徑
    }
    
    /**
     * 生成 Dogecoin 地址
     */
    override suspend fun generateAddress(
        seed: ByteArray,
        derivationPath: String
    ): Address {
        // 簡化實現 - 實際需要使用 TrustWallet Core
        return Address(
            value = "D${(1..33).map { ('A'..'Z').random() }.joinToString("")}"
        )
    }
    
    /**
     * 獲取地址餘額
     */
    override suspend fun getBalance(address: String): Long {
        return try {
            apiClient.getBalance(address)
        } catch (e: Exception) {
            println("Error getting Dogecoin balance: ${e.message}")
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
            apiClient.getTransactions(address).take(limit)
        } catch (e: Exception) {
            println("Error getting Dogecoin transactions: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 創建 Dogecoin 交易
     */
    override suspend fun createTransaction(
        from: String,
        to: String,
        amount: Long,
        data: String? // Dogecoin 不使用 data 欄位
    ): UnsignedTransaction {
        try {
            // 獲取 UTXOs
            val utxos = apiClient.getUtxos(from)
            if (utxos.isEmpty()) {
                throw Exception("No UTXOs available for address: $from")
            }
            
            // 獲取手續費估算
            val feeEstimates = apiClient.getFeeEstimates()
            val feeRate = feeEstimates["1"] ?: 1.0  // Dogecoin 手續費很低
            
            // 估算交易大小
            val estimatedSize = (utxos.size * 148) + (2 * 34) + 10
            val estimatedFee = maxOf(
                (feeRate * estimatedSize).toLong(),
                MIN_FEE
            )
            
            // 選擇 UTXOs
            val requiredAmount = amount + estimatedFee
            val selection = utxoSelector.selectOptimal(
                utxos = utxos,
                targetAmount = amount,
                feeRate = feeRate.toLong()
            )
            
            val totalInput = selection.selectedUTXOs.sumOf { it.value }
            if (totalInput < requiredAmount) {
                throw Exception("Insufficient balance. Required: $requiredAmount, Available: $totalInput")
            }
            
            // 計算找零
            val change = selection.change
            
            return UnsignedTransaction(
                fromAddress = from,
                toAddress = to,
                amount = amount.toString(),
                gasPrice = feeRate.toLong().toString(),
                gasLimit = estimatedSize.toString(),
                nonce = "",
                data = buildDogecoinTransactionData(
                    selection.selectedUTXOs,
                    to,
                    amount,
                    change,
                    from
                ),
                chainId = when (currentNetwork) {
                    Network.DOGECOIN_MAINNET -> "doge"
                    Network.DOGECOIN_TESTNET -> "dogetest"
                    else -> "doge"
                },
                fee = estimatedFee.toString()
            )
        } catch (e: Exception) {
            throw Exception("Failed to create Dogecoin transaction: ${e.message}")
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
            println("Error broadcasting Dogecoin transaction: ${e.message}")
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
            println("Error broadcasting Dogecoin transaction: ${e.message}")
            null
        }
    }
    
    /**
     * 驗證地址格式
     */
    override fun validateAddress(address: String): Boolean {
        return when (currentNetwork) {
            Network.DOGECOIN_MAINNET -> {
                // 主網地址以 D 或 A 開頭
                address.startsWith("D") || address.startsWith("A")
            }
            Network.DOGECOIN_TESTNET -> {
                // 測試網地址以 n 或 m 開頭
                address.startsWith("n") || address.startsWith("m")
            }
            else -> false
        }
    }
    
    /**
     * 構建 Dogecoin 交易數據
     */
    private fun buildDogecoinTransactionData(
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
                "scriptPubKey" to utxo.scriptPubKey
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
            "outputs": ${outputs.toString()}
        }
        """.trimIndent()
    }
    
    /**
     * 獲取區塊瀏覽器 URL
     */
    fun getExplorerUrl(txHash: String): String {
        return when (currentNetwork) {
            Network.DOGECOIN_MAINNET -> "https://dogechain.info/tx/$txHash"
            Network.DOGECOIN_TESTNET -> "https://testnet.dogechain.info/tx/$txHash"
            else -> "https://dogechain.info/tx/$txHash"
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
            // 獲取 UTXOs 來估算需要的輸入數量
            val utxos = apiClient.getUtxos(from)
            val feeRate = apiClient.getFeeEstimates()["1"] ?: 1.0
            
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
            maxOf(estimatedSize.toLong(), MIN_FEE)
        } catch (e: Exception) {
            MIN_FEE // 預設最低手續費
        }
    }
    
    /**
     * 監聽新交易
     */
    override fun observeTransactions(address: String): Flow<BitcoinTransaction> {
        return flow {
            // Dogecoin 尚未實現 WebSocket，使用輪詢
            // 這裡先返回空 Flow
        }
    }
    
    /**
     * 獲取 Dogecoin 網路資訊
     */
    suspend fun getNetworkInfo(): Map<String, Any> {
        return mapOf(
            "network" to currentNetwork.name,
            "blockTime" to "1 minute",
            "coinType" to 3,
            "symbol" to "DOGE",
            "decimals" to 8,
            "algorithm" to "Scrypt",
            "maxSupply" to "No limit",
            "currentFeeRate" to apiClient.getFeeEstimates()
        )
    }
}
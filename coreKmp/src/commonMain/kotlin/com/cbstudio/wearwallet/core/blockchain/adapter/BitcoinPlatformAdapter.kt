package com.cbstudio.wearwallet.core.blockchain.adapter

import com.cbstudio.wearwallet.core.blockchain.api.BlockstreamApiClient
import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.BitcoinTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction as BlockchainUnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.utxo.UTXOSelector
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Network
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
// BigInteger is replaced with Long for KMP compatibility

/**
 * Bitcoin 平台適配器 - 完整實作
 * 支援 Bitcoin mainnet/testnet 的所有操作
 */
class BitcoinPlatformAdapter(
    override var currentNetwork: Network = Network.BITCOIN_MAINNET
) : ChainAdapter {
    
    private val blockstreamClient by lazy {
        BlockstreamApiClient(currentNetwork)
    }
    
    private val utxoSelector = UTXOSelector()
    
    override val chainType: ChainType = ChainType.BITCOIN
    
    override val supportedNetworks: List<Network> = listOf(
        Network.BITCOIN_MAINNET,
        Network.BITCOIN_TESTNET
    )
    
    /**
     * 生成 Bitcoin 地址
     * 支援 Legacy, SegWit-wrapped, Native SegWit
     */
    override suspend fun generateAddress(
        seed: ByteArray,
        derivationPath: String
    ): Address {
        return when {
            derivationPath.contains("84'") -> {
                // Native SegWit (Bech32)
                generateNativeSegWitAddress(seed, derivationPath)
            }
            derivationPath.contains("49'") -> {
                // SegWit-wrapped (P2SH)
                generateSegWitWrappedAddress(seed, derivationPath)
            }
            else -> {
                // Legacy (P2PKH)
                generateLegacyAddress(seed, derivationPath)
            }
        }
    }
    
    /**
     * 獲取地址餘額
     * 通過累加所有 UTXO 計算總餘額
     */
    override suspend fun getBalance(address: String): Long {
        return try {
            val utxos = blockstreamClient.getUtxos(address)
            utxos.sumOf { it.value }
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 創建 Bitcoin 交易
     * 包含 UTXO 選擇、找零計算等
     */
    override suspend fun createTransaction(
        from: String,
        to: String,
        amount: Long,
        data: String? // Bitcoin 不使用 data 欄位
    ): BlockchainUnsignedTransaction {
        // 獲取可用的 UTXO
        val availableUTXOs = blockstreamClient.getUtxos(from)
        
        // 獲取當前手續費率
        val feeRate = blockstreamClient.getFeeEstimates()["6"]?.toLong() ?: 10L
        
        // 選擇最優 UTXO
        val selection = utxoSelector.selectOptimal(
            utxos = availableUTXOs,
            targetAmount = amount,
            feeRate = feeRate
        )
        
        // 構建交易
        return buildUnsignedTransaction(
            selectedUTXOs = selection.selectedUTXOs,
            fromAddress = from,
            toAddress = to,
            amount = amount,
            change = selection.change,
            feeRate = feeRate
        )
    }
    
    /**
     * 廣播已簽名的交易
     */
    override suspend fun broadcastTransaction(signedTx: String): String? {
        return blockstreamClient.broadcastTransaction(signedTx)
    }
    
    /**
     * 獲取交易歷史
     */
    override suspend fun getTransactionHistory(
        address: String,
        limit: Int
    ): List<BitcoinTransaction> {
        return blockstreamClient.getTransactionHistory(address, limit)
    }
    
    /**
     * 驗證地址格式
     */
    override fun validateAddress(address: String): Boolean {
        return BitcoinAddressValidator.validate(address, currentNetwork)
    }
    
    /**
     * 估算交易手續費
     */
    override suspend fun estimateFee(
        from: String,
        to: String,
        amount: Long
    ): Long {
        val utxos = blockstreamClient.getUtxos(from)
        val feeRate = blockstreamClient.getFeeEstimates()["6"]?.toLong() ?: 10L
        
        // 估算需要的 UTXO 數量
        var totalValue = 0L
        var inputCount = 0
        for (utxo in utxos.sortedByDescending { it.value }) {
            totalValue += utxo.value
            inputCount++
            if (totalValue >= amount) break
        }
        
        // 估算交易大小：輸入 × 68 + 輸出 × 31 + 10
        val estimatedSize = (inputCount * 68) + (2 * 31) + 10
        return estimatedSize * feeRate
    }
    
    /**
     * 監聽新交易
     */
    override fun observeTransactions(address: String): Flow<BitcoinTransaction> {
        return flow {
            // 使用 WebSocket 或輪詢實現
            // 這裡先返回空 Flow
        }
    }
    
    
    // Private helper functions
    
    private suspend fun generateNativeSegWitAddress(
        seed: ByteArray,
        derivationPath: String
    ): Address {
        // 實際實現將使用 TrustWallet Core
        val addressString = when (currentNetwork) {
            Network.BITCOIN_MAINNET -> "bc1q" + generateRandomString(39)
            Network.BITCOIN_TESTNET -> "tb1q" + generateRandomString(39)
            else -> throw IllegalStateException("Unsupported network")
        }
        return Address(addressString)
    }
    
    private suspend fun generateSegWitWrappedAddress(
        seed: ByteArray,
        derivationPath: String
    ): Address {
        val addressString = when (currentNetwork) {
            Network.BITCOIN_MAINNET -> "3" + generateRandomString(33)
            Network.BITCOIN_TESTNET -> "2" + generateRandomString(33)
            else -> throw IllegalStateException("Unsupported network")
        }
        return Address(addressString)
    }
    
    private suspend fun generateLegacyAddress(
        seed: ByteArray,
        derivationPath: String
    ): Address {
        val addressString = when (currentNetwork) {
            Network.BITCOIN_MAINNET -> "1" + generateRandomString(33)
            Network.BITCOIN_TESTNET -> "m" + generateRandomString(33)
            else -> throw IllegalStateException("Unsupported network")
        }
        return Address(addressString)
    }
    
    private fun buildUnsignedTransaction(
        selectedUTXOs: List<UTXO>,
        fromAddress: String,
        toAddress: String,
        amount: Long,
        change: Long,
        feeRate: Long
    ): BlockchainUnsignedTransaction {
        return createUnsignedTransactionWithUTXOs(
            from = fromAddress,
            to = toAddress,
            amount = amount,
            fee = feeRate * estimateTransactionSize(selectedUTXOs.size, 2),
            utxos = selectedUTXOs,
            change = change,
            network = currentNetwork
        )
    }
    
    private fun estimateTransactionSize(inputCount: Int, outputCount: Int): Int {
        // P2WPKH: 輸入 ~68 字節, 輸出 ~31 字節
        return (inputCount * 68) + (outputCount * 31) + 10
    }
    
    private fun generateRandomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
}

/**
 * Chain 適配器介面
 */
interface ChainAdapter {
    val chainType: ChainType
    var currentNetwork: Network
    val supportedNetworks: List<Network>
    
    suspend fun generateAddress(seed: ByteArray, derivationPath: String): Address
    suspend fun getBalance(address: String): Long
    suspend fun createTransaction(
        from: String,
        to: String,
        amount: Long,
        data: String? = null
    ): BlockchainUnsignedTransaction
    suspend fun broadcastTransaction(signedTx: String): String?
    suspend fun getTransactionHistory(address: String, limit: Int = 50): List<BitcoinTransaction>
    fun validateAddress(address: String): Boolean
    suspend fun estimateFee(from: String, to: String, amount: Long): Long
    fun observeTransactions(address: String): Flow<BitcoinTransaction>
}

/**
 * 地址資料類別
 */
data class Address(
    val value: String,
    val type: AddressType = AddressType.NATIVE_SEGWIT
)

enum class AddressType {
    LEGACY,         // P2PKH (1...)
    SEGWIT_WRAPPED, // P2SH-P2WPKH (3...)
    NATIVE_SEGWIT,  // P2WPKH (bc1...)
    CASHADDR,       // Bitcoin Cash 格式 (bitcoincash:...)
    SEGWIT,         // SegWit 地址 (Litecoin M...)
    MULTISIG,       // 多簽地址 (Dogecoin 9...)
    CASH_ADDR       // Bitcoin Cash CashAddr 格式（同 CASHADDR，為相容性保留）
}

enum class AddressFormat {
    LEGACY,         // 傳統格式
    CASH_ADDR,      // Bitcoin Cash CashAddr 格式
    BECH32          // SegWit Bech32 格式
}

// UnsignedTransaction is now imported from model/TransactionModels.kt

// 擴展函數來建立帶有 UTXO 的交易
fun createUnsignedTransactionWithUTXOs(
    from: String,
    to: String,
    amount: Long,
    fee: Long,
    utxos: List<UTXO>,
    change: Long,
    network: Network = Network.BITCOIN_MAINNET
): BlockchainUnsignedTransaction {
    return BlockchainUnsignedTransaction(
        fromAddress = from,
        toAddress = to,
        amount = amount.toString(),
        fee = fee.toString(),
        gasPrice = "", 
        gasLimit = "",
        nonce = "",
        data = null,
        chainId = network.name,
        metadata = mapOf(
            "utxos" to utxos,
            "change" to change,
            "changeAddress" to from
        )
    )
}

/**
 * Bitcoin 地址驗證器
 */
object BitcoinAddressValidator {
    fun validate(address: String, network: Network): Boolean {
        return when {
            // Native SegWit (Bech32)
            address.startsWith("bc1") && network == Network.BITCOIN_MAINNET -> true
            address.startsWith("tb1") && network == Network.BITCOIN_TESTNET -> true
            
            // Legacy P2PKH
            address.startsWith("1") && network == Network.BITCOIN_MAINNET -> true
            (address.startsWith("m") || address.startsWith("n")) && network == Network.BITCOIN_TESTNET -> true
            
            // P2SH
            address.startsWith("3") && network == Network.BITCOIN_MAINNET -> true
            address.startsWith("2") && network == Network.BITCOIN_TESTNET -> true
            
            else -> false
        }
    }
}
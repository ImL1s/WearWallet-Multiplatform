package com.cbstudio.wearwallet.core.blockchain.adapter

import com.cbstudio.wearwallet.core.blockchain.api.LitecoinApiClient
import com.cbstudio.wearwallet.core.blockchain.model.UTXO
import com.cbstudio.wearwallet.core.blockchain.model.BitcoinTransaction
import com.cbstudio.wearwallet.core.blockchain.model.UnsignedTransaction
import com.cbstudio.wearwallet.core.blockchain.utxo.UTXOSelector
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Network
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Litecoin 平台適配器
 * 基於 Bitcoin 架構，調整 Litecoin 特定參數
 */
class LitecoinPlatformAdapter(
    override var currentNetwork: Network = Network.LITECOIN_MAINNET
) : ChainAdapter {
    
    private val apiClient by lazy {
        LitecoinApiClient(currentNetwork)
    }
    
    private val utxoSelector = UTXOSelector()
    
    override val chainType: ChainType = ChainType.LITECOIN
    
    override val supportedNetworks: List<Network> = listOf(
        Network.LITECOIN_MAINNET,
        Network.LITECOIN_TESTNET
    )
    
    /**
     * 生成 Litecoin 地址
     * 支援 Legacy, SegWit-wrapped, Native SegWit
     * 派生路徑: m/44'/2'/0'/0/0 (主網) 或 m/44'/1'/0'/0/0 (測試網)
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
     */
    override suspend fun getBalance(address: String): Long {
        return try {
            val utxos = apiClient.getUtxos(address)
            utxos.sumOf { it.value }
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 創建 Litecoin 交易
     */
    override suspend fun createTransaction(
        from: String,
        to: String,
        amount: Long,
        data: String?
    ): UnsignedTransaction {
        // 獲取可用的 UTXO
        val availableUTXOs = apiClient.getUtxos(from)
        
        // 獲取當前手續費率 (Litecoin 通常更低)
        val feeRate = apiClient.getFeeEstimates()["6"]?.toLong() ?: 5L
        
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
        return apiClient.broadcastTransaction(signedTx)
    }
    
    /**
     * 獲取交易歷史
     */
    override suspend fun getTransactionHistory(
        address: String,
        limit: Int
    ): List<BitcoinTransaction> {
        return apiClient.getTransactionHistory(address, limit)
    }
    
    /**
     * 驗證 Litecoin 地址格式
     */
    override fun validateAddress(address: String): Boolean {
        return LitecoinAddressValidator.validate(address, currentNetwork)
    }
    
    /**
     * 估算交易手續費
     */
    override suspend fun estimateFee(
        from: String,
        to: String,
        amount: Long
    ): Long {
        val utxos = apiClient.getUtxos(from)
        val feeRate = apiClient.getFeeEstimates()["6"]?.toLong() ?: 5L
        
        // 估算需要的 UTXO 數量
        var totalValue = 0L
        var inputCount = 0
        for (utxo in utxos.sortedByDescending { it.value }) {
            totalValue += utxo.value
            inputCount++
            if (totalValue >= amount) break
        }
        
        // 估算交易大小
        val estimatedSize = (inputCount * 68) + (2 * 31) + 10
        return estimatedSize * feeRate
    }
    
    /**
     * 監聽新交易
     */
    override fun observeTransactions(address: String): Flow<BitcoinTransaction> {
        return flow {
            // WebSocket 或輪詢實現
        }
    }
    
    // Private helper functions
    
    private suspend fun generateNativeSegWitAddress(
        seed: ByteArray,
        derivationPath: String
    ): Address {
        // 實際實現將使用 TrustWallet Core
        val addressString = when (currentNetwork) {
            Network.LITECOIN_MAINNET -> "ltc1q" + generateRandomString(38)
            Network.LITECOIN_TESTNET -> "tltc1q" + generateRandomString(38)
            else -> throw IllegalStateException("Unsupported network")
        }
        return Address(addressString, AddressType.NATIVE_SEGWIT)
    }
    
    private suspend fun generateSegWitWrappedAddress(
        seed: ByteArray,
        derivationPath: String
    ): Address {
        val addressString = when (currentNetwork) {
            Network.LITECOIN_MAINNET -> "M" + generateRandomString(33)  // M 開頭的 P2SH
            Network.LITECOIN_TESTNET -> "Q" + generateRandomString(33)  // Q 或 2 開頭
            else -> throw IllegalStateException("Unsupported network")
        }
        return Address(addressString, AddressType.SEGWIT_WRAPPED)
    }
    
    private suspend fun generateLegacyAddress(
        seed: ByteArray,
        derivationPath: String
    ): Address {
        val addressString = when (currentNetwork) {
            Network.LITECOIN_MAINNET -> "L" + generateRandomString(33)  // L 開頭的 Legacy
            Network.LITECOIN_TESTNET -> "m" + generateRandomString(33)  // m 或 n 開頭
            else -> throw IllegalStateException("Unsupported network")
        }
        return Address(addressString, AddressType.LEGACY)
    }
    
    private fun buildUnsignedTransaction(
        selectedUTXOs: List<UTXO>,
        fromAddress: String,
        toAddress: String,
        amount: Long,
        change: Long,
        feeRate: Long
    ): UnsignedTransaction {
        val estimatedTxSize = estimateTransactionSize(selectedUTXOs.size, 2)
        return UnsignedTransaction(
            fromAddress = fromAddress,
            toAddress = toAddress,
            amount = amount.toString(),
            fee = (feeRate * estimatedTxSize).toString(),
            gasPrice = feeRate.toString(),
            gasLimit = estimatedTxSize.toString(),
            nonce = "",
            data = null,
            chainId = currentNetwork.name,
            metadata = mapOf(
                "utxos" to selectedUTXOs,
                "change" to change,
                "changeAddress" to fromAddress
            )
        )
    }
    
    private fun estimateTransactionSize(inputCount: Int, outputCount: Int): Int {
        // Litecoin 交易大小估算與 Bitcoin 相似
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
 * Litecoin 地址驗證器
 */
object LitecoinAddressValidator {
    fun validate(address: String, network: Network): Boolean {
        return when {
            // Native SegWit (Bech32)
            address.startsWith("ltc1") && network == Network.LITECOIN_MAINNET -> true
            address.startsWith("tltc1") && network == Network.LITECOIN_TESTNET -> true
            
            // Legacy P2PKH
            address.startsWith("L") && network == Network.LITECOIN_MAINNET -> true
            (address.startsWith("m") || address.startsWith("n")) && network == Network.LITECOIN_TESTNET -> true
            
            // P2SH (SegWit-wrapped)
            address.startsWith("M") && network == Network.LITECOIN_MAINNET -> true
            (address.startsWith("Q") || address.startsWith("2")) && network == Network.LITECOIN_TESTNET -> true
            
            else -> false
        }
    }
}
package com.cbstudio.wearwallet.core.blockchain.model

import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * UTXO (Unspent Transaction Output) 模型
 * 代表一個未花費的交易輸出
 */
@Serializable
data class UTXO(
    val txid: String,           // 交易 ID
    val vout: Int,              // 輸出索引
    val value: Long,            // 金額（satoshis）
    val confirmed: Boolean,     // 是否已確認
    val blockHeight: Long = 0,  // 區塊高度
    val scriptPubKey: String? = null,  // 鎖定腳本
    val address: String? = null        // 地址
)

/**
 * Bitcoin 交易模型
 */
@Serializable
data class BitcoinTransaction(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,
    val fee: String,
    val blockNumber: String? = null,
    val timestamp: Instant? = null,
    val status: TransactionStatus = TransactionStatus.PENDING,
    val confirmations: Int = 0,
    val inputs: List<BitcoinInput> = emptyList(),
    val outputs: List<BitcoinOutput> = emptyList(),
    val size: Int = 0,
    val weight: Int = 0,
    val version: Int = 2,
    val lockTime: Long = 0
)

/**
 * Bitcoin 交易輸入
 */
@Serializable
data class BitcoinInput(
    val previousTxHash: String,
    val previousIndex: Int,
    val script: ByteArray,
    val sequence: Int = 0xFFFFFFFD.toInt(),
    val witness: List<String>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BitcoinInput) return false
        
        if (previousTxHash != other.previousTxHash) return false
        if (previousIndex != other.previousIndex) return false
        if (!script.contentEquals(other.script)) return false
        if (sequence != other.sequence) return false
        if (witness != other.witness) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = previousTxHash.hashCode()
        result = 31 * result + previousIndex
        result = 31 * result + script.contentHashCode()
        result = 31 * result + sequence
        result = 31 * result + (witness?.hashCode() ?: 0)
        return result
    }
}

/**
 * Bitcoin 交易輸出
 */
@Serializable
data class BitcoinOutput(
    val value: Long,
    val script: ByteArray,
    val address: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BitcoinOutput) return false
        
        if (value != other.value) return false
        if (!script.contentEquals(other.script)) return false
        if (address != other.address) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + script.contentHashCode()
        result = 31 * result + (address?.hashCode() ?: 0)
        return result
    }
}

/**
 * 交易構建器參數
 */
data class TransactionBuilderParams(
    val inputs: List<UTXO>,
    val outputs: List<TransactionOutputParams>,
    val changeAddress: String,
    val feeRate: Long,
    val rbfEnabled: Boolean = true
)

/**
 * 交易輸出參數
 */
data class TransactionOutputParams(
    val address: String,
    val amount: Long
)

/**
 * 手續費優先級
 */
enum class FeePriority {
    LOW,      // ~24小時
    MEDIUM,   // ~1小時
    HIGH,     // ~20分鐘
    URGENT    // 下一個區塊
}

/**
 * UTXO 選擇結果
 */
data class UTXOSelection(
    val selectedUTXOs: List<UTXO>,
    val totalValue: Long,
    val change: Long,
    val estimatedFee: Long
)

/**
 * Bitcoin 腳本類型
 */
enum class ScriptType {
    P2PKH,      // Pay to Public Key Hash (Legacy)
    P2SH,       // Pay to Script Hash
    P2WPKH,     // Pay to Witness Public Key Hash (Native SegWit)
    P2WSH,      // Pay to Witness Script Hash
    P2TR        // Pay to Taproot
}

/**
 * Bitcoin 網路參數
 */
data class BitcoinNetworkParams(
    val name: String,
    val bech32Prefix: String,
    val p2pkhPrefix: Byte,
    val p2shPrefix: Byte,
    val wifPrefix: Byte,
    val bip32Private: Long,
    val bip32Public: Long
) {
    companion object {
        val MAINNET = BitcoinNetworkParams(
            name = "mainnet",
            bech32Prefix = "bc",
            p2pkhPrefix = 0x00,
            p2shPrefix = 0x05,
            wifPrefix = 0x80.toByte(),
            bip32Private = 0x0488ADE4,
            bip32Public = 0x0488B21E
        )
        
        val TESTNET = BitcoinNetworkParams(
            name = "testnet",
            bech32Prefix = "tb",
            p2pkhPrefix = 0x6F,
            p2shPrefix = 0xC4.toByte(),
            wifPrefix = 0xEF.toByte(),
            bip32Private = 0x04358394,
            bip32Public = 0x043587CF
        )
    }
}

/**
 * 錢包派生路徑
 */
enum class DerivationPath(val path: String, val scriptType: ScriptType) {
    // Bitcoin Mainnet
    BTC_LEGACY("m/44'/0'/0'/0/0", ScriptType.P2PKH),
    BTC_SEGWIT_WRAPPED("m/49'/0'/0'/0/0", ScriptType.P2SH),
    BTC_NATIVE_SEGWIT("m/84'/0'/0'/0/0", ScriptType.P2WPKH),
    BTC_TAPROOT("m/86'/0'/0'/0/0", ScriptType.P2TR),
    
    // Bitcoin Testnet
    BTC_TESTNET_LEGACY("m/44'/1'/0'/0/0", ScriptType.P2PKH),
    BTC_TESTNET_SEGWIT("m/84'/1'/0'/0/0", ScriptType.P2WPKH),
    
    // Litecoin
    LTC_LEGACY("m/44'/2'/0'/0/0", ScriptType.P2PKH),
    LTC_NATIVE_SEGWIT("m/84'/2'/0'/0/0", ScriptType.P2WPKH),
    
    // Dogecoin
    DOGE_LEGACY("m/44'/3'/0'/0/0", ScriptType.P2PKH),
    
    // Bitcoin Cash
    BCH_LEGACY("m/44'/145'/0'/0/0", ScriptType.P2PKH);
    
    fun getAccountPath(): String {
        return path.substringBeforeLast("/")
    }
    
    fun getCoinType(): Int {
        val parts = path.split("/")
        return parts[2].removeSuffix("'").toInt()
    }
}
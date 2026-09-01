package com.cbstudio.wearwallet.core.multichain.monero.crypto

import com.cbstudio.wearwallet.core.common.Result
import kotlin.jvm.JvmName
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Monero 密碼學操作提供者接口
 * 
 * 為不同平台提供統一的密碼學操作介面
 * - Android/JVM: 使用 monero-java
 * - iOS: 使用 monero-cpp 透過 cinterop
 * - 測試: 使用模擬實現
 */
interface MoneroCryptoProvider {
    
    /**
     * 從 BIP39 助記詞派生 Monero 密鑰
     */
    suspend fun deriveKeysFromMnemonic(
        mnemonic: String,
        password: String = ""
    ): Result<MoneroKeys>
    
    /**
     * 生成 Monero 地址
     */
    suspend fun generateAddress(
        publicSpendKey: ByteArray,
        publicViewKey: ByteArray,
        network: MoneroNetwork
    ): Result<String>
    
    /**
     * 生成密鑰圖像（用於防止雙重支出）
     */
    suspend fun generateKeyImage(
        privateKey: ByteArray,
        publicKey: ByteArray
    ): Result<ByteArray>
    
    /**
     * 創建 MLSAG 簽名
     */
    suspend fun createMLSAGSignature(
        message: ByteArray,
        privateKeys: List<ByteArray>,
        publicKeys: List<List<ByteArray>>,
        realIndex: Int,
        keyImages: List<ByteArray>? = null
    ): Result<MLSAGSignature>
    
    /**
     * 驗證 MLSAG 簽名
     */
    suspend fun verifyMLSAGSignature(
        message: ByteArray,
        signature: MLSAGSignature,
        publicKeys: List<List<ByteArray>>
    ): Result<Boolean>
    
    /**
     * 創建 Pedersen 承諾（RingCT）
     */
    suspend fun createPedersenCommitment(
        amount: BigDecimal,
        mask: ByteArray
    ): Result<ByteArray>
    
    /**
     * 創建 Bulletproof 範圍證明
     */
    suspend fun createBulletproof(
        amounts: List<BigDecimal>,
        masks: List<ByteArray>
    ): Result<Bulletproof>
    
    /**
     * Ed25519 橢圓曲線運算
     */
    suspend fun ed25519ScalarMultBase(scalar: ByteArray): Result<ByteArray>
    suspend fun ed25519ScalarMult(scalar: ByteArray, point: ByteArray): Result<ByteArray>
    suspend fun ed25519PointAdd(p1: ByteArray, p2: ByteArray): Result<ByteArray>
    
    /**
     * 哈希函數
     */
    suspend fun keccak256(data: ByteArray): Result<ByteArray>
    suspend fun sha256(data: ByteArray): Result<ByteArray>
    
    /**
     * 編碼/解碼
     */
    suspend fun base58Encode(data: ByteArray): Result<String>
    suspend fun base58Decode(encoded: String): Result<ByteArray>
    
    /**
     * 掃描 UTXO
     */
    suspend fun scanForUTXOs(
        viewKey: String,
        address: String,
        fromHeight: Long = 0,
        toHeight: Long? = null
    ): Result<List<MoneroUTXO>>
    
    /**
     * 創建交易
     */
    suspend fun createTransaction(
        inputs: List<MoneroUTXO>,
        outputs: List<TransactionOutput>,
        changeAddress: String,
        feeAmount: BigDecimal
    ): Result<SerializedTransaction>

    /**
     * 簽名交易
     */
    suspend fun signTransaction(
        transaction: Any, // 可以是 TransactionRequest 或其他交易類型
        privateKeys: List<ByteArray>
    ): Result<Any>

    /**
     * 廣播交易
     */
    suspend fun broadcastTransaction(
        signedTransaction: Any
    ): Result<String>
}

/**
 * 取得平台特定的 MoneroCryptoProvider 實現
 */
expect fun getMoneroCryptoProvider(): MoneroCryptoProvider

/**
 * Monero 密鑰數據
 */
data class MoneroKeys(
    val privateSpendKey: ByteArray,
    val privateViewKey: ByteArray,
    val publicSpendKey: ByteArray,
    val publicViewKey: ByteArray,
    val address: String,
    val mnemonic: String = "",
    val isViewOnly: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MoneroKeys
        return privateSpendKey.contentEquals(other.privateSpendKey) &&
               privateViewKey.contentEquals(other.privateViewKey) &&
               publicSpendKey.contentEquals(other.publicSpendKey) &&
               publicViewKey.contentEquals(other.publicViewKey) &&
               address == other.address &&
               mnemonic == other.mnemonic &&
               isViewOnly == other.isViewOnly
    }

    override fun hashCode(): Int {
        var result = privateSpendKey.contentHashCode()
        result = 31 * result + privateViewKey.contentHashCode()
        result = 31 * result + publicSpendKey.contentHashCode()
        result = 31 * result + publicViewKey.contentHashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + mnemonic.hashCode()
        result = 31 * result + isViewOnly.hashCode()
        return result
    }

    /**
     * 轉換為私鑰列表（用於簽名）
     */
    fun toPrivateKeys(): List<ByteArray> {
        return listOf(privateSpendKey, privateViewKey)
    }
}

// MoneroNetwork 已在 MoneroKeyDerivation.kt 中定義

/**
 * MLSAG 簽名數據
 */
data class MLSAGSignature(
    val ss: List<List<String>>,
    val cc: String,
    val keyImages: List<String>
)

/**
 * Bulletproof 範圍證明
 */
data class Bulletproof(
    @get:JvmName("getUpperA")
    val A: String,
    val S: String,
    val T1: String,
    val T2: String,
    val taux: String,
    val mu: String,
    val L: List<String>,
    val R: List<String>,
    @get:JvmName("getLowerA")
    val a: String,
    val b: String,
    val t: String
)

/**
 * Monero UTXO
 */
data class MoneroUTXO(
    val txHash: String,
    val txPublicKey: String,
    val outputIndex: Int,
    val globalIndex: Long,
    val amount: String,
    val mask: String,
    val keyImage: String? = null,
    val stealthAddress: String,
    val height: Long,
    val unlocked: Boolean = false
)

/**
 * 交易輸出
 */
data class TransactionOutput(
    val address: String,
    val amount: BigDecimal
)

/**
 * 序列化的交易
 */
data class SerializedTransaction(
    val txHex: String,
    val txHash: String,
    val txKey: String,
    val fee: BigDecimal,
    val weight: Int
)


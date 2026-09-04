package com.cbstudio.wearwallet.core.multichain.monero

import com.cbstudio.wearwallet.core.blockchain.crypto.CryptoSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kotlincrypto.hash.sha3.Keccak256
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.experimental.and

/**
 * Monero BIP39 兼容性支援
 * 
 * Monero 與 BIP39 的差異：
 * 1. Monero 使用 25 個單詞（最後一個是校驗和），而 BIP39 使用 12/24 個
 * 2. Monero 使用 Ed25519 曲線，而 Bitcoin 使用 secp256k1
 * 3. Monero 使用 Keccak256 進行密鑰推導
 * 
 * 轉換流程：
 * BIP39 助記詞 -> BIP39 種子 -> BIP44 推導 (m/44'/128'/0'/0') -> SHA3/Keccak256 -> Monero 私鑰
 */
class MoneroBIP39Support {
    
    companion object {
        // Monero 的 coin type 是 128
        const val MONERO_COIN_TYPE = 128
        
        // Ed25519 曲線參數
        const val ED25519_L = "7237005577332262213973186563042994240857116359379907606001950938285454250989"
        
        // Monero 地址前綴
        const val MAINNET_PREFIX: Byte = 0x12  // 18 in decimal
        const val TESTNET_PREFIX: Byte = 0x35  // 53 in decimal
        const val STAGENET_PREFIX: Byte = 0x18 // 24 in decimal
    }
    
    /**
     * 從 BIP39 助記詞生成 Monero 密鑰
     * @param bip39Mnemonic BIP39 格式的助記詞（12 或 24 個單詞）
     * @param passphrase 可選的密碼短語
     * @return Monero 密鑰對
     */
    suspend fun generateMoneroKeysFromBIP39(
        bip39Mnemonic: String,
        passphrase: String = ""
    ): MoneroKeyPair = withContext(Dispatchers.Default) {
        
        // 步驟 1: 從 BIP39 助記詞生成種子
        val bip39Seed = generateBIP39Seed(bip39Mnemonic, passphrase)
        
        // 步驟 2: 使用 BIP44 路徑推導
        // 路徑：m/44'/128'/0'/0' (128 是 Monero 的 coin type)
        val derivedKey = deriveMoneroKey(bip39Seed)
        
        // 步驟 3: 使用 Keccak256 生成 Monero 私鑰
        val privateSpendKey = keccak256(derivedKey)
        
        // 步驟 4: 縮減為有效的 Ed25519 標量
        val reducedSpendKey = reduceEd25519Scalar(privateSpendKey)
        
        // 步驟 5: 生成查看密鑰
        val privateViewKey = generateViewKey(reducedSpendKey)
        
        // 步驟 6: 生成公鑰
        val publicSpendKey = generatePublicKey(reducedSpendKey)
        val publicViewKey = generatePublicKey(privateViewKey)
        
        // 步驟 7: 生成地址
        val address = generateMoneroAddress(publicSpendKey, publicViewKey, NetworkType.MAINNET)
        
        return@withContext MoneroKeyPair(
            privateSpendKey = reducedSpendKey,
            privateViewKey = privateViewKey,
            publicSpendKey = publicSpendKey,
            publicViewKey = publicViewKey,
            address = address
        )
    }
    
    /**
     * 從 BIP39 助記詞生成種子
     */
    private fun generateBIP39Seed(mnemonic: String, passphrase: String): ByteArray {
        // 使用 PBKDF2 與 "mnemonic" + passphrase 作為鹽
        val salt = "mnemonic$passphrase".encodeToByteArray()
        return pbkdf2(mnemonic.encodeToByteArray(), salt, 2048, 64)
    }
    
    /**
     * 使用 BIP44 路徑推導 Monero 密鑰
     */
    private fun deriveMoneroKey(seed: ByteArray): ByteArray {
        // 簡化實現，實際需要完整的 SLIP-10 推導
        // m/44'/128'/0'/0'
        val path = listOf(
            0x8000002C, // 44'
            0x80000080, // 128'
            0x80000000, // 0'
            0x00000000  // 0
        )
        
        var key = seed.take(32).toByteArray()
        
        // 對每個路徑段進行推導
        path.forEach { index ->
            key = deriveChildKey(key, index.toInt())
        }
        
        return key
    }
    
    /**
     * 推導子密鑰
     */
    private fun deriveChildKey(parentKey: ByteArray, index: Int): ByteArray {
        // 使用 HMAC-SHA512 進行子密鑰推導
        val indexBytes = ByteArray(4)
        indexBytes[0] = (index shr 24).toByte()
        indexBytes[1] = (index shr 16).toByte()
        indexBytes[2] = (index shr 8).toByte()
        indexBytes[3] = index.toByte()
        
        val data = parentKey + indexBytes
        return hmacSha512("ed25519 seed".encodeToByteArray(), data).sliceArray(0..31)
    }
    
    /**
     * Keccak256 哈希（Monero 使用）
     */
    private fun keccak256(data: ByteArray): ByteArray {
        val keccak = Keccak256()
        keccak.update(data)
        return keccak.digest()
    }
    
    /**
     * 將數據縮減為有效的 Ed25519 標量
     */
    private fun reduceEd25519Scalar(data: ByteArray): ByteArray {
        // Ed25519 標量縮減
        val result = data.copyOf()
        
        // 清除高位和低位的某些位
        result[0] = (result[0].toInt() and 248).toByte()
        result[31] = (result[31].toInt() and 127).toByte()
        result[31] = (result[31].toInt() or 64).toByte()
        
        return result
    }
    
    /**
     * 生成查看密鑰
     */
    private fun generateViewKey(spendKey: ByteArray): ByteArray {
        // 查看密鑰 = Keccak256(支出密鑰)
        val viewKey = keccak256(spendKey)
        return reduceEd25519Scalar(viewKey)
    }
    
    /**
     * 生成公鑰（Ed25519 點乘）
     */
    private fun generatePublicKey(privateKey: ByteArray): ByteArray {
        return com.cbstudio.wearwallet.core.multichain.monero.crypto.MoneroEd25519.publicFromSecret(privateKey)
    }
    
    /**
     * 生成 Monero 地址
     */
    private fun generateMoneroAddress(
        publicSpendKey: ByteArray,
        publicViewKey: ByteArray,
        networkType: NetworkType
    ): String {
        // 地址格式：network_byte + public_spend_key + public_view_key + checksum
        val prefix = when (networkType) {
            NetworkType.MAINNET -> MAINNET_PREFIX
            NetworkType.TESTNET -> TESTNET_PREFIX
            NetworkType.STAGENET -> STAGENET_PREFIX
        }
        
        val addressData = ByteArray(1 + 32 + 32)
        addressData[0] = prefix
        publicSpendKey.copyInto(addressData, 1, 0, 32)
        publicViewKey.copyInto(addressData, 33, 0, 32)
        
        // 計算校驗和（Keccak256 的前 4 字節）
        val checksum = keccak256(addressData).sliceArray(0..3)
        
        // Base58 編碼
        val fullAddress = addressData + checksum
        return base58Encode(fullAddress)
    }
    
    /**
     * PBKDF2 實現
     */
    private fun pbkdf2(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray {
        return io.github.iml1s.crypto.Pbkdf2.deriveKey(password, salt, iterations, keyLength)
    }
    
    /**
     * HMAC-SHA512 實現
     */
    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        return io.github.iml1s.crypto.HmacSha512.hmac(key, data)
    }
    
    /**
     * Base58 編碼
     */
    private fun base58Encode(data: ByteArray): String {
        return io.github.iml1s.crypto.Base58.encode(data)
    }
    
    /**
     * 將 Monero 種子轉換為 25 個單詞的助記詞
     */
    fun moneroSeedToMnemonic(seed: ByteArray): String {
        return com.cbstudio.wearwallet.core.multichain.monero.crypto.MoneroXMR25().generateMnemonic(seed)
    }
    
    /**
     * 驗證 Monero 助記詞（25 個單詞）
     */
    fun validateMoneroMnemonic(mnemonic: String): Boolean {
        val words = mnemonic.split(" ")
        return words.size == 25 // Monero 使用 25 個單詞
    }
}

/**
 * Monero 密鑰對
 */
data class MoneroKeyPair(
    val privateSpendKey: ByteArray,
    val privateViewKey: ByteArray,
    val publicSpendKey: ByteArray,
    val publicViewKey: ByteArray,
    val address: String
) {
    fun toDebugString(): String {
        return """
            |Monero Key Pair:
            |  Private Spend Key: ${privateSpendKey.toHexString().take(32)}...
            |  Private View Key: ${privateViewKey.toHexString().take(32)}...
            |  Public Spend Key: ${publicSpendKey.toHexString().take(32)}...
            |  Public View Key: ${publicViewKey.toHexString().take(32)}...
            |  Address: $address
        """.trimMargin()
    }
}

/**
 * Monero 網路類型
 */
enum class NetworkType {
    MAINNET,
    TESTNET,
    STAGENET
}

/**
 * ByteArray 擴展函數：轉換為十六進制字符串
 */
fun ByteArray.toHexString(): String {
    return joinToString("") { byte -> 
        val value = byte.toInt() and 0xFF
        value.toString(16).padStart(2, '0')
    }
}
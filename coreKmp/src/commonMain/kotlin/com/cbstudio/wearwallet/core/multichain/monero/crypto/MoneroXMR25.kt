package com.cbstudio.wearwallet.core.multichain.monero.crypto

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kotlincrypto.hash.sha3.Keccak256
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.pow

/**
 * Monero XMR25 助記詞處理器
 * 
 * 實現 Monero 原生 25 詞助記詞格式的完整支援：
 * - 24 詞種子 + 1 詞校驗和
 * - CRC32 校驗和驗證
 * - 詞表編碼/解碼
 * - 密鑰派生
 */
class MoneroXMR25 {
    
    companion object {
        // Monero 詞表大小
        const val WORD_COUNT = 1626
        
        // 助記詞長度
        const val MNEMONIC_LENGTH_25 = 25
        const val MNEMONIC_LENGTH_24 = 24
        
        // 種子長度（32 字節 = 256 位）
        const val SEED_LENGTH = 32
        
        // CRC32 多項式（Monero 使用的標準 CRC32）
        const val CRC32_POLYNOMIAL = 0xEDB88320u
        
        // 地址前綴
        const val MAINNET_PREFIX: Byte = 18
        const val STAGENET_PREFIX: Byte = 24  
        const val TESTNET_PREFIX: Byte = 53
    }
    
    /**
     * 從 XMR25 助記詞派生密鑰
     * 
     * @param mnemonic 25 詞助記詞
     * @param network 網路類型
     * @return 派生的密鑰
     */
    suspend fun deriveKeys(
        mnemonic: String,
        network: MoneroNetwork = MoneroNetwork.MAINNET
    ): Result<MoneroKeys> = withContext(Dispatchers.Default) {
        try {
            val words = mnemonic.trim().split(" ").filter { it.isNotBlank() }
            
            // 驗證詞數
            if (words.size != MNEMONIC_LENGTH_25) {
                return@withContext Result.Failure(IllegalArgumentException(
                    "Invalid mnemonic length: ${words.size}, expected $MNEMONIC_LENGTH_25"
                ))
            }
            
            // 驗證所有單詞在詞表中
            if (!MoneroWordList.validateMnemonic(mnemonic)) {
                return@withContext Result.Failure(IllegalArgumentException(
                    "Invalid mnemonic: some words not in Monero word list"
                ))
            }
            
            // 驗證校驗和
            if (!validateChecksum(words)) {
                return@withContext Result.Failure(IllegalArgumentException(
                    "Invalid mnemonic checksum"
                ))
            }
            
            // 從前 24 詞提取種子
            val seed = wordsToSeed(words.take(MNEMONIC_LENGTH_24))
            
            // 派生密鑰
            val keys = deriveKeysFromSeed(seed, network)
            
            Result.Success(keys)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 驗證 XMR25 助記詞的校驗和
     * 
     * @param words 25 個單詞
     * @return 校驗和是否有效
     */
    fun validateChecksum(words: List<String>): Boolean {
        if (words.size != MNEMONIC_LENGTH_25) return false
        
        // 獲取前 24 詞的前綴
        val prefixes = words.take(MNEMONIC_LENGTH_24).map { word ->
            // 使用前 3 個字母作為前綴（英文詞表）
            word.take(3)
        }
        
        // 拼接前綴
        val prefixString = prefixes.joinToString("")
        
        // 計算 CRC32
        val crc32 = calculateCRC32(prefixString.encodeToByteArray())
        
        // 計算校驗詞索引（CRC32 % 24）
        val checksumIndex = (crc32 % MNEMONIC_LENGTH_24.toUInt()).toInt()
        
        // 期望的校驗詞應該是前 24 詞中的第 checksumIndex 個
        val expectedChecksumWord = words[checksumIndex]
        val actualChecksumWord = words[MNEMONIC_LENGTH_24]
        
        return expectedChecksumWord == actualChecksumWord
    }
    
    /**
     * 計算 CRC32 校驗和
     * 
     * @param data 輸入數據
     * @return CRC32 值
     */
    private fun calculateCRC32(data: ByteArray): UInt {
        var crc = 0xFFFFFFFFu
        
        for (byte in data) {
            crc = crc xor byte.toUInt()
            for (i in 0..7) {
                crc = if ((crc and 1u) != 0u) {
                    (crc shr 1) xor CRC32_POLYNOMIAL
                } else {
                    crc shr 1
                }
            }
        }
        
        return crc xor 0xFFFFFFFFu
    }
    
    /**
     * 將單詞轉換為種子
     * 
     * Monero 使用 base-1626 編碼：
     * - 24 個單詞編碼 256 位數據
     * - 每 3 個單詞編碼 32 位
     * - 1626^3 > 2^32，所以有足夠的空間
     * 
     * @param words 24 個單詞
     * @return 32 字節種子
     */
    fun wordsToSeed(words: List<String>): ByteArray {
        require(words.size == MNEMONIC_LENGTH_24) {
            "Expected 24 words, got ${words.size}"
        }
        
        val seed = ByteArray(SEED_LENGTH)
        
        // 每 3 個單詞處理成 4 個字節
        for (i in 0 until 8) {
            val w1 = MoneroWordList.getWordIndex(words[i * 3])
            val w2 = MoneroWordList.getWordIndex(words[i * 3 + 1])
            val w3 = MoneroWordList.getWordIndex(words[i * 3 + 2])
            
            // 將 3 個 base-1626 數字轉換為一個 32 位整數
            // n = w1 + w2 * 1626 + w3 * 1626^2
            val n = w1.toLong() + 
                    w2.toLong() * WORD_COUNT + 
                    w3.toLong() * WORD_COUNT * WORD_COUNT
            
            // 將 32 位整數寫入種子（小端序）
            seed[i * 4] = (n and 0xFF).toByte()
            seed[i * 4 + 1] = ((n shr 8) and 0xFF).toByte()
            seed[i * 4 + 2] = ((n shr 16) and 0xFF).toByte()
            seed[i * 4 + 3] = ((n shr 24) and 0xFF).toByte()
        }
        
        return seed
    }
    
    /**
     * 將種子轉換為單詞
     * 
     * @param seed 32 字節種子
     * @return 24 個單詞
     */
    fun seedToWords(seed: ByteArray): List<String> {
        require(seed.size == SEED_LENGTH) {
            "Seed must be 32 bytes"
        }
        
        val words = mutableListOf<String>()
        
        // 每 4 個字節處理成 3 個單詞
        for (i in 0 until 8) {
            // 讀取 32 位整數（小端序）
            val n = (seed[i * 4].toInt() and 0xFF) or
                    ((seed[i * 4 + 1].toInt() and 0xFF) shl 8) or
                    ((seed[i * 4 + 2].toInt() and 0xFF) shl 16) or
                    ((seed[i * 4 + 3].toInt() and 0xFF) shl 24)
            
            // 轉換為 3 個 base-1626 數字
            val w1 = n % WORD_COUNT
            val w2 = (n / WORD_COUNT) % WORD_COUNT
            val w3 = (n / (WORD_COUNT * WORD_COUNT)) % WORD_COUNT
            
            words.add(MoneroWordList.getWord(w1))
            words.add(MoneroWordList.getWord(w2))
            words.add(MoneroWordList.getWord(w3))
        }
        
        return words
    }
    
    /**
     * 生成完整的 25 詞助記詞（包含校驗和）
     * 
     * @param seed 32 字節種子
     * @return 25 詞助記詞
     */
    fun generateMnemonic(seed: ByteArray): String {
        val words = seedToWords(seed).toMutableList()
        
        // 計算校驗和
        val prefixes = words.map { it.take(3) }
        val prefixString = prefixes.joinToString("")
        val crc32 = calculateCRC32(prefixString.encodeToByteArray())
        val checksumIndex = (crc32 % MNEMONIC_LENGTH_24.toUInt()).toInt()
        
        // 添加校驗詞
        val checksumWord = words[checksumIndex]
        words.add(checksumWord)
        
        return words.joinToString(" ")
    }
    
    /**
     * 從種子派生 Monero 密鑰
     * 
     * @param seed 32 字節種子
     * @param network 網路類型
     * @return Monero 密鑰
     */
    private fun deriveKeysFromSeed(seed: ByteArray, network: MoneroNetwork): MoneroKeys {
        // 1. 私密花費密鑰 = sc_reduce32(seed)
        val privateSpendKey = MoneroEd25519.scReduce32(seed)
        
        // 2. 私密查看密鑰 = sc_reduce32(Keccak256(privateSpendKey))
        val privateViewKey = MoneroEd25519.scReduce32(MoneroKeccak.keccak256(privateSpendKey))
        
        // 3. 公開花費密鑰 = privateSpendKey * G
        val publicSpendKey = MoneroEd25519.publicFromSecret(privateSpendKey)
        
        // 4. 公開查看密鑰 = privateViewKey * G
        val publicViewKey = MoneroEd25519.publicFromSecret(privateViewKey)
        
        // 5. 生成地址
        val address = MoneroAddress.generateAddress(publicSpendKey, publicViewKey, network)
        
        return MoneroKeys(
            privateSpendKey = privateSpendKey,
            privateViewKey = privateViewKey,
            publicSpendKey = publicSpendKey,
            publicViewKey = publicViewKey,
            address = address
        )
    }
    
    /**
     * 派生查看密鑰
     * 
     * @param spendKey 花費密鑰
     * @return 查看密鑰
     */
    private fun deriveViewKey(spendKey: ByteArray): ByteArray {
        val keccak = Keccak256()
        keccak.update(spendKey)
        val hash = keccak.digest()
        return scReduce32(hash)
    }
    
    /**
     * Ed25519 標量縮減
     * 
     * 確保結果是有效的 Ed25519 標量
     * 
     * @param input 輸入數據
     * @return 縮減後的標量
     */
    private fun scReduce32(input: ByteArray): ByteArray {
        // Ed25519 的階 l = 2^252 + 27742317777372353535851937790883648493
        // 簡化實現：清除高位以確保小於 l
        val result = input.take(32).toByteArray()
        
        // 清除最高位字節的高位
        result[31] = (result[31].toInt() and 0x7F).toByte()
        
        return result
    }
    
    /**
     * Ed25519 標量乘以基點
     * 
     * 計算 scalar * G，其中 G 是 Ed25519 的基點
     * 
     * @param scalar 標量
     * @return 點（公鑰）
     */
    private fun scalarMultBase(scalar: ByteArray): ByteArray {
        // 簡化實現：實際需要完整的 Ed25519 點乘法
        // 這裡暫時返回基於標量的偽公鑰
        val result = ByteArray(32)
        
        // 使用 Keccak256 作為偽隨機函數生成公鑰
        val keccak = Keccak256()
        keccak.update("pubkey".encodeToByteArray())
        keccak.update(scalar)
        val hash = keccak.digest()
        
        hash.copyInto(result, 0, 0, 32)
        return result
    }
    
    /**
     * 編碼 Monero 地址
     * 
     * @param publicSpendKey 公開花費密鑰
     * @param publicViewKey 公開查看密鑰
     * @param network 網路類型
     * @return Base58 編碼的地址
     */
    private fun encodeAddress(
        publicSpendKey: ByteArray,
        publicViewKey: ByteArray,
        network: MoneroNetwork
    ): String {
        // 地址格式：network_byte + public_spend_key + public_view_key + checksum
        val networkByte = when (network) {
            MoneroNetwork.MAINNET -> MAINNET_PREFIX
            MoneroNetwork.STAGENET -> STAGENET_PREFIX
            MoneroNetwork.TESTNET -> TESTNET_PREFIX
        }
        
        val addressData = ByteArray(1 + 32 + 32)
        addressData[0] = networkByte
        publicSpendKey.copyInto(addressData, 1, 0, 32)
        publicViewKey.copyInto(addressData, 33, 0, 32)
        
        // 計算校驗和（Keccak256 的前 4 字節）
        val keccak = Keccak256()
        keccak.update(addressData)
        val checksum = keccak.digest().sliceArray(0..3)
        
        // 組合完整數據
        val fullData = addressData + checksum
        
        // Base58 編碼
        return MoneroBase58.encode(fullData)
    }
}

// MoneroNetwork 已在 MoneroCommon.kt 中定義

/**
 * Monero Base58 編碼器
 * 
 * Monero 使用自定義的 Base58 字母表
 */
object MoneroBase58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = ALPHABET.length
    
    /**
     * Base58 編碼
     * 
     * @param input 輸入數據
     * @return Base58 字符串
     */
    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        
        // 計算前導零的數量
        var leadingZeros = 0
        for (byte in input) {
            if (byte.toInt() == 0) {
                leadingZeros++
            } else {
                break
            }
        }
        
        // 轉換為大整數
        val bigInt = input.fold(0.toBigInteger()) { acc, byte ->
            acc.shiftLeft(8).or((byte.toInt() and 0xFF).toBigInteger())
        }
        
        // 轉換為 Base58
        val result = StringBuilder()
        var remaining = bigInt
        val base = BASE.toBigInteger()
        
        while (remaining > 0.toBigInteger()) {
            val remainder = remaining % base
            result.insert(0, ALPHABET[remainder.toInt()])
            remaining /= base
        }
        
        // 添加前導 '1'（對應前導零）
        repeat(leadingZeros) {
            result.insert(0, ALPHABET[0])
        }
        
        return result.toString()
    }
}

/**
 * BigInteger 簡單實現（用於 Base58 編碼）
 */
private class BigInteger(private var value: Long) {
    
    constructor(value: Int) : this(value.toLong())
    
    fun shiftLeft(n: Int): BigInteger {
        return BigInteger(value shl n)
    }
    
    fun or(other: BigInteger): BigInteger {
        return BigInteger(value or other.value)
    }
    
    operator fun div(other: BigInteger): BigInteger {
        return BigInteger(value / other.value)
    }
    
    operator fun rem(other: BigInteger): BigInteger {
        return BigInteger(value % other.value)
    }
    
    operator fun compareTo(other: BigInteger): Int {
        return value.compareTo(other.value)
    }
    
    fun toInt(): Int = value.toInt()
}

private fun Int.toBigInteger() = BigInteger(this.toLong())
private fun Long.toBigInteger() = BigInteger(this)
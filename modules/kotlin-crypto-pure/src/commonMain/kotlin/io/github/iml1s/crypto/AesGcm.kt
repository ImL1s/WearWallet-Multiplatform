package io.github.iml1s.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * AES-256-GCM 加密結果
 *
 * @property nonce 12-byte 隨機 nonce (IV)
 * @property ciphertext 加密後的密文
 * @property tag 16-byte 認證標籤
 */
data class AesGcmResult(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AesGcmResult

        if (!nonce.contentEquals(other.nonce)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!tag.contentEquals(other.tag)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }

    /**
     * 編碼為 Base64 字串: nonce || tag || ciphertext
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun toBase64(): String {
        val combined = nonce + tag + ciphertext
        return Base64.encode(combined)
    }

    companion object {
        /**
         * 從 Base64 字串解碼: nonce (12) || tag (16) || ciphertext
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromBase64(encoded: String): AesGcmResult {
            val combined = Base64.decode(encoded)
            require(combined.size >= 28) { "Invalid AesGcmResult: too short (need at least 28 bytes)" }

            val nonce = combined.sliceArray(0 until 12)
            val tag = combined.sliceArray(12 until 28)
            val ciphertext = combined.sliceArray(28 until combined.size)

            return AesGcmResult(nonce, ciphertext, tag)
        }
    }
}

/**
 * 跨平台 AES-256-GCM 加密提供者
 *
 * 使用標準的 GCM 參數:
 * - 密鑰長度: 256 bits (從 PBKDF2 派生)
 * - Nonce 長度: 12 bytes (96 bits)
 * - Tag 長度: 16 bytes (128 bits)
 *
 * 平台實現:
 * - Android: JCA (javax.crypto) - Java 原生加密庫
 * - iOS: CommonCrypto - Apple 原生加密框架
 * - watchOS: CommonCrypto - Apple 原生加密框架
 */
expect object AesGcm {
    /**
     * AES-256-GCM 加密
     *
     * @param plaintext 明文數據
     * @param password 密碼 (將使用 PBKDF2-HMAC-SHA256 派生 256-bit 密鑰)
     * @param salt 鹽值 (預設使用固定鹽值,生產環境應使用隨機鹽)
     * @param iterations PBKDF2 迭代次數 (預設 100,000)
     * @return 加密結果 (nonce + tag + ciphertext)
     * @throws IllegalArgumentException 參數無效
     * @throws IllegalStateException 加密失敗
     */
    suspend fun encrypt(
        plaintext: ByteArray,
        password: String,
        salt: ByteArray = "wearwallet_salt_v2".encodeToByteArray(),
        iterations: Int = 100_000
    ): AesGcmResult

    /**
     * AES-256-GCM 解密
     *
     * @param encrypted 加密結果
     * @param password 密碼 (必須與加密時相同)
     * @param salt 鹽值 (必須與加密時相同)
     * @param iterations PBKDF2 迭代次數 (必須與加密時相同)
     * @return 解密後的明文
     * @throws IllegalArgumentException 參數無效
     * @throws IllegalStateException 解密失敗或認證失敗
     */
    suspend fun decrypt(
        encrypted: AesGcmResult,
        password: String,
        salt: ByteArray = "wearwallet_salt_v2".encodeToByteArray(),
        iterations: Int = 100_000
    ): ByteArray
}

/**
 * 便利擴展函數: String -> Base64 編碼的加密結果
 */
suspend fun AesGcm.encryptString(
    plaintext: String,
    password: String,
    salt: ByteArray = "wearwallet_salt_v2".encodeToByteArray(),
    iterations: Int = 100_000
): String {
    val result = encrypt(plaintext.encodeToByteArray(), password, salt, iterations)
    return result.toBase64()
}

/**
 * 便利擴展函數: Base64 編碼的加密結果 -> String
 */
suspend fun AesGcm.decryptString(
    encryptedBase64: String,
    password: String,
    salt: ByteArray = "wearwallet_salt_v2".encodeToByteArray(),
    iterations: Int = 100_000
): String {
    val encrypted = AesGcmResult.fromBase64(encryptedBase64)
    val decrypted = decrypt(encrypted, password, salt, iterations)
    return decrypted.decodeToString()
}

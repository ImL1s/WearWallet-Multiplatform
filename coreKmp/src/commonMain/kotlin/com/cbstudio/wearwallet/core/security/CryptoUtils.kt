package com.cbstudio.wearwallet.core.security

/**
 * 加密數據結構
 * @property ciphertext 密文
 * @property nonce 隨機數/IV (12 bytes for GCM)
 * @property authTag 認證標籤 (16 bytes)
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val authTag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedData) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!authTag.contentEquals(other.authTag)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }
}

/**
 * 跨平台加密工具接口
 * 使用 expect/actual 模式實現平台特定的加密功能
 */
expect object CryptoUtils {
    /**
     * 計算 SHA256 哈希
     */
    fun sha256(data: ByteArray): ByteArray

    /**
     * 計算 Keccak256 哈希（用於以太坊地址生成）
     */
    fun keccak256(data: ByteArray): ByteArray

    /**
     * 生成隨機字節
     */
    fun randomBytes(size: Int): ByteArray

    /**
     * PBKDF2 密鑰派生
     */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray

    /**
     * AES-256-GCM 加密（舊版本，使用分離的參數）
     */
    fun aesEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

    /**
     * AES-256-GCM 解密（舊版本，使用分離的參數）
     */
    fun aesDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

    /**
     * AES-GCM 加密（返回 EncryptedData 包含 nonce 和 authTag）
     */
    fun aesGcmEncrypt(data: ByteArray, key: ByteArray): EncryptedData

    /**
     * AES-GCM 解密（從 EncryptedData 解密）
     */
    fun aesGcmDecrypt(encryptedData: EncryptedData, key: ByteArray): ByteArray

    /**
     * AES-GCM 加密並附加 AAD 認證數據
     */
    fun aesGcmEncryptWithAad(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray = randomBytes(12),
        aad: ByteArray = byteArrayOf()
    ): EncryptedData

    /**
     * AES-GCM 解密並驗證 AAD 認證數據
     */
    fun aesGcmDecryptWithAad(
        encryptedData: EncryptedData,
        key: ByteArray,
        aad: ByteArray = byteArrayOf()
    ): ByteArray
}

/**
 * 字節數組轉十六進制字符串
 */
fun ByteArray.toHexString(): String {
    return joinToString("") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }
}

/**
 * 十六進制字符串轉字節數組
 */
fun String.hexToByteArray(): ByteArray {
    val hex = removePrefix("0x")
    return ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

/**
 * 字節數組轉二進制字符串
 */
fun ByteArray.toBitString(): String {
    return joinToString("") { byte ->
        byte.toUByte().toString(2).padStart(8, '0')
    }
}

/**
 * 字節數組轉 Base64 字符串
 */
expect fun ByteArray.toBase64(): String

/**
 * Base64 字符串轉字節數組
 */
expect fun String.fromBase64(): ByteArray
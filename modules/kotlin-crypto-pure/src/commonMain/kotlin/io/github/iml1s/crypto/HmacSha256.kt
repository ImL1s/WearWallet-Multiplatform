package io.github.iml1s.crypto

/**
 * HMAC-SHA256 跨平台實現
 * 符合 RFC 2104 標準
 */
object HmacSha256 {
    /**
     * 計算 HMAC-SHA256
     * @param key HMAC 密鑰
     * @param data 需要計算 MAC 的數據
     * @return 32 字節的 HMAC-SHA256 結果
     */
    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "HMAC key cannot be empty" }
        require(data.isNotEmpty()) { "HMAC data cannot be empty" }

        return platformHmacSha256(key, data)
    }
}

/**
 * 平台特定的 HMAC-SHA256 實現
 */
internal expect fun platformHmacSha256(key: ByteArray, data: ByteArray): ByteArray

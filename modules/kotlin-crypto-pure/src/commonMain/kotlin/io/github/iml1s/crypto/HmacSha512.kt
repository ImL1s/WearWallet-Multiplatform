package io.github.iml1s.crypto

/**
 * HMAC-SHA512 跨平台實現
 *
 * 符合以下標準：
 * - RFC 2104: HMAC: Keyed-Hashing for Message Authentication
 * - FIPS 198-1: The Keyed-Hash Message Authentication Code (HMAC)
 *
 * ## 功能特性
 * - 跨平台一致性：所有平台產生相同結果
 * - 安全性：使用平台原生加密庫
 * - 標準相容：通過 RFC 測試向量驗證
 *
 * ## 平台實現
 * - Android: javax.crypto.Mac (HmacSHA512)
 * - iOS: CommonCrypto CCHmac
 * - watchOS: CommonCrypto CCHmac
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc2104">RFC 2104</a>
 * @see <a href="https://csrc.nist.gov/publications/detail/fips/198/1/final">FIPS 198-1</a>
 */
object HmacSha512 {

    /**
     * 計算 HMAC-SHA512
     *
     * ### 使用場景
     * - BIP32 密鑰派生
     * - 訊息認證碼 (MAC)
     * - 密鑰派生函數 (KDF)
     *
     * ### 安全注意事項
     * - **密鑰長度**：建議至少 64 字節（SHA512 輸出長度）
     * - **密鑰清理**：使用後應立即清除密鑰數組
     * - **時序攻擊**：比較 MAC 時使用常數時間比較
     *
     * ### 使用範例
     * ```kotlin
     * val key = "Bitcoin seed".encodeToByteArray()
     * val data = seedBytes
     * val hmac = HmacSha512.hmac(key, data)
     * ```
     *
     * @param key HMAC 密鑰
     * @param data 需要計算 MAC 的數據
     * @return 64 字節的 HMAC-SHA512 結果
     *
     * @throws IllegalArgumentException 如果密鑰或數據為空
     */
    fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "HMAC key cannot be empty" }
        require(data.isNotEmpty()) { "HMAC data cannot be empty" }

        return platformHmacSha512(key, data)
    }
}

/**
 * 平台特定的 HMAC-SHA512 實現
 *
 * 每個平台應提供自己的實現：
 * - Android: 使用 javax.crypto.Mac
 * - iOS: 使用 CommonCrypto CCHmac
 * - watchOS: 使用 CommonCrypto CCHmac
 *
 * @param key HMAC 密鑰
 * @param data 需要計算 MAC 的數據
 * @return 64 字節的 HMAC-SHA512 結果
 */
internal expect fun platformHmacSha512(key: ByteArray, data: ByteArray): ByteArray

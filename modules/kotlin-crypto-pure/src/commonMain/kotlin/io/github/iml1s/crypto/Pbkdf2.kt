package io.github.iml1s.crypto

/**
 * PBKDF2-HMAC-SHA512 跨平台實現
 *
 * 符合以下標準：
 * - RFC 2898 (PKCS #5): Password-Based Key Derivation Function 2
 * - BIP39: Bitcoin Improvement Proposal 39 (助記詞生成)
 *
 * ## 功能特性
 * - 跨平台一致性：所有平台產生相同結果
 * - 安全性：使用平台原生加密庫
 * - 標準相容：通過 BIP39 和 RFC 2898 測試向量驗證
 *
 * ## 平台實現
 * - Android: javax.crypto.SecretKeyFactory (PBKDF2WithHmacSHA512)
 * - iOS: CommonCrypto CCKeyDerivationPBKDF
 * - watchOS: CommonCrypto CCKeyDerivationPBKDF
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc2898">RFC 2898</a>
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39</a>
 */
object Pbkdf2 {

    /**
     * 派生密鑰（通用方法）
     *
     * 使用 PBKDF2-HMAC-SHA512 算法從密碼派生固定長度的密鑰。
     *
     * ### 安全注意事項
     * - **迭代次數**：建議至少 10,000 次（BIP39 使用 2048 次）
     * - **鹽值長度**：建議至少 16 字節（BIP39 使用字符串前綴）
     * - **密碼清理**：使用後應立即清除密碼數組
     *
     * ### BIP39 特殊用法
     * BIP39 使用特定的參數：
     * ```kotlin
     * // BIP39 標準參數
     * val password = mnemonic.encodeToByteArray()  // UTF-8 NFKD 正規化
     * val salt = "mnemonic$passphrase".encodeToByteArray()  // UTF-8 NFKD
     * val iterations = 2048
     * val keyLength = 64  // 512 bits
     * ```
     *
     * @param password 密碼字節數組（將被派生為密鑰）
     * @param salt 鹽值字節數組（增加暴力破解難度）
     * @param iterations 迭代次數（越高越安全但越慢，BIP39 使用 2048）
     * @param keyLength 輸出密鑰長度（字節數，BIP39 使用 64）
     * @return 派生的密鑰
     *
     * @throws IllegalArgumentException 如果參數無效（迭代次數 < 1，密鑰長度 < 1）
     */
    fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int = 2048,
        keyLength: Int = 64
    ): ByteArray {
        require(iterations > 0) { "Iterations must be positive: $iterations" }
        require(keyLength > 0) { "Key length must be positive: $keyLength" }
        require(password.isNotEmpty()) { "Password cannot be empty" }

        return pbkdf2HmacSha512(password, salt, iterations, keyLength)
    }

    /**
     * BIP39 專用：從助記詞派生種子
     *
     * 這是符合 BIP39 標準的助記詞轉種子實現。
     *
     * ### BIP39 規範
     * - 密碼：助記詞（空格分隔的單詞）
     * - 鹽值：字符串 "mnemonic" + 可選的密語（passphrase）
     * - 迭代次數：固定 2048 次
     * - 輸出長度：固定 64 字節（512 位）
     * - 編碼：UTF-8 NFKD 正規化
     *
     * ### 安全考量
     * - **密語保護**：使用強密語可以防止助記詞被盜時的資金損失
     * - **離線計算**：種子派生過程完全離線，不需要網絡連接
     * - **確定性**：相同的助記詞和密語總是產生相同的種子
     *
     * ### 使用範例
     * ```kotlin
     * // 無密語（大多數錢包的默認行為）
     * val seed = Pbkdf2.bip39Seed(
     *     mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
     * )
     *
     * // 使用密語（額外的安全層）
     * val seedWithPassphrase = Pbkdf2.bip39Seed(
     *     mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
     *     passphrase = "my secret passphrase"
     * )
     * ```
     *
     * @param mnemonic 助記詞（空格分隔的 12/15/18/21/24 個單詞）
     * @param passphrase 可選的密語（空字符串表示無密語）
     * @return 64 字節的種子（用於 BIP32 層次確定性密鑰派生）
     *
     * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki#from-mnemonic-to-seed">BIP39: From mnemonic to seed</a>
     */
    fun bip39Seed(
        mnemonic: String,
        passphrase: String = ""
    ): ByteArray {
        // BIP39 規範：
        // 1. 密碼 = 助記詞（UTF-8 NFKD 正規化）
        // 2. 鹽值 = "mnemonic" + passphrase（UTF-8 NFKD 正規化）
        // 3. 迭代 = 2048
        // 4. 長度 = 64 字節

        val password = normalizeNfkd(mnemonic).encodeToByteArray()
        val salt = normalizeNfkd("mnemonic$passphrase").encodeToByteArray()

        return deriveKey(
            password = password,
            salt = salt,
            iterations = BIP39_ITERATIONS,
            keyLength = BIP39_SEED_LENGTH
        )
    }

    /**
     * UTF-8 NFKD 正規化
     *
     * BIP39 要求使用 NFKD (Normalization Form Compatibility Decomposition) 正規化。
     * 這確保了不同 Unicode 表示形式的相同字符產生相同的結果。
     *
     * ### 為什麼需要正規化？
     * - 某些字符有多種 Unicode 表示（例如：é 可以是單個字符 U+00E9 或 e + ´）
     * - NFKD 將複合字符分解為基礎字符 + 組合標記
     * - 確保跨平台、跨鍵盤輸入的一致性
     *
     * @param text 需要正規化的文本
     * @return NFKD 正規化後的文本
     */
    private fun normalizeNfkd(text: String): String {
        // 平台特定實現
        // Android: java.text.Normalizer
        // iOS/watchOS: CFStringNormalize
        return normalizeNfkdPlatform(text)
    }

    /**
     * BIP39 標準迭代次數
     *
     * 固定為 2048 次迭代，這是 BIP39 規範要求的值。
     */
    private const val BIP39_ITERATIONS = 2048

    /**
     * BIP39 標準種子長度
     *
     * 固定為 64 字節（512 位），這是 BIP39 規範要求的種子長度。
     * 這個長度與 BIP32 HD 錢包派生相容。
     */
    private const val BIP39_SEED_LENGTH = 64
}

/**
 * 平台特定的 PBKDF2-HMAC-SHA512 實現
 *
 * 各平台需要實現這個函數：
 * - Android: 使用 javax.crypto.SecretKeyFactory
 * - iOS: 使用 CommonCrypto CCKeyDerivationPBKDF
 * - watchOS: 使用 CommonCrypto CCKeyDerivationPBKDF
 *
 * @param password 密碼字節數組
 * @param salt 鹽值字節數組
 * @param iterations 迭代次數
 * @param keyLength 輸出密鑰長度（字節）
 * @return 派生的密鑰
 */
internal expect fun pbkdf2HmacSha512(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int
): ByteArray

/**
 * 平台特定的 NFKD 正規化實現
 *
 * 各平台需要實現這個函數：
 * - Android: 使用 java.text.Normalizer
 * - iOS/watchOS: 使用 CFStringNormalize
 *
 * @param text 需要正規化的文本
 * @return NFKD 正規化後的文本
 */
internal expect fun normalizeNfkdPlatform(text: String): String

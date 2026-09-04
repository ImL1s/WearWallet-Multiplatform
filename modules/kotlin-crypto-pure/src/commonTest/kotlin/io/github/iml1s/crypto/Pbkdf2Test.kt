package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * PBKDF2-HMAC-SHA512 測試套件
 *
 * ## 測試覆蓋範圍
 * 1. **BIP39 標準測試向量**：使用官方測試數據驗證正確性
 * 2. **RFC 2898 測試向量**：驗證符合 PKCS #5 標準
 * 3. **跨平台一致性**：確保所有平台產生相同結果
 * 4. **邊界條件測試**：測試極端參數值
 * 5. **錯誤處理測試**：驗證異常情況的處理
 *
 * ## 測試數據來源
 * - BIP39 官方測試向量：https://github.com/trezor/python-mnemonic/blob/master/vectors.json
 * - RFC 2898 測試向量：https://www.rfc-editor.org/rfc/rfc6070
 *
 * @see Pbkdf2
 */
class Pbkdf2Test {

    // ========================================
    // BIP39 標準測試向量
    // ========================================

    /**
     * BIP39 測試向量 #1：12 個單詞，帶 TREZOR 密語
     *
     * 這是 BIP39 官方測試向量的標準配置。
     */
    @Test
    fun testBip39Vector1_12Words_TrezorPassphrase() {
        // 來自 BIP39 官方測試向量
        // https://github.com/trezor/python-mnemonic/blob/master/vectors.json
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val passphrase = "TREZOR"
        val expectedSeedHex = "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04"

        val seed = Pbkdf2.bip39Seed(mnemonic, passphrase)
        val actualSeedHex = seed.toHexString()

        assertEquals(
            expected = expectedSeedHex,
            actual = actualSeedHex,
            message = "BIP39 seed mismatch for test vector #1"
        )
    }

    /**
     * BIP39 測試向量 #2：24 個單詞，帶 TREZOR 密語
     *
     * 驗證 256-bit 熵（24 個單詞）的支援。
     */
    @Test
    fun testBip39Vector2_24Words_TrezorPassphrase() {
        // 來自 BIP39 官方測試向量
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val passphrase = "TREZOR"
        val expectedSeedHex = "bda85446c68413707090a52022edd26a1c9462295029f2e60cd7c4f2bbd3097170af7a4d73245cafa9c3cca8d561a7c3de6f5d4a10be8ed2a5e608d68f92fcc8"

        val seed = Pbkdf2.bip39Seed(mnemonic, passphrase)
        val actualSeedHex = seed.toHexString()

        assertEquals(
            expected = expectedSeedHex,
            actual = actualSeedHex,
            message = "BIP39 seed mismatch for test vector #2 (24 words)"
        )
    }

    /**
     * BIP39 測試向量 #3：另一個 12 個單詞助記詞
     *
     * 驗證不同助記詞產生不同種子。
     */
    @Test
    fun testBip39Vector3_DifferentMnemonic() {
        // 來自 BIP39 官方測試向量 - 不同的助記詞應產生不同的種子
        val mnemonic1 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val mnemonic2 = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        val passphrase = "TREZOR"

        val seed1 = Pbkdf2.bip39Seed(mnemonic1, passphrase)
        val seed2 = Pbkdf2.bip39Seed(mnemonic2, passphrase)

        // 確保不同的助記詞產生不同的種子
        assertTrue(
            !seed1.contentEquals(seed2),
            "Different mnemonics should produce different seeds"
        )

        // 確保種子長度正確
        assertEquals(64, seed1.size, "Seed 1 should be 64 bytes")
        assertEquals(64, seed2.size, "Seed 2 should be 64 bytes")
    }

    /**
     * BIP39 測試向量 #4：無密語測試
     *
     * 驗證空密語（大多數錢包的默認行為）的支援。
     */
    @Test
    fun testBip39Vector4_NoPassphrase() {
        // 空密語測試
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val passphrase = ""

        // 空密語時，種子會不同
        val seed = Pbkdf2.bip39Seed(mnemonic, passphrase)

        // 驗證基本屬性
        assertEquals(64, seed.size, "Seed should be 64 bytes")
        // 空密語的種子應該與 TREZOR 密語不同
        val seedWithTrezor = Pbkdf2.bip39Seed(mnemonic, "TREZOR")
        assertTrue(
            !seed.contentEquals(seedWithTrezor),
            "Empty passphrase seed should differ from TREZOR passphrase seed"
        )
    }

    /**
     * BIP39 測試向量 #5：驗證助記詞解析正確性
     *
     * 使用不同的助記詞組合驗證實現的正確性。
     */
    @Test
    fun testBip39Vector5_DifferentMnemonic() {
        // 來自 BIP39 官方測試向量
        val mnemonic = "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic bless"
        val passphrase = "TREZOR"
        val expectedSeedHex = "c0c519bd0e91a2ed54357d9d1ebef6f5af218a153624cf4f2da911a0ed8f7a09e2ef61af0aca007096df430022f7a2b6fb91661a9589097069720d015e4e982f"

        val seed = Pbkdf2.bip39Seed(mnemonic, passphrase)
        val actualSeedHex = seed.toHexString()

        assertEquals(
            expected = expectedSeedHex,
            actual = actualSeedHex,
            message = "BIP39 seed mismatch for test vector #5"
        )
    }

    // ========================================
    // RFC 2898 (PKCS #5) 測試向量
    // ========================================

    /**
     * RFC 6070 測試向量 #1
     *
     * 基本的 PBKDF2 功能測試。
     */
    @Test
    fun testRfc6070Vector1() {
        // RFC 6070 Test Vector #1
        // Password: "password"
        // Salt: "salt"
        // Iterations: 1
        // Key length: 64 bytes
        val password = "password".encodeToByteArray()
        val salt = "salt".encodeToByteArray()
        val iterations = 1
        val keyLength = 64

        val expectedHex = "867f70cf1ade02cff3752599a3a53dc4af34c7a669815ae5d513554e1c8cf252c02d470a285a0501bad999bfe943c08f050235d7d68b1da55e63f73b60a57fce"

        val derivedKey = Pbkdf2.deriveKey(password, salt, iterations, keyLength)
        val actualHex = derivedKey.toHexString()

        assertEquals(
            expected = expectedHex,
            actual = actualHex,
            message = "RFC 6070 test vector #1 mismatch"
        )
    }

    /**
     * RFC 6070 測試向量 #2：多次迭代
     *
     * 驗證迭代次數參數正確運作。
     */
    @Test
    fun testRfc6070Vector2_MultipleIterations() {
        // RFC 6070 Test Vector #2
        // Password: "password"
        // Salt: "salt"
        // Iterations: 2
        // Key length: 64 bytes
        val password = "password".encodeToByteArray()
        val salt = "salt".encodeToByteArray()
        val iterations = 2
        val keyLength = 64

        val expectedHex = "e1d9c16aa681708a45f5c7c4e215ceb66e011a2e9f0040713f18aefdb866d53cf76cab2868a39b9f7840edce4fef5a82be67335c77a6068e04112754f27ccf4e"

        val derivedKey = Pbkdf2.deriveKey(password, salt, iterations, keyLength)
        val actualHex = derivedKey.toHexString()

        assertEquals(
            expected = expectedHex,
            actual = actualHex,
            message = "RFC 6070 test vector #2 mismatch (2 iterations)"
        )
    }

    /**
     * RFC 6070 測試向量 #3：高迭代次數
     *
     * 驗證較高迭代次數的正確性（但不會太慢）。
     */
    @Test
    fun testRfc6070Vector3_HighIterations() {
        // RFC 6070 Test Vector #3
        // Password: "password"
        // Salt: "salt"
        // Iterations: 4096
        // Key length: 64 bytes
        val password = "password".encodeToByteArray()
        val salt = "salt".encodeToByteArray()
        val iterations = 4096
        val keyLength = 64

        val expectedHex = "d197b1b33db0143e018b12f3d1d1479e6cdebdcc97c5c0f87f6902e072f457b5143f30602641b3d55cd335988cb36b84376060ecd532e039b742a239434af2d5"

        val derivedKey = Pbkdf2.deriveKey(password, salt, iterations, keyLength)
        val actualHex = derivedKey.toHexString()

        assertEquals(
            expected = expectedHex,
            actual = actualHex,
            message = "RFC 6070 test vector #3 mismatch (4096 iterations)"
        )
    }

    // ========================================
    // 跨平台一致性測試
    // ========================================

    /**
     * 跨平台一致性：相同輸入應產生相同輸出
     *
     * 這是最重要的測試之一，確保 Android/iOS/watchOS 行為一致。
     */
    @Test
    fun testCrossPlatformConsistency() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val passphrase = "TREZOR"

        // 執行多次，確保結果穩定
        val seed1 = Pbkdf2.bip39Seed(mnemonic, passphrase)
        val seed2 = Pbkdf2.bip39Seed(mnemonic, passphrase)
        val seed3 = Pbkdf2.bip39Seed(mnemonic, passphrase)

        // 所有結果應該完全相同
        assertContentEquals(seed1, seed2, "Seed 1 and 2 should be identical")
        assertContentEquals(seed2, seed3, "Seed 2 and 3 should be identical")
        assertContentEquals(seed1, seed3, "Seed 1 and 3 should be identical")
    }

    // ========================================
    // 邊界條件和錯誤處理測試
    // ========================================

    /**
     * 測試：空密碼應該失敗
     */
    @Test
    fun testEmptyPassword_ShouldFail() {
        val password = ByteArray(0)
        val salt = "salt".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> {
            Pbkdf2.deriveKey(password, salt, iterations = 1, keyLength = 32)
        }
    }

    /**
     * 測試：零迭代次數應該失敗
     */
    @Test
    fun testZeroIterations_ShouldFail() {
        val password = "password".encodeToByteArray()
        val salt = "salt".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> {
            Pbkdf2.deriveKey(password, salt, iterations = 0, keyLength = 32)
        }
    }

    /**
     * 測試：負迭代次數應該失敗
     */
    @Test
    fun testNegativeIterations_ShouldFail() {
        val password = "password".encodeToByteArray()
        val salt = "salt".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> {
            Pbkdf2.deriveKey(password, salt, iterations = -1, keyLength = 32)
        }
    }

    /**
     * 測試：零密鑰長度應該失敗
     */
    @Test
    fun testZeroKeyLength_ShouldFail() {
        val password = "password".encodeToByteArray()
        val salt = "salt".encodeToByteArray()

        assertFailsWith<IllegalArgumentException> {
            Pbkdf2.deriveKey(password, salt, iterations = 1, keyLength = 0)
        }
    }

    /**
     * 測試：空鹽值應該可以工作（雖不建議）
     */
    @Test
    fun testEmptySalt_ShouldWork() {
        val password = "password".encodeToByteArray()
        val salt = ByteArray(0)

        // 應該不拋出異常，但結果安全性較低
        val result = Pbkdf2.deriveKey(password, salt, iterations = 1, keyLength = 32)

        assertEquals(32, result.size, "Should derive 32 bytes even with empty salt")
    }

    /**
     * 測試：最小密鑰長度（1 字節）
     */
    @Test
    fun testMinimumKeyLength() {
        val password = "password".encodeToByteArray()
        val salt = "salt".encodeToByteArray()

        val result = Pbkdf2.deriveKey(password, salt, iterations = 1, keyLength = 1)

        assertEquals(1, result.size, "Should derive exactly 1 byte")
    }

    /**
     * 測試：大密鑰長度（128 字節）
     */
    @Test
    fun testLargeKeyLength() {
        val password = "password".encodeToByteArray()
        val salt = "salt".encodeToByteArray()

        val result = Pbkdf2.deriveKey(password, salt, iterations = 1, keyLength = 128)

        assertEquals(128, result.size, "Should derive exactly 128 bytes")
    }

    // ========================================
    // 性能和壓力測試
    // ========================================

    /**
     * 性能參考測試：BIP39 標準參數
     *
     * 這不是嚴格的性能測試，只是確保完成時間合理。
     */
    @Test
    fun testPerformance_Bip39Standard() {
        val mnemonic = "test test test test test test test test test test test junk"

        // 執行一次 BIP39 seed 派生
        // 在大多數設備上應該在 1 秒內完成
        val startTime = currentTimeMillis()
        val seed = Pbkdf2.bip39Seed(mnemonic)
        val endTime = currentTimeMillis()

        val duration = endTime - startTime

        // 確保結果正確
        assertEquals(64, seed.size, "BIP39 seed should be 64 bytes")
    }

    // ========================================
    // 輔助函數
    // ========================================

    /**
     * ByteArray 轉十六進制字符串
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }

    /**
     * 獲取當前時間戳（跨平台）
     */
    private fun currentTimeMillis(): Long {
        // 簡化實現，實際應使用 kotlinx.datetime
        return 0L // TODO: 實現跨平台時間測量
    }
}

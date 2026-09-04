package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.Clock

/**
 * Keccak256 跨平台一致性測試
 *
 * 驗證：
 * 1. Keccak256 在所有平台上產生相同結果
 * 2. 符合以太坊標準測試向量
 * 3. 可正確用於以太坊地址生成
 *
 * 測試向量來源：
 * - Ethereum Yellow Paper
 * - https://emn178.github.io/online-tools/keccak_256.html
 * - Web3.js 和 Ethers.js 測試套件
 */
class Keccak256CrossPlatformTest {

    /**
     * 測試空字節數組
     * Keccak256("") = 0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470
     */
    @Test
    fun testKeccak256_emptyInput() {
        val input = ByteArray(0)
        val expected = "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"

        val result = CryptoUtils.keccak256(input)
        val resultHex = result.toHexString()

        assertEquals(expected, resultHex, "Empty input Keccak256 hash mismatch")
        assertEquals(32, result.size, "Keccak256 should produce 32 bytes")
    }

    /**
     * 測試簡單字符串 "abc"
     * Keccak256("abc") = 0x4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45
     */
    @Test
    fun testKeccak256_abc() {
        val input = "abc".encodeToByteArray()
        val expected = "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"

        val result = CryptoUtils.keccak256(input)
        val resultHex = result.toHexString()

        assertEquals(expected, resultHex, "Keccak256('abc') hash mismatch")
    }

    /**
     * 測試字符串 "Hello, World!"
     * Keccak256("Hello, World!") = 0xacaf3289d7b601cbd114fb36c4d29c85bbfd5e133f14cb355c3fd8d99367964f
     */
    @Test
    fun testKeccak256_helloWorld() {
        val input = "Hello, World!".encodeToByteArray()
        val expected = "acaf3289d7b601cbd114fb36c4d29c85bbfd5e133f14cb355c3fd8d99367964f"

        val result = CryptoUtils.keccak256(input)
        val resultHex = result.toHexString()

        assertEquals(expected, resultHex, "Keccak256('Hello, World!') hash mismatch")
    }

    /**
     * 測試長字符串
     * 驗證處理較大輸入的能力
     */
    @Test
    fun testKeccak256_longString() {
        val input = "The quick brown fox jumps over the lazy dog".encodeToByteArray()
        val expected = "4d741b6f1eb29cb2a9b9911c82f56fa8d73b04959d3d9d222895df6c0b28aa15"

        val result = CryptoUtils.keccak256(input)
        val resultHex = result.toHexString()

        assertEquals(expected, resultHex, "Keccak256(long string) hash mismatch")
    }

    /**
     * 測試 Keccak256 與 SHA3-256 的差異
     *
     * Keccak256 是以太坊使用的原始 Keccak 算法
     * SHA3-256 是 NIST 標準化後的版本（padding 不同）
     *
     * 對於 "abc":
     * - Keccak256: 0x4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45
     * - SHA3-256:  0x3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532
     */
    @Test
    fun testKeccak256_notSHA3() {
        val input = "abc".encodeToByteArray()
        val keccak256Expected = "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"
        val sha3256Result = "3a985da74fe225b2045c172d6bd390bd855f086e3e9d525b46bfe24511431532"

        val result = CryptoUtils.keccak256(input)
        val resultHex = result.toHexString()

        assertEquals(keccak256Expected, resultHex, "Should use Keccak256, not SHA3-256")
        assertTrue(
            resultHex != sha3256Result,
            "Keccak256 and SHA3-256 should produce different results"
        )
    }

    /**
     * 測試以太坊地址生成場景
     *
     * 以太坊地址生成步驟：
     * 1. 取公鑰（去掉 0x04 前綴的 64 字節）
     * 2. Keccak256 哈希
     * 3. 取最後 20 字節
     * 4. 加上 0x 前綴
     */
    @Test
    fun testKeccak256_ethereumAddressGeneration() {
        // 測試公鑰（不含 0x04 前綴的 64 字節）
        val publicKey = "3a443d8381a6798a70c6ff9304bdc8cb0163c23211d11628fae52ef9e0dca11a001cf066d56a8156fc201cd5df8a36ef694eecd258903fca7086c1fae7441e1d"
            .hexToByteArray()

        // Keccak256 哈希
        val hash = CryptoUtils.keccak256(publicKey)

        // 取最後 20 字節作為地址
        val address = hash.takeLast(20).toByteArray()
        val addressHex = "0x" + address.toHexString()

        // 驗證地址格式
        assertEquals(42, addressHex.length, "Ethereum address should be 42 chars (0x + 40 hex)")
        assertTrue(addressHex.startsWith("0x"), "Ethereum address should start with 0x")

        // 實際的以太坊地址（已驗證）
        val expectedAddress = "0x2f015c60e0be116b1f0cd534704db9c92118fb6a"
        assertEquals(expectedAddress, addressHex, "Ethereum address generation mismatch")
    }

    /**
     * 測試多次哈希產生相同結果（確定性）
     */
    @Test
    fun testKeccak256_deterministic() {
        val input = "deterministic test".encodeToByteArray()

        val result1 = CryptoUtils.keccak256(input)
        val result2 = CryptoUtils.keccak256(input)
        val result3 = CryptoUtils.keccak256(input)

        assertTrue(result1.contentEquals(result2), "Keccak256 should be deterministic")
        assertTrue(result2.contentEquals(result3), "Keccak256 should be deterministic")
    }

    /**
     * 測試不同輸入產生不同輸出（抗碰撞性）
     */
    @Test
    fun testKeccak256_collisionResistance() {
        val input1 = "test1".encodeToByteArray()
        val input2 = "test2".encodeToByteArray()

        val result1 = CryptoUtils.keccak256(input1)
        val result2 = CryptoUtils.keccak256(input2)

        assertTrue(
            !result1.contentEquals(result2),
            "Different inputs should produce different hashes"
        )
    }

    /**
     * 測試邊界情況：單字節輸入
     */
    @Test
    fun testKeccak256_singleByte() {
        val input = byteArrayOf(0x00)
        val expected = "bc36789e7a1e281436464229828f817d6612f7b477d66591ff96a9e064bcc98a"

        val result = CryptoUtils.keccak256(input)
        val resultHex = result.toHexString()

        assertEquals(expected, resultHex, "Single byte Keccak256 hash mismatch")
    }

    /**
     * 測試二進制數據（非 UTF-8）
     */
    @Test
    fun testKeccak256_binaryData() {
        val input = byteArrayOf(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
        )
        // 實際的 Keccak256 輸出（已驗證）
        val expected = "01aec967ba5d2a807edd3fd8942c6f72c0c62961bfeb10c1f79c756f7294b0e3"

        val result = CryptoUtils.keccak256(input)
        val resultHex = result.toHexString()

        assertEquals(expected, resultHex, "Binary data Keccak256 hash mismatch")
    }

    /**
     * 測試 256 字節輸入（Keccak 內部狀態大小）
     */
    @Test
    fun testKeccak256_256bytes() {
        val input = ByteArray(256) { it.toByte() }

        val result = CryptoUtils.keccak256(input)

        assertNotNull(result, "Keccak256 should handle 256-byte input")
        assertEquals(32, result.size, "Keccak256 should produce 32 bytes")
    }

    /**
     * 測試大輸入（1 MB）
     */
    @Test
    fun testKeccak256_largeInput() {
        val input = ByteArray(1024 * 1024) { (it % 256).toByte() }

        val result = CryptoUtils.keccak256(input)

        assertNotNull(result, "Keccak256 should handle large input")
        assertEquals(32, result.size, "Keccak256 should produce 32 bytes")
    }

    /**
     * 性能基準測試：1000 次哈希
     */
    @Test
    fun testKeccak256_performance() {
        val input = "performance test input".encodeToByteArray()
        val iterations = 1000

        val startTime = Clock.System.now().toEpochMilliseconds()

        repeat(iterations) {
            CryptoUtils.keccak256(input)
        }

        val endTime = Clock.System.now().toEpochMilliseconds()
        val duration = endTime - startTime

        println("Keccak256 performance: $iterations iterations in $duration ms")
        println("Average: ${duration.toDouble() / iterations} ms per hash")

        // 確保性能合理（每次哈希不超過 10ms）
        assertTrue(duration < iterations * 10, "Keccak256 performance is too slow")
    }
}

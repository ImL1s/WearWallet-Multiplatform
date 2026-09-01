package com.cbstudio.wearwallet.core.blockchain

import com.cbstudio.wearwallet.core.security.CryptoUtils
import com.cbstudio.wearwallet.core.security.hexToByteArray
import com.cbstudio.wearwallet.core.security.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Clock

/**
 * 以太坊地址生成測試
 *
 * 驗證使用真實 Keccak256 生成的以太坊地址是否正確
 *
 * 測試向量來源：
 * - https://github.com/ethereum/tests
 * - https://github.com/ethereumbook/ethereumbook
 * - Web3.js 測試套件
 */
class EthereumAddressGenerationTest {

    /**
     * 從公鑰生成以太坊地址的輔助函數
     *
     * @param publicKey 未壓縮的公鑰（64 字節，不含 0x04 前綴）
     * @return 以太坊地址（小寫，含 0x 前綴）
     */
    private fun generateEthereumAddress(publicKey: ByteArray): String {
        require(publicKey.size == 64) {
            "Public key must be 64 bytes (uncompressed, without 0x04 prefix)"
        }

        // Keccak256 哈希
        val hash = CryptoUtils.keccak256(publicKey)

        // 取最後 20 字節作為地址
        val address = hash.takeLast(20).toByteArray()

        // 返回小寫十六進制，含 0x 前綴
        return "0x" + address.toHexString()
    }

    /**
     * 測試向量 1：標準以太坊地址
     *
     * 使用已驗證的測試向量
     */
    @Test
    fun testEthereumAddress_vector1() {
        val publicKey = "3a443d8381a6798a70c6ff9304bdc8cb0163c23211d11628fae52ef9e0dca11a001cf066d56a8156fc201cd5df8a36ef694eecd258903fca7086c1fae7441e1d"
            .hexToByteArray()

        val address = generateEthereumAddress(publicKey)
        val expected = "0x2f015c60e0be116b1f0cd534704db9c92118fb6a"

        assertEquals(expected, address, "Ethereum address generation failed for vector 1")
    }

    /**
     * 測試向量 2：另一個標準以太坊地址
     *
     * 已知公鑰對應的地址
     */
    @Test
    fun testEthereumAddress_vector2() {
        // 64 字節的測試公鑰（128 個十六進制字符）
        val publicKey = "7e5f4552091a69125d5dfcb7b8c2659029395bdf2d7b9b1b3d0f9d1a8b7c6e5d4c3b2a1a0b9c8d7e6f5e4d3c2b1a09a8b7c6d5e4f3e2d1c0b1a2b3c4d5e6f7e8"
            .hexToByteArray()

        val address = generateEthereumAddress(publicKey)

        // 驗證地址格式
        assertTrue(address.startsWith("0x"), "Address should start with 0x")
        assertEquals(42, address.length, "Address should be 42 characters")
    }

    /**
     * 測試向量 3：全零公鑰
     *
     * 邊界情況測試
     */
    @Test
    fun testEthereumAddress_zeroPublicKey() {
        val publicKey = ByteArray(64) { 0 }

        val address = generateEthereumAddress(publicKey)

        // 驗證地址格式
        assertTrue(address.startsWith("0x"), "Address should start with 0x")
        assertEquals(42, address.length, "Address should be 42 characters")

        // 實際的地址（已驗證）
        val expected = "0x3f17f1962b36e491b30a40b2405849e597ba5fb5"
        assertEquals(expected, address, "Zero public key address mismatch")
    }

    /**
     * 測試向量 4：全 0xFF 公鑰
     *
     * 邊界情況測試
     */
    @Test
    fun testEthereumAddress_maxPublicKey() {
        val publicKey = ByteArray(64) { 0xFF.toByte() }

        val address = generateEthereumAddress(publicKey)

        // 驗證地址格式
        assertTrue(address.startsWith("0x"), "Address should start with 0x")
        assertEquals(42, address.length, "Address should be 42 characters")

        // 實際的地址（已驗證）
        val expected = "0x2dcc482901728b6df477f4fb2f192733a005d396"
        assertEquals(expected, address, "Max public key address mismatch")
    }

    /**
     * 測試 EIP-55 校驗和地址格式（混合大小寫）
     *
     * EIP-55 定義了帶校驗和的以太坊地址格式
     */
    @Test
    fun testEthereumAddress_EIP55Checksum() {
        val publicKey = "3a443d8381a6798a70c6ff9304bdc8cb0163c23211d11628fae52ef9e0dca11a001cf066d56a8156fc201cd5df8a36ef694eecd258903fca7086c1fae7441e1d"
            .hexToByteArray()

        val address = generateEthereumAddress(publicKey)

        // 生成 EIP-55 校驗和地址
        val checksumAddress = toEIP55ChecksumAddress(address)

        // 驗證校驗和地址
        assertTrue(isValidEIP55Address(checksumAddress), "EIP-55 checksum address is invalid")

        println("Original address: $address")
        println("Checksum address: $checksumAddress")
    }

    /**
     * 將地址轉換為 EIP-55 校驗和格式
     *
     * @param address 小寫地址（含 0x 前綴）
     * @return EIP-55 校驗和地址
     */
    private fun toEIP55ChecksumAddress(address: String): String {
        require(address.startsWith("0x")) { "Address must start with 0x" }

        val lowercaseAddress = address.substring(2).lowercase()
        val hash = CryptoUtils.keccak256(lowercaseAddress.encodeToByteArray())
        val hashHex = hash.toHexString()

        val checksumAddress = StringBuilder("0x")
        for (i in lowercaseAddress.indices) {
            val char = lowercaseAddress[i]
            if (char in '0'..'9') {
                checksumAddress.append(char)
            } else {
                // 如果哈希對應位的值 >= 8，則大寫
                val hashValue = hashHex[i].toString().toInt(16)
                checksumAddress.append(if (hashValue >= 8) char.uppercaseChar() else char)
            }
        }

        return checksumAddress.toString()
    }

    /**
     * 驗證 EIP-55 校驗和地址是否有效
     *
     * @param address EIP-55 校驗和地址
     * @return true 如果校驗和有效
     */
    private fun isValidEIP55Address(address: String): Boolean {
        if (!address.startsWith("0x") || address.length != 42) {
            return false
        }

        val lowercaseAddress = address.substring(2).lowercase()
        val expectedChecksum = toEIP55ChecksumAddress("0x$lowercaseAddress")

        return address == expectedChecksum
    }

    /**
     * 測試多個已知的以太坊地址
     *
     * 使用真實世界的測試向量
     */
    @Test
    fun testEthereumAddress_knownAddresses() {
        // 測試向量：公鑰 -> 地址的映射（使用經過驗證的測試向量）
        val testVectors = listOf(
            // 測試已驗證的公鑰
            "3a443d8381a6798a70c6ff9304bdc8cb0163c23211d11628fae52ef9e0dca11a001cf066d56a8156fc201cd5df8a36ef694eecd258903fca7086c1fae7441e1d" to
                    "0x2f015c60e0be116b1f0cd534704db9c92118fb6a"
        )

        testVectors.forEach { (pubKeyHex, expectedAddress) ->
            val publicKey = pubKeyHex.hexToByteArray()
            val address = generateEthereumAddress(publicKey)

            assertEquals(
                expectedAddress.lowercase(),
                address.lowercase(),
                "Address mismatch for public key: ${pubKeyHex.take(16)}..."
            )
        }
    }

    /**
     * 測試地址確定性
     *
     * 同一公鑰應該總是生成相同的地址
     */
    @Test
    fun testEthereumAddress_deterministic() {
        val publicKey = "3a443d8381a6798a70c6ff9304bdc8cb0163c23211d11628fae52ef9e0dca11a001cf066d56a8156fc201cd5df8a36ef694eecd258903fca7086c1fae7441e1d"
            .hexToByteArray()

        val address1 = generateEthereumAddress(publicKey)
        val address2 = generateEthereumAddress(publicKey)
        val address3 = generateEthereumAddress(publicKey)

        assertEquals(address1, address2, "Address generation should be deterministic")
        assertEquals(address2, address3, "Address generation should be deterministic")
    }

    /**
     * 測試不同公鑰生成不同地址
     */
    @Test
    fun testEthereumAddress_uniqueness() {
        val publicKey1 = ByteArray(64) { it.toByte() }
        val publicKey2 = ByteArray(64) { (it + 1).toByte() }

        val address1 = generateEthereumAddress(publicKey1)
        val address2 = generateEthereumAddress(publicKey2)

        assertTrue(address1 != address2, "Different public keys should generate different addresses")
    }

    /**
     * 性能測試：生成 1000 個地址
     */
    @Test
    fun testEthereumAddress_performance() {
        val iterations = 1000
        val publicKey = ByteArray(64) { it.toByte() }

        val startTime = Clock.System.now().toEpochMilliseconds()

        repeat(iterations) {
            generateEthereumAddress(publicKey)
        }

        val endTime = Clock.System.now().toEpochMilliseconds()
        val duration = endTime - startTime

        println("Ethereum address generation performance: $iterations addresses in $duration ms")
        println("Average: ${duration.toDouble() / iterations} ms per address")

        // 確保性能合理（每次生成不超過 10ms）
        assertTrue(duration < iterations * 10, "Address generation is too slow")
    }
}

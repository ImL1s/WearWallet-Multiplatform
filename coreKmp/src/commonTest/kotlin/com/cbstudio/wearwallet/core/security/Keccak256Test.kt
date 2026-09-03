package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.github.iml1s.crypto.Keccak256
import io.github.iml1s.crypto.keccak256

/**
 * Keccak-256 測試
 *
 * 測試向量來源：
 * - https://emn178.github.io/online-tools/keccak_256.html
 * - https://ethereum.stackexchange.com/questions/17051/how-to-generate-a-new-ethereum-address-and-private-key-from-a-seed
 */
class Keccak256Test {

    @Test
    fun testEmptyString() {
        // Keccak-256("") = c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470
        val input = ByteArray(0)
        val hash = Keccak256.hash(input)
        val expected = "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
        assertEquals(expected, hash.toHexString())
    }

    @Test
    fun testSimpleString() {
        // Keccak-256("hello") = 1c8aff950685c2ed4bc3174f3472287b56d9517b9c948127319a09a7a36deac8
        val input = "hello".encodeToByteArray()
        val hash = Keccak256.hash(input)
        val expected = "1c8aff950685c2ed4bc3174f3472287b56d9517b9c948127319a09a7a36deac8"
        assertEquals(expected, hash.toHexString())
    }

    @Test
    fun testEthereumAddressGeneration() {
        // 測試以太坊地址生成功能
        // 使用測試公鑰（65 字節，含 0x04 前綴）
        val publicKeyHex = "04" + "a".repeat(128) // 65 字節公鑰
        val publicKey = publicKeyHex.hexToByteArray()

        val address = Keccak256.ethereumAddress(publicKey)

        // 驗證地址格式
        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length) // "0x" + 40 hex chars
    }

    @Test
    fun testEthereumAddressFrom64BytePublicKey() {
        // 測試不含 0x04 前綴的 64 字節公鑰
        val publicKeyHex = "a".repeat(128) // 64 字節公鑰
        val publicKey = publicKeyHex.hexToByteArray()

        val address = Keccak256.ethereumAddress(publicKey)

        // 驗證地址格式
        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length)
    }

    @Test
    fun testEthereumAddressInvalidLength() {
        // 無效的公鑰長度應拋出異常
        val invalidPublicKey = ByteArray(32) { 0 }
        assertFailsWith<IllegalArgumentException> {
            Keccak256.ethereumAddress(invalidPublicKey)
        }
    }

    @Test
    fun testExtensionFunction() {
        // 測試 ByteArray.keccak256() 擴展函數
        val input = "hello".encodeToByteArray()
        val hash = input.keccak256()
        val expected = "1c8aff950685c2ed4bc3174f3472287b56d9517b9c948127319a09a7a36deac8"
        assertEquals(expected, hash.toHexString())
    }

    @Test
    fun testStringExtensionFunction() {
        // 測試 String.keccak256() 擴展函數
        val hexString = "68656c6c6f" // "hello" in hex
        val hash = hexString.keccak256()
        val expected = "1c8aff950685c2ed4bc3174f3472287b56d9517b9c948127319a09a7a36deac8"
        assertEquals(expected, hash.toHexString())
    }

    @Test
    fun testLongData() {
        // 測試較長的數據
        val input = ByteArray(1000) { it.toByte() }
        val hash = Keccak256.hash(input)
        // 驗證輸出長度是 32 字節
        assertEquals(32, hash.size)
    }

    // 輔助函數
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        val hex = this.removePrefix("0x")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

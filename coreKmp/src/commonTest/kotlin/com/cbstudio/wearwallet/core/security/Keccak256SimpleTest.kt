package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import io.github.iml1s.crypto.Keccak256
import io.github.iml1s.crypto.keccak256
import kotlin.test.assertTrue

/**
 * Keccak-256 簡單測試
 * 使用已驗證的標準測試向量
 */
class Keccak256SimpleTest {

    @Test
    fun testEmptyString() {
        // Keccak-256("") - 這是標準的空字串測試向量
        val input = ByteArray(0)
        val hash = Keccak256.hash(input)
        val expected = "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
        assertEquals(expected, hash.toHexString())
    }

    @Test
    fun testHello() {
        // Keccak-256("hello")
        val input = "hello".encodeToByteArray()
        val hash = Keccak256.hash(input)
        val expected = "1c8aff950685c2ed4bc3174f3472287b56d9517b9c948127319a09a7a36deac8"
        assertEquals(expected, hash.toHexString())
    }

    @Test
    fun testOutputLength() {
        // 確保 Keccak-256 輸出是 32 字節
        val input = "test".encodeToByteArray()
        val hash = Keccak256.hash(input)
        assertEquals(32, hash.size)
    }

    @Test
    fun testDeterministic() {
        // 相同輸入應產生相同輸出
        val input = "deterministic".encodeToByteArray()
        val hash1 = Keccak256.hash(input)
        val hash2 = Keccak256.hash(input)
        assertEquals(hash1.toHexString(), hash2.toHexString())
    }

    @Test
    fun testEthereumAddressBasic() {
        // 簡單驗證 ethereumAddress 函數能運行
        // 創建一個 64 字節的假公鑰
        val publicKey = ByteArray(64) { it.toByte() }
        val address = Keccak256.ethereumAddress(publicKey)

        // 驗證格式
        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length) // "0x" + 40 hex chars
    }

    @Test
    fun testExtensionFunction() {
        val input = "test".encodeToByteArray()
        val hash1 = Keccak256.hash(input)
        val hash2 = input.keccak256()
        assertEquals(hash1.toHexString(), hash2.toHexString())
    }

    // 輔助函數
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            byte.toUByte().toString(16).padStart(2, '0')
        }
    }
}

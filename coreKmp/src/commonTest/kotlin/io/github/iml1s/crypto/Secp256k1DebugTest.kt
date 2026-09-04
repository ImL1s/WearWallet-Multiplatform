package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 診斷測試：檢查 Secp256k1Provider 的實際行為
 */
class Secp256k1DebugTest {

    @Test
    fun debug_publicKeyFormat() {
        val privateKey = ByteArray(32) { 0x01 }

        // 測試壓縮格式
        val compressedKey = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        println("🔍 Compressed public key:")
        println("   Size: ${compressedKey.size} bytes")
        println("   Prefix: 0x${compressedKey[0].toHex()}")
        println("   Full: ${compressedKey.toHex()}")

        // 測試未壓縮格式
        val uncompressedKey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)
        println("🔍 Uncompressed public key:")
        println("   Size: ${uncompressedKey.size} bytes")
        println("   Prefix: 0x${uncompressedKey[0].toHex()}")
        println("   Full: ${uncompressedKey.copyOfRange(0, minOf(10, uncompressedKey.size)).toHex()}...")

        // 驗證
        assertEquals(33, compressedKey.size, "Compressed key should be 33 bytes")
        assertEquals(65, uncompressedKey.size, "Uncompressed key should be 65 bytes")

        // 驗證前綴
        kotlin.test.assertTrue(
            compressedKey[0] == 0x02.toByte() || compressedKey[0] == 0x03.toByte(),
            "Compressed key should start with 0x02 or 0x03, got 0x${compressedKey[0].toHex()}"
        )
        assertEquals(0x04.toByte(), uncompressedKey[0], "Uncompressed key should start with 0x04")
    }

    // Helper function for hex conversion
    private fun Byte.toHex(): String {
        val value = this.toInt() and 0xFF
        return value.toString(16).padStart(2, '0')
    }

    private fun ByteArray.toHex(): String {
        return this.joinToString("") { it.toHex() }
    }
}

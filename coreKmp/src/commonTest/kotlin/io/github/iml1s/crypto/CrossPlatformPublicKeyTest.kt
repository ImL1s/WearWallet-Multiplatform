// 跨平台公鑰壓縮/解壓縮一致性測試
package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrossPlatformPublicKeyTest {

    @Test
    fun testPublicKeyCompressionConsistency() {
        val privateKey = ByteArray(32) { it.toByte() }

        // 測試壓縮格式
        val compressed = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        assertEquals(33, compressed.size, "Compressed key should be 33 bytes")
        assertTrue(
            compressed[0] == 0x02.toByte() || compressed[0] == 0x03.toByte(),
            "Compressed key should start with 0x02 or 0x03"
        )

        // 測試未壓縮格式
        val uncompressed = Secp256k1Provider.computePublicKey(privateKey, compressed = false)
        assertEquals(65, uncompressed.size, "Uncompressed key should be 65 bytes")
        assertEquals(0x04.toByte(), uncompressed[0], "Uncompressed key should start with 0x04")

        // 驗證 x 座標一致性
        val compressedX = compressed.copyOfRange(1, 33)
        val uncompressedX = uncompressed.copyOfRange(1, 33)
        assertTrue(
            compressedX.contentEquals(uncompressedX),
            "X coordinates should match between compressed and uncompressed"
        )
    }

    @Test
    fun testRoundTripCompression() {
        val privateKey = ByteArray(32) { 0x42 }

        // 先生成未壓縮公鑰
        val uncompressed = Secp256k1Provider.computePublicKey(privateKey, compressed = false)

        // 再生成壓縮公鑰
        val compressed = Secp256k1Provider.computePublicKey(privateKey, compressed = true)

        // 驗證兩者可以互相驗證簽名
        val messageHash = ByteArray(32) { 0xFF.toByte() }
        val signature = Secp256k1Provider.sign(privateKey, messageHash)

        assertTrue(
            Secp256k1Provider.verify(signature, messageHash, compressed),
            "Signature should verify with compressed key"
        )
        assertTrue(
            Secp256k1Provider.verify(signature, messageHash, uncompressed),
            "Signature should verify with uncompressed key"
        )
    }
}

package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * iOS 平台公鑰格式驗證測試
 *
 * 目的：驗證 P0-2 修復 - computePublicKey() 正確返回壓縮/未壓縮格式
 */
class Secp256k1ProviderFormatTest {

    @Test
    fun testComputePublicKeyCompressed() {
        // 測試私鑰（32 bytes）
        val privateKey = ByteArray(32) { it.toByte() }

        // 生成壓縮公鑰
        val compressedPubkey = Secp256k1Provider.computePublicKey(
            privateKey,
            compressed = true
        )

        // ✅ 驗證格式
        assertEquals(33, compressedPubkey.size, "Compressed public key should be 33 bytes")
        assertTrue(
            compressedPubkey[0] == 0x02.toByte() || compressedPubkey[0] == 0x03.toByte(),
            "Compressed public key should start with 0x02 or 0x03, got 0x${compressedPubkey[0].toHexString()}"
        )
    }

    @Test
    fun testComputePublicKeyUncompressed() {
        // 測試私鑰（32 bytes）
        val privateKey = ByteArray(32) { it.toByte() }

        // 生成未壓縮公鑰
        val uncompressedPubkey = Secp256k1Provider.computePublicKey(
            privateKey,
            compressed = false
        )

        // ✅ 驗證格式
        assertEquals(65, uncompressedPubkey.size, "Uncompressed public key should be 65 bytes")
        assertEquals(
            0x04.toByte(),
            uncompressedPubkey[0],
            "Uncompressed public key should start with 0x04, got 0x${uncompressedPubkey[0].toHexString()}"
        )
    }

    @Test
    fun testCompressedAndUncompressedConsistency() {
        // 相同私鑰生成的壓縮和未壓縮公鑰應該一致（x 座標相同）
        val privateKey = ByteArray(32) { it.toByte() }

        val compressed = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        val uncompressed = Secp256k1Provider.computePublicKey(privateKey, compressed = false)

        // 提取 x 座標
        val xFromCompressed = compressed.copyOfRange(1, 33)
        val xFromUncompressed = uncompressed.copyOfRange(1, 33)

        assertTrue(
            xFromCompressed.contentEquals(xFromUncompressed),
            "X coordinate should match between compressed and uncompressed formats"
        )
    }

    @Test
    fun testMultiplePrivateKeys() {
        // 測試多個不同的私鑰，確保格式始終正確
        val testCases = listOf(
            ByteArray(32) { 0x01 },
            ByteArray(32) { 0xFF.toByte() },
            ByteArray(32) { it.toByte() },
            ByteArray(32) { (it * 7).toByte() }
        )

        testCases.forEach { privateKey ->
            val compressed = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
            val uncompressed = Secp256k1Provider.computePublicKey(privateKey, compressed = false)

            assertEquals(33, compressed.size, "All compressed keys should be 33 bytes")
            assertEquals(65, uncompressed.size, "All uncompressed keys should be 65 bytes")

            assertTrue(
                compressed[0] == 0x02.toByte() || compressed[0] == 0x03.toByte(),
                "Compressed prefix should be valid"
            )
            assertEquals(0x04.toByte(), uncompressed[0], "Uncompressed prefix should be 0x04")
        }
    }

    @Test
    fun testSignatureVerificationWithCompressedKey() {
        // 確保壓縮公鑰可以正確用於簽名驗證
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash = ByteArray(32) { 0xFF.toByte() }

        val signature = Secp256k1Provider.sign(privateKey, messageHash)
        val compressedPubkey = Secp256k1Provider.computePublicKey(privateKey, compressed = true)

        // ✅ 關鍵驗證：33-byte 壓縮公鑰應該能正確驗證簽名
        assertEquals(33, compressedPubkey.size, "Should use compressed key (33 bytes)")
        val isValid = Secp256k1Provider.verify(signature, messageHash, compressedPubkey)
        assertTrue(isValid, "Signature verification should succeed with compressed public key")
    }

    @Test
    fun testSignatureVerificationWithUncompressedKey() {
        // 確保未壓縮公鑰也可以正確用於簽名驗證
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash = ByteArray(32) { 0xFF.toByte() }

        val signature = Secp256k1Provider.sign(privateKey, messageHash)
        val uncompressedPubkey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)

        // ✅ 65-byte 未壓縮公鑰也應該能正確驗證簽名
        assertEquals(65, uncompressedPubkey.size, "Should use uncompressed key (65 bytes)")
        val isValid = Secp256k1Provider.verify(signature, messageHash, uncompressedPubkey)
        assertTrue(isValid, "Signature verification should succeed with uncompressed public key")
    }

    // Helper function
    private fun Byte.toHexString(): String = "%02x".format(this)
}

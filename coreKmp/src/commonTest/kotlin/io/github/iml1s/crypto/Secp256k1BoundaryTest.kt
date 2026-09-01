package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Secp256k1 邊界條件測試
 *
 * 測試極端值和邊界情況，確保實現的健壯性
 *
 * 測試範圍：
 * - 無效私鑰（零、過大、過小）
 * - 無效消息（空、過大、過小）
 * - 無效公鑰（不在曲線上、格式錯誤）
 * - 無效簽名（零值、過大值、格式錯誤）
 */
class Secp256k1BoundaryTest {

    //region 私鑰邊界測試

    /**
     * 測試：全零私鑰（無效）
     */
    @Test
    fun testZeroPrivateKey_shouldBeInvalid() {
        val zeroKey = ByteArray(32) { 0 }

        // 驗證私鑰無效
        assertFalse(
            Secp256k1Provider.isValidPrivateKey(zeroKey),
            "All-zero private key should be invalid"
        )
    }

    /**
     * 測試：最小有效私鑰（值為 1）
     */
    @Test
    fun testMinValidPrivateKey() {
        val minKey = ByteArray(32) { 0 }
        minKey[31] = 1 // 最低位設為 1

        // 驗證私鑰有效
        assertTrue(
            Secp256k1Provider.isValidPrivateKey(minKey),
            "Private key = 1 should be valid"
        )

        // 測試簽名和驗證
        val messageHash = ByteArray(32) { 0x01 }
        val signature = Secp256k1Provider.sign(minKey, messageHash)
        val publicKey = Secp256k1Provider.computePublicKey(minKey)

        assertEquals(64, signature.size, "Signature should be 64 bytes")
        assertTrue(
            Secp256k1Provider.verify(signature, messageHash, publicKey),
            "Signature with min valid private key should verify"
        )
    }

    /**
     * 測試：最大有效私鑰（n - 1）
     *
     * secp256k1 曲線階 n =
     * 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
     *
     * 最大有效私鑰 = n - 1 =
     * 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364140
     */
    @Test
    fun testMaxValidPrivateKey() {
        // n - 1 (secp256k1 曲線階減 1)
        val maxKey = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte(),
            0xBA.toByte(), 0xAE.toByte(), 0xDC.toByte(), 0xE6.toByte(),
            0xAF.toByte(), 0x48.toByte(), 0xA0.toByte(), 0x3B.toByte(),
            0xBF.toByte(), 0xD2.toByte(), 0x5E.toByte(), 0x8C.toByte(),
            0xD0.toByte(), 0x36.toByte(), 0x41.toByte(), 0x40.toByte()
        )

        // 驗證私鑰有效
        assertTrue(
            Secp256k1Provider.isValidPrivateKey(maxKey),
            "Private key = n - 1 should be valid"
        )

        // 測試簽名和驗證
        val messageHash = ByteArray(32) { 0x01 }
        val signature = Secp256k1Provider.sign(maxKey, messageHash)
        val publicKey = Secp256k1Provider.computePublicKey(maxKey)

        assertEquals(64, signature.size, "Signature should be 64 bytes")
        assertTrue(
            Secp256k1Provider.verify(signature, messageHash, publicKey),
            "Signature with max valid private key should verify"
        )
    }

    /**
     * 測試：私鑰 = n（應無效，超出範圍）
     */
    @Test
    fun testPrivateKeyEqualsN_shouldBeInvalid() {
        // secp256k1 曲線階 n
        val nKey = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte(),
            0xBA.toByte(), 0xAE.toByte(), 0xDC.toByte(), 0xE6.toByte(),
            0xAF.toByte(), 0x48.toByte(), 0xA0.toByte(), 0x3B.toByte(),
            0xBF.toByte(), 0xD2.toByte(), 0x5E.toByte(), 0x8C.toByte(),
            0xD0.toByte(), 0x36.toByte(), 0x41.toByte(), 0x41.toByte()
        )

        // 驗證私鑰無效
        assertFalse(
            Secp256k1Provider.isValidPrivateKey(nKey),
            "Private key = n should be invalid (out of range)"
        )
    }

    /**
     * 測試：私鑰 > n（應無效）
     */
    @Test
    fun testPrivateKeyGreaterThanN_shouldBeInvalid() {
        // 遠大於 n 的值（全 0xFF）
        val invalidKey = ByteArray(32) { 0xFF.toByte() }

        // 驗證私鑰無效
        assertFalse(
            Secp256k1Provider.isValidPrivateKey(invalidKey),
            "Private key > n should be invalid"
        )
    }

    /**
     * 測試：錯誤長度的私鑰
     */
    @Test
    fun testInvalidPrivateKeyLength() {
        // 太短（31 字節）
        val shortKey = ByteArray(31) { 0x01 }
        assertFalse(
            Secp256k1Provider.isValidPrivateKey(shortKey),
            "31-byte private key should be invalid"
        )

        // 太長（33 字節）
        val longKey = ByteArray(33) { 0x01 }
        assertFalse(
            Secp256k1Provider.isValidPrivateKey(longKey),
            "33-byte private key should be invalid"
        )

        // 空數組
        val emptyKey = ByteArray(0)
        assertFalse(
            Secp256k1Provider.isValidPrivateKey(emptyKey),
            "Empty private key should be invalid"
        )
    }

    //endregion

    //region 消息哈希邊界測試

    /**
     * 測試：全零消息哈希
     */
    @Test
    fun testZeroMessageHash() {
        val privateKey = ByteArray(32) { 0x01 }
        val zeroHash = ByteArray(32) { 0 }

        // 應該能正常簽名
        val signature = Secp256k1Provider.sign(privateKey, zeroHash)
        assertEquals(64, signature.size, "Signature should be 64 bytes")

        // 驗證簽名
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)
        assertTrue(
            Secp256k1Provider.verify(signature, zeroHash, publicKey),
            "Signature with zero message hash should verify"
        )
    }

    /**
     * 測試：全 0xFF 消息哈希
     */
    @Test
    fun testMaxMessageHash() {
        val privateKey = ByteArray(32) { 0x01 }
        val maxHash = ByteArray(32) { 0xFF.toByte() }

        // 應該能正常簽名
        val signature = Secp256k1Provider.sign(privateKey, maxHash)
        assertEquals(64, signature.size, "Signature should be 64 bytes")

        // 驗證簽名
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)
        assertTrue(
            Secp256k1Provider.verify(signature, maxHash, publicKey),
            "Signature with max message hash should verify"
        )
    }

    //endregion

    //region 公鑰邊界測試

    /**
     * 測試：壓縮公鑰格式驗證
     */
    @Test
    fun testCompressedPublicKeyFormat() {
        val privateKey = ByteArray(32) { 0x01 }
        val compressedPubKey = Secp256k1Provider.computePublicKey(privateKey, compressed = true)

        // 驗證長度
        assertEquals(33, compressedPubKey.size, "Compressed public key must be 33 bytes")

        // 驗證前綴（0x02 或 0x03）
        assertTrue(
            compressedPubKey[0] == 0x02.toByte() || compressedPubKey[0] == 0x03.toByte(),
            "Compressed public key must start with 0x02 or 0x03"
        )
    }

    /**
     * 測試：未壓縮公鑰格式驗證
     */
    @Test
    fun testUncompressedPublicKeyFormat() {
        val privateKey = ByteArray(32) { 0x01 }
        val uncompressedPubKey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)

        // 驗證長度
        assertEquals(65, uncompressedPubKey.size, "Uncompressed public key must be 65 bytes")

        // 驗證前綴（0x04）
        assertEquals(
            0x04.toByte(),
            uncompressedPubKey[0],
            "Uncompressed public key must start with 0x04"
        )
    }

    //endregion

    //region 簽名邊界測試

    /**
     * 測試：簽名格式驗證
     */
    @Test
    fun testSignatureFormat() {
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash = ByteArray(32) { 0x02 }

        val signature = Secp256k1Provider.sign(privateKey, messageHash)

        // 驗證長度
        assertEquals(64, signature.size, "Compact signature must be 64 bytes")

        // 驗證 r 和 s 不全為零
        val r = signature.sliceArray(0 until 32)
        val s = signature.sliceArray(32 until 64)

        assertTrue(r.any { it != 0.toByte() }, "r component should not be all zeros")
        assertTrue(s.any { it != 0.toByte() }, "s component should not be all zeros")
    }

    /**
     * 測試：使用錯誤格式的簽名驗證（應失敗）
     */
    @Test
    fun testInvalidSignatureFormat_shouldFailVerification() {
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash = ByteArray(32) { 0x02 }
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)

        // 全零簽名（無效）
        val zeroSignature = ByteArray(64) { 0 }
        assertFalse(
            Secp256k1Provider.verify(zeroSignature, messageHash, publicKey),
            "All-zero signature should fail verification"
        )

        // 全 0xFF 簽名（無效，r 和 s 都大於 n）
        val maxSignature = ByteArray(64) { 0xFF.toByte() }
        assertFalse(
            Secp256k1Provider.verify(maxSignature, messageHash, publicKey),
            "All-0xFF signature should fail verification"
        )
    }

    /**
     * 測試：簽名長度錯誤
     */
    @Test
    fun testInvalidSignatureLength_shouldFailVerification() {
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash = ByteArray(32) { 0x02 }
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)

        // 太短的簽名（63 字節）
        val shortSignature = ByteArray(63) { 0x01 }
        assertFalse(
            Secp256k1Provider.verify(shortSignature, messageHash, publicKey),
            "63-byte signature should fail verification"
        )

        // 太長的簽名（65 字節）
        val longSignature = ByteArray(65) { 0x01 }
        assertFalse(
            Secp256k1Provider.verify(longSignature, messageHash, publicKey),
            "65-byte signature should fail verification"
        )
    }

    //endregion

    //region 跨邊界一致性測試

    /**
     * 測試：邊界值的簽名一致性（多次簽名應相同）
     */
    @Test
    fun testBoundarySignatureDeterminism() {
        // 使用最小私鑰
        val minKey = ByteArray(32) { 0 }
        minKey[31] = 1

        // 使用零消息
        val zeroMessage = ByteArray(32) { 0 }

        // 多次簽名
        val sig1 = Secp256k1Provider.sign(minKey, zeroMessage)
        val sig2 = Secp256k1Provider.sign(minKey, zeroMessage)
        val sig3 = Secp256k1Provider.sign(minKey, zeroMessage)

        // 驗證確定性
        assertTrue(sig1.contentEquals(sig2), "Deterministic signing: sig1 == sig2")
        assertTrue(sig2.contentEquals(sig3), "Deterministic signing: sig2 == sig3")
        assertTrue(sig1.contentEquals(sig3), "Deterministic signing: sig1 == sig3")
    }

    /**
     * 測試：批量邊界值測試
     */
    @Test
    fun testBatchBoundaryValues() {
        val testCases = listOf(
            "min_key_zero_msg" to Pair(
                ByteArray(32) { 0 }.apply { this[31] = 1 },
                ByteArray(32) { 0 }
            ),
            "min_key_max_msg" to Pair(
                ByteArray(32) { 0 }.apply { this[31] = 1 },
                ByteArray(32) { 0xFF.toByte() }
            ),
            "med_key_zero_msg" to Pair(
                ByteArray(32) { (it % 256).toByte() },
                ByteArray(32) { 0 }
            ),
            "med_key_max_msg" to Pair(
                ByteArray(32) { (it % 256).toByte() },
                ByteArray(32) { 0xFF.toByte() }
            )
        )

        testCases.forEach { (name, data) ->
            val (privateKey, messageHash) = data

            // 簽名
            val signature = Secp256k1Provider.sign(privateKey, messageHash)
            assertNotNull(signature, "Signature should not be null for $name")
            assertEquals(64, signature.size, "Signature size should be 64 for $name")

            // 驗證
            val publicKey = Secp256k1Provider.computePublicKey(privateKey)
            assertTrue(
                Secp256k1Provider.verify(signature, messageHash, publicKey),
                "Signature verification should succeed for $name"
            )
        }
    }

    //endregion
}

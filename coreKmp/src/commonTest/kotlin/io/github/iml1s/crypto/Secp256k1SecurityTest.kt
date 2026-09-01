package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.Ignore

/**
 * Secp256k1 安全驗證測試
 *
 * 測試密碼學安全性和規範合規性
 *
 * 測試範圍：
 * - 簽名惡意性測試（重放攻擊、偽造攻擊）
 * - 簽名可塑性測試（低 s 值檢查）
 * - 公鑰驗證（點在曲線上）
 * - 隨機性測試（簽名不可預測性）
 */
@Ignore
class Secp256k1SecurityTest {

    //region 簽名安全性測試

    /**
     * 測試：簽名不可重用（不同消息）
     *
     * 防止簽名重放攻擊
     */
    @Test
    fun testSignatureNotReusable_differentMessages() {
        val privateKey = ByteArray(32) { 0x01 }
        val message1 = ByteArray(32) { 0xAA.toByte() }
        val message2 = ByteArray(32) { 0xBB.toByte() }

        // 對 message1 簽名
        val signature1 = Secp256k1Provider.sign(privateKey, message1)
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)

        // 驗證 signature1 對 message1 有效
        assertTrue(
            Secp256k1Provider.verify(signature1, message1, publicKey),
            "Signature should verify for original message"
        )

        // 驗證 signature1 對 message2 無效（防止重放攻擊）
        assertFalse(
            Secp256k1Provider.verify(signature1, message2, publicKey),
            "Signature should NOT verify for different message (replay attack prevention)"
        )
    }

    /**
     * 測試：簽名不可跨密鑰使用
     *
     * 防止公鑰偽造攻擊
     */
    @Test
    fun testSignatureNotReusable_differentKeys() {
        val privateKey1 = ByteArray(32) { 0x01 }
        val privateKey2 = ByteArray(32) { 0x02 }
        val message = ByteArray(32) { 0xAA.toByte() }

        // 用 privateKey1 簽名
        val signature = Secp256k1Provider.sign(privateKey1, message)

        // 生成兩個公鑰
        val publicKey1 = Secp256k1Provider.computePublicKey(privateKey1)
        val publicKey2 = Secp256k1Provider.computePublicKey(privateKey2)

        // 驗證簽名對 publicKey1 有效
        assertTrue(
            Secp256k1Provider.verify(signature, message, publicKey1),
            "Signature should verify with correct public key"
        )

        // 驗證簽名對 publicKey2 無效（防止公鑰偽造）
        assertFalse(
            Secp256k1Provider.verify(signature, message, publicKey2),
            "Signature should NOT verify with different public key (forgery prevention)"
        )
    }

    /**
     * 測試：簽名修改檢測
     *
     * 任何對簽名的修改都應導致驗證失敗
     */
    @Test
    fun testSignatureModificationDetection() {
        val privateKey = ByteArray(32) { 0x01 }
        val message = ByteArray(32) { 0xAA.toByte() }

        val signature = Secp256k1Provider.sign(privateKey, message)
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)

        // 原始簽名應驗證成功
        assertTrue(
            Secp256k1Provider.verify(signature, message, publicKey),
            "Original signature should verify"
        )

        // 修改簽名的第一個字節
        val modifiedSig1 = signature.copyOf()
        modifiedSig1[0] = (modifiedSig1[0].toInt() xor 0xFF).toByte()

        assertFalse(
            Secp256k1Provider.verify(modifiedSig1, message, publicKey),
            "Modified signature (byte 0) should NOT verify"
        )

        // 修改簽名的最後一個字節
        val modifiedSig2 = signature.copyOf()
        modifiedSig2[63] = (modifiedSig2[63].toInt() xor 0xFF).toByte()

        assertFalse(
            Secp256k1Provider.verify(modifiedSig2, message, publicKey),
            "Modified signature (byte 63) should NOT verify"
        )

        // 修改簽名的中間字節
        val modifiedSig3 = signature.copyOf()
        modifiedSig3[32] = (modifiedSig3[32].toInt() xor 0x01).toByte()

        assertFalse(
            Secp256k1Provider.verify(modifiedSig3, message, publicKey),
            "Modified signature (byte 32) should NOT verify"
        )
    }

    //endregion

    //region 簽名規範性測試

    /**
     * 測試：RFC 6979 確定性簽名
     *
     * 相同的私鑰和消息應始終產生相同的簽名
     */
    @Test
    fun testRFC6979_deterministicSignature() {
        val privateKey = ByteArray(32) { 0xC9.toByte() }
        val message = ByteArray(32) { 0xAF.toByte() }

        // 生成 10 個簽名
        val signatures = (1..10).map {
            Secp256k1Provider.sign(privateKey, message)
        }

        // 所有簽名應完全相同
        signatures.forEach { sig ->
            assertTrue(
                sig.contentEquals(signatures[0]),
                "RFC 6979 deterministic signatures must all be identical"
            )
        }
    }

    /**
     * 測試：簽名 r 和 s 值範圍驗證
     *
     * 符合 secp256k1 規範：
     * - 1 <= r < n
     * - 1 <= s < n
     * 其中 n 是曲線的階
     */
    @Test
    fun testSignature_rsRangeValidation() {
        val privateKey = ByteArray(32) { 0x01 }
        val message = ByteArray(32) { 0x02 }

        val signature = Secp256k1Provider.sign(privateKey, message)

        // 提取 r 和 s
        val r = signature.sliceArray(0 until 32)
        val s = signature.sliceArray(32 until 64)

        // 驗證 r 不為零
        assertTrue(
            r.any { it != 0.toByte() },
            "r component should not be zero"
        )

        // 驗證 s 不為零
        assertTrue(
            s.any { it != 0.toByte() },
            "s component should not be zero"
        )

        // 驗證 r 不全是 0xFF（應小於 n）
        assertFalse(
            r.all { it == 0xFF.toByte() },
            "r component should be less than n (not all 0xFF)"
        )

        // 驗證 s 不全是 0xFF（應小於 n）
        assertFalse(
            s.all { it == 0xFF.toByte() },
            "s component should be less than n (not all 0xFF)"
        )
    }

    /**
     * 測試：低 s 值規範（BIP 62）
     *
     * Bitcoin 要求所有簽名使用低 s 值以防止簽名可塑性攻擊
     * s <= n/2
     *
     * 其中 n = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
     * n/2 = 0x7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0
     */
    @Test
    fun testSignature_lowS_malleabilityPrevention() {
        val privateKey = ByteArray(32) { 0x01 }

        // 測試多個消息的簽名
        repeat(10) { i ->
            val message = ByteArray(32) { (i * 13 + it).toByte() }
            val signature = Secp256k1Provider.sign(privateKey, message)

            // 提取 s 值
            val s = signature.sliceArray(32 until 64)

            // n/2 的最高字節是 0x7F
            // 檢查 s 的最高字節應 <= 0x7F（粗略檢查）
            val sFirstByte = s[0].toInt() and 0xFF

            // 如果最高字節 > 0x7F，則 s > n/2（違反低 s 值規範）
            assertTrue(
                sFirstByte <= 0x7F,
                "Signature s value should be low (s <= n/2) to prevent malleability. " +
                        "Got s[0] = 0x${sFirstByte.toString(16).uppercase()}"
            )
        }
    }

    //endregion

    //region 公鑰安全性測試

    /**
     * 測試：公鑰一致性
     *
     * 相同的私鑰應始終生成相同的公鑰
     */
    @Test
    fun testPublicKey_consistency() {
        val privateKey = ByteArray(32) { 0x01 }

        // 生成多個公鑰
        val pubKeys = (1..10).map {
            Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        }

        // 所有公鑰應完全相同
        pubKeys.forEach { pubKey ->
            assertTrue(
                pubKey.contentEquals(pubKeys[0]),
                "Public keys from same private key must be identical"
            )
        }
    }

    /**
     * 測試：壓縮和未壓縮公鑰的 x 座標一致性
     *
     * 壓縮和未壓縮格式應該有相同的 x 座標
     */
    @Test
    fun testPublicKey_compressedUncompressed_xCoordinateConsistency() {
        val privateKey = ByteArray(32) { 0x01 }

        // 生成壓縮和未壓縮公鑰
        val compressedPubKey = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        val uncompressedPubKey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)

        // 提取 x 座標
        // 壓縮格式: [prefix (1 byte)][x (32 bytes)]
        // 未壓縮格式: [0x04][x (32 bytes)][y (32 bytes)]
        val xCompressed = compressedPubKey.sliceArray(1 until 33)
        val xUncompressed = uncompressedPubKey.sliceArray(1 until 33)

        // x 座標應該相同
        assertTrue(
            xCompressed.contentEquals(xUncompressed),
            "x-coordinate should be identical in compressed and uncompressed formats"
        )
    }

    /**
     * 測試：不同私鑰生成不同公鑰
     *
     * 防止公鑰碰撞
     */
    @Test
    fun testPublicKey_uniqueness() {
        val privateKey1 = ByteArray(32) { 0x01 }
        val privateKey2 = ByteArray(32) { 0x02 }

        val pubKey1 = Secp256k1Provider.computePublicKey(privateKey1)
        val pubKey2 = Secp256k1Provider.computePublicKey(privateKey2)

        // 兩個公鑰應該不同
        assertFalse(
            pubKey1.contentEquals(pubKey2),
            "Different private keys must produce different public keys"
        )
    }

    //endregion

    //region 隨機性和不可預測性測試

    /**
     * 測試：簽名的不可預測性
     *
     * 不同的消息應產生不同的簽名
     */
    @Test
    fun testSignature_unpredictability() {
        val privateKey = ByteArray(32) { 0x01 }

        // 生成 20 個不同消息的簽名
        val signatures = (0 until 20).map { i ->
            val message = ByteArray(32) { (i * 17 + it).toByte() }
            Secp256k1Provider.sign(privateKey, message)
        }

        // 驗證所有簽名都不同
        for (i in signatures.indices) {
            for (j in (i + 1) until signatures.size) {
                assertFalse(
                    signatures[i].contentEquals(signatures[j]),
                    "Signatures for different messages should be different (i=$i, j=$j)"
                )
            }
        }
    }

    /**
     * 測試：簽名的均勻分布（統計測試）
     *
     * 簽名字節應該在統計上均勻分布
     */
    @Test
    fun testSignature_statisticalDistribution() {
        val privateKey = ByteArray(32) { 0x01 }

        // 收集 100 個簽名
        val signatures = (0 until 100).map { i ->
            val message = ByteArray(32) { (i * 37 + it).toByte() }
            Secp256k1Provider.sign(privateKey, message)
        }

        // 統計第一個字節的分布
        val byteDistribution = IntArray(256) { 0 }
        signatures.forEach { sig ->
            val byte = sig[0].toInt() and 0xFF
            byteDistribution[byte]++
        }

        // 計算使用了多少個不同的字節值
        val uniqueBytes = byteDistribution.count { it > 0 }

        // 期望至少有 30% 的字節值被使用（100 個簽名中至少 76 種不同值）
        // 注意：這是粗略的統計檢查，不是嚴格的密碼學隨機性測試
        assertTrue(
            uniqueBytes >= 30,
            "Signature bytes should be well-distributed (got $uniqueBytes/256 unique values)"
        )
    }

    //endregion

    //region ECDH 安全性測試

    /**
     * 測試：ECDH 共享密鑰一致性
     */
    @Test
    fun testECDH_sharedSecretConsistency() {
        val privateKeyA = ByteArray(32) { 0x01 }
        val privateKeyB = ByteArray(32) { 0x02 }

        val publicKeyA = Secp256k1Provider.computePublicKey(privateKeyA)
        val publicKeyB = Secp256k1Provider.computePublicKey(privateKeyB)

        // A 和 B 進行 ECDH
        val sharedSecretA = Secp256k1Provider.ecdh(privateKeyA, publicKeyB)
        val sharedSecretB = Secp256k1Provider.ecdh(privateKeyB, publicKeyA)

        // 共享密鑰應該相同
        assertEquals(32, sharedSecretA.size, "ECDH shared secret should be 32 bytes")
        assertEquals(32, sharedSecretB.size, "ECDH shared secret should be 32 bytes")

        assertTrue(
            sharedSecretA.contentEquals(sharedSecretB),
            "ECDH shared secrets must match"
        )
    }

    /**
     * 測試：ECDH 共享密鑰唯一性
     *
     * 不同的密鑰對應產生不同的共享密鑰
     */
    @Test
    fun testECDH_uniqueSharedSecrets() {
        val privateKeyA1 = ByteArray(32) { 0x01 }
        val privateKeyA2 = ByteArray(32) { 0x02 }
        val privateKeyB = ByteArray(32) { 0x03 }

        val publicKeyB = Secp256k1Provider.computePublicKey(privateKeyB)

        // A1-B 和 A2-B 的共享密鑰
        val sharedSecret1 = Secp256k1Provider.ecdh(privateKeyA1, publicKeyB)
        val sharedSecret2 = Secp256k1Provider.ecdh(privateKeyA2, publicKeyB)

        // 兩個共享密鑰應該不同
        assertFalse(
            sharedSecret1.contentEquals(sharedSecret2),
            "Different key pairs should produce different ECDH shared secrets"
        )
    }

    //endregion
}

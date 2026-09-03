package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 測試 Secp256k1Provider 的 compact 簽名格式
 * 驗證跨平台簽名格式一致性
 */
class Secp256k1CompactSignatureTest {

    @Test
    fun testSignatureFormat_shouldBe64Bytes() {
        // 準備測試數據
        val privateKey = ByteArray(32) { 0xAA.toByte() }
        val messageHash = ByteArray(32) { 0xBB.toByte() }

        // 執行簽名
        val signature = Secp256k1Provider.sign(privateKey, messageHash)

        // 驗證簽名長度為 64 bytes (compact 格式)
        assertEquals(64, signature.size, "Signature must be 64 bytes (compact format)")
    }

    @Test
    fun testSignatureVerification_shouldSucceed() {
        // 準備測試數據
        val privateKey = ByteArray(32) { 0xAA.toByte() }
        val messageHash = ByteArray(32) { 0xBB.toByte() }

        // 生成簽名
        val signature = Secp256k1Provider.sign(privateKey, messageHash)

        // 生成公鑰
        val publicKey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)

        // 驗證簽名
        val isValid = Secp256k1Provider.verify(signature, messageHash, publicKey)
        assertTrue(isValid, "Signature verification should succeed")
    }

    @Test
    fun testCompactSignatureStructure() {
        // 準備測試數據
        val privateKey = ByteArray(32) { 0xAA.toByte() }
        val messageHash = ByteArray(32) { 0xBB.toByte() }

        // 生成簽名
        val signature = Secp256k1Provider.sign(privateKey, messageHash)

        // 驗證簽名結構: 前 32 bytes 是 r, 後 32 bytes 是 s
        val r = signature.sliceArray(0 until 32)
        val s = signature.sliceArray(32 until 64)

        assertEquals(32, r.size, "r component must be 32 bytes")
        assertEquals(32, s.size, "s component must be 32 bytes")

        // 驗證 r 和 s 不全為零
        assertTrue(r.any { it != 0.toByte() }, "r component should not be all zeros")
        assertTrue(s.any { it != 0.toByte() }, "s component should not be all zeros")
    }

    @Test
    fun testDeterministicSignature() {
        // RFC 6979: 相同的私鑰和消息應產生相同的簽名
        val privateKey = ByteArray(32) { 0xAA.toByte() }
        val messageHash = ByteArray(32) { 0xBB.toByte() }

        val signature1 = Secp256k1Provider.sign(privateKey, messageHash)
        val signature2 = Secp256k1Provider.sign(privateKey, messageHash)

        // 驗證兩次簽名完全相同
        assertTrue(signature1.contentEquals(signature2), "RFC 6979 signatures must be deterministic")
    }

    @Test
    fun testCrossPlatformCompatibility() {
        // 測試跨平台互操作性
        // 使用已知的私鑰生成簽名
        val privateKey = ByteArray(32) { it.toByte() }
        val messageHash = ByteArray(32) { (it + 100).toByte() }

        // 生成簽名
        val signature = Secp256k1Provider.sign(privateKey, messageHash)

        // 驗證簽名格式
        assertEquals(64, signature.size, "Cross-platform signature must be 64 bytes")

        // 生成公鑰並驗證
        val publicKey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)
        val isValid = Secp256k1Provider.verify(signature, messageHash, publicKey)

        assertTrue(isValid, "Cross-platform signature verification should succeed")
    }
}

package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Secp256k1ProviderTest {

    @Test
    fun testSignAndVerify() {
        // 測試私鑰
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash = ByteArray(32) { 0xFF.toByte() }

        // 簽名
        val signature = Secp256k1Provider.sign(privateKey, messageHash)
        assertEquals(64, signature.size, "Signature should be 64 bytes")

        // 派生公鑰
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)
        assertEquals(33, publicKey.size, "Compressed public key should be 33 bytes")

        // 驗證
        val isValid = Secp256k1Provider.verify(signature, messageHash, publicKey)
        assertTrue(isValid, "Signature verification should succeed")
    }

    @Test
    fun testInvalidPrivateKey() {
        // 全零私鑰無效
        val invalidKey = ByteArray(32) { 0x00 }
        assertFalse(Secp256k1Provider.isValidPrivateKey(invalidKey))

        // 有效私鑰
        val validKey = ByteArray(32) { 0x01 }
        assertTrue(Secp256k1Provider.isValidPrivateKey(validKey))
    }

    @Test
    fun testPublicKeyCompression() {
        val privateKey = ByteArray(32) { 0x01 }

        // 壓縮格式
        val compressedPubKey = Secp256k1Provider.computePublicKey(privateKey, compressed = true)
        assertEquals(33, compressedPubKey.size)
        assertTrue(
            compressedPubKey[0] == 0x02.toByte() || compressedPubKey[0] == 0x03.toByte(),
            "Compressed key should start with 0x02 or 0x03"
        )

        // 未壓縮格式
        val uncompressedPubKey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)
        assertEquals(65, uncompressedPubKey.size)
        assertEquals(0x04.toByte(), uncompressedPubKey[0],
            "Uncompressed key should start with 0x04")
    }

    // TODO: 恢復此測試當 secp256k1-kmp 支援 signRecoverable API
    /*
    @Test
    fun testRecoverableSignature() {
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash = ByteArray(32) { 0xFF.toByte() }

        // 可恢復簽名
        val recSig = Secp256k1Provider.signRecoverable(privateKey, messageHash)
        assertEquals(32, recSig.r.size)
        assertEquals(32, recSig.s.size)
        assertTrue(recSig.v in 0..3, "Recovery ID should be 0-3")

        // 恢復公鑰
        val recoveredPubKey = Secp256k1Provider.recoverPublicKey(recSig, messageHash)
        val expectedPubKey = Secp256k1Provider.computePublicKey(privateKey, compressed = false)

        assertTrue(recoveredPubKey.contentEquals(expectedPubKey),
            "Recovered public key should match expected key")
    }
    */

    @Test
    fun testECDH() {
        // 兩個密鑰對
        val privateKeyA = ByteArray(32) { 0x01 }
        val privateKeyB = ByteArray(32) { 0x02 }

        val publicKeyA = Secp256k1Provider.computePublicKey(privateKeyA)
        val publicKeyB = Secp256k1Provider.computePublicKey(privateKeyB)

        // A 和 B 進行 ECDH
        val sharedSecretA = Secp256k1Provider.ecdh(privateKeyA, publicKeyB)
        val sharedSecretB = Secp256k1Provider.ecdh(privateKeyB, publicKeyA)

        // 共享密鑰應該相同
        assertEquals(32, sharedSecretA.size)
        assertEquals(32, sharedSecretB.size)
        assertTrue(sharedSecretA.contentEquals(sharedSecretB),
            "Shared secrets should match")
    }

    @Test
    fun testSignatureVerificationWithWrongKey() {
        val privateKey1 = ByteArray(32) { 0x01 }
        val privateKey2 = ByteArray(32) { 0x02 }
        val messageHash = ByteArray(32) { 0xFF.toByte() }

        // 用 key1 簽名
        val signature = Secp256k1Provider.sign(privateKey1, messageHash)

        // 用 key2 的公鑰驗證（應該失敗）
        val publicKey2 = Secp256k1Provider.computePublicKey(privateKey2)
        val isValid = Secp256k1Provider.verify(signature, messageHash, publicKey2)

        assertFalse(isValid, "Signature verification with wrong key should fail")
    }

    @Test
    fun testSignatureVerificationWithWrongMessage() {
        val privateKey = ByteArray(32) { 0x01 }
        val messageHash1 = ByteArray(32) { 0xFF.toByte() }
        val messageHash2 = ByteArray(32) { 0xEE.toByte() }

        // 對 message1 簽名
        val signature = Secp256k1Provider.sign(privateKey, messageHash1)
        val publicKey = Secp256k1Provider.computePublicKey(privateKey)

        // 用 message2 驗證（應該失敗）
        val isValid = Secp256k1Provider.verify(signature, messageHash2, publicKey)

        assertFalse(isValid, "Signature verification with wrong message should fail")
    }
}

package com.cbstudio.wearwallet.core.blockchain.crypto

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import io.github.andreypfau.curve25519.ed25519.Ed25519
import kotlin.random.Random

/**
 * Ed25519 簽名驗證測試
 * 使用 curve25519-kotlin 進行真實的 Ed25519 簽名和驗證
 */
class Ed25519SignatureVerificationTest {

    /**
     * 測試基本的簽名和驗證流程
     */
    @Test
    fun testBasicSignatureVerification() {
        println("=== 測試 Ed25519 基本簽名驗證 ===")

        // 生成密鑰對
        val privateKey = Ed25519.generateKey(Random.Default)
        val publicKey = privateKey.publicKey()

        // 測試消息
        val message = "Hello, Solana!".encodeToByteArray()

        // 簽名
        val signature = privateKey.sign(message)

        println("✅ 密鑰對生成成功")
        println("   公鑰長度: ${publicKey.key.size} bytes")
        println("   簽名長度: ${signature.size} bytes")

        // 驗證簽名
        val isValid = publicKey.verify(message, signature)

        assertTrue(isValid, "有效的簽名應該通過驗證")
        println("✅ 簽名驗證成功")
    }

    /**
     * 測試無效簽名的拒絕
     */
    @Test
    fun testInvalidSignatureRejection() {
        println("=== 測試 Ed25519 無效簽名拒絕 ===")

        // 生成密鑰對
        val privateKey = Ed25519.generateKey(Random.Default)
        val publicKey = privateKey.publicKey()

        // 原始消息和簽名
        val originalMessage = "Valid message".encodeToByteArray()
        val signature = privateKey.sign(originalMessage)

        // 測試 1: 篡改的消息
        val tamperedMessage = "Tampered message".encodeToByteArray()
        val isValidTampered = publicKey.verify(tamperedMessage, signature)
        assertFalse(isValidTampered, "篡改的消息應該驗證失敗")
        println("✅ 成功拒絕篡改的消息")

        // 測試 2: 錯誤的簽名
        val invalidSignature = ByteArray(64) { 0 }
        val isValidInvalidSig = publicKey.verify(originalMessage, invalidSignature)
        assertFalse(isValidInvalidSig, "無效的簽名應該驗證失敗")
        println("✅ 成功拒絕無效的簽名")
    }

    /**
     * 測試密鑰對從種子生成
     */
    @Test
    fun testKeyPairFromSeed() {
        println("=== 測試 Ed25519 從種子生成密鑰對 ===")

        // 使用固定種子生成密鑰對（用於確定性測試）
        val seed = ByteArray(32) { it.toByte() }
        val privateKey = Ed25519.keyFromSeed(seed)
        val publicKey = privateKey.publicKey()

        println("✅ 從種子生成密鑰對成功")
        println("   公鑰: ${publicKey.key.joinToString("") { "%02x".format(it) }.take(32)}...")

        // 測試簽名和驗證
        val message = "Deterministic test".encodeToByteArray()
        val signature = privateKey.sign(message)
        val isValid = publicKey.verify(message, signature)

        assertTrue(isValid, "從種子生成的密鑰對應該能正確簽名和驗證")
        println("✅ 種子密鑰對簽名驗證成功")
    }

    /**
     * 測試 RFC 8032 測試向量
     * 使用官方標準的測試向量來驗證實現的正確性
     */
    @Test
    fun testRFC8032TestVector() {
        println("=== 測試 RFC 8032 測試向量 ===")

        // RFC 8032 Test Vector 1
        // Secret key: 9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60
        // Public key: d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a
        // Message: (empty)
        // Signature: e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b

        val publicKeyHex = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        val signatureHex = "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
        val message = ByteArray(0) // Empty message

        val publicKeyBytes = hexStringToByteArray(publicKeyHex)
        val signatureBytes = hexStringToByteArray(signatureHex)

        val publicKey = io.github.andreypfau.curve25519.ed25519.Ed25519PublicKey(publicKeyBytes)
        val isValid = publicKey.verify(message, signatureBytes)

        assertTrue(isValid, "RFC 8032 測試向量應該驗證成功")
        println("✅ RFC 8032 測試向量驗證成功")
    }

    /**
     * 輔助函數：將十六進制字符串轉換為字節數組
     */
    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4)
                    + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
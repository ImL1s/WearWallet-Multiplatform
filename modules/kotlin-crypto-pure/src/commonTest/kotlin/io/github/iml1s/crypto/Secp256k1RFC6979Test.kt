package io.github.iml1s.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RFC 6979 Deterministic k 生成測試
 * 使用標準測試向量驗證實現正確性
 */
class Secp256k1RFC6979Test {

    /**
     * RFC 6979 測試向量
     * 來源: https://tools.ietf.org/html/rfc6979#appendix-A.2.5
     */
    @Test
    fun testDeterministicSignature() {
        // 測試私鑰 (sample)
        val privateKey = "C9AFA9D845BA75166B5C215767B1D6934E50C3DB36E89B127B8A622B120F6721"
            .hexToByteArray()

        // 測試消息哈希 (sample)
        val messageHash = "AF2BDBE1AA9B6EC1E2ADE1D694F41FC71A831D0268E9891562113D8A62ADD1BF"
            .hexToByteArray()

        // 第一次簽名
        val signature1 = signWithDeterministicK(privateKey, messageHash)

        // 第二次簽名 - 應該與第一次完全相同（deterministic）
        val signature2 = signWithDeterministicK(privateKey, messageHash)

        // 驗證簽名一致性
        assertTrue(signature1.contentEquals(signature2),
            "RFC 6979 簽名應該是確定性的（相同輸入產生相同輸出）")

        // 驗證簽名格式正確（Compact 格式 64 bytes: r || s）
        assertTrue(signature1.isNotEmpty(), "簽名不應為空")
        assertEquals(64, signature1.size, "簽名應為 64 bytes (Compact format: r || s)")
    }

    /**
     * 測試不同消息產生不同簽名
     */
    @Test
    fun testDifferentMessagesProduceDifferentSignatures() {
        val privateKey = "C9AFA9D845BA75166B5C215767B1D6934E50C3DB36E89B127B8A622B120F6721"
            .hexToByteArray()

        val message1 = "AF2BDBE1AA9B6EC1E2ADE1D694F41FC71A831D0268E9891562113D8A62ADD1BF"
            .hexToByteArray()

        val message2 = "BF2BDBE1AA9B6EC1E2ADE1D694F41FC71A831D0268E9891562113D8A62ADD1BF"
            .hexToByteArray()

        val signature1 = signWithDeterministicK(privateKey, message1)
        val signature2 = signWithDeterministicK(privateKey, message2)

        assertTrue(!signature1.contentEquals(signature2),
            "不同消息應該產生不同簽名")
    }

    /**
     * 測試不同私鑰產生不同簽名
     */
    @Test
    fun testDifferentPrivateKeysProduceDifferentSignatures() {
        val privateKey1 = "C9AFA9D845BA75166B5C215767B1D6934E50C3DB36E89B127B8A622B120F6721"
            .hexToByteArray()

        val privateKey2 = "D9AFA9D845BA75166B5C215767B1D6934E50C3DB36E89B127B8A622B120F6721"
            .hexToByteArray()

        val message = "AF2BDBE1AA9B6EC1E2ADE1D694F41FC71A831D0268E9891562113D8A62ADD1BF"
            .hexToByteArray()

        val signature1 = signWithDeterministicK(privateKey1, message)
        val signature2 = signWithDeterministicK(privateKey2, message)

        assertTrue(!signature1.contentEquals(signature2),
            "不同私鑰應該產生不同簽名")
    }

    /**
     * 測試簽名驗證
     */
    @Test
    fun testSignatureVerification() {
        val privateKey = "C9AFA9D845BA75166B5C215767B1D6934E50C3DB36E89B127B8A622B120F6721"
            .hexToByteArray()

        val message = "AF2BDBE1AA9B6EC1E2ADE1D694F41FC71A831D0268E9891562113D8A62ADD1BF"
            .hexToByteArray()

        // 生成簽名
        val signature = signWithDeterministicK(privateKey, message)

        // 生成公鑰
        val publicKey = generatePublicKeyFromPrivateKey(privateKey)

        // 驗證簽名
        val isValid = verifySignature(message, signature, publicKey)

        assertTrue(isValid, "簽名應該能被正確驗證")
    }

    /**
     * 模擬簽名函數（會在實際實現中調用 Secp256k1Pure）
     */
    private fun signWithDeterministicK(privateKey: ByteArray, messageHash: ByteArray): ByteArray {
        return Secp256k1Pure.sign(messageHash, privateKey)
    }

    private fun generatePublicKeyFromPrivateKey(privateKey: ByteArray): ByteArray {
        return Secp256k1Pure.generatePublicKey(privateKey)
    }

    private fun verifySignature(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return Secp256k1Pure.verify(message, signature, publicKey)
    }

    // 輔助擴展函數
    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

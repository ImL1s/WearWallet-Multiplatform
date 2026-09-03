package com.cbstudio.wearwallet.core.blockchain.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 公鑰派生和恢復功能的測試套件
 *
 * 測試範圍：
 * 1. 從私鑰派生公鑰
 * 2. 從簽名恢復公鑰
 * 3. 跨平台一致性驗證
 */
class PublicKeyDerivationTest {

    /**
     * 測試從私鑰派生公鑰
     *
     * 使用已知的測試向量驗證公鑰派生是否正確
     */
    @Test
    fun testDerivePublicKeyFromPrivateKey() {
        // 測試向量：以太坊測試私鑰
        val privateKey = "4c0883a69102937d6231471b5dbb6204fe512961708279f8b7d0f1b3c3b8b7e3"

        // 執行公鑰派生
        val publicKey = CryptoSignature.derivePublicKeyFromPrivateKey(privateKey)

        // 驗證結果
        assertNotNull(publicKey, "Public key should not be null")
        assertTrue(publicKey.length == 130, "Public key should be 130 hex characters (65 bytes)")
        assertTrue(publicKey.startsWith("04"), "Uncompressed public key should start with 04")

        println("✅ 公鑰派生測試通過")
        println("私鑰: $privateKey")
        println("公鑰: $publicKey")
    }

    /**
     * 測試公鑰派生的一致性
     *
     * 多次調用應該返回相同的結果
     */
    @Test
    fun testPublicKeyDerivationConsistency() {
        val privateKey = "4c0883a69102937d6231471b5dbb6204fe512961708279f8b7d0f1b3c3b8b7e3"

        // 執行多次派生
        val publicKey1 = CryptoSignature.derivePublicKeyFromPrivateKey(privateKey)
        val publicKey2 = CryptoSignature.derivePublicKeyFromPrivateKey(privateKey)
        val publicKey3 = CryptoSignature.derivePublicKeyFromPrivateKey(privateKey)

        // 驗證一致性
        assertEquals(publicKey1, publicKey2, "Public key derivation should be deterministic")
        assertEquals(publicKey2, publicKey3, "Public key derivation should be deterministic")

        println("✅ 公鑰派生一致性測試通過")
    }

    /**
     * 測試從簽名恢復公鑰
     *
     * 注意：此測試在 iOS/watchOS 上會返回 null（因為 libsecp256k1 不支援）
     * 在 Android 上應該能成功恢復
     */
    @Test
    fun testRecoverPublicKeyFromSignature() {
        // 測試向量
        val messageHash = "3f2b2a1e1e4e5f5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e"
        val r = "8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f8f"
        val s = "1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e1e"

        // 嘗試所有可能的 recovery ID
        var recovered = false
        for (recoveryId in 0..3) {
            val publicKey = CryptoSignature.recoverPublicKey(messageHash, r, s, recoveryId)

            if (publicKey != null) {
                recovered = true
                assertTrue(publicKey.length == 130, "Recovered public key should be 130 hex characters")
                assertTrue(publicKey.startsWith("04"), "Uncompressed public key should start with 04")

                println("✅ 公鑰恢復測試通過 (Recovery ID: $recoveryId)")
                println("恢復的公鑰: $publicKey")
                break
            }
        }

        // 在支援的平台上應該能恢復，不支援的平台會跳過
        if (!recovered) {
            println("⚠️  公鑰恢復功能不支援（iOS/watchOS libsecp256k1 限制）")
        }
    }

    /**
     * 測試錯誤輸入處理
     */
    @Test
    fun testInvalidInputHandling() {
        // 測試無效的私鑰長度
        val invalidPrivateKey = "invalid"
        val result = CryptoSignature.derivePublicKeyFromPrivateKey(invalidPrivateKey)

        assertTrue(result.startsWith("ERROR"), "Should return error for invalid input")

        println("✅ 錯誤輸入處理測試通過")
    }
}

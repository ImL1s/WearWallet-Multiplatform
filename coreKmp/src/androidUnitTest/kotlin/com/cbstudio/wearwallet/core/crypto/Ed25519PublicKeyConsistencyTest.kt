package io.github.iml1s.crypto

import io.github.andreypfau.curve25519.ed25519.Ed25519
import io.github.andreypfau.curve25519.ed25519.Ed25519PublicKey
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Ed25519 公鑰一致性測試
 *
 * 目的：驗證多次 Ed25519.keyFromSeed() 調用是否產生一致的公鑰
 *
 * 背景：剩餘 3 個測試失敗都涉及自生成簽名的驗證
 * 假設：兩次 keyFromSeed() 調用可能產生不一致的結果
 */
class Ed25519PublicKeyConsistencyTest {

    @Test
    fun testPublicKeyConsistencyWithSameSeed() {
        println("\n=== 測試：相同種子的公鑰一致性 ===")

        // 使用固定種子
        val seed = ByteArray(32) { it.toByte() }

        // 第一次調用
        val privateKey1 = Ed25519.keyFromSeed(seed)
        val publicKey1Bytes = privateKey1.publicKey().toByteArray()

        // 第二次調用（模擬 signWithEd25519 中的調用）
        val privateKey2 = Ed25519.keyFromSeed(seed)
        val publicKey2Bytes = privateKey2.publicKey().toByteArray()

        // 驗證公鑰字節數組是否相同
        println("公鑰 1: ${bytesToHex(publicKey1Bytes)}")
        println("公鑰 2: ${bytesToHex(publicKey2Bytes)}")

        assertTrue(
            publicKey1Bytes.contentEquals(publicKey2Bytes),
            "兩次 keyFromSeed() 調用應產生相同的公鑰字節"
        )
    }

    @Test
    fun testSignAndVerifyWithSeparateKeyGeneration() {
        println("\n=== 測試：分別生成的密鑰進行簽名和驗證 ===")

        val seed = ByteArray(32) { it.toByte() }
        val message = "test message".encodeToByteArray()

        // 場景 1: 簽名時生成一次密鑰
        val signingPrivateKey = Ed25519.keyFromSeed(seed)
        val signature = signingPrivateKey.sign(message)
        println("簽名生成成功: ${bytesToHex(signature)}")

        // 場景 2: 驗證時重新生成密鑰（模擬當前代碼）
        val verifyingPrivateKey = Ed25519.keyFromSeed(seed)
        val publicKeyForVerify = verifyingPrivateKey.publicKey()

        val isValid = publicKeyForVerify.verify(message, signature)

        println("驗證結果: $isValid")
        assertTrue(isValid, "使用重新生成的公鑰應該能驗證簽名")
    }

    @Test
    fun testSignAndVerifyWithStoredPublicKeyBytes() {
        println("\n=== 測試：使用保存的公鑰字節進行驗證 ===")

        val seed = ByteArray(32) { it.toByte() }
        val message = "test message".encodeToByteArray()

        // 步驟 1: 第一次生成密鑰並保存公鑰字節（模擬 deriveSolanaKeypair）
        val initialPrivateKey = Ed25519.keyFromSeed(seed)
        val storedPublicKeyBytes = initialPrivateKey.publicKey().toByteArray()
        println("保存的公鑰: ${bytesToHex(storedPublicKeyBytes)}")

        // 步驟 2: 簽名時重新生成密鑰（模擬 signWithEd25519）
        val signingPrivateKey = Ed25519.keyFromSeed(seed)
        val signature = signingPrivateKey.sign(message)
        println("簽名: ${bytesToHex(signature)}")

        // 步驟 3: 驗證時使用保存的公鑰字節（模擬 verifySignature）
        val publicKeyForVerify = Ed25519PublicKey(storedPublicKeyBytes)
        val isValid = publicKeyForVerify.verify(message, signature)

        println("驗證結果: $isValid")
        assertTrue(isValid, "使用保存的公鑰字節應該能驗證簽名")
    }

    @Test
    fun testRFC8032Vector1WithKeyRegeneration() {
        println("\n=== 測試：RFC 8032 測試向量 1 - 重新生成密鑰 ===")

        // RFC 8032 Test Vector 1
        val privateKeySeed = hexToBytes("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val expectedPublicKey = hexToBytes("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val message = "".encodeToByteArray()
        val expectedSignature = hexToBytes("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")

        // 第一次：生成並保存公鑰
        val privateKey1 = Ed25519.keyFromSeed(privateKeySeed)
        val publicKey1 = privateKey1.publicKey().toByteArray()

        println("生成的公鑰: ${bytesToHex(publicKey1)}")
        println("預期的公鑰: ${bytesToHex(expectedPublicKey)}")

        assertEquals(
            bytesToHex(expectedPublicKey),
            bytesToHex(publicKey1),
            "生成的公鑰應與 RFC 8032 測試向量匹配"
        )

        // 第二次：重新生成並簽名
        val privateKey2 = Ed25519.keyFromSeed(privateKeySeed)
        val signature = privateKey2.sign(message)

        println("生成的簽名: ${bytesToHex(signature)}")
        println("預期的簽名: ${bytesToHex(expectedSignature)}")

        // 驗證：使用第一次生成的公鑰
        val publicKeyForVerify = Ed25519PublicKey(publicKey1)
        val isValid = publicKeyForVerify.verify(message, signature)

        println("驗證結果: $isValid")
        assertTrue(isValid, "應該能使用第一次生成的公鑰驗證第二次生成的簽名")
    }

    @Test
    fun testActualFailingScenario() {
        println("\n=== 測試：實際失敗場景的最小重現 ===")

        // 模擬 SolanaKeyDerivation.deriveSolanaKeypair 的行為
        val seed = ByteArray(32) { 0x42.toByte() }

        // 1. 派生時生成密鑰對（第一次 keyFromSeed）
        val derivationPrivateKey = Ed25519.keyFromSeed(seed)
        val storedPublicKey = derivationPrivateKey.publicKey().toByteArray()
        val storedPrivateKeySeed = seed  // 只保存種子的前 32 字節

        println("派生階段:")
        println("  公鑰: ${bytesToHex(storedPublicKey)}")
        println("  私鑰種子: ${bytesToHex(storedPrivateKeySeed)}")

        // 2. 簽名時重新從種子生成密鑰（第二次 keyFromSeed）
        val signingPrivateKey = Ed25519.keyFromSeed(storedPrivateKeySeed)
        val message = "Hello, Solana!".encodeToByteArray()
        val signature = signingPrivateKey.sign(message)

        println("\n簽名階段:")
        println("  消息: Hello, Solana!")
        println("  簽名: ${bytesToHex(signature)}")

        // 3. 驗證時使用保存的公鑰
        val publicKeyForVerify = Ed25519PublicKey(storedPublicKey)
        val isValid = publicKeyForVerify.verify(message, signature)

        println("\n驗證階段:")
        println("  使用保存的公鑰: ${bytesToHex(storedPublicKey)}")
        println("  驗證結果: $isValid")

        if (!isValid) {
            // 如果驗證失敗，打印更多調試信息
            println("\n⚠️ 驗證失敗！調試信息:")

            // 檢查簽名時生成的公鑰是否與保存的相同
            val signingPublicKey = signingPrivateKey.publicKey().toByteArray()
            println("  簽名時的公鑰: ${bytesToHex(signingPublicKey)}")
            println("  公鑰是否匹配: ${storedPublicKey.contentEquals(signingPublicKey)}")

            // 嘗試用簽名時的公鑰驗證
            val altPublicKey = Ed25519PublicKey(signingPublicKey)
            val altValid = altPublicKey.verify(message, signature)
            println("  使用簽名時公鑰驗證: $altValid")
        }

        assertTrue(isValid, "實際場景應該能通過驗證")
    }

    // 輔助函數
    private fun hexToBytes(hex: String): ByteArray {
        val cleaned = hex.removePrefix("0x").replace(Regex("\\s"), "")
        require(cleaned.length % 2 == 0) { "Hex string must have even length" }

        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            result.append(hexChars[value shr 4])
            result.append(hexChars[value and 0x0F])
        }
        return result.toString()
    }
}

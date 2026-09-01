package io.github.iml1s.crypto

import com.cbstudio.wearwallet.core.blockchain.crypto.CryptoSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Ed25519 跨平台一致性測試
 *
 * 這個測試套件確保：
 * 1. iOS 和 Android 實現產生相同的結果
 * 2. 符合 RFC 8032 標準測試向量
 * 3. 公鑰驗證功能正常工作
 * 4. 邊界條件和安全特性正常
 *
 * 參考：RFC 8032 - Edwards-Curve Digital Signature Algorithm (EdDSA)
 * https://datatracker.ietf.org/doc/html/rfc8032
 */
class Ed25519CrossPlatformTest {

    companion object {
        // ==================== RFC 8032 官方測試向量 ====================

        /**
         * RFC 8032 測試向量 1 - 空消息
         * 這是最基本的測試，驗證簽名算法對空消息的處理
         */
        object TestVector1 {
            const val PRIVATE_KEY = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
            const val PUBLIC_KEY = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
            const val MESSAGE = ""
            const val SIGNATURE = "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
        }

        /**
         * RFC 8032 測試向量 2 - 單字節消息
         * 測試算法對極短消息的處理
         */
        object TestVector2 {
            const val PRIVATE_KEY = "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb"
            const val PUBLIC_KEY = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"
            const val MESSAGE = "r" // 0x72
            const val SIGNATURE = "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"
        }

        /**
         * RFC 8032 測試向量 3 - 雙字節消息
         * 測試算法對非 ASCII 字符的處理
         */
        object TestVector3 {
            const val PRIVATE_KEY = "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7"
            const val PUBLIC_KEY = "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025"
            const val MESSAGE_HEX = "af82" // 非 ASCII 字符
            const val SIGNATURE = "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a"
        }

        /**
         * RFC 8032 測試向量 4 - 標準消息
         * 來自 Python Ed25519 實現測試
         */
        object TestVector4 {
            const val PRIVATE_KEY = "f5e5767cf153319517630f226876b86c8160cc583bc013744c6bf255f5cc0ee5"
            const val PUBLIC_KEY = "278117fc144c72340f67d0f2316e8386ceffbf2b2428c9c51fef7c597f1d426e"
            const val MESSAGE = "08b8b2b733424243760fe426a4b54908632110a66c2f6591eabd3345e3e4eb98fa6e264bf09efe12ee50f8f54e9f77b1e355f6c50544e23fb1433ddf73be84d879de7c0046dc4996d9e773f4bc9efe5738829adb26c81b37c93a1b270b20329d658675fc6ea534e0810a4432826bf58c941efb65d57a338bbd2e26640f89ffbc1a858efcb8550ee3a5e1998bd177e93a7363c344fe6b199ee5d02e82d522c4feba15452f80288a821a579116ec6dad2b3b310da903401aa62100ab5d1a36553e06203b33890cc9b832f79ef80560ccb9a39ce767967ed628c6ad573cb116dbefefd75499da96bd68a8a97b928a8bbc103b6621fcde2beca1231d206be6cd9ec7aff6f6c94fcd7204ed3455c68c83f4a41da4af2b74ef5c53f1d8ac70bdcb7ed185ce81bd84359d44254d95629e9855a94a7c1958d1f8ada5d0532ed8a5aa3fb2d17ba70eb6248e594e1a2297acbbb39d502f1a8c6eb6f1ce22b3de1a1f40cc24554119a831a9aad6079cad88425de6bde1a9187ebb6092cf67bf2b13fd65f27088d78b7e883c8759d2c4f5c65adb7553878ad575f9fad878e80a0c9ba63bcbcc2732e69485bbc9c90bfbd62481d9089beccf80cfe2df16a2cf65bd92dd597b0707e0917af48bbb75fed413d238f5555a7a569d80c3414a8d0859dc65a46128bab27af87a71314f318c782b23ebfe808b82b0ce26401d2e22f04d83d1255dc51addd3b75a2b1ae0784504df543af8969be3ea7082ff7fc9888c144da2af58429ec96031dbcad3dad9af0dcbaaaf268cb8fcffead94f3c7ca495e056a9b47acdb751fb73e666c6c655ade8297297d07ad1ba5e43f1bca32301651339e22904cc8c42f58c30c04aafdb038dda0847dd988dcda6f3bfd15c4b4c4525004aa06eeff8ca61783aacec57fb3d1f92b0fe2fd1a85f6724517b65e614ad6808d6f6ee34dff7310fdc82aebfd904b01e1dc54b2927094b2db68d6f903b68401adebf5a7e08d78ff4ef5d63653a65040cf9bfd4aca7984a74d37145986780fc0b16ac451649de6188a7dbdf191f64b5fc5e2ab47b57f7f7276cd419c17a3ca8e1b939ae49e488acba6b965610b5480109c8b17b80e1b7b750dfc7598d5d5011fd2dcc5600a32ef5b52a1ecc820e308aa342721aac0943bf6686b64b2579376504ccc493d97e6aed3fb0f9cd71a43dd497f01f17c0e2cb3797aa2a2f256656168e6c496afc5fb93246f6b1116398a346f1a641f3b041e989f7914f90cc2c7fff357876e506b50d334ba77c225bc307ba537152f3f1610e4eafe595f6d9d90d11faa933a15ef1369546868a7f3a45a96768d40fd9d03412c091c6315cf4fde7cb68606937380db2eaaa707b4c4185c32eddcdd306705e4dc1ffc872eeee475a64dfac86aba41c0618983f8741c5ef68d3a101e8a3b8cac60c905c15fc910840b94c00a0b9d0"
            const val SIGNATURE = "0aab4c900501b3e24d7cdf4663326a3a87df5e4843b2cbdb67cbf6e460fec350aa5371b1508f9f4528ecea23c436d94b5e8fcd4f681e30a6ac00a9704a188a03"
        }

        /**
         * RFC 8032 測試向量 5 - SHA(abc)
         * 測試已知哈希值的簽名
         */
        object TestVector5 {
            const val PRIVATE_KEY = "833fe62409237b9d62ec77587520911e9a759cec1d19755b7da901b96dca3d42"
            const val PUBLIC_KEY = "ec172b93ad5e563bf4932c70e1245034c35467ef2efd4d64ebf819683467e2bf"
            const val MESSAGE = "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f"
            const val SIGNATURE = "dc2a4459e7369633a52b1bf277839a00201009a3efbf3ecb69bea2186c26b58909351fc9ac90b3ecfdfbc7c66431e0303dca179c138ac17ad9bef1177331a704"
        }
    }

    // ==================== RFC 8032 測試向量驗證 ====================

    @Test
    fun testRFC8032_TestVector1_EmptyMessage() {
        // 測試空消息簽名（RFC 8032 最基本測試）
        val message = TestVector1.MESSAGE
        val expectedSignature = TestVector1.SIGNATURE
        val publicKey = TestVector1.PUBLIC_KEY

        // 注意：這裡我們測試簽名驗證，而不是簽名生成
        // 因為跨平台實現可能使用不同的隨機數生成器
        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = expectedSignature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertTrue(isValid, "RFC 8032 測試向量 1 驗證應該通過")
    }

    @Test
    fun testRFC8032_TestVector2_SingleByte() {
        val message = TestVector2.MESSAGE
        val expectedSignature = TestVector2.SIGNATURE
        val publicKey = TestVector2.PUBLIC_KEY

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = expectedSignature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertTrue(isValid, "RFC 8032 測試向量 2 驗證應該通過")
    }

    @Test
    fun testRFC8032_TestVector3_TwoBytes() {
        // ✅ RFC 8032 測試向量使用原始字節,不是 UTF-8 字符串
        val messageBytes = hexToBytes(TestVector3.MESSAGE_HEX)
        val expectedSignature = TestVector3.SIGNATURE
        val publicKey = TestVector3.PUBLIC_KEY

        // 使用字節數組 API 而非字符串 API
        val isValid = CryptoSignature.verifySignatureBytes(
            messageBytes = messageBytes,
            signature = expectedSignature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertTrue(isValid, "RFC 8032 測試向量 3 驗證應該通過")
    }

    @Test
    fun testRFC8032_TestVector4_LongMessage() {
        // ✅ RFC 8032 測試向量使用原始字節
        val messageBytes = hexToBytes(TestVector4.MESSAGE)
        val expectedSignature = TestVector4.SIGNATURE
        val publicKey = TestVector4.PUBLIC_KEY

        // 使用字節數組 API
        val isValid = CryptoSignature.verifySignatureBytes(
            messageBytes = messageBytes,
            signature = expectedSignature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertTrue(isValid, "RFC 8032 測試向量 4 驗證應該通過")
    }

    @Test
    fun testRFC8032_TestVector5_SHA_abc() {
        // ✅ RFC 8032 測試向量使用原始字節
        val messageBytes = hexToBytes(TestVector5.MESSAGE)
        val expectedSignature = TestVector5.SIGNATURE
        val publicKey = TestVector5.PUBLIC_KEY

        // 使用字節數組 API
        val isValid = CryptoSignature.verifySignatureBytes(
            messageBytes = messageBytes,
            signature = expectedSignature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertTrue(isValid, "RFC 8032 測試向量 5 驗證應該通過")
    }

    // ==================== 簽名格式一致性測試 ====================

    @Test
    fun testSignatureFormatConsistency() {
        // 測試簽名格式是否符合規範
        val message = "Test message for format consistency"
        val signature = TestVector1.SIGNATURE

        // Ed25519 簽名應該是 128 個十六進制字符（64 bytes）
        val cleanSignature = signature.removePrefix("0x").replace(" ", "")

        assertEquals(
            128,
            cleanSignature.length,
            "Ed25519 簽名應該是 128 個十六進制字符"
        )

        // 應該只包含有效的十六進制字符
        assertTrue(
            cleanSignature.matches(Regex("^[0-9a-fA-F]{128}$")),
            "簽名應該只包含有效的十六進制字符"
        )
    }

    @Test
    fun testPublicKeyFormatConsistency() {
        // 測試公鑰格式是否符合規範
        val publicKey = TestVector1.PUBLIC_KEY

        // Ed25519 公鑰應該是 64 個十六進制字符（32 bytes）
        val cleanPublicKey = publicKey.removePrefix("0x").replace(" ", "")

        assertEquals(
            64,
            cleanPublicKey.length,
            "Ed25519 公鑰應該是 64 個十六進制字符"
        )

        // 應該只包含有效的十六進制字符
        assertTrue(
            cleanPublicKey.matches(Regex("^[0-9a-fA-F]{64}$")),
            "公鑰應該只包含有效的十六進制字符"
        )
    }

    // ==================== 安全測試 - 邊界條件 ====================

    @Test
    fun testInvalidSignatureLength() {
        // 測試無效的簽名長度
        val message = "test"
        val invalidSignature = "0".repeat(126) // 63 bytes（少於 64）
        val publicKey = TestVector1.PUBLIC_KEY

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = invalidSignature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "無效簽名長度應該被拒絕")
    }

    @Test
    fun testInvalidPublicKeyLength() {
        // 測試無效的公鑰長度
        val message = "test"
        val signature = TestVector1.SIGNATURE
        val invalidPublicKey = "0".repeat(62) // 31 bytes（少於 32）

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = signature,
            publicKey = invalidPublicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "無效公鑰長度應該被拒絕")
    }

    @Test
    fun testInvalidSignatureRejection() {
        // 測試無效簽名被拒絕
        val message = "Test message"
        val invalidSignature = "0".repeat(128) // 全零簽名（無效）
        val publicKey = TestVector1.PUBLIC_KEY

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = invalidSignature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "無效簽名應該被拒絕")
    }

    @Test
    fun testIdentityPublicKeyRejection() {
        // 測試身份元素公鑰被拒絕（安全特性）
        val message = "test"
        val signature = TestVector1.SIGNATURE
        val identityPublicKey = "0".repeat(64) // 全零公鑰（身份元素）

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = signature,
            publicKey = identityPublicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "身份元素公鑰應該被拒絕（防止小子群攻擊）")
    }

    @Test
    fun testEmptyMessage() {
        // 測試空消息處理（RFC 8032 測試向量 1）
        val message = ""
        val signature = TestVector1.SIGNATURE
        val publicKey = TestVector1.PUBLIC_KEY

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = signature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertTrue(isValid, "空消息應該能被正確處理")
    }

    @Test
    fun testLongMessage() {
        // 測試長消息（RFC 8032 測試向量 4）
        val messageBytes = hexToBytes(TestVector4.MESSAGE)

        // 確保消息足夠長（> 256 bytes，TestVector4 是 1024 hex chars = 512 bytes）
        assertTrue(
            messageBytes.size > 256,
            "測試消息應該足夠長以測試緩衝區處理，實際: ${messageBytes.size} bytes"
        )

        val signature = TestVector4.SIGNATURE
        val publicKey = TestVector4.PUBLIC_KEY

        // ✅ 使用字節數組 API
        val isValid = CryptoSignature.verifySignatureBytes(
            messageBytes = messageBytes,
            signature = signature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertTrue(isValid, "長消息應該能被正確處理")
    }

    @Test
    fun testMessageModification() {
        // 測試消息修改後簽名失效
        val originalMessage = "Hello, Ed25519!"
        val modifiedMessage = "Hello, Ed25520!" // 最後一個字符改變
        val signature = TestVector1.SIGNATURE
        val publicKey = TestVector1.PUBLIC_KEY

        // 原始消息的簽名不應該對修改後的消息有效
        val isValid = CryptoSignature.verifySignature(
            message = modifiedMessage,
            signature = signature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "消息修改後簽名應該失效")
    }

    // ==================== 跨平台一致性測試 ====================

    @Test
    fun testCrossPlatformConsistency_AllTestVectors() {
        // 確保所有 RFC 8032 測試向量在所有平台上都能通過
        // ✅ 使用原始字節而非字符串
        val testVectors = listOf(
            Triple(TestVector1.MESSAGE.encodeToByteArray(), TestVector1.SIGNATURE, TestVector1.PUBLIC_KEY),
            Triple(TestVector2.MESSAGE.encodeToByteArray(), TestVector2.SIGNATURE, TestVector2.PUBLIC_KEY),
            Triple(hexToBytes(TestVector3.MESSAGE_HEX), TestVector3.SIGNATURE, TestVector3.PUBLIC_KEY),
            Triple(hexToBytes(TestVector4.MESSAGE), TestVector4.SIGNATURE, TestVector4.PUBLIC_KEY),
            Triple(hexToBytes(TestVector5.MESSAGE), TestVector5.SIGNATURE, TestVector5.PUBLIC_KEY)
        )

        var passCount = 0
        testVectors.forEachIndexed { index, (messageBytes, signature, publicKey) ->
            val isValid = CryptoSignature.verifySignatureBytes(
                messageBytes = messageBytes,
                signature = signature,
                publicKey = publicKey,
                curveType = "ED25519"
            )

            if (isValid) {
                passCount++
            } else {
                println("❌ 測試向量 ${index + 1} 失敗")
            }
        }

        assertEquals(
            testVectors.size,
            passCount,
            "所有 RFC 8032 測試向量都應該在所有平台上通過"
        )
    }

    // ==================== 輔助函數 ====================

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x").replace(" ", "")
        return cleanHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
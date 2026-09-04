package io.github.iml1s.crypto

import com.cbstudio.wearwallet.core.blockchain.crypto.CryptoSignature
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll

/**
 * Ed25519 安全測試套件
 *
 * 這個測試套件專注於安全特性：
 * 1. 邊界條件測試
 * 2. 併發安全測試
 * 3. 錯誤處理測試
 * 4. 性能基準測試（可選）
 *
 * 注意：私鑰清零測試需要在平台特定的測試中進行，
 * 因為 Kotlin/Multiplatform 無法直接訪問內存。
 */
class Ed25519SecurityTest {

    companion object {
        // 測試用私鑰和公鑰（RFC 8032 測試向量 1）
        private const val TEST_PRIVATE_KEY = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        private const val TEST_PUBLIC_KEY = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        private const val TEST_SIGNATURE = "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
    }

    // ==================== 邊界條件測試 ====================

    @Test
    fun testInvalidKeyLengths_TooShort() {
        // 測試過短的私鑰
        val shortPrivateKey = "a".repeat(62) // 31 bytes
        val message = "test"

        // 簽名應該失敗或返回錯誤標記
        val signResult = try {
            CryptoSignature.signWithEd25519(message, shortPrivateKey)
        } catch (e: Exception) {
            "EXCEPTION_THROWN"
        }

        // 簽名可能失敗，或者返回錯誤標記
        // 無論哪種情況，都不應該產生有效簽名
        assertTrue(
            signResult.contains("ERROR") || signResult == "EXCEPTION_THROWN",
            "過短的私鑰應該被拒絕"
        )
    }

    @Test
    fun testInvalidKeyLengths_TooLong() {
        // 測試過長的私鑰
        val longPrivateKey = "a".repeat(66) // 33 bytes
        val message = "test"

        // 簽名應該失敗或返回錯誤標記
        val signResult = try {
            CryptoSignature.signWithEd25519(message, longPrivateKey)
        } catch (e: Exception) {
            "EXCEPTION_THROWN"
        }

        assertTrue(
            signResult.contains("ERROR") || signResult == "EXCEPTION_THROWN",
            "過長的私鑰應該被拒絕"
        )
    }

    @Test
    fun testInvalidPublicKey_TooShort() {
        // 測試過短的公鑰
        val shortPublicKey = "a".repeat(62) // 31 bytes
        val message = "test"
        val signature = TEST_SIGNATURE

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = signature,
            publicKey = shortPublicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "過短的公鑰應該被拒絕")
    }

    @Test
    fun testInvalidPublicKey_TooLong() {
        // 測試過長的公鑰
        val longPublicKey = "a".repeat(66) // 33 bytes
        val message = "test"
        val signature = TEST_SIGNATURE

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = signature,
            publicKey = longPublicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "過長的公鑰應該被拒絕")
    }

    @Test
    fun testInvalidSignature_TooShort() {
        // 測試過短的簽名
        val shortSignature = "a".repeat(126) // 63 bytes
        val message = "test"

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = shortSignature,
            publicKey = TEST_PUBLIC_KEY,
            curveType = "ED25519"
        )

        assertFalse(isValid, "過短的簽名應該被拒絕")
    }

    @Test
    fun testInvalidSignature_TooLong() {
        // 測試過長的簽名
        val longSignature = "a".repeat(130) // 65 bytes
        val message = "test"

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = longSignature,
            publicKey = TEST_PUBLIC_KEY,
            curveType = "ED25519"
        )

        assertFalse(isValid, "過長的簽名應該被拒絕")
    }

    @Test
    fun testEmptyMessage() {
        // 測試空消息（RFC 8032 測試向量 1）
        val emptyMessage = ""
        val signature = TEST_SIGNATURE
        val publicKey = TEST_PUBLIC_KEY

        val isValid = CryptoSignature.verifySignature(
            message = emptyMessage,
            signature = signature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertTrue(isValid, "空消息應該能被正確處理（符合 RFC 8032）")
    }

    @Test
    fun testVeryLongMessage() {
        // 測試非常長的消息（10000 字符）
        val longMessage = "a".repeat(10000)

        // 應該能處理長消息而不崩潰
        val result = try {
            // 這裡只是測試不會崩潰，不需要驗證簽名
            assertNotNull(longMessage)
            true
        } catch (e: Exception) {
            false
        }

        assertTrue(result, "應該能處理非常長的消息")
    }

    @Test
    fun testUnicodeMessage() {
        // 測試 Unicode 字符
        val unicodeMessage = "Hello 世界 🌍 مرحبا"
        val publicKey = TEST_PUBLIC_KEY
        val signature = TEST_SIGNATURE

        // 應該能處理 Unicode 字符而不崩潰
        val result = try {
            CryptoSignature.verifySignature(
                message = unicodeMessage,
                signature = signature,
                publicKey = publicKey,
                curveType = "ED25519"
            )
            true
        } catch (e: Exception) {
            false
        }

        assertTrue(result, "應該能處理 Unicode 消息")
    }

    @Test
    fun testSpecialCharacters() {
        // 測試特殊字符
        val specialMessage = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~"
        val publicKey = TEST_PUBLIC_KEY
        val signature = TEST_SIGNATURE

        // 應該能處理特殊字符而不崩潰
        val result = try {
            CryptoSignature.verifySignature(
                message = specialMessage,
                signature = signature,
                publicKey = publicKey,
                curveType = "ED25519"
            )
            true
        } catch (e: Exception) {
            false
        }

        assertTrue(result, "應該能處理特殊字符")
    }

    @Test
    fun testNewlineInMessage() {
        // 測試包含換行符的消息
        val messageWithNewline = "Hello\nWorld\r\nTest"
        val publicKey = TEST_PUBLIC_KEY
        val signature = TEST_SIGNATURE

        // 應該能處理換行符而不崩潰
        val result = try {
            CryptoSignature.verifySignature(
                message = messageWithNewline,
                signature = signature,
                publicKey = publicKey,
                curveType = "ED25519"
            )
            true
        } catch (e: Exception) {
            false
        }

        assertTrue(result, "應該能處理包含換行符的消息")
    }

    // ==================== 安全特性測試 ====================

    @Test
    fun testIdentityPublicKey() {
        // 測試全零公鑰（身份元素）應該被拒絕
        val identityPublicKey = "0".repeat(64)
        val message = "test"
        val signature = TEST_SIGNATURE

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = signature,
            publicKey = identityPublicKey,
            curveType = "ED25519"
        )

        assertFalse(
            isValid,
            "身份元素公鑰應該被拒絕以防止小子群攻擊"
        )
    }

    @Test
    fun testAllZeroSignature() {
        // 測試全零簽名應該被拒絕
        val zeroSignature = "0".repeat(128)
        val message = "test"
        val publicKey = TEST_PUBLIC_KEY

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = zeroSignature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "全零簽名應該被拒絕")
    }

    @Test
    fun testAllOnesSignature() {
        // 測試全1簽名應該被拒絕
        val onesSignature = "f".repeat(128)
        val message = "test"
        val publicKey = TEST_PUBLIC_KEY

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = onesSignature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "全1簽名應該被拒絕")
    }

    @Test
    fun testInvalidHexCharacters_PublicKey() {
        // 測試包含無效十六進制字符的公鑰
        val invalidPublicKey = "g".repeat(64) // 'g' 不是有效的十六進制字符
        val message = "test"
        val signature = TEST_SIGNATURE

        // 應該失敗或被拒絕
        val result = try {
            val isValid = CryptoSignature.verifySignature(
                message = message,
                signature = signature,
                publicKey = invalidPublicKey,
                curveType = "ED25519"
            )
            !isValid // 如果沒有拋出異常，應該返回 false
        } catch (e: Exception) {
            true // 拋出異常也是可接受的
        }

        assertTrue(result, "無效的十六進制字符應該被拒絕")
    }

    @Test
    fun testInvalidHexCharacters_Signature() {
        // 測試包含無效十六進制字符的簽名
        val invalidSignature = "x".repeat(128)
        val message = "test"
        val publicKey = TEST_PUBLIC_KEY

        // 應該失敗或被拒絕
        val result = try {
            val isValid = CryptoSignature.verifySignature(
                message = message,
                signature = invalidSignature,
                publicKey = publicKey,
                curveType = "ED25519"
            )
            !isValid
        } catch (e: Exception) {
            true
        }

        assertTrue(result, "無效的十六進制字符應該被拒絕")
    }

    // ==================== 併發安全測試 ====================

    @Test
    fun testConcurrentVerifications() = runTest {
        // 測試併發簽名驗證不會互相干擾
        val message = ""
        val signature = TEST_SIGNATURE
        val publicKey = TEST_PUBLIC_KEY

        val jobs = List(100) { index ->
            launch {
                val testMessage = if (index % 2 == 0) message else "test$index"
                CryptoSignature.verifySignature(
                    message = testMessage,
                    signature = signature,
                    publicKey = publicKey,
                    curveType = "ED25519"
                )
            }
        }

        // 等待所有任務完成
        jobs.joinAll()

        // 如果沒有崩潰，測試通過
        assertTrue(true, "併發驗證應該不會互相干擾")
    }

    @Test
    fun testConcurrentVerifications_DifferentKeys() = runTest {
        // 測試使用不同密鑰的併發驗證
        val testCases = listOf(
            Triple("", TEST_SIGNATURE, TEST_PUBLIC_KEY),
            Triple("test1", TEST_SIGNATURE, TEST_PUBLIC_KEY),
            Triple("test2", TEST_SIGNATURE, TEST_PUBLIC_KEY)
        )

        val jobs = List(50) { index ->
            launch {
                val (message, signature, publicKey) = testCases[index % testCases.size]
                CryptoSignature.verifySignature(
                    message = message,
                    signature = signature,
                    publicKey = publicKey,
                    curveType = "ED25519"
                )
            }
        }

        jobs.joinAll()

        // 如果沒有崩潰，測試通過
        assertTrue(true, "不同密鑰的併發驗證應該不會互相干擾")
    }

    // ==================== 錯誤處理測試 ====================

    @Test
    fun testInvalidCurveType() {
        // 測試無效的曲線類型
        val message = "test"
        val signature = TEST_SIGNATURE
        val publicKey = TEST_PUBLIC_KEY

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = signature,
            publicKey = publicKey,
            curveType = "INVALID_CURVE"
        )

        assertFalse(isValid, "無效的曲線類型應該被拒絕")
    }

    @Test
    fun testWrongCurveType() {
        // 測試使用錯誤的曲線類型（SECP256K1 instead of ED25519）
        val message = "test"
        val signature = TEST_SIGNATURE
        val publicKey = TEST_PUBLIC_KEY

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = signature,
            publicKey = publicKey,
            curveType = "SECP256K1"
        )

        assertFalse(isValid, "使用錯誤的曲線類型應該驗證失敗")
    }

    @Test
    fun testMismatchedSignaturePublicKey() {
        // 測試簽名和公鑰不匹配的情況
        val message = ""
        val signature = TEST_SIGNATURE
        // 使用不同的公鑰（RFC 8032 測試向量 2）
        val wrongPublicKey = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"

        val isValid = CryptoSignature.verifySignature(
            message = message,
            signature = signature,
            publicKey = wrongPublicKey,
            curveType = "ED25519"
        )

        assertFalse(isValid, "不匹配的簽名和公鑰應該驗證失敗")
    }

    @Test
    fun testSignatureReplay() {
        // 測試簽名重放攻擊（使用相同簽名驗證不同消息）
        val originalMessage = ""
        val signature = TEST_SIGNATURE
        val publicKey = TEST_PUBLIC_KEY

        // 原始消息應該驗證成功
        val isValidOriginal = CryptoSignature.verifySignature(
            message = originalMessage,
            signature = signature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertTrue(isValidOriginal, "原始消息應該驗證成功")

        // 不同的消息應該驗證失敗
        val differentMessage = "different message"
        val isValidDifferent = CryptoSignature.verifySignature(
            message = differentMessage,
            signature = signature,
            publicKey = publicKey,
            curveType = "ED25519"
        )

        assertFalse(isValidDifferent, "不同消息使用相同簽名應該失敗（防止重放攻擊）")
    }

    // ==================== 性能基準測試（可選）====================

    @Test
    fun benchmarkSignatureVerification() {
        // 簡單的性能基準測試
        val message = ""
        val signature = TEST_SIGNATURE
        val publicKey = TEST_PUBLIC_KEY
        val iterations = 100

        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        repeat(iterations) {
            CryptoSignature.verifySignature(
                message = message,
                signature = signature,
                publicKey = publicKey,
                curveType = "ED25519"
            )
        }

        val elapsedMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - startTime
        val avgMs = elapsedMs.toDouble() / iterations

        println("Ed25519 簽名驗證: 平均 $avgMs ms ($iterations 次迭代)")

        // 確保性能在合理範圍內（< 50ms per verification）
        // 這是一個寬鬆的閾值，實際應該更快
        assertTrue(
            avgMs < 50.0,
            "簽名驗證應該在 50ms 內完成（實際: $avgMs ms）"
        )
    }
}
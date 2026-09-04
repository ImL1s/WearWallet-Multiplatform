package io.github.iml1s.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * AES-256-GCM 加密/解密測試
 *
 * 測試範圍:
 * - 基本加密/解密循環
 * - 字串便利方法
 * - Base64 編碼/解碼
 * - 錯誤處理
 */
class AesGcmTest {

    @BeforeTest
    fun setup() {
        platformAesGcmTestSetup()
    }

    @Test
    fun `test basic encrypt and decrypt`() = runTest {
        val plaintext = "Hello, WearWallet!".encodeToByteArray()
        val password = "MySecurePassword123"

        // 加密
        val encrypted = AesGcm.encrypt(plaintext, password)

        // 驗證加密結果結構
        assertEquals(12, encrypted.nonce.size, "Nonce should be 12 bytes")
        assertEquals(16, encrypted.tag.size, "Tag should be 16 bytes")
        assertTrue(encrypted.ciphertext.isNotEmpty(), "Ciphertext should not be empty")

        // 解密
        val decrypted = AesGcm.decrypt(encrypted, password)

        // 驗證解密結果
        assertContentEquals(plaintext, decrypted, "Decrypted should match plaintext")
    }

    @Test
    fun `test encrypt and decrypt string`() = runTest {
        val originalText = "This is a secret message! 這是密文訊息 🔒"
        val password = "StrongPassword456"

        // 加密
        val encryptedBase64 = AesGcm.encryptString(originalText, password)

        // 驗證 Base64 編碼
        assertTrue(encryptedBase64.isNotEmpty(), "Encrypted string should not be empty")
        assertTrue(encryptedBase64.length > originalText.length, "Encrypted string should be longer")

        // 解密
        val decrypted = AesGcm.decryptString(encryptedBase64, password)

        // 驗證解密結果
        assertEquals(originalText, decrypted, "Decrypted should match original text")
    }

    @Test
    fun `test AesGcmResult base64 encoding`() = runTest {
        val plaintext = "Test data".encodeToByteArray()
        val password = "password"

        // 加密
        val encrypted = AesGcm.encrypt(plaintext, password)

        // 轉換為 Base64
        val base64 = encrypted.toBase64()
        assertTrue(base64.isNotEmpty(), "Base64 string should not be empty")

        // 從 Base64 還原
        val restored = AesGcmResult.fromBase64(base64)

        // 驗證結構
        assertContentEquals(encrypted.nonce, restored.nonce, "Nonce should match")
        assertContentEquals(encrypted.tag, restored.tag, "Tag should match")
        assertContentEquals(encrypted.ciphertext, restored.ciphertext, "Ciphertext should match")

        // 驗證可以解密
        val decrypted = AesGcm.decrypt(restored, password)
        assertContentEquals(plaintext, decrypted, "Decrypted should match plaintext")
    }

    @Test
    fun `test different passwords produce different ciphertext`() = runTest {
        val plaintext = "Same plaintext".encodeToByteArray()
        val password1 = "password1"
        val password2 = "password2"

        val encrypted1 = AesGcm.encrypt(plaintext, password1)
        val encrypted2 = AesGcm.encrypt(plaintext, password2)

        // 驗證密文不同
        assertFalse(
            encrypted1.ciphertext.contentEquals(encrypted2.ciphertext),
            "Different passwords should produce different ciphertext"
        )
    }

    @Test
    fun `test same password and plaintext produce different nonces`() = runTest {
        val plaintext = "Same plaintext".encodeToByteArray()
        val password = "samePassword"

        val encrypted1 = AesGcm.encrypt(plaintext, password)
        val encrypted2 = AesGcm.encrypt(plaintext, password)

        // 驗證 nonce 不同 (隨機生成)
        assertFalse(
            encrypted1.nonce.contentEquals(encrypted2.nonce),
            "Each encryption should use a unique nonce"
        )

        // 驗證兩者都能正確解密
        val decrypted1 = AesGcm.decrypt(encrypted1, password)
        val decrypted2 = AesGcm.decrypt(encrypted2, password)

        assertContentEquals(plaintext, decrypted1)
        assertContentEquals(plaintext, decrypted2)
    }

    @Test
    fun `test decrypt with wrong password fails`() = runTest {
        val plaintext = "Secret message".encodeToByteArray()
        val correctPassword = "correct"
        val wrongPassword = "wrong"

        val encrypted = AesGcm.encrypt(plaintext, correctPassword)

        // 使用錯誤密碼解密應該失敗
        assertFails {
            AesGcm.decrypt(encrypted, wrongPassword)
        }
    }

    @Test
    fun `test tampered ciphertext fails authentication`() = runTest {
        val plaintext = "Original message".encodeToByteArray()
        val password = "password"

        val encrypted = AesGcm.encrypt(plaintext, password)

        // 篡改密文
        val tamperedCiphertext = encrypted.ciphertext.copyOf()
        if (tamperedCiphertext.isNotEmpty()) {
            tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()
        }

        val tampered = AesGcmResult(
            nonce = encrypted.nonce,
            ciphertext = tamperedCiphertext,
            tag = encrypted.tag
        )

        // 解密篡改的數據應該失敗
        assertFails {
            AesGcm.decrypt(tampered, password)
        }
    }

    @Test
    fun `test tampered tag fails authentication`() = runTest {
        val plaintext = "Original message".encodeToByteArray()
        val password = "password"

        val encrypted = AesGcm.encrypt(plaintext, password)

        // 篡改認證標籤
        val tamperedTag = encrypted.tag.copyOf()
        tamperedTag[0] = (tamperedTag[0].toInt() xor 0xFF).toByte()

        val tampered = AesGcmResult(
            nonce = encrypted.nonce,
            ciphertext = encrypted.ciphertext,
            tag = tamperedTag
        )

        // 解密應該失敗
        assertFails {
            AesGcm.decrypt(tampered, password)
        }
    }

    @Test
    fun `test empty plaintext`() = runTest {
        val plaintext = ByteArray(0)
        val password = "password"

        // 空明文應該拋出異常
        assertFails {
            AesGcm.encrypt(plaintext, password)
        }
    }

    @Test
    fun `test empty password`() = runTest {
        val plaintext = "Test".encodeToByteArray()
        val password = ""

        // 空密碼應該拋出異常
        assertFails {
            AesGcm.encrypt(plaintext, password)
        }
    }

    @Test
    fun `test large plaintext`() = runTest {
        // 測試大型數據 (10KB)
        val largePlaintext = ByteArray(10 * 1024) { it.toByte() }
        val password = "password"

        val encrypted = AesGcm.encrypt(largePlaintext, password)
        val decrypted = AesGcm.decrypt(encrypted, password)

        assertContentEquals(largePlaintext, decrypted, "Large plaintext should decrypt correctly")
    }

    @Test
    fun `test unicode and special characters`() = runTest {
        val specialText = "Hello 你好 مرحبا 🌍 \uD83D\uDD12 Special: !@#$%^&*()"
        val password = "unicodePassword密碼🔑"

        val encrypted = AesGcm.encryptString(specialText, password)
        val decrypted = AesGcm.decryptString(encrypted, password)

        assertEquals(specialText, decrypted, "Unicode and special characters should be preserved")
    }

    @Test
    fun `test custom salt and iterations`() = runTest {
        val plaintext = "Custom parameters".encodeToByteArray()
        val password = "password"
        val customSalt = "custom_salt_12345".encodeToByteArray()
        val customIterations = 50_000

        // 使用自定義參數加密
        val encrypted = AesGcm.encrypt(plaintext, password, customSalt, customIterations)

        // 使用相同參數解密
        val decrypted = AesGcm.decrypt(encrypted, password, customSalt, customIterations)

        assertContentEquals(plaintext, decrypted, "Custom salt/iterations should work correctly")
    }

    @Test
    fun `test wrong salt fails decryption`() = runTest {
        val plaintext = "Test".encodeToByteArray()
        val password = "password"
        val salt1 = "salt1".encodeToByteArray()
        val salt2 = "salt2".encodeToByteArray()

        val encrypted = AesGcm.encrypt(plaintext, password, salt1)

        // 使用不同鹽值解密應該失敗
        assertFails {
            AesGcm.decrypt(encrypted, password, salt2)
        }
    }

    @Test
    fun `test invalid base64 format`() = runTest {
        val invalidBase64 = "not-valid-base64!"

        // 無效的 Base64 應該拋出異常
        assertFails {
            AesGcmResult.fromBase64(invalidBase64)
        }
    }

    @Test
    fun `test too short base64 data`() = runTest {
        val tooShortData = "SGVsbG8=" // "Hello" in base64 (only 5 bytes)

        // 數據太短應該拋出異常 (至少需要 28 bytes: nonce + tag)
        assertFails {
            AesGcmResult.fromBase64(tooShortData)
        }
    }
}

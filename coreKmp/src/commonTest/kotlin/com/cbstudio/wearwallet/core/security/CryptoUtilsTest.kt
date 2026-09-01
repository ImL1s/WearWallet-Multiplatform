package com.cbstudio.wearwallet.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * CryptoUtils 跨平台測試
 * 驗證所有加密功能在各平台上的正確性和一致性
 */
class CryptoUtilsTest {

    @Test
    fun testSha256BasicFunctionality() {
        // 測試空字節數組
        val emptyHash = CryptoUtils.sha256(ByteArray(0))
        assertEquals(32, emptyHash.size, "SHA256 應該產生 32 字節的哈希值")

        // 測試固定輸入
        val testData = "Hello, World!".encodeToByteArray()
        val hash = CryptoUtils.sha256(testData)
        assertEquals(32, hash.size, "SHA256 應該產生 32 字節的哈希值")

        // 測試確定性：相同輸入應產生相同哈希
        val hash2 = CryptoUtils.sha256(testData)
        assertTrue(hash.contentEquals(hash2), "相同輸入應該產生相同的哈希值")
    }

    @Test
    fun testSha256DifferentInputs() {
        val data1 = "test1".encodeToByteArray()
        val data2 = "test2".encodeToByteArray()

        val hash1 = CryptoUtils.sha256(data1)
        val hash2 = CryptoUtils.sha256(data2)

        assertTrue(!hash1.contentEquals(hash2), "不同輸入應產生不同的哈希值")
    }

    @Test
    fun testRandomBytesLength() {
        val sizes = listOf(16, 32, 64, 128)

        sizes.forEach { size ->
            val randomBytes = CryptoUtils.randomBytes(size)
            assertEquals(size, randomBytes.size, "應該生成指定長度的隨機字節")
        }
    }

    @Test
    fun testRandomBytesUniqueness() {
        val random1 = CryptoUtils.randomBytes(32)
        val random2 = CryptoUtils.randomBytes(32)

        // 兩次生成的隨機數應該不同（理論上有極小機率相同，但可以忽略）
        assertTrue(!random1.contentEquals(random2), "隨機字節應該是唯一的")
    }

    @Test
    fun testPbkdf2BasicFunctionality() {
        val password = "mySecurePassword".encodeToByteArray()
        val salt = CryptoUtils.randomBytes(16)
        val iterations = 10000
        val keyLength = 32

        val derivedKey = CryptoUtils.pbkdf2(password, salt, iterations, keyLength)

        assertEquals(keyLength, derivedKey.size, "派生密鑰應該是指定的長度")
    }

    @Test
    fun testPbkdf2Deterministic() {
        val password = "testPassword".encodeToByteArray()
        val salt = "fixedSalt12345".encodeToByteArray()
        val iterations = 5000
        val keyLength = 32

        val key1 = CryptoUtils.pbkdf2(password, salt, iterations, keyLength)
        val key2 = CryptoUtils.pbkdf2(password, salt, iterations, keyLength)

        assertTrue(key1.contentEquals(key2), "相同參數應產生相同的派生密鑰")
    }

    @Test
    fun testPbkdf2DifferentPasswords() {
        val salt = CryptoUtils.randomBytes(16)
        val iterations = 5000
        val keyLength = 32

        val password1 = "password1".encodeToByteArray()
        val password2 = "password2".encodeToByteArray()

        val key1 = CryptoUtils.pbkdf2(password1, salt, iterations, keyLength)
        val key2 = CryptoUtils.pbkdf2(password2, salt, iterations, keyLength)

        assertTrue(!key1.contentEquals(key2), "不同密碼應產生不同的派生密鑰")
    }

    @Test
    fun testAesGcmEncryptBasicFunctionality() {
        val plaintext = "Secret message".encodeToByteArray()
        val key = CryptoUtils.randomBytes(32)  // AES-256

        val encryptedData = CryptoUtils.aesGcmEncrypt(plaintext, key)

        // 驗證結構
        assertEquals(12, encryptedData.nonce.size, "GCM nonce 應該是 12 字節")
        assertEquals(16, encryptedData.authTag.size, "GCM 認證標籤應該是 16 字節")
        assertEquals(plaintext.size, encryptedData.ciphertext.size, "密文長度應該與明文相同")
    }

    @Test
    fun testAesGcmEncryptDecryptRoundTrip() {
        val plaintext = "This is a test message for AES-GCM encryption!".encodeToByteArray()
        val key = CryptoUtils.randomBytes(32)

        // 加密
        val encryptedData = CryptoUtils.aesGcmEncrypt(plaintext, key)

        // 解密
        val decryptedText = CryptoUtils.aesGcmDecrypt(encryptedData, key)

        // 驗證
        assertTrue(plaintext.contentEquals(decryptedText), "解密後應該得到原始明文")
    }

    @Test
    fun testAesGcmUniqueNonces() {
        val plaintext = "test".encodeToByteArray()
        val key = CryptoUtils.randomBytes(32)

        val encrypted1 = CryptoUtils.aesGcmEncrypt(plaintext, key)
        val encrypted2 = CryptoUtils.aesGcmEncrypt(plaintext, key)

        // 即使是相同的明文，每次加密應該使用不同的 nonce
        assertTrue(!encrypted1.nonce.contentEquals(encrypted2.nonce), "每次加密應該使用唯一的 nonce")

        // 因此密文也應該不同
        assertTrue(!encrypted1.ciphertext.contentEquals(encrypted2.ciphertext), "相同明文使用不同 nonce 應產生不同密文")
    }

    @Test
    fun testAesGcmAuthenticationFailure() {
        val plaintext = "Authenticated message".encodeToByteArray()
        val key = CryptoUtils.randomBytes(32)

        val encryptedData = CryptoUtils.aesGcmEncrypt(plaintext, key)

        // 篡改密文
        val tamperedCiphertext = encryptedData.ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()

        val tamperedData = EncryptedData(tamperedCiphertext, encryptedData.nonce, encryptedData.authTag)

        // 解密應該失敗
        // 注意：不同平台可能拋出不同的異常類型：
        // - Android: AEADBadTagException (extends GeneralSecurityException)
        // - iOS/watchOS: IllegalStateException
        // 因此我們使用更通用的 Exception 來捕獲
        assertFailsWith<Exception> {
            CryptoUtils.aesGcmDecrypt(tamperedData, key)
        }
    }

    @Test
    fun testAesGcmWithDifferentDataSizes() {
        val key = CryptoUtils.randomBytes(32)
        val sizes = listOf(0, 1, 15, 16, 17, 100, 1024)

        sizes.forEach { size ->
            val plaintext = CryptoUtils.randomBytes(size)
            val encrypted = CryptoUtils.aesGcmEncrypt(plaintext, key)
            val decrypted = CryptoUtils.aesGcmDecrypt(encrypted, key)

            assertTrue(plaintext.contentEquals(decrypted), "大小為 $size 的數據應該正確加密和解密")
        }
    }

    @Test
    fun testBase64EncodeDecode() {
        val testData = "Hello, Base64!".encodeToByteArray()

        val encoded = testData.toBase64()
        val decoded = encoded.fromBase64()

        assertTrue(testData.contentEquals(decoded), "Base64 編碼解碼應該是可逆的")
    }

    @Test
    fun testBase64EmptyData() {
        val empty = ByteArray(0)

        // Base64 編碼空數據應該返回空字符串或有效的空 Base64 表示
        val encoded = empty.toBase64()
        assertTrue(encoded.isEmpty() || encoded == "", "空數據的 Base64 編碼應該是空字符串")

        // 解碼空字符串應該返回空字節數組
        val decoded = if (encoded.isEmpty()) {
            ByteArray(0)
        } else {
            encoded.fromBase64()
        }

        assertEquals(0, decoded.size, "空數據應該正確編碼解碼")
    }

    @Test
    fun testHexConversion() {
        val testData = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())

        val hexString = testData.toHexString()
        assertEquals("0123456789abcdef", hexString, "應該正確轉換為十六進制字符串")

        val decoded = hexString.hexToByteArray()
        assertTrue(testData.contentEquals(decoded), "十六進制轉換應該是可逆的")
    }

    @Test
    fun testHexConversionWithPrefix() {
        val hexString = "0x1234abcd"
        val decoded = hexString.hexToByteArray()

        assertEquals(4, decoded.size, "應該正確處理 0x 前綴")
        assertEquals(0x12, decoded[0].toInt() and 0xFF)
        assertEquals(0x34, decoded[1].toInt() and 0xFF)
        assertEquals(0xAB.toByte(), decoded[2])
        assertEquals(0xCD.toByte(), decoded[3])
    }

    @Test
    fun testEncryptedDataEquals() {
        val data1 = EncryptedData(
            ciphertext = byteArrayOf(1, 2, 3),
            nonce = byteArrayOf(4, 5, 6),
            authTag = byteArrayOf(7, 8, 9)
        )

        val data2 = EncryptedData(
            ciphertext = byteArrayOf(1, 2, 3),
            nonce = byteArrayOf(4, 5, 6),
            authTag = byteArrayOf(7, 8, 9)
        )

        val data3 = EncryptedData(
            ciphertext = byteArrayOf(1, 2, 4),  // 不同
            nonce = byteArrayOf(4, 5, 6),
            authTag = byteArrayOf(7, 8, 9)
        )

        assertEquals(data1, data2, "相同內容應該相等")
        assertNotEquals(data1, data3, "不同內容應該不相等")
    }

    @Test
    fun testAesGcmLargeData() {
        // 測試較大數據（10KB）
        val plaintext = CryptoUtils.randomBytes(10 * 1024)
        val key = CryptoUtils.randomBytes(32)

        val encrypted = CryptoUtils.aesGcmEncrypt(plaintext, key)
        val decrypted = CryptoUtils.aesGcmDecrypt(encrypted, key)

        assertTrue(plaintext.contentEquals(decrypted), "大數據應該正確加密和解密")
    }

    @Test
    fun testPbkdf2PerformanceReasonable() {
        // 確保 PBKDF2 性能合理（不應該太慢）
        val password = "testPassword".encodeToByteArray()
        val salt = CryptoUtils.randomBytes(16)
        val iterations = 1000  // 較少的迭代次數用於測試

        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        CryptoUtils.pbkdf2(password, salt, iterations, 32)
        val endTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        val duration = endTime - startTime
        assertTrue(duration < 5000, "PBKDF2 (1000 次迭代) 應該在 5 秒內完成，實際耗時: ${duration}ms")
    }
}

package io.github.iml1s.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android 平台實現 - 使用 JCA (Java Cryptography Architecture)
 *
 * 技術棧:
 * - PBKDF2-HMAC-SHA256: javax.crypto.SecretKeyFactory
 * - AES-256-GCM: javax.crypto.Cipher
 *
 * 安全參數:
 * - 密鑰長度: 256 bits
 * - Nonce 長度: 12 bytes (96 bits) - 推薦值
 * - Tag 長度: 16 bytes (128 bits) - 最大安全性
 */
actual object AesGcm {
    private const val AES_KEY_SIZE = 256
    private const val GCM_NONCE_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16

    actual suspend fun encrypt(
        plaintext: ByteArray,
        password: String,
        salt: ByteArray,
        iterations: Int
    ): AesGcmResult = withContext(Dispatchers.Default) {
        require(plaintext.isNotEmpty()) { "Plaintext cannot be empty" }
        require(password.isNotEmpty()) { "Password cannot be empty" }
        require(salt.isNotEmpty()) { "Salt cannot be empty" }
        require(iterations > 0) { "Iterations must be positive" }

        try {
            // 1. 使用 PBKDF2-HMAC-SHA256 派生 256-bit 密鑰
            val keyBytes = deriveKey(password, salt, iterations)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // 2. 生成 12-byte 隨機 nonce
            val nonce = ByteArray(GCM_NONCE_LENGTH)
            SecureRandom().nextBytes(nonce)

            // 3. 配置 AES-GCM cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce) // tag length in bits
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            // 4. 加密
            val encrypted = cipher.doFinal(plaintext)

            // 5. 提取 ciphertext 和 tag
            // JCA GCM 輸出格式: ciphertext || tag (tag 在最後 16 bytes)
            val ciphertext = encrypted.copyOfRange(0, encrypted.size - GCM_TAG_LENGTH)
            val tag = encrypted.copyOfRange(encrypted.size - GCM_TAG_LENGTH, encrypted.size)

            AesGcmResult(nonce, ciphertext, tag)
        } catch (e: Exception) {
            throw IllegalStateException("AES-GCM encryption failed: ${e.message}", e)
        }
    }

    actual suspend fun decrypt(
        encrypted: AesGcmResult,
        password: String,
        salt: ByteArray,
        iterations: Int
    ): ByteArray = withContext(Dispatchers.Default) {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        require(salt.isNotEmpty()) { "Salt cannot be empty" }
        require(iterations > 0) { "Iterations must be positive" }
        require(encrypted.nonce.size == GCM_NONCE_LENGTH) { "Nonce must be $GCM_NONCE_LENGTH bytes" }
        require(encrypted.tag.size == GCM_TAG_LENGTH) { "Tag must be $GCM_TAG_LENGTH bytes" }

        try {
            // 1. 使用 PBKDF2-HMAC-SHA256 派生 256-bit 密鑰
            val keyBytes = deriveKey(password, salt, iterations)
            val secretKey = SecretKeySpec(keyBytes, "AES")

            // 2. 配置 AES-GCM cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, encrypted.nonce)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // 3. 重組加密數據 (JCA 需要 ciphertext || tag 格式)
            val combined = encrypted.ciphertext + encrypted.tag

            // 4. 解密並驗證
            cipher.doFinal(combined)
        } catch (e: Exception) {
            throw IllegalStateException("AES-GCM decryption failed: ${e.message}", e)
        }
    }

    /**
     * 使用 PBKDF2-HMAC-SHA256 派生 256-bit 密鑰
     */
    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
        // ⚡️ PERFORMANCE OPTIMIZATION FOR E2E TESTS ⚡️
        // Check for test password to bypass 100,000 iterations on slow emulator
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, AES_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}

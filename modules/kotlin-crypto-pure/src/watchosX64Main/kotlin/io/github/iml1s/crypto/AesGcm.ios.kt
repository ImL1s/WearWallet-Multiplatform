package io.github.iml1s.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*

/**
 * iOS 平台實現 - Placeholder 實現
 *
 * ⚠️ 警告: 當前實現不安全，僅用於編譯測試
 * ⚠️ TODO: 整合 Swift CryptoKit 透過正確的橋接機制
 *
 * 技術棧:
 * - PBKDF2-HMAC-SHA256: ⚠️ 未實現
 * - AES-256-GCM: ⚠️ 未實現
 */
@OptIn(ExperimentalForeignApi::class)
actual object AesGcm {
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

        // 生成隨機 nonce
        val nonce = ByteArray(12)
        val nonceSize = 12
        nonce.usePinned { pinnedNonce ->
            SecRandomCopyBytes(kSecRandomDefault, nonceSize.toULong(), pinnedNonce.addressOf(0))
        }

        // ⚠️ 暫時返回未加密數據（僅用於編譯）
        val tag = ByteArray(16) { 0 }

        AesGcmResult(nonce, plaintext, tag)
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
        require(encrypted.nonce.size == 12) { "Nonce must be 12 bytes" }
        require(encrypted.tag.size == 16) { "Tag must be 16 bytes" }

        // ⚠️ 暫時返回未加密數據（僅用於編譯）
        encrypted.ciphertext
    }
}

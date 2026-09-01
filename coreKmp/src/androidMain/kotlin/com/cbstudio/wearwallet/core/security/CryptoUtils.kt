@file:JvmName("CryptoUtilsAndroid")
package com.cbstudio.wearwallet.core.security

import android.util.Base64
import org.kotlincrypto.hash.sha3.Keccak256
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android 平台的加密工具實現
 * 使用 Java 標準加密庫
 */
actual object CryptoUtils {
    
    /**
     * 計算 SHA256 哈希
     */
    actual fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
    
    /**
     * 計算 Keccak256 哈希（用於以太坊地址生成）
     * 使用 KotlinCrypto 庫提供的真實 Keccak256 實現
     *
     * 注意：Keccak256 與 SHA3-256 不同
     * - Keccak256: 以太坊使用的原始 Keccak 算法
     * - SHA3-256: NIST 標準化後的版本（padding 不同）
     */
    actual fun keccak256(data: ByteArray): ByteArray {
        val keccak = Keccak256()
        keccak.update(data)
        return keccak.digest()
    }
    
    /**
     * 生成隨機字節
     */
    actual fun randomBytes(size: Int): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }
    
    /**
     * PBKDF2 密鑰派生
     */
    actual fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val spec = PBEKeySpec(
            String(password, Charsets.UTF_8).toCharArray(),
            salt,
            iterations,
            keyLength * 8 // 位數
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
    
    /**
     * AES-256-GCM 加密
     */
    actual fun aesEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.take(32).toByteArray(), "AES")
        val gcmSpec = GCMParameterSpec(128, iv.take(12).toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(data)
    }

    /**
     * AES-256-GCM 解密
     */
    actual fun aesDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.take(32).toByteArray(), "AES")
        val gcmSpec = GCMParameterSpec(128, iv.take(12).toByteArray())
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(data)
    }

    /**
     * AES-GCM 加密（返回 EncryptedData 包含 nonce 和 authTag）
     * 使用 AES/GCM/NoPadding 模式提供認證加密
     */
    actual fun aesGcmEncrypt(data: ByteArray, key: ByteArray): EncryptedData {
        // 生成 12 字節的隨機 nonce（GCM 標準）
        val nonce = randomBytes(12)

        // 使用 AES-GCM 模式
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.take(32).toByteArray(), "AES")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, nonce) // 128-bit auth tag
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        // 加密數據（GCM 模式會自動在結尾添加 auth tag）
        val encryptedWithTag = cipher.doFinal(data)

        // 分離密文和認證標籤（最後 16 字節是 auth tag）
        val ciphertext = encryptedWithTag.copyOfRange(0, encryptedWithTag.size - 16)
        val authTag = encryptedWithTag.copyOfRange(encryptedWithTag.size - 16, encryptedWithTag.size)

        return EncryptedData(
            ciphertext = ciphertext,
            nonce = nonce,
            authTag = authTag
        )
    }

    /**
     * AES-GCM 解密（從 EncryptedData 解密）
     * 驗證認證標籤並解密數據
     */
    actual fun aesGcmDecrypt(encryptedData: EncryptedData, key: ByteArray): ByteArray {
        // 使用 AES-GCM 模式
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.take(32).toByteArray(), "AES")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, encryptedData.nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        // 合併密文和認證標籤（GCM 需要它們在一起）
        val ciphertextWithTag = encryptedData.ciphertext + encryptedData.authTag

        // 解密並驗證（如果 auth tag 不匹配會拋出異常）
        return cipher.doFinal(ciphertextWithTag)
    }

    /**
     * AES-GCM 加密並附加 AAD 認證數據
     */
    actual fun aesGcmEncryptWithAad(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray
    ): EncryptedData {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.take(32).toByteArray(), "AES")
        val gcmSpec = GCMParameterSpec(128, nonce.take(12).toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        val encryptedWithTag = cipher.doFinal(data)
        val ciphertext = encryptedWithTag.copyOfRange(0, encryptedWithTag.size - 16)
        val authTag = encryptedWithTag.copyOfRange(encryptedWithTag.size - 16, encryptedWithTag.size)
        return EncryptedData(
            ciphertext = ciphertext,
            nonce = nonce.take(12).toByteArray(),
            authTag = authTag
        )
    }

    /**
     * AES-GCM 解密並驗證 AAD 認證數據
     */
    actual fun aesGcmDecryptWithAad(
        encryptedData: EncryptedData,
        key: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.take(32).toByteArray(), "AES")
        val gcmSpec = GCMParameterSpec(128, encryptedData.nonce.take(12).toByteArray())
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        val ciphertextWithTag = encryptedData.ciphertext + encryptedData.authTag
        return cipher.doFinal(ciphertextWithTag)
    }
}

/**
 * 字節數組轉 Base64 字符串
 */
actual fun ByteArray.toBase64(): String {
    if (this.isEmpty()) {
        return ""
    }
    return try {
        java.util.Base64.getEncoder().encodeToString(this)
    } catch (e: Throwable) {
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    }
}

/**
 * Base64 字符串轉字節數組
 */
actual fun String.fromBase64(): ByteArray {
    if (this.isEmpty()) {
        return ByteArray(0)
    }
    return try {
        java.util.Base64.getDecoder().decode(this)
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Throwable) {
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
    }
}
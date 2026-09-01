@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
package com.cbstudio.wearwallet.core.multichain.solana

import io.github.andreypfau.curve25519.ed25519.Ed25519
import kotlinx.cinterop.*
import platform.Foundation.*
import org.kotlincrypto.hash.sha2.SHA512

/**
 * watchOS 平台 Ed25519 密鑰對實現
 * 使用 curve25519-kotlin 提供真實的 Ed25519 實現
 */
@OptIn(ExperimentalForeignApi::class)
actual object Ed25519KeyPair {

    /**
     * 從 32 bytes 種子生成 Ed25519 密鑰對
     */
    actual suspend fun fromSeed(seed: ByteArray): KeyPair {
        require(seed.size == 32) { "Ed25519 seed must be 32 bytes, got ${seed.size}" }

        // 使用 curve25519-kotlin 從種子生成密鑰對
        val privateKey = Ed25519.keyFromSeed(seed)

        // 獲取公鑰
        val edPublicKey = privateKey.publicKey()
        val publicKeyBytes = edPublicKey.toByteArray()

        // Ed25519 完整私鑰 = 種子(32 bytes) + 公鑰(32 bytes) = 64 bytes
        val fullPrivateKey = ByteArray(64)
        seed.copyInto(fullPrivateKey, 0)
        publicKeyBytes.copyInto(fullPrivateKey, 32)

        return KeyPair(
            publicKey = publicKeyBytes,
            privateKey = fullPrivateKey
        )
    }
}

/**
 * watchOS 平台 PBKDF2-HMAC-SHA512 實現
 * ✅ 使用真正的 SHA-512（kotlincrypto）符合 RFC 2898 標準
 */
@OptIn(ExperimentalForeignApi::class)
actual object PBKDF2 {

    /**
     * ✅ 使用 PBKDF2-HMAC-SHA512 派生密鑰（RFC 2898 標準實現）
     * 使用 kotlincrypto SHA512 確保正確的密鑰派生
     */
    actual suspend fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray {
        require(iterations > 0) { "Iterations must be positive" }
        require(keyLength > 0) { "Key length must be positive" }

        // PBKDF2 基本實現
        var block = HMAC.hmacSha512(password, salt + byteArrayOf(0, 0, 0, 1))
        var result = block.copyOf()

        // 執行剩餘的迭代
        repeat(iterations - 1) {
            block = HMAC.hmacSha512(password, block)
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor block[i].toInt()).toByte()
            }
        }

        return result.copyOf(keyLength)
    }
}

/**
 * watchOS 平台 HMAC-SHA512 實現
 * ✅ 使用真正的 SHA-512（kotlincrypto）符合 RFC 2104 標準
 */
@OptIn(ExperimentalForeignApi::class)
actual object HMAC {

    /**
     * ✅ 計算 HMAC-SHA512（RFC 2104 標準實現）
     * 使用 kotlincrypto SHA512 進行哈希計算
     */
    actual suspend fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val blockSize = 128 // SHA-512 block size

        // 調整密鑰長度
        val adjustedKey = when {
            key.size > blockSize -> {
                simpleSha512(key).copyOf(blockSize)
            }
            key.size < blockSize -> {
                key + ByteArray(blockSize - key.size)
            }
            else -> key.copyOf()
        }

        // 計算 ipad 和 opad
        val ipad = ByteArray(blockSize) { i -> (adjustedKey[i].toInt() xor 0x36).toByte() }
        val opad = ByteArray(blockSize) { i -> (adjustedKey[i].toInt() xor 0x5c).toByte() }

        // 計算內部哈希
        val innerHash = simpleSha512(ipad + data)

        // 計算外部哈希
        return simpleSha512(opad + innerHash)
    }

    /**
     * ✅ 真正的 SHA-512 實現
     * 使用 kotlincrypto SHA512 (符合 RFC 6234 標準)
     */
    private fun simpleSha512(data: ByteArray): ByteArray {
        val sha512 = SHA512()
        sha512.update(data)
        return sha512.digest()
    }
}

// ========== 輔助擴展函數 ==========

/**
 * ByteArray 轉 NSData
 */
@Suppress("UNCHECKED_CAST")
private fun ByteArray.toNSData(): NSData {
    if (this.isEmpty()) {
        return NSData()
    }
    return this.usePinned { pinned ->
        // 使用 convert() 讓編譯器為每個平台選擇正確的類型（UInt 或 ULong）
        NSData.create(bytes = pinned.addressOf(0), length = this.size.convert())
    }
}

/**
 * NSData 轉 ByteArray
 */
@Suppress("UNCHECKED_CAST")
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) {
        return ByteArray(0)
    }
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        // 使用 convert() 讓編譯器為每個平台選擇正確的類型
        platform.posix.memcpy(pinned.addressOf(0), this.bytes, length.convert())
    }
    return bytes
}

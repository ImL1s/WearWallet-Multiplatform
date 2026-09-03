package com.cbstudio.wearwallet.core.multichain.solana

import io.github.andreypfau.curve25519.ed25519.Ed25519
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import org.kotlincrypto.hash.sha2.SHA512

/**
 * iOS 平台 Ed25519 密鑰對實現
 * 使用 curve25519-kotlin 提供真實的 Ed25519 實現
 */
@OptIn(ExperimentalForeignApi::class)
actual object Ed25519KeyPair {

    /**
     * 從 32 bytes 種子生成 Ed25519 密鑰對
     */
    actual suspend fun fromSeed(seed: ByteArray): KeyPair {
        require(seed.size == 32) { "Ed25519 seed must be 32 bytes, got ${seed.size}" }

        println("[Ed25519KeyPair] 🔍 fromSeed called")
        println("[Ed25519KeyPair]    種子長度: ${seed.size}")
        println("[Ed25519KeyPair]    種子前8字節: ${seed.take(8).joinToString("") { it.toString(16).padStart(2, '0') }}")

        // 使用 curve25519-kotlin 從種子生成密鑰對
        val privateKey = Ed25519.keyFromSeed(seed)
        println("[Ed25519KeyPair]    ✅ Ed25519.keyFromSeed 成功")

        // 獲取公鑰
        // Ed25519PrivateKey.publicKey() 方法返回 Ed25519PublicKey
        val edPublicKey = privateKey.publicKey()
        println("[Ed25519KeyPair]    ✅ privateKey.publicKey() 成功")

        val publicKeyBytes = edPublicKey.toByteArray()
        println("[Ed25519KeyPair]    公鑰長度: ${publicKeyBytes.size}")
        println("[Ed25519KeyPair]    公鑰前8字節: ${publicKeyBytes.take(8).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")

        // 檢查是否為全零公鑰
        if (publicKeyBytes.all { it == 0.toByte() }) {
            println("[Ed25519KeyPair]    ❌ 警告：公鑰為全零！")
        }

        // 🔧 修復：創建公鑰的獨立副本
        val publicKeyCopy = publicKeyBytes.copyOf()
        println("[Ed25519KeyPair]    公鑰副本前8字節: ${publicKeyCopy.take(8).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")

        // Ed25519 完整私鑰 = 種子(32 bytes) + 公鑰(32 bytes) = 64 bytes
        val fullPrivateKey = ByteArray(64)
        seed.copyInto(fullPrivateKey, 0)
        publicKeyCopy.copyInto(fullPrivateKey, 32)

        println("[Ed25519KeyPair]    完整私鑰長度: ${fullPrivateKey.size}")
        println("[Ed25519KeyPair]    完整私鑰中的公鑰部分: ${fullPrivateKey.sliceArray(32..39).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")

        val result = KeyPair(
            publicKey = publicKeyCopy,
            privateKey = fullPrivateKey
        )

        println("[Ed25519KeyPair]    返回前檢查 - result.publicKey: ${result.publicKey.take(8).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")

        return result
    }
}

/**
 * iOS 平台 PBKDF2-HMAC-SHA512 實現
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
        // 使用 HMAC-SHA512 進行迭代派生
        var block = HMAC.hmacSha512(password, salt + byteArrayOf(0, 0, 0, 1))
        var result = block.copyOf()

        // 執行剩餘的迭代
        repeat(iterations - 1) {
            block = HMAC.hmacSha512(password, block)
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor block[i].toInt()).toByte()
            }
        }

        // 返回指定長度的密鑰
        return result.copyOf(keyLength)
    }
}

/**
 * iOS 平台 HMAC-SHA512 實現
 * ✅ 使用真正的 SHA-512（kotlincrypto）符合 RFC 2104 標準
 */
@OptIn(ExperimentalForeignApi::class)
actual object HMAC {

    /**
     * ✅ 計算 HMAC-SHA512（RFC 2104 標準實現）
     * 使用 kotlincrypto SHA512 進行哈希計算
     */
    actual suspend fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        // 簡化的 HMAC-SHA512 實現
        // 使用基本的異或操作

        val blockSize = 128 // SHA-512 block size
        val outputSize = 64 // SHA-512 output size

        // 調整密鑰長度
        val adjustedKey = when {
            key.size > blockSize -> {
                // 如果密鑰太長，先哈希
                simpleSha512(key).copyOf(blockSize)
            }
            key.size < blockSize -> {
                // 如果密鑰太短，填充零
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
     * 使用 kotlincrypto SHA512（符合 RFC 6234 標準）
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
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (this.isEmpty()) {
        return NSData()
    }
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}

/**
 * NSData 轉 ByteArray
 */
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
private fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) {
        return ByteArray(0)
    }
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), this.bytes, length.toULong())
    }
    return bytes
}

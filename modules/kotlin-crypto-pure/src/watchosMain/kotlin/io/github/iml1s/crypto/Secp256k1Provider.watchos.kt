package io.github.iml1s.crypto

/**
 * watchOS 平台的 Secp256k1Provider 實現
 *
 * 由於 secp256k1-kmp 不支援 watchOS，使用純 Kotlin 實現 (Secp256k1Pure)
 */
actual object Secp256k1Provider {

    /**
     * ECDSA 簽名 (使用純 Kotlin 實現)
     */
    actual fun sign(privateKey: ByteArray, messageHash: ByteArray): ByteArray {
        return Secp256k1Pure.sign(messageHash, privateKey)
    }

    /**
     * ECDSA 簽名驗證 (使用純 Kotlin 實現)
     */
    actual fun verify(signature: ByteArray, messageHash: ByteArray, publicKey: ByteArray): Boolean {
        return Secp256k1Pure.verify(messageHash, signature, publicKey)
    }

    /**
     * 從私鑰派生公鑰 (使用純 Kotlin 實現)
     */
    actual fun computePublicKey(privateKey: ByteArray, compressed: Boolean): ByteArray {
        val fullPubKey = Secp256k1Pure.generatePublicKey(privateKey)
        return if (compressed) {
            val point = Secp256k1Pure.decodePublicKey(fullPubKey)
            Secp256k1Pure.encodePublicKey(point, compressed = true)
        } else {
            fullPubKey
        }
    }

    /**
     * 驗證私鑰是否有效 (使用純 Kotlin 實現)
     * 簡化版：檢查長度和非零
     */
    actual fun isValidPrivateKey(privateKey: ByteArray): Boolean {
        return try {
            if (privateKey.size != 32) return false
            // 檢查是否全為零
            if (privateKey.all { it == 0.toByte() }) return false
            // 嘗試生成公鑰，如果成功則私鑰有效
            Secp256k1Pure.generatePublicKey(privateKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * ECDH 密鑰交換 (使用純 Kotlin 實現)
     */
    actual fun ecdh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        return Secp256k1Pure.ecdh(privateKey, publicKey)
    }
}

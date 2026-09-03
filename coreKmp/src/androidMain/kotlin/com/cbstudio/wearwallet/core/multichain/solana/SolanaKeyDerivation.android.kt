package com.cbstudio.wearwallet.core.multichain.solana

import wallet.core.jni.PrivateKey
import wallet.core.jni.Curve
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android 平台 Ed25519 密鑰對實現
 * 使用 TrustWallet Core 提供的 Ed25519 實現
 */
actual object Ed25519KeyPair {

    init {
        // 確保 TrustWallet Core 已加載
        try {
            System.loadLibrary("TrustWalletCore")
        } catch (e: UnsatisfiedLinkError) {
            // 可能已經加載過了
        }
    }

    /**
     * 從 32 bytes 種子生成 Ed25519 密鑰對
     */
    actual suspend fun fromSeed(seed: ByteArray): KeyPair {
        require(seed.size == 32) { "Ed25519 seed must be 32 bytes, got ${seed.size}" }

        // 使用 TrustWallet Core 從種子生成密鑰對
        // Ed25519 私鑰在 TrustWallet Core 中就是種子本身
        val privateKey = PrivateKey(seed)

        // 獲取公鑰（Solana 使用 ED25519）
        val publicKeyBytes = privateKey.getPublicKeyEd25519().data()

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
 * Android 平台 PBKDF2-HMAC-SHA512 實現
 * 使用 Java Cryptography Extension (JCE)
 */
actual object PBKDF2 {

    /**
     * 使用 PBKDF2-HMAC-SHA512 派生密鑰
     */
    actual suspend fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray {
        require(iterations > 0) { "Iterations must be positive" }
        require(keyLength > 0) { "Key length must be positive" }

        // 將 ByteArray password 轉換為 char array
        // 注意：BIP39 規範使用 UTF-8 編碼的助記詞作為 password
        val passwordChars = password.decodeToString().toCharArray()

        val spec = PBEKeySpec(
            passwordChars,
            salt,
            iterations,
            keyLength * 8 // bits
        )

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val key = factory.generateSecret(spec).encoded

        // 清理敏感數據
        passwordChars.fill('0')
        spec.clearPassword()

        return key
    }
}

/**
 * Android 平台 HMAC-SHA512 實現
 * 使用 Java Cryptography Extension (JCE)
 */
actual object HMAC {

    private const val HMAC_SHA512_ALGORITHM = "HmacSHA512"

    /**
     * 計算 HMAC-SHA512
     */
    actual suspend fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA512_ALGORITHM)
        val secretKey = SecretKeySpec(key, HMAC_SHA512_ALGORITHM)
        mac.init(secretKey)
        return mac.doFinal(data)
    }
}

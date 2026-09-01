package io.github.iml1s.crypto

import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKeyType
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import java.security.MessageDigest

/**
 * Android 平台的 BIP32 輔助函數實現
 * 使用 TrustWallet Core 和 BouncyCastle
 */

/**
 * 使用 TrustWallet Core 計算 secp256k1 公鑰
 */
public actual fun platformGetPublicKey(privateKey: ByteArray): ByteArray {
    return try {
        val key = PrivateKey(privateKey)
        val publicKey = key.getPublicKeySecp256k1(true) // compressed = true
        publicKey.data()
    } catch (e: Throwable) {
        Secp256k1Pure.pubKeyOf(privateKey, compressed = true)
    }
}

/**
 * 計算 SHA256 哈希
 */
public actual fun platformSha256(data: ByteArray): ByteArray {

    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(data)
}

/**
 * 計算 RIPEMD160 哈希
 */
public actual fun platformRipemd160(data: ByteArray): ByteArray {

    val digest = RIPEMD160Digest()
    digest.update(data, 0, data.size)
    val result = ByteArray(digest.digestSize)
    digest.doFinal(result, 0)
    return result
}

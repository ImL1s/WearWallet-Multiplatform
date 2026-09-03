package com.cbstudio.wearwallet.core.security

import org.kotlincrypto.hash.sha2.SHA256

/**
 * watchOS 平台的 SHA256 實現
 *
 * 使用 kotlincrypto 純 Kotlin 實現，與 CryptoSignature 保持一致
 */
internal actual fun platformSha256(data: ByteArray): ByteArray {
    val sha256 = SHA256()
    sha256.update(data)
    return sha256.digest()
}

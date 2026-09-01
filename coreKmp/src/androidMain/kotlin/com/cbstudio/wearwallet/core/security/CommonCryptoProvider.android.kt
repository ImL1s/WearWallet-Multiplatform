package com.cbstudio.wearwallet.core.security

import java.security.MessageDigest

/**
 * Android 平台的 SHA256 實現
 */
internal actual fun platformSha256(data: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(data)
}

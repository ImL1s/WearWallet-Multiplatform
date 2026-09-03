package io.github.iml1s.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Android 平台的 HMAC-SHA512 實現
 * 使用 Java Cryptography Architecture (JCA)
 */
internal actual fun platformHmacSha512(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA512")
    val keySpec = SecretKeySpec(key, "HmacSHA512")
    mac.init(keySpec)
    return mac.doFinal(data)
}

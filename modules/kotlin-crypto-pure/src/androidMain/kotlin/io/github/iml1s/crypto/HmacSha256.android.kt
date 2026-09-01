package io.github.iml1s.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec



internal actual fun platformHmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val hmacParams = SecretKeySpec(key, "HmacSHA256")
    val m = Mac.getInstance("HmacSHA256")
    m.init(hmacParams)
    return m.doFinal(data)
}

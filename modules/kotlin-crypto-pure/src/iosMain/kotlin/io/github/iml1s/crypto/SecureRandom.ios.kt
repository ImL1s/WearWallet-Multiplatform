package io.github.iml1s.crypto

import kotlinx.cinterop.*
import platform.Security.*

@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    if (size == 0) return bytes
    bytes.usePinned { pinned ->
        val result = SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        if (result != 0) {
            error("SecRandomCopyBytes failed with code $result")
        }
    }
    return bytes
}

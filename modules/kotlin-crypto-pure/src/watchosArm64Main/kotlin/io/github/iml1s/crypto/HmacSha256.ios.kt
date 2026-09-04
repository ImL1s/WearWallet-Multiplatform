package io.github.iml1s.crypto

import kotlinx.cinterop.*
import platform.CoreCrypto.*

/**
 * iOS 平台的 HMAC-SHA256 實現
 * 使用 CommonCrypto CCHmac
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformHmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val result = ByteArray(CC_SHA256_DIGEST_LENGTH)

    key.usePinned { keyPinned ->
        data.usePinned { dataPinned ->
            result.usePinned { resultPinned ->
                CCHmac(
                    kCCHmacAlgSHA256.toUInt(),
                    keyPinned.addressOf(0),
                    key.size.toUInt(),
                    dataPinned.addressOf(0),
                    data.size.toUInt(),
                    resultPinned.addressOf(0)
                )
            }
        }
    }

    return result
}

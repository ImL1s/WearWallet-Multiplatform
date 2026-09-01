package io.github.iml1s.crypto

import kotlinx.cinterop.*
import platform.CoreCrypto.*

/**
 * iOS 平台的 HMAC-SHA512 實現
 * 使用 CommonCrypto CCHmac
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun platformHmacSha512(key: ByteArray, data: ByteArray): ByteArray {
    val result = ByteArray(CC_SHA512_DIGEST_LENGTH)

    key.usePinned { keyPinned ->
        data.usePinned { dataPinned ->
            result.usePinned { resultPinned ->
                CCHmac(
                    kCCHmacAlgSHA512.toUInt(),
                    keyPinned.addressOf(0),
                    key.size.toUInt(),  // 使用 convert() 自動處理平台差異
                    dataPinned.addressOf(0),
                    data.size.toUInt(),  // 使用 convert() 自動處理平台差異
                    resultPinned.addressOf(0)
                )
            }
        }
    }

    return result
}

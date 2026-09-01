package com.cbstudio.wearwallet.core.security

import kotlinx.cinterop.*
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

/**
 * iOS 平台的 SHA256 實現
 */
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual fun platformSha256(data: ByteArray): ByteArray {
    val digestLength = CC_SHA256_DIGEST_LENGTH.toInt()
    val digest = ByteArray(digestLength)

    data.usePinned { pinnedData ->
        digest.usePinned { pinnedDigest ->
            CC_SHA256(
                pinnedData.addressOf(0),
                data.size.toUInt(),
                pinnedDigest.addressOf(0)?.reinterpret()
            )
        }
    }

    return digest
}

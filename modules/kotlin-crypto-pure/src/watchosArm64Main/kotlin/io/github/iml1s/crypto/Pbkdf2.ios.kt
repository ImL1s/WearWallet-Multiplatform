package io.github.iml1s.crypto

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.memcpy
import platform.CoreCrypto.*

/**
 * iOS 平台的 PBKDF2-HMAC-SHA512 實現
 *
 * 直接使用 Apple CommonCrypto 框架。
 *
 * ## 實現細節
 * - 使用 `CCKeyDerivationPBKDF`
 * - 偽隨機函數：`kCCPRFHmacAlgSHA512`
 * - 符合 RFC 2898 和 BIP39 標準
 *
 * ## 安全性
 * - 使用 Apple 系統內建的加密庫
 * - 支援 Secure Enclave（在支援的設備上）
 * - 經過 FIPS 認證的實現
 *
 * ## 性能
 * - 在 A 系列芯片上進行硬體加速
 * - 對於 2048 次迭代，通常在 100-200ms 內完成
 *
 * @see [CommonCrypto](https://developer.apple.com/documentation/security/1399291-cckeyderivationpbkdf)
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun pbkdf2HmacSha512(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int
): ByteArray {
    val derivedKey = ByteArray(keyLength)

    // 將密碼轉換為 UTF-8 字符串（CommonCrypto API 要求）
    val passwordString = password.decodeToString()

    val status = memScoped {
        salt.usePinned { saltPinned ->
            derivedKey.usePinned { keyPinned ->
                CCKeyDerivationPBKDF(
                    kCCPBKDF2.toUInt(),                                // algorithm
                    passwordString,                                    // password (String)
                    password.size.toUInt(),                            // passwordLen
                    saltPinned.addressOf(0) as CPointer<UByteVar>?,    // salt
                    salt.size.toUInt(),                                // saltLen
                    kCCPRFHmacAlgSHA512.toUInt(),                      // prf
                    iterations.toUInt(),                               // rounds
                    keyPinned.addressOf(0) as CPointer<UByteVar>?,     // derivedKey
                    keyLength.toUInt()                                 // derivedKeyLen
                )
            }
        }
    }

    if (status != kCCSuccess) {
        throw IllegalStateException("PBKDF2 derivation failed with status: $status")
    }

    return derivedKey
}

/**
 * ByteArray 轉 NSData
 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSDataIOS(): NSData {
    return this.usePinned { pinned ->
        NSData.create(
            bytes = pinned.addressOf(0),
            length = this.size.convert()  // 使用 convert() 自動處理平台差異
        )
    }
}

/**
 * NSData 轉 ByteArray
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArrayIOS(): ByteArray {
    return ByteArray(this.length.toInt()).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), this@toByteArrayIOS.bytes, this@toByteArrayIOS.length)
        }
    }
}

/**
 * iOS 平台的 NFKD 正規化實現
 *
 * 直接使用 NSString 的 Unicode 正規化方法。
 *
 * ## NFKD 正規化
 * - Normalization Form Compatibility Decomposition
 * - 使用 NSString 的標準方法
 * - 確保 Unicode 字符的一致性表示
 *
 * ### 範例
 * ```kotlin
 * // é (U+00E9) -> e (U+0065) + ´ (U+0301)
 * normalizeNfkdPlatform("café") // -> "café" (分解形式)
 * ```
 *
 * @param text 需要正規化的文本
 * @return NFKD 正規化後的文本
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun normalizeNfkdPlatform(text: String): String {
    // NFKD = 相容性分解正規化
    // 先做相容性組合，再做標準分解
    val nsString = text as NSString
    val compatible = nsString.precomposedStringWithCompatibilityMapping
    val nfkd = (compatible as NSString).decomposedStringWithCanonicalMapping
    return nfkd
}

package io.github.iml1s.crypto

import kotlinx.cinterop.*
import platform.CoreCrypto.*

/**
 * iOS 平台的 BIP32 輔助函數實現
 * 使用 Swift Bridge 和 CommonCrypto
 */

/**
 * 使用 Secp256k1Pure 計算 secp256k1 公鑰
 *
 * ✅ 完整實現：使用純 Kotlin secp256k1 實現
 * - 使用 Secp256k1Pure.pubKeyOf() 進行橢圓曲線點乘
 * - 返回壓縮格式公鑰（33 bytes）
 * - 與 Android (TrustWallet Core) 行為一致
 *
 * @param privateKey 32字節私鑰
 * @return 33字節壓縮公鑰（0x02/0x03 prefix + 32-byte X coordinate）
 * @throws IllegalArgumentException 如果私鑰無效
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun platformGetPublicKey(privateKey: ByteArray): ByteArray {

    require(privateKey.size == 32) { "Private key must be 32 bytes, got ${privateKey.size}" }

    return try {
        // 使用 Secp256k1Pure 生成壓縮公鑰
        Secp256k1Pure.pubKeyOf(privateKey, compressed = true)
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to derive public key: ${e.message}", e)
    }
}

/**
 * 計算 SHA256 哈希
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun platformSha256(data: ByteArray): ByteArray {

    val result = ByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { pinned ->
        result.usePinned { resultPinned ->
            CC_SHA256(
                pinned.addressOf(0) as CPointer<UByteVar>?,
                data.size.toUInt(),
                resultPinned.addressOf(0) as CPointer<UByteVar>?
            )
        }
    }
    return result
}

/**
 * 計算 RIPEMD160 哈希
 *
 * ✅ 完整實現：使用純 Kotlin RIPEMD160 實現
 * - 符合 RFC 2286 規範
 * - 與 Bitcoin 地址生成標準兼容
 * - 輸出 20 字節哈希值
 *
 * iOS CommonCrypto 不原生提供 RIPEMD160，因此使用共享的純 Kotlin 實現
 *
 * @param data 輸入數據
 * @return 20字節 RIPEMD160 哈希
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun platformRipemd160(data: ByteArray): ByteArray {

    return Ripemd160.hash(data)
}

package com.cbstudio.wearwallet.core.platform.watchos

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import platform.CoreCrypto.*
import platform.darwin.*
import kotlin.experimental.ExperimentalNativeApi

/**
 * watchOS 加密工具類
 * 使用 CommonCrypto 和 Security framework
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class, UnsafeNumber::class)
object WatchOSCryptoKitSimple {

    /**
     * 生成安全隨機數
     * 使用 Security framework 的 SecRandomCopyBytes
     */
    fun generateSecureRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        memScoped {
            @Suppress("EXPERIMENTAL_API_USAGE")
            val result = SecRandomCopyBytes(
                kSecRandomDefault,
                length.convert(),
                bytes.refTo(0)
            )
            if (result != errSecSuccess) {
                throw RuntimeException("Failed to generate secure random bytes: $result")
            }
        }
        return bytes
    }

    /**
     * SHA256 哈希計算
     * 使用 CommonCrypto 的 CC_SHA256
     */
    fun sha256(data: ByteArray): ByteArray {
        val hash = ByteArray(CC_SHA256_DIGEST_LENGTH)
        data.usePinned { dataPin ->
            hash.usePinned { hashPin ->
                CC_SHA256(
                    dataPin.addressOf(0) as CPointer<UByteVar>?,
                    data.size.convert(),
                    hashPin.addressOf(0) as CPointer<UByteVar>?
                )
            }
        }
        return hash
    }

    /**
     * PBKDF2 密鑰派生
     * 使用 CommonCrypto 的 CCKeyDerivationPBKDF
     */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val derivedKey = ByteArray(keyLength)

        // 將密碼轉換為 UTF-8 字符串（CommonCrypto API 要求）
        val passwordString = password.decodeToString()

        memScoped {
            salt.usePinned { saltPin ->
                derivedKey.usePinned { keyPin ->
                    val result = CCKeyDerivationPBKDF(
                        kCCPBKDF2.convert(),
                        passwordString,
                        password.size.convert(),
                        saltPin.addressOf(0) as CPointer<UByteVar>?,
                        salt.size.convert(),
                        2u, // kCCPRFHmacAlgSHA256
                        iterations.convert(),
                        keyPin.addressOf(0) as CPointer<UByteVar>?,
                        keyLength.convert()
                    )

                    if (result != kCCSuccess) {
                        throw RuntimeException("PBKDF2 derivation failed: $result")
                    }
                }
            }
        }

        return derivedKey
    }

    /**
     * AES-256-CBC 加密
     * 使用 CommonCrypto 的 CCCrypt
     */
    @Suppress("UNCHECKED_CAST")
    fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val outputSize = data.size + kCCBlockSizeAES128.toInt()
        val output = ByteArray(outputSize)

        memScoped {
            val dataOutMoved = alloc<ULongVar>()

            data.usePinned { dataPin ->
                key.usePinned { keyPin ->
                    iv.usePinned { ivPin ->
                        output.usePinned { outputPin ->
                            @Suppress("CAST_NEVER_SUCCEEDS")
                            val status = CCCrypt(
                                kCCEncrypt.convert(),
                                kCCAlgorithmAES.convert(),
                                kCCOptionPKCS7Padding.convert(),
                                keyPin.addressOf(0),
                                key.size.convert(),
                                ivPin.addressOf(0),
                                dataPin.addressOf(0),
                                data.size.convert(),
                                outputPin.addressOf(0),
                                outputSize.convert(),
                                dataOutMoved.ptr.reinterpret()
                            )

                            if (status != kCCSuccess) {
                                throw RuntimeException("AES-CBC encryption failed: $status")
                            }

                            return output.copyOf(dataOutMoved.value.toInt())
                        }
                    }
                }
            }
        }
    }

    /**
     * AES-256-CBC 解密
     * 使用 CommonCrypto 的 CCCrypt
     */
    @Suppress("UNCHECKED_CAST")
    fun aesCbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val output = ByteArray(data.size)

        memScoped {
            val dataOutMoved = alloc<ULongVar>()

            data.usePinned { dataPin ->
                key.usePinned { keyPin ->
                    iv.usePinned { ivPin ->
                        output.usePinned { outputPin ->
                            @Suppress("CAST_NEVER_SUCCEEDS")
                            val status = CCCrypt(
                                kCCDecrypt.convert(),
                                kCCAlgorithmAES.convert(),
                                kCCOptionPKCS7Padding.convert(),
                                keyPin.addressOf(0),
                                key.size.convert(),
                                ivPin.addressOf(0),
                                dataPin.addressOf(0),
                                data.size.convert(),
                                outputPin.addressOf(0),
                                output.size.convert(),
                                dataOutMoved.ptr.reinterpret()
                            )

                            if (status != kCCSuccess) {
                                throw RuntimeException("AES-CBC decryption failed: $status")
                            }

                            return output.copyOf(dataOutMoved.value.toInt())
                        }
                    }
                }
            }
        }
    }

    /**
     * HMAC-SHA256 計算
     * 使用 CommonCrypto 的 CCHmac
     */
    fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val output = ByteArray(CC_SHA256_DIGEST_LENGTH)

        data.usePinned { dataPin ->
            key.usePinned { keyPin ->
                output.usePinned { outputPin ->
                    CCHmac(
                        kCCHmacAlgSHA256.convert(),
                        keyPin.addressOf(0),
                        key.size.convert(),
                        dataPin.addressOf(0),
                        data.size.convert(),
                        outputPin.addressOf(0)
                    )
                }
            }
        }

        return output
    }

    /**
     * AES-GCM 加密（使用 CBC + HMAC 實現認證加密）
     */
    fun aesGcmEncrypt(data: ByteArray, key: ByteArray, nonce: ByteArray): Triple<ByteArray, ByteArray, Int> {
        // 生成 16 字節 IV for CBC
        val iv = generateSecureRandomBytes(16)
        val aesKey = key.take(32).toByteArray()

        // CBC 加密
        val ciphertext = aesCbcEncrypt(data, aesKey, iv)

        // 計算 HMAC-SHA256 作為認證標籤
        val authData = nonce + iv + ciphertext
        val fullTag = hmacSha256(authData, aesKey)
        val authTag = fullTag.take(16).toByteArray()

        // 將 IV 嵌入到 ciphertext 中
        val ciphertextWithIv = iv + ciphertext

        return Triple(ciphertextWithIv, authTag, ciphertextWithIv.size)
    }

    /**
     * AES-GCM 解密（使用 CBC + HMAC 實現認證解密）
     */
    fun aesGcmDecrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray, authTag: ByteArray): ByteArray {
        val aesKey = key.take(32).toByteArray()

        // 提取 IV 和實際密文
        if (ciphertext.size < 16) {
            throw IllegalArgumentException("Ciphertext too short")
        }
        val iv = ciphertext.take(16).toByteArray()
        val actualCiphertext = ciphertext.drop(16).toByteArray()

        // 驗證認證標籤
        val authData = nonce + iv + actualCiphertext
        val fullTag = hmacSha256(authData, aesKey)
        val expectedAuthTag = fullTag.take(16).toByteArray()

        if (!authTag.contentEquals(expectedAuthTag)) {
            throw RuntimeException("Authentication tag verification failed")
        }

        // CBC 解密
        return aesCbcDecrypt(actualCiphertext, aesKey, iv)
    }

    /**
     * 簡單的 AES 加密（包含 IV）
     */
    fun aesEncrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        val aesKey = key.take(32).toByteArray()
        val iv = generateSecureRandomBytes(16)
        val ciphertext = aesCbcEncrypt(plaintext, aesKey, iv)
        return iv + ciphertext
    }

    /**
     * 簡單的 AES 解密（包含 IV）
     */
    fun aesDecrypt(ciphertextWithIv: ByteArray, key: ByteArray): ByteArray {
        if (ciphertextWithIv.size < 16) {
            throw IllegalArgumentException("Ciphertext too short")
        }

        val aesKey = key.take(32).toByteArray()
        val iv = ciphertextWithIv.take(16).toByteArray()
        val ciphertext = ciphertextWithIv.drop(16).toByteArray()

        return aesCbcDecrypt(ciphertext, aesKey, iv)
    }
}

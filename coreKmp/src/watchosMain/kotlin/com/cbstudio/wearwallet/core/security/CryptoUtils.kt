@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
package com.cbstudio.wearwallet.core.security

import kotlinx.cinterop.*
import org.kotlincrypto.hash.sha3.Keccak256
import platform.CoreCrypto.*
import platform.Foundation.*
import platform.Security.*
import platform.darwin.*

/**
 * watchOS 平台的加密工具實現 (Fail-Closed & NIST SP 800-38D Compliant)
 * 使用 Security framework、CommonCrypto 與原生 CCCryptorGCM (CryptoKit 核心引擎)
 */
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
actual object CryptoUtils {

    actual fun sha256(data: ByteArray): ByteArray {
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

    actual fun keccak256(data: ByteArray): ByteArray {
        val keccak = Keccak256()
        keccak.update(data)
        return keccak.digest()
    }

    actual fun randomBytes(size: Int): ByteArray {
        require(size >= 0) { "Size must be non-negative" }
        if (size == 0) return ByteArray(0)
        val bytes = ByteArray(size)
        memScoped {
            val result = SecRandomCopyBytes(
                kSecRandomDefault,
                size.convert(),
                bytes.refTo(0)
            )
            if (result != errSecSuccess) {
                throw SecurityException("Failed to generate secure random bytes: $result")
            }
        }
        return bytes
    }

    actual fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        require(iterations > 0) { "Iterations must be positive" }
        require(keyLength > 0) { "KeyLength must be positive" }
        val derivedKey = ByteArray(keyLength)
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
                        throw SecurityException("PBKDF2 key derivation failed with status: $result")
                    }
                }
            }
        }
        return derivedKey
    }

    actual fun aesEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val encrypted = aesGcmEncryptWithAad(data = data, key = key, nonce = iv.take(12).toByteArray(), aad = byteArrayOf())
        return encrypted.ciphertext + encrypted.authTag
    }

    actual fun aesDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(data.size >= 16) { "Ciphertext must contain at least 16 bytes for auth tag" }
        val ct = data.copyOfRange(0, data.size - 16)
        val tag = data.copyOfRange(data.size - 16, data.size)
        val encData = EncryptedData(ciphertext = ct, nonce = iv.take(12).toByteArray(), authTag = tag)
        return aesGcmDecryptWithAad(encryptedData = encData, key = key, aad = byteArrayOf())
    }

    actual fun aesGcmEncrypt(data: ByteArray, key: ByteArray): EncryptedData {
        return aesGcmEncryptWithAad(data = data, key = key, nonce = randomBytes(12), aad = byteArrayOf())
    }

    actual fun aesGcmDecrypt(encryptedData: EncryptedData, key: ByteArray): ByteArray {
        return aesGcmDecryptWithAad(encryptedData = encryptedData, key = key, aad = byteArrayOf())
    }

    /**
     * 真正的 watchOS 原生 AES-256-GCM 加密 (支援 AAD)
     */
    actual fun aesGcmEncryptWithAad(
        data: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray
    ): EncryptedData {
        val aesKey = key.take(32).toByteArray()
        require(aesKey.size == 32) { "AES-256 key must be 32 bytes, got ${aesKey.size}" }
        val gcmNonce = nonce.take(12).toByteArray()
        require(gcmNonce.size == 12) { "AES-GCM nonce must be 12 bytes, got ${gcmNonce.size}" }

        val ciphertext = ByteArray(data.size)
        val tag = ByteArray(16)

        val fnPtr = platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "CCCryptorGCM")
            ?: throw UnsupportedPlatformKeyException("CCCryptorGCM is not supported on this Darwin runtime")

        val cccryptorGcm = fnPtr.reinterpret<CFunction<(
            UInt, UInt, COpaquePointer?, ULong, COpaquePointer?, ULong,
            COpaquePointer?, ULong, COpaquePointer?, ULong, COpaquePointer?,
            COpaquePointer?, CPointer<ULongVar>?
        ) -> Int>>()

        memScoped {
            val tagLenVar = alloc<ULongVar>()
            tagLenVar.value = 16uL

            aesKey.usePinned { keyPin ->
                gcmNonce.usePinned { noncePin ->
                    ciphertext.usePinned { ctPin ->
                        tag.usePinned { tagPin ->
                            val status = if (data.isEmpty() && aad.isEmpty()) {
                                cccryptorGcm(
                                    0u, 0u,
                                    keyPin.addressOf(0), 32uL,
                                    noncePin.addressOf(0), 12uL,
                                    null, 0uL,
                                    null, 0uL,
                                    ctPin.addressOf(0),
                                    tagPin.addressOf(0),
                                    tagLenVar.ptr
                                )
                            } else if (data.isEmpty()) {
                                aad.usePinned { aadPin ->
                                    cccryptorGcm(
                                        0u, 0u,
                                        keyPin.addressOf(0), 32uL,
                                        noncePin.addressOf(0), 12uL,
                                        aadPin.addressOf(0), aad.size.toULong(),
                                        null, 0uL,
                                        ctPin.addressOf(0),
                                        tagPin.addressOf(0),
                                        tagLenVar.ptr
                                    )
                                }
                            } else if (aad.isEmpty()) {
                                data.usePinned { dataPin ->
                                    cccryptorGcm(
                                        0u, 0u,
                                        keyPin.addressOf(0), 32uL,
                                        noncePin.addressOf(0), 12uL,
                                        null, 0uL,
                                        dataPin.addressOf(0), data.size.toULong(),
                                        ctPin.addressOf(0),
                                        tagPin.addressOf(0),
                                        tagLenVar.ptr
                                    )
                                }
                            } else {
                                data.usePinned { dataPin ->
                                    aad.usePinned { aadPin ->
                                        cccryptorGcm(
                                            0u, 0u,
                                            keyPin.addressOf(0), 32uL,
                                            noncePin.addressOf(0), 12uL,
                                            aadPin.addressOf(0), aad.size.toULong(),
                                            dataPin.addressOf(0), data.size.toULong(),
                                            ctPin.addressOf(0),
                                            tagPin.addressOf(0),
                                            tagLenVar.ptr
                                        )
                                    }
                                }
                            }

                            if (status != 0) {
                                throw SecurityException("CCCryptorGCM encryption failed with status $status")
                            }
                        }
                    }
                }
            }
        }

        return EncryptedData(
            ciphertext = ciphertext,
            nonce = gcmNonce,
            authTag = tag
        )
    }

    /**
     * 真正的 watchOS 原生 AES-256-GCM 解密與 AAD 認證驗證
     */
    actual fun aesGcmDecryptWithAad(
        encryptedData: EncryptedData,
        key: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val aesKey = key.take(32).toByteArray()
        require(aesKey.size == 32) { "AES-256 key must be 32 bytes, got ${aesKey.size}" }
        val gcmNonce = encryptedData.nonce.take(12).toByteArray()
        require(gcmNonce.size == 12) { "AES-GCM nonce must be 12 bytes, got ${gcmNonce.size}" }
        val authTag = encryptedData.authTag.take(16).toByteArray()
        require(authTag.size == 16) { "AES-GCM authTag must be 16 bytes, got ${authTag.size}" }

        val plaintext = ByteArray(encryptedData.ciphertext.size)

        val fnPtr = platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "CCCryptorGCM")
            ?: throw UnsupportedPlatformKeyException("CCCryptorGCM is not supported on this Darwin runtime")

        val cccryptorGcm = fnPtr.reinterpret<CFunction<(
            UInt, UInt, COpaquePointer?, ULong, COpaquePointer?, ULong,
            COpaquePointer?, ULong, COpaquePointer?, ULong, COpaquePointer?,
            COpaquePointer?, CPointer<ULongVar>?
        ) -> Int>>()

        memScoped {
            val tagLenVar = alloc<ULongVar>()
            tagLenVar.value = 16uL

            aesKey.usePinned { keyPin ->
                gcmNonce.usePinned { noncePin ->
                    plaintext.usePinned { ptPin ->
                        authTag.usePinned { tagPin ->
                            val status = if (encryptedData.ciphertext.isEmpty() && aad.isEmpty()) {
                                cccryptorGcm(
                                    1u, 0u,
                                    keyPin.addressOf(0), 32uL,
                                    noncePin.addressOf(0), 12uL,
                                    null, 0uL,
                                    null, 0uL,
                                    ptPin.addressOf(0),
                                    tagPin.addressOf(0),
                                    tagLenVar.ptr
                                )
                            } else if (encryptedData.ciphertext.isEmpty()) {
                                aad.usePinned { aadPin ->
                                    cccryptorGcm(
                                        1u, 0u,
                                        keyPin.addressOf(0), 32uL,
                                        noncePin.addressOf(0), 12uL,
                                        aadPin.addressOf(0), aad.size.toULong(),
                                        null, 0uL,
                                        ptPin.addressOf(0),
                                        tagPin.addressOf(0),
                                        tagLenVar.ptr
                                    )
                                }
                            } else if (aad.isEmpty()) {
                                encryptedData.ciphertext.usePinned { ctPin ->
                                    cccryptorGcm(
                                        1u, 0u,
                                        keyPin.addressOf(0), 32uL,
                                        noncePin.addressOf(0), 12uL,
                                        null, 0uL,
                                        ctPin.addressOf(0), encryptedData.ciphertext.size.toULong(),
                                        ptPin.addressOf(0),
                                        tagPin.addressOf(0),
                                        tagLenVar.ptr
                                    )
                                }
                            } else {
                                encryptedData.ciphertext.usePinned { ctPin ->
                                    aad.usePinned { aadPin ->
                                        cccryptorGcm(
                                            1u, 0u,
                                            keyPin.addressOf(0), 32uL,
                                            noncePin.addressOf(0), 12uL,
                                            aadPin.addressOf(0), aad.size.toULong(),
                                            ctPin.addressOf(0), encryptedData.ciphertext.size.toULong(),
                                            ptPin.addressOf(0),
                                            tagPin.addressOf(0),
                                            tagLenVar.ptr
                                        )
                                    }
                                }
                            }

                            if (status != 0) {
                                throw EnvelopeIntegrityException("AES-GCM decryption failed: tag mismatch or tampered ciphertext/AAD (OSStatus: $status)")
                            }
                        }
                    }
                }
            }
        }

        return plaintext
    }
}

actual fun ByteArray.toBase64(): String {
    if (this.isEmpty()) return ""
    val nsData = toNSData()
    return nsData.base64EncodedStringWithOptions(0u)
}

actual fun String.fromBase64(): ByteArray {
    if (this.isEmpty()) return ByteArray(0)
    val nsData = NSData.create(base64EncodedString = this@fromBase64, options = 0u)
        ?: throw IllegalArgumentException("Invalid Base64 string")
    return nsData.toByteArray()
}

private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.convert())
}

private fun NSData.toByteArray(): ByteArray {
    val length = this.length
    return ByteArray(length.toInt()).apply {
        usePinned {
            platform.posix.memcpy(it.addressOf(0), this@toByteArray.bytes, length)
        }
    }
}

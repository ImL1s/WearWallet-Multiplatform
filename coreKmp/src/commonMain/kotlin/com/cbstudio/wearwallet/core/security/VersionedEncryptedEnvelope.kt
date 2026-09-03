package com.cbstudio.wearwallet.core.security

import io.github.iml1s.crypto.SecureByteArray

/**
 * 密鑰派生函數演算法識別符
 */
enum class KdfAlgorithm(val id: Byte, val algorithmName: String, val isSupported: Boolean) {
    PBKDF2_HMAC_SHA256(1, "PBKDF2WithHmacSHA256", true),
    ARGON2ID(2, "Argon2id", false),
    HKDF_SHA256(3, "HKDF-SHA256", false);

    companion object {
        fun fromId(id: Byte): KdfAlgorithm {
            val algo = entries.firstOrNull { it.id == id }
                ?: throw UnsupportedEnvelopeVersionException("Unsupported KDF algorithm ID: $id")
            if (!algo.isSupported) {
                throw UnsupportedEnvelopeVersionException(
                    "KDF algorithm '${algo.algorithmName}' (ID: $id) is not supported in this version"
                )
            }
            return algo
        }

        fun fromName(name: String): KdfAlgorithm {
            val algo = entries.firstOrNull { it.algorithmName.equals(name, ignoreCase = true) }
                ?: throw UnsupportedEnvelopeVersionException("Unsupported KDF algorithm Name: $name")
            if (!algo.isSupported) {
                throw UnsupportedEnvelopeVersionException(
                    "KDF algorithm '${algo.algorithmName}' is not supported in this version"
                )
            }
            return algo
        }
    }
}

/**
 * 對稱加密演算法識別符
 */
enum class CipherAlgorithm(val id: Byte, val algorithmName: String, val isSupported: Boolean) {
    AES_256_GCM(1, "AES-256-GCM", true),
    CHACHA20_POLY1305(2, "ChaCha20-Poly1305", false);

    companion object {
        fun fromId(id: Byte): CipherAlgorithm {
            val algo = entries.firstOrNull { it.id == id }
                ?: throw UnsupportedEnvelopeVersionException("Unsupported Cipher algorithm ID: $id")
            if (!algo.isSupported) {
                throw UnsupportedEnvelopeVersionException(
                    "Cipher algorithm '${algo.algorithmName}' (ID: $id) is not supported in this version"
                )
            }
            return algo
        }

        fun fromName(name: String): CipherAlgorithm {
            val algo = entries.firstOrNull { it.algorithmName.equals(name, ignoreCase = true) }
                ?: throw UnsupportedEnvelopeVersionException("Unsupported Cipher algorithm Name: $name")
            if (!algo.isSupported) {
                throw UnsupportedEnvelopeVersionException(
                    "Cipher algorithm '${algo.algorithmName}' is not supported in this version"
                )
            }
            return algo
        }
    }
}

/**
 * VersionedEncryptedEnvelope 例外類別階層 (Fail-Closed)
 */
open class EnvelopeException(message: String, cause: Throwable? = null) : SecurityException(message, cause)
class InvalidEnvelopeHeaderException(message: String, cause: Throwable? = null) : EnvelopeException(message, cause)
class UnsupportedEnvelopeVersionException(message: String, cause: Throwable? = null) : EnvelopeException(message, cause)
class EnvelopeIntegrityException(message: String, cause: Throwable? = null) : EnvelopeException(message, cause)
class EnvelopeCorruptedException(message: String, cause: Throwable? = null) : EnvelopeException(message, cause)
class UnversionedPlaintextException(message: String, cause: Throwable? = null) : EnvelopeException(message, cause)

/**
 * 安全密鑰管理器基礎例外類別 (Fail-Closed)
 */
open class KeyManagementException(message: String, cause: Throwable? = null) : SecurityException(message, cause)
class KeyNotFoundException(keyId: String) : KeyManagementException("Key with id '$keyId' not found")
class KeyAuthenticationException(keyId: String, cause: Throwable? = null) : KeyManagementException("Authentication failed for key '$keyId'", cause)
class AuthenticationRequiredException(message: String, cause: Throwable? = null) : KeyManagementException(message, cause)
open class KeyStorageException(message: String, cause: Throwable? = null) : KeyManagementException(message, cause)
class UnsupportedPlatformKeyException(message: String) : KeyManagementException(message)

/**
 * 當已遷移至 KeyVault 的錢包在安全庫中找不到私鑰素材時拋出 (Downgrade Safety / Fail-Closed, P1-4 / M4)
 */
class KeyMaterialUnavailableException(
    message: String = "Key material unavailable in KeyVault. Downgrade or fallback to raw signing is prohibited.",
    cause: Throwable? = null
) : KeyManagementException(message, cause)

/**
 * 當 AndroidKeyStore 服務不可用或初始化失敗時拋出 (P0-1)
 */
class AndroidKeyStoreUnavailableException(message: String, cause: Throwable? = null) : KeyStorageException(message, cause)

/**
 * 當 EncryptedSharedPreferences / MasterKey 服務不可用或初始化失敗時拋出 (P0-1)
 */
class EncryptedStorageUnavailableException(message: String, cause: Throwable? = null) : KeyStorageException(message, cause)

/**
 * 當在 Keystore 中生成硬體支援的對稱加密密鑰失敗時拋出 (P0-1)
 */
class KeyGenerationException(message: String, cause: Throwable? = null) : KeyStorageException(message, cause)

/**
 * watchOS 熱錢包操作不支援例外 (Fail-Closed, P0-2)
 */
class WatchOSHotWalletUnsupportedException(
    message: String = "Hot-wallet private key operations are unsupported on watchOS. Use cold-wallet/Keystone or paired phone."
) : KeyManagementException(message)

/**
 * 版本化加密信封 (VersionedEncryptedEnvelope)
 *
 * 遵循 Issue #14 規格與安全原則：
 * - 4-byte Magic Header: 0x5757454E ("WWEN")
 * - 1-byte Version: 0x01
 * - KDF 演算法與參數 (PBKDF2 / Argon2id, 迭代次數 10_000~1_000_000, 密鑰長度 32)
 * - 鹽值 (Salt 16~64 bytes)
 * - 對稱加密演算法識別 (AES-256-GCM / ChaCha20-Poly1305)
 * - Nonce / IV (固定 12 bytes)
 * - 密文 (Ciphertext, max 1MB)
 * - 認證標籤 (Auth Tag 固定 16 bytes)
 * - 密鑰識別符 (Key Identifier, max 256 bytes)
 * - 認證附加數據 (Authenticated Additional Data / AAD, max 4096 bytes)
 * - 嚴格拒絕尾隨垃圾位元組
 */
data class VersionedEncryptedEnvelope(
    val version: Byte = CURRENT_VERSION,
    val kdfAlgorithm: KdfAlgorithm = KdfAlgorithm.PBKDF2_HMAC_SHA256,
    val kdfIterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    val kdfKeyLength: Int = REQUIRED_KEY_LENGTH,
    val salt: ByteArray,
    val cipherAlgorithm: CipherAlgorithm = CipherAlgorithm.AES_256_GCM,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val authTag: ByteArray,
    val keyId: String = "",
    val aad: ByteArray = byteArrayOf()
) {

    init {
        require(version == CURRENT_VERSION) { "Unsupported envelope version: $version" }
        require(kdfAlgorithm == KdfAlgorithm.PBKDF2_HMAC_SHA256) { "Unsupported KDF algorithm: ${kdfAlgorithm.algorithmName}" }
        require(cipherAlgorithm == CipherAlgorithm.AES_256_GCM) { "Unsupported cipher algorithm: ${cipherAlgorithm.algorithmName}" }

        require(kdfIterations in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
            "KDF iterations out of bounds [$MIN_PBKDF2_ITERATIONS, $MAX_PBKDF2_ITERATIONS]: $kdfIterations"
        }
        require(kdfKeyLength == REQUIRED_KEY_LENGTH) {
            "KDF key length must be exactly $REQUIRED_KEY_LENGTH bytes, got $kdfKeyLength"
        }
        require(salt.size in MIN_SALT_LENGTH..MAX_SALT_LENGTH) {
            "Salt size out of bounds [$MIN_SALT_LENGTH, $MAX_SALT_LENGTH]: ${salt.size}"
        }
        require(nonce.size == REQUIRED_NONCE_LENGTH) {
            "Nonce size must be exactly $REQUIRED_NONCE_LENGTH bytes, got ${nonce.size}"
        }
        require(authTag.size == REQUIRED_AUTH_TAG_LENGTH) {
            "Auth tag size must be exactly $REQUIRED_AUTH_TAG_LENGTH bytes, got ${authTag.size}"
        }
        require(ciphertext.isNotEmpty() && ciphertext.size <= MAX_CIPHERTEXT_LENGTH) {
            "Ciphertext size out of bounds (1..$MAX_CIPHERTEXT_LENGTH bytes): ${ciphertext.size}"
        }
        require(keyId.encodeToByteArray().size <= MAX_KEY_ID_LENGTH) {
            "KeyId length exceeds maximum of $MAX_KEY_ID_LENGTH bytes: ${keyId.length}"
        }
        require(aad.size <= MAX_AAD_LENGTH) {
            "AAD length exceeds maximum of $MAX_AAD_LENGTH bytes: ${aad.size}"
        }
    }

    /**
     * 二進制序列化
     */
    fun serialize(): ByteArray {
        val keyIdBytes = keyId.encodeToByteArray()
        val totalSize = 4 + // MAGIC (4)
                1 + // version (1)
                1 + // kdfId (1)
                4 + // kdfIterations (4)
                2 + // kdfKeyLength (2)
                1 + salt.size + // saltLen (1) + salt
                1 + // cipherId (1)
                1 + nonce.size + // nonceLen (1) + nonce
                2 + keyIdBytes.size + // keyIdLen (2) + keyId
                4 + aad.size + // aadLen (4) + aad
                1 + authTag.size + // tagLen (1) + authTag
                4 + ciphertext.size // ctLen (4) + ciphertext

        val buffer = ByteArray(totalSize)
        var offset = 0

        // 1. Magic header
        MAGIC.copyInto(buffer, offset, 0, 4)
        offset += 4

        // 2. Version
        buffer[offset++] = version

        // 3. KDF info
        buffer[offset++] = kdfAlgorithm.id
        writeInt(kdfIterations, buffer, offset)
        offset += 4
        writeShort(kdfKeyLength.toShort(), buffer, offset)
        offset += 2

        // 4. Salt
        buffer[offset++] = salt.size.toByte()
        salt.copyInto(buffer, offset, 0, salt.size)
        offset += salt.size

        // 5. Cipher info
        buffer[offset++] = cipherAlgorithm.id
        buffer[offset++] = nonce.size.toByte()
        nonce.copyInto(buffer, offset, 0, nonce.size)
        offset += nonce.size

        // 6. KeyId
        writeShort(keyIdBytes.size.toShort(), buffer, offset)
        offset += 2
        keyIdBytes.copyInto(buffer, offset, 0, keyIdBytes.size)
        offset += keyIdBytes.size

        // 7. AAD
        writeInt(aad.size, buffer, offset)
        offset += 4
        if (aad.isNotEmpty()) {
            aad.copyInto(buffer, offset, 0, aad.size)
            offset += aad.size
        }

        // 8. Auth Tag
        buffer[offset++] = authTag.size.toByte()
        authTag.copyInto(buffer, offset, 0, authTag.size)
        offset += authTag.size

        // 9. Ciphertext
        writeInt(ciphertext.size, buffer, offset)
        offset += 4
        ciphertext.copyInto(buffer, offset, 0, ciphertext.size)
        offset += ciphertext.size

        return buffer
    }

    /**
     * 序列化為 Base64 字串
     */
    fun serializeToBase64(): String {
        return serialize().toBase64()
    }

    /**
     * 使用密碼解密信封內容並驗證 AAD 完整性
     *
     * @param password 解密密碼 (ByteArray)
     * @param expectedAad 預期的 AAD (若提供則嚴格比對，不符時 fail-closed)
     * @return 解密後的明文字節陣列
     */
    fun decrypt(password: ByteArray, expectedAad: ByteArray? = null): ByteArray {
        if (expectedAad != null && !this.aad.contentEquals(expectedAad)) {
            throw EnvelopeIntegrityException("Authenticated additional data (AAD) mismatch: expected contextual binding failed")
        }

        var derivedKey: ByteArray? = null
        try {
            // 窮舉式 KDF 分派 (P1-1)
            derivedKey = when (kdfAlgorithm) {
                KdfAlgorithm.PBKDF2_HMAC_SHA256 -> {
                    CryptoUtils.pbkdf2(
                        password = password,
                        salt = salt,
                        iterations = kdfIterations,
                        keyLength = kdfKeyLength
                    )
                }
                KdfAlgorithm.ARGON2ID -> throw UnsupportedEnvelopeVersionException("Argon2id KDF is not implemented")
                KdfAlgorithm.HKDF_SHA256 -> throw UnsupportedEnvelopeVersionException("HKDF-SHA256 is not implemented")
            }

            // 窮舉式 Cipher 分派 (P1-1)
            return when (cipherAlgorithm) {
                CipherAlgorithm.AES_256_GCM -> {
                    val encryptedData = EncryptedData(
                        ciphertext = ciphertext,
                        nonce = nonce,
                        authTag = authTag
                    )
                    CryptoUtils.aesGcmDecryptWithAad(
                        encryptedData = encryptedData,
                        key = derivedKey,
                        aad = aad
                    )
                }
                CipherAlgorithm.CHACHA20_POLY1305 -> throw UnsupportedEnvelopeVersionException("ChaCha20-Poly1305 is not implemented")
            }
        } catch (e: EnvelopeException) {
            throw e
        } catch (e: Exception) {
            throw EnvelopeIntegrityException("Failed to decrypt or authenticate envelope: ${e.message}", e)
        } finally {
            derivedKey?.let { SecureByteArray.secureZero(it) }
        }
    }

    /**
     * 安全清零敏感內部記憶體緩衝區
     */
    fun secureZero() {
        SecureByteArray.secureZero(salt)
        SecureByteArray.secureZero(nonce)
        SecureByteArray.secureZero(ciphertext)
        SecureByteArray.secureZero(authTag)
        SecureByteArray.secureZero(aad)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VersionedEncryptedEnvelope) return false
        if (version != other.version) return false
        if (kdfAlgorithm != other.kdfAlgorithm) return false
        if (kdfIterations != other.kdfIterations) return false
        if (kdfKeyLength != other.kdfKeyLength) return false
        if (!salt.contentEquals(other.salt)) return false
        if (cipherAlgorithm != other.cipherAlgorithm) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!authTag.contentEquals(other.authTag)) return false
        if (keyId != other.keyId) return false
        if (!aad.contentEquals(other.aad)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + kdfAlgorithm.hashCode()
        result = 31 * result + kdfIterations
        result = 31 * result + kdfKeyLength
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + cipherAlgorithm.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        result = 31 * result + keyId.hashCode()
        result = 31 * result + aad.contentHashCode()
        return result
    }

    companion object {
        val MAGIC: ByteArray = byteArrayOf(0x57.toByte(), 0x57.toByte(), 0x45.toByte(), 0x4E.toByte()) // "WWEN"
        const val CURRENT_VERSION: Byte = 1
        const val DEFAULT_PBKDF2_ITERATIONS = 100_000
        const val MIN_PBKDF2_ITERATIONS = 10_000
        const val MAX_PBKDF2_ITERATIONS = 1_000_000
        const val REQUIRED_KEY_LENGTH = 32 // 256-bit
        const val MIN_SALT_LENGTH = 16
        const val MAX_SALT_LENGTH = 64
        const val REQUIRED_NONCE_LENGTH = 12
        const val MIN_NONCE_LENGTH = 12
        const val REQUIRED_AUTH_TAG_LENGTH = 16
        const val AUTH_TAG_LENGTH = 16
        const val MAX_KEY_ID_LENGTH = 256
        const val MAX_AAD_LENGTH = 4096
        const val MAX_CIPHERTEXT_LENGTH = 1_000_000
        const val MAX_TOTAL_ENVELOPE_SIZE = 1_050_000

        private const val MIN_HEADER_SIZE = 4 + 1 + 1 + 4 + 2 + 1 + MIN_SALT_LENGTH + 1 + 1 + REQUIRED_NONCE_LENGTH + 2 + 4 + 1 + REQUIRED_AUTH_TAG_LENGTH + 4 + 1

        /**
         * 加密明文並建立 VersionedEncryptedEnvelope
         */
        fun encrypt(
            plaintext: ByteArray,
            password: ByteArray,
            keyId: String = "",
            aad: ByteArray = byteArrayOf(),
            kdfAlgorithm: KdfAlgorithm = KdfAlgorithm.PBKDF2_HMAC_SHA256,
            kdfIterations: Int = DEFAULT_PBKDF2_ITERATIONS,
            cipherAlgorithm: CipherAlgorithm = CipherAlgorithm.AES_256_GCM,
            salt: ByteArray = CryptoUtils.randomBytes(MIN_SALT_LENGTH),
            nonce: ByteArray = CryptoUtils.randomBytes(REQUIRED_NONCE_LENGTH)
        ): VersionedEncryptedEnvelope {
            require(plaintext.isNotEmpty()) { "Plaintext cannot be empty" }
            require(password.isNotEmpty()) { "Password cannot be empty" }
            require(kdfIterations in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) { "KDF iterations out of bounds" }
            require(salt.size in MIN_SALT_LENGTH..MAX_SALT_LENGTH) { "Salt size out of bounds" }
            require(nonce.size == REQUIRED_NONCE_LENGTH) { "Nonce size must be $REQUIRED_NONCE_LENGTH bytes" }

            var derivedKey: ByteArray? = null
            try {
                // 窮舉式 KDF 分派 (P1-1)
                derivedKey = when (kdfAlgorithm) {
                    KdfAlgorithm.PBKDF2_HMAC_SHA256 -> {
                        CryptoUtils.pbkdf2(
                            password = password,
                            salt = salt,
                            iterations = kdfIterations,
                            keyLength = REQUIRED_KEY_LENGTH
                        )
                    }
                    KdfAlgorithm.ARGON2ID -> throw UnsupportedEnvelopeVersionException("Argon2id KDF is not implemented")
                    KdfAlgorithm.HKDF_SHA256 -> throw UnsupportedEnvelopeVersionException("HKDF-SHA256 is not implemented")
                }

                // 窮舉式 Cipher 分派 (P1-1)
                val encrypted = when (cipherAlgorithm) {
                    CipherAlgorithm.AES_256_GCM -> {
                        CryptoUtils.aesGcmEncryptWithAad(
                            data = plaintext,
                            key = derivedKey,
                            nonce = nonce,
                            aad = aad
                        )
                    }
                    CipherAlgorithm.CHACHA20_POLY1305 -> throw UnsupportedEnvelopeVersionException("ChaCha20-Poly1305 is not implemented")
                }

                return VersionedEncryptedEnvelope(
                    version = CURRENT_VERSION,
                    kdfAlgorithm = kdfAlgorithm,
                    kdfIterations = kdfIterations,
                    kdfKeyLength = REQUIRED_KEY_LENGTH,
                    salt = salt.copyOf(),
                    cipherAlgorithm = cipherAlgorithm,
                    nonce = encrypted.nonce.copyOf(),
                    ciphertext = encrypted.ciphertext.copyOf(),
                    authTag = encrypted.authTag.copyOf(),
                    keyId = keyId,
                    aad = aad.copyOf()
                )
            } finally {
                derivedKey?.let { SecureByteArray.secureZero(it) }
            }
        }

        /**
         * 反序列化二進制信封 (Fail-Closed)
         */
        fun deserialize(bytes: ByteArray): VersionedEncryptedEnvelope {
            if (bytes.size < MIN_HEADER_SIZE) {
                throw EnvelopeCorruptedException("Envelope buffer too short: ${bytes.size} bytes (min: $MIN_HEADER_SIZE)")
            }
            if (bytes.size > MAX_TOTAL_ENVELOPE_SIZE) {
                throw EnvelopeCorruptedException("Envelope buffer exceeds maximum allowed size: ${bytes.size} bytes (max: $MAX_TOTAL_ENVELOPE_SIZE)")
            }

            // 1. Check Magic Header
            if (bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1] || bytes[2] != MAGIC[2] || bytes[3] != MAGIC[3]) {
                throw InvalidEnvelopeHeaderException("Invalid envelope magic header: ${bytes.take(4).toByteArray().toHexString()}")
            }

            return try {
                var offset = 4

                // 2. Check Version
                if (offset >= bytes.size) throw EnvelopeCorruptedException("Truncated envelope at version")
                val version = bytes[offset++]
                if (version != CURRENT_VERSION) {
                    throw UnsupportedEnvelopeVersionException("Unsupported envelope version: $version")
                }

                // 3. KDF info
                if (offset >= bytes.size) throw EnvelopeCorruptedException("Truncated envelope at kdfId")
                val kdfId = bytes[offset++]
                val kdfAlgorithm = KdfAlgorithm.fromId(kdfId)
                if (offset + 4 > bytes.size) throw EnvelopeCorruptedException("Truncated envelope at kdfIterations")
                val kdfIterations = readInt(bytes, offset)
                offset += 4
                if (kdfIterations !in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
                    throw EnvelopeCorruptedException("KDF iterations out of bounds: $kdfIterations")
                }
                if (offset + 2 > bytes.size) throw EnvelopeCorruptedException("Truncated envelope at kdfKeyLength")
                val kdfKeyLength = readShort(bytes, offset).toInt() and 0xFFFF
                offset += 2
                if (kdfKeyLength != REQUIRED_KEY_LENGTH) {
                    throw EnvelopeCorruptedException("Invalid KDF key length: $kdfKeyLength (must be $REQUIRED_KEY_LENGTH)")
                }

                // 4. Salt
                if (offset >= bytes.size) throw EnvelopeCorruptedException("Truncated envelope at saltLen")
                val saltLen = bytes[offset++].toInt() and 0xFF
                if (saltLen !in MIN_SALT_LENGTH..MAX_SALT_LENGTH || offset + saltLen > bytes.size) {
                    throw EnvelopeCorruptedException("Invalid salt length: $saltLen")
                }
                val salt = bytes.copyOfRange(offset, offset + saltLen)
                offset += saltLen

                // 5. Cipher info
                if (offset >= bytes.size) throw EnvelopeCorruptedException("Truncated envelope at cipherId")
                val cipherId = bytes[offset++]
                val cipherAlgorithm = CipherAlgorithm.fromId(cipherId)
                if (offset >= bytes.size) throw EnvelopeCorruptedException("Truncated envelope at nonceLen")
                val nonceLen = bytes[offset++].toInt() and 0xFF
                if (nonceLen != REQUIRED_NONCE_LENGTH || offset + nonceLen > bytes.size) {
                    throw EnvelopeCorruptedException("Invalid nonce length: $nonceLen (must be $REQUIRED_NONCE_LENGTH)")
                }
                val nonce = bytes.copyOfRange(offset, offset + nonceLen)
                offset += nonceLen

                // 6. KeyId
                if (offset + 2 > bytes.size) throw EnvelopeCorruptedException("Truncated envelope at keyIdLen")
                val keyIdLen = readShort(bytes, offset).toInt() and 0xFFFF
                offset += 2
                if (keyIdLen > MAX_KEY_ID_LENGTH || offset + keyIdLen > bytes.size) {
                    throw EnvelopeCorruptedException("Invalid keyId length: $keyIdLen")
                }
                val keyId = bytes.copyOfRange(offset, offset + keyIdLen).decodeToString()
                offset += keyIdLen

                // 7. AAD
                if (offset + 4 > bytes.size) throw EnvelopeCorruptedException("Truncated envelope at aadLen")
                val aadLen = readInt(bytes, offset)
                offset += 4
                if (aadLen < 0 || aadLen > MAX_AAD_LENGTH || offset + aadLen > bytes.size) {
                    throw EnvelopeCorruptedException("Invalid AAD length: $aadLen")
                }
                val aad = if (aadLen > 0) bytes.copyOfRange(offset, offset + aadLen) else byteArrayOf()
                offset += aadLen

                // 8. Auth Tag
                if (offset >= bytes.size) throw EnvelopeCorruptedException("Truncated envelope at tagLen")
                val tagLen = bytes[offset++].toInt() and 0xFF
                if (tagLen != REQUIRED_AUTH_TAG_LENGTH || offset + tagLen > bytes.size) {
                    throw EnvelopeCorruptedException("Invalid auth tag length: $tagLen (must be $REQUIRED_AUTH_TAG_LENGTH)")
                }
                val authTag = bytes.copyOfRange(offset, offset + tagLen)
                offset += tagLen

                // 9. Ciphertext
                if (offset + 4 > bytes.size) throw EnvelopeCorruptedException("Truncated envelope at ctLen")
                val ctLen = readInt(bytes, offset)
                offset += 4
                if (ctLen <= 0 || ctLen > MAX_CIPHERTEXT_LENGTH || offset + ctLen > bytes.size) {
                    throw EnvelopeCorruptedException("Invalid ciphertext length: $ctLen")
                }
                val ciphertext = bytes.copyOfRange(offset, offset + ctLen)
                offset += ctLen

                // 10. 嚴格拒絕尾隨垃圾位元組 (P1-2 Trailing Bytes Gate)
                if (offset != bytes.size) {
                    throw EnvelopeCorruptedException(
                        "Envelope contains ${bytes.size - offset} trailing garbage bytes (expected exact length $offset, got ${bytes.size})"
                    )
                }

                VersionedEncryptedEnvelope(
                    version = version,
                    kdfAlgorithm = kdfAlgorithm,
                    kdfIterations = kdfIterations,
                    kdfKeyLength = kdfKeyLength,
                    salt = salt,
                    cipherAlgorithm = cipherAlgorithm,
                    nonce = nonce,
                    ciphertext = ciphertext,
                    authTag = authTag,
                    keyId = keyId,
                    aad = aad
                )
            } catch (e: EnvelopeException) {
                throw e
            } catch (e: IndexOutOfBoundsException) {
                throw EnvelopeCorruptedException("Truncated or malformed envelope payload: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw EnvelopeCorruptedException("Truncated or malformed envelope payload: ${e.message}", e)
            }
        }

        /**
         * 從 Base64 字串反序列化 (Fail-Closed)
         */
        fun deserializeFromBase64(base64: String): VersionedEncryptedEnvelope {
            if (base64.isBlank()) {
                throw UnversionedPlaintextException("Envelope data is empty or blank")
            }

            val decodedBytes = try {
                base64.fromBase64()
            } catch (e: Exception) {
                throw UnversionedPlaintextException("Invalid base64 envelope format: ${e.message}")
            }

            if (decodedBytes.size < 4 ||
                decodedBytes[0] != MAGIC[0] ||
                decodedBytes[1] != MAGIC[1] ||
                decodedBytes[2] != MAGIC[2] ||
                decodedBytes[3] != MAGIC[3]
            ) {
                throw UnversionedPlaintextException("Unversioned plaintext or legacy format rejected: use migrateLegacy() for migration")
            }

            return deserialize(decodedBytes)
        }

        /**
         * 檢查是否為舊版格式 (包含冒號分隔格式及 Android 舊版 Base64(IV + Ciphertext))
         */
        fun isLegacyFormat(raw: String): Boolean {
            if (raw.isBlank()) return false
            val parts = raw.split(":")
            if (parts.size == 4 || parts.size == 5) {
                return true
            }
            val decoded = try { raw.fromBase64() } catch (e: Exception) { null }
            if (decoded != null && decoded.size >= 4) {
                if (decoded[0] == MAGIC[0] && decoded[1] == MAGIC[1] && decoded[2] == MAGIC[2] && decoded[3] == MAGIC[3]) {
                    return false // 這是合法 WWEN Envelope，不是 legacy
                }
                // 若至少包含 12 bytes IV + 16 bytes Tag (28 bytes)，且為合法 Base64，視為 Android 舊版加密格式
                if (decoded.size >= 28) {
                    return true
                }
            }
            return false
        }

        /**
         * 遷移舊版格式數據為 VersionedEncryptedEnvelope
         * 支援：
         * 1. 5-part "v1:salt:nonce:tag:ciphertext" (PBKDF2)
         * 2. 4-part "salt:nonce:tag:ciphertext" (PBKDF2)
         * 3. Android 舊版 Base64(12-byte IV + GCM ciphertext with tag), key = SHA-256(password)
         */
        fun migrateLegacy(
            legacyString: String,
            password: String,
            keyId: String = "",
            aad: ByteArray = byteArrayOf()
        ): VersionedEncryptedEnvelope {
            val chars = password.toCharArray()
            try {
                return migrateLegacy(legacyString, chars, keyId, aad)
            } finally {
                chars.fill('\u0000')
            }
        }

        fun migrateLegacy(
            legacyString: String,
            password: CharArray,
            keyId: String = "",
            aad: ByteArray = byteArrayOf()
        ): VersionedEncryptedEnvelope {
            if (legacyString.isBlank()) {
                throw UnversionedPlaintextException("Legacy encrypted data is empty or blank")
            }

            val parts = legacyString.split(":")
            val passwordBytes = password.encodeToUtf8Bytes()
            var derivedKey: ByteArray? = null
            var decryptedPlaintext: ByteArray? = null
            var saltToClear: ByteArray? = null
            var nonceToClear: ByteArray? = null
            var tagToClear: ByteArray? = null
            var ctToClear: ByteArray? = null

            try {
                if (parts.size == 4 || parts.size == 5) {
                    val (salt, nonce, authTag, ciphertext) = try {
                        when (parts.size) {
                            5 -> {
                                if (parts[0] != "v1") {
                                    throw UnsupportedEnvelopeVersionException("Unsupported legacy version: ${parts[0]}")
                                }
                                Quadruple(
                                    parts[1].fromBase64(),
                                    parts[2].fromBase64(),
                                    parts[3].fromBase64(),
                                    parts[4].fromBase64()
                                )
                            }
                            4 -> {
                                Quadruple(
                                    parts[0].fromBase64(),
                                    parts[1].fromBase64(),
                                    parts[2].fromBase64(),
                                    parts[3].fromBase64()
                                )
                            }
                            else -> throw UnversionedPlaintextException("Invalid legacy encrypted format")
                        }
                    } catch (e: UnsupportedEnvelopeVersionException) {
                        throw e
                    } catch (e: EnvelopeException) {
                        throw e
                    } catch (e: Exception) {
                        throw UnversionedPlaintextException("Invalid legacy envelope format: ${e.message}", e)
                    }
                    saltToClear = salt
                    nonceToClear = nonce
                    tagToClear = authTag
                    ctToClear = ciphertext

                    derivedKey = CryptoUtils.pbkdf2(
                        password = passwordBytes,
                        salt = salt,
                        iterations = DEFAULT_PBKDF2_ITERATIONS,
                        keyLength = REQUIRED_KEY_LENGTH
                    )

                    val legacyData = EncryptedData(ciphertext, nonce, authTag)
                    decryptedPlaintext = try {
                        CryptoUtils.aesGcmDecrypt(legacyData, derivedKey)
                    } catch (e: Exception) {
                        throw EnvelopeIntegrityException("Legacy PBKDF2 decryption failed: ${e.message}", e)
                    }
                } else {
                    // Android legacy Base64(IV + CiphertextWithTag)
                    val combined = try {
                        legacyString.fromBase64()
                    } catch (e: Exception) {
                        throw UnversionedPlaintextException("Invalid base64 in legacy encrypted data: ${e.message}", e)
                    }

                    if (combined.size < 28) {
                        throw UnversionedPlaintextException("Invalid legacy encrypted payload length: ${combined.size} (expected >= 28)")
                    }

                    // 檢查是否為 WWEN
                    if (combined.size >= 4 &&
                        combined[0] == MAGIC[0] && combined[1] == MAGIC[1] &&
                        combined[2] == MAGIC[2] && combined[3] == MAGIC[3]
                    ) {
                        // 已經是 WWEN，直接解密
                        val envelope = deserialize(combined)
                        decryptedPlaintext = envelope.decrypt(passwordBytes, expectedAad = aad)
                        return envelope
                    }

                    val iv = combined.copyOfRange(0, 12)
                    val encryptedWithTag = combined.copyOfRange(12, combined.size)
                    val authTag = encryptedWithTag.copyOfRange(encryptedWithTag.size - 16, encryptedWithTag.size)
                    val ciphertext = encryptedWithTag.copyOfRange(0, encryptedWithTag.size - 16)

                    nonceToClear = iv
                    tagToClear = authTag
                    ctToClear = ciphertext

                    // Android 舊版派生密鑰: SHA-256(password)
                    derivedKey = CryptoUtils.sha256(passwordBytes)

                    val legacyData = EncryptedData(ciphertext, iv, authTag)
                    decryptedPlaintext = try {
                        CryptoUtils.aesGcmDecrypt(legacyData, derivedKey)
                    } catch (e: Exception) {
                        throw EnvelopeIntegrityException("Legacy Android SHA-256 AES-GCM decryption failed: ${e.message}", e)
                    }
                }

                // 重新以現代 VersionedEncryptedEnvelope 加密
                return encrypt(
                    plaintext = decryptedPlaintext,
                    password = passwordBytes,
                    keyId = keyId,
                    aad = aad,
                    salt = CryptoUtils.randomBytes(MIN_SALT_LENGTH),
                    nonce = CryptoUtils.randomBytes(REQUIRED_NONCE_LENGTH)
                )
            } finally {
                SecureByteArray.secureZero(passwordBytes)
                derivedKey?.let { SecureByteArray.secureZero(it) }
                decryptedPlaintext?.let { SecureByteArray.secureZero(it) }
                saltToClear?.let { SecureByteArray.secureZero(it) }
                nonceToClear?.let { SecureByteArray.secureZero(it) }
                tagToClear?.let { SecureByteArray.secureZero(it) }
                ctToClear?.let { SecureByteArray.secureZero(it) }
            }
        }

        private fun writeInt(value: Int, buffer: ByteArray, offset: Int) {
            buffer[offset] = (value shr 24).toByte()
            buffer[offset + 1] = (value shr 16).toByte()
            buffer[offset + 2] = (value shr 8).toByte()
            buffer[offset + 3] = value.toByte()
        }

        private fun readInt(buffer: ByteArray, offset: Int): Int {
            return ((buffer[offset].toInt() and 0xFF) shl 24) or
                    ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                    ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                    (buffer[offset + 3].toInt() and 0xFF)
        }

        private fun writeShort(value: Short, buffer: ByteArray, offset: Int) {
            buffer[offset] = (value.toInt() shr 8).toByte()
            buffer[offset + 1] = value.toByte()
        }

        private fun readShort(buffer: ByteArray, offset: Int): Short {
            return (((buffer[offset].toInt() and 0xFF) shl 8) or
                    (buffer[offset + 1].toInt() and 0xFF)).toShort()
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

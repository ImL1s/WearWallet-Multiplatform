package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 私鑰管理器 - 負責安全地管理和解密私鑰
 *
 * 安全規範：
 * 1. 嚴格阻斷未版本化明文 (P0-4)：凡非合法 WWEN 信封或合法 Legacy 格式，一律拋出 UnversionedPlaintextException。
 * 2. 簽章與私鑰導出路徑一律使用 SecureByteArray，記憶體主動清零。
 * 3. 隔離與棄用 raw String getPrivateKey 介面。
 */
class PrivateKeyManager(
    private val keystoreManager: KeystoreManager
) {

    /**
     * 獲取解密的私鑰（安全版本）
     *
     * @param encryptedData 加密存儲的私鑰或助記詞 (WWEN 信封或 Legacy 格式)
     * @param isMnemonic 是否為助記詞 (true: 助記詞推導; false: 私鑰解密)
     * @param password 解鎖密碼
     * @param chainType 鏈類型（用於 HD 推導）
     * @param derivationPath 推導路徑（可選）
     * @param passwordHash 可選的密碼哈希（用於驗證）
     * @return 解密的私鑰（SecureByteArray 格式）
     */
    suspend fun getPrivateKeySecure(
        encryptedData: String,
        isMnemonic: Boolean,
        password: String,
        chainType: ChainType,
        derivationPath: String? = null,
        passwordHash: String? = null
    ): Result<SecureByteArray> = withContext(Dispatchers.Default) {
        try {
            // 如果提供了密碼哈希，則驗證密碼
            if (passwordHash != null && !verifyPasswordHash(password, passwordHash)) {
                return@withContext Result.failure(
                    KeyAuthenticationException("unspecified", Exception("Invalid password"))
                )
            }

            // P0-4: 嚴格阻斷未加密明文，無合法信封時拋出 UnversionedPlaintextException
            if (!isEncryptedData(encryptedData)) {
                throw UnversionedPlaintextException(
                    "Unversioned plaintext ${if (isMnemonic) "mnemonic" else "private key"} rejected. Stored records must be encrypted."
                )
            }

            val decryptedKey = if (isMnemonic) {
                val decryptedMnemonic = decryptMnemonic(encryptedData, password)
                derivePrivateKey(decryptedMnemonic, chainType, derivationPath)
            } else {
                decryptPrivateKey(encryptedData, password)
            }

            // 將 hex 字符串轉換為 SecureByteArray
            Result.success(SecureByteArray.fromHex(decryptedKey))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 獲取解密的私鑰（安全版本 - 錢包帳戶重載）
     *
     * @param wallet 錢包帳戶
     * @param encryptedData 加密存儲的私鑰或助記詞 (WWEN 信封或 Legacy 格式)
     * @param isMnemonic 是否為助記詞
     * @param password 解鎖密碼
     * @param chainType 鏈類型（用於 HD 推導）
     * @param passwordHash 可選的密碼哈希（用於驗證）
     * @return 解密的私鑰（SecureByteArray 格式）
     */
    suspend fun getPrivateKeySecure(
        wallet: WalletAccount,
        encryptedData: String,
        isMnemonic: Boolean,
        password: String,
        chainType: ChainType,
        passwordHash: String? = null
    ): Result<SecureByteArray> = withContext(Dispatchers.Default) {
        // 硬體錢包不需要私鑰
        if (wallet.isHardwareWallet) {
            return@withContext Result.failure(
                IllegalArgumentException("Hardware wallet does not have private key in software storage")
            )
        }

        // 如果提供了密碼哈希，則驗證密碼
        if (passwordHash != null && !verifyPasswordHash(password, passwordHash)) {
            return@withContext Result.failure(
                KeyAuthenticationException(wallet.id, Exception("Invalid password for wallet '${wallet.id}'"))
            )
        }

        getPrivateKeySecure(
            encryptedData = encryptedData,
            isMnemonic = isMnemonic,
            password = password,
            chainType = chainType,
            derivationPath = wallet.derivationPath,
            passwordHash = null
        )
    }

    /**
     * 嚴格驗證資料是否為合法加密格式 (WWEN 信封或合法之 Legacy 格式)
     * 凡未通過者一律判定為 false，禁止當作正常私鑰或助記詞推導
     */
    private fun isEncryptedData(data: String): Boolean {
        if (data.isBlank()) return false

        // 1. 檢查是否為 Base64 WWEN VersionedEncryptedEnvelope
        try {
            val bytes = data.fromBase64()
            if (bytes.size >= 4 &&
                bytes[0] == VersionedEncryptedEnvelope.MAGIC[0] &&
                bytes[1] == VersionedEncryptedEnvelope.MAGIC[1] &&
                bytes[2] == VersionedEncryptedEnvelope.MAGIC[2] &&
                bytes[3] == VersionedEncryptedEnvelope.MAGIC[3]
            ) {
                return true
            }
        } catch (_: Exception) {
            // Not valid base64
        }

        // 2. 檢查是否為 Legacy 格式 (v1:salt:nonce:tag:ciphertext 或 salt:nonce:tag:ciphertext)
        return VersionedEncryptedEnvelope.isLegacyFormat(data)
    }

    /**
     * 驗證密碼哈希
     * 使用恆定時間比較防止 timing attack
     *
     * @param password 待驗證的密碼
     * @param storedHash 存儲的密碼哈希（格式: salt:hash）
     * @return 密碼是否正確
     */
    suspend fun verifyPasswordHash(
        password: String,
        storedHash: String
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            // 格式: salt:hash (都是 Base64)
            val parts = storedHash.split(":")
            if (parts.size != 2) {
                return@withContext false
            }

            val salt = parts[0].fromBase64()
            val expectedHash = parts[1].fromBase64()

            // 使用相同參數重新計算哈希
            val computedHash = CryptoUtils.pbkdf2(
                password = password.encodeToByteArray(),
                salt = salt,
                iterations = PBKDF2_ITERATIONS,
                keyLength = KEY_LENGTH
            )

            // 使用恆定時間比較防止 timing attack
            constantTimeEquals(expectedHash, computedHash)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 恆定時間比較，防止 timing attack
     * 使用位運算確保不會因為提前返回而洩漏時間信息
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    companion object {
        // OWASP 2024 推薦的 PBKDF2 參數
        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_LENGTH = 32                // 256-bit AES 密鑰
        private const val SALT_LENGTH = 16               // 128-bit salt
        private const val ENCRYPTION_VERSION = "v1"      // 加密格式版本號
    }

    /**
     * 解密私鑰（使用 SecureByteArray 保證安全清除）
     * 支援 VersionedEncryptedEnvelope (Base64) 及舊版格式 (v1:salt:nonce:authTag:ciphertext)
     */
    private suspend fun decryptPrivateKey(
        encryptedKey: String,
        password: String
    ): String = withContext(Dispatchers.Default) {
        val passwordBytes = password.encodeToByteArray()
        var decrypted: ByteArray? = null

        try {
            val decryptedBytes = if (!VersionedEncryptedEnvelope.isLegacyFormat(encryptedKey)) {
                try {
                    val envelope = VersionedEncryptedEnvelope.deserializeFromBase64(encryptedKey)
                    envelope.decrypt(passwordBytes)
                } catch (e: Exception) {
                    if (VersionedEncryptedEnvelope.isLegacyFormat(encryptedKey)) {
                        decryptLegacyKey(encryptedKey, passwordBytes)
                    } else {
                        throw UnversionedPlaintextException("Unversioned plaintext or corrupt key rejected: ${e.message}")
                    }
                }
            } else {
                decryptLegacyKey(encryptedKey, passwordBytes)
            }

            decrypted = decryptedBytes
            return@withContext decryptedBytes.decodeToString()
        } catch (e: Exception) {
            if (e is UnversionedPlaintextException) throw e
            throw Exception("Failed to decrypt private key: ${e.message}", e)
        } finally {
            SecureByteArray.secureZero(passwordBytes)
            decrypted?.let { SecureByteArray.secureZero(it) }
        }
    }

    private fun decryptLegacyKey(encryptedKey: String, passwordBytes: ByteArray): ByteArray {
        val parts = encryptedKey.split(":")
        var salt: ByteArray? = null
        var nonce: ByteArray? = null
        var authTag: ByteArray? = null
        var ciphertext: ByteArray? = null
        var derivedKey: ByteArray? = null

        try {
            when (parts.size) {
                5 -> {
                    require(parts[0] == ENCRYPTION_VERSION) {
                        "Unsupported encryption format version: ${parts[0]}"
                    }
                    salt = parts[1].fromBase64()
                    nonce = parts[2].fromBase64()
                    authTag = parts[3].fromBase64()
                    ciphertext = parts[4].fromBase64()
                }
                4 -> {
                    salt = parts[0].fromBase64()
                    nonce = parts[1].fromBase64()
                    authTag = parts[2].fromBase64()
                    ciphertext = parts[3].fromBase64()
                }
                else -> throw UnversionedPlaintextException(
                    "Invalid encrypted data format: expected 4 or 5 parts, got ${parts.size}"
                )
            }

            derivedKey = CryptoUtils.pbkdf2(
                password = passwordBytes,
                salt = salt,
                iterations = PBKDF2_ITERATIONS,
                keyLength = KEY_LENGTH
            )

            val encryptedData = EncryptedData(ciphertext, nonce, authTag)
            return CryptoUtils.aesGcmDecrypt(encryptedData, derivedKey)
        } finally {
            derivedKey?.let { SecureByteArray.secureZero(it) }
            salt?.let { SecureByteArray.secureZero(it) }
            nonce?.let { SecureByteArray.secureZero(it) }
            authTag?.let { SecureByteArray.secureZero(it) }
            ciphertext?.let { SecureByteArray.secureZero(it) }
        }
    }

    /**
     * 解密助記詞
     * 格式與私鑰相同: salt:nonce:authTag:ciphertext (Base64) 或 WWEN Envelope
     */
    private suspend fun decryptMnemonic(
        encryptedMnemonic: String,
        password: String
    ): String = withContext(Dispatchers.Default) {
        try {
            decryptPrivateKey(encryptedMnemonic, password)
        } catch (e: Exception) {
            if (e is UnversionedPlaintextException) throw e
            throw Exception("Failed to decrypt mnemonic: ${e.message}", e)
        }
    }

    /**
     * 從助記詞推導私鑰
     */
    private suspend fun derivePrivateKey(
        mnemonic: String,
        chainType: ChainType,
        customPath: String? = null
    ): String = withContext(Dispatchers.Default) {
        try {
            val derivationPath = customPath ?: getDerivationPath(chainType)
            keystoreManager.derivePrivateKey(mnemonic, derivationPath)
        } catch (e: Exception) {
            throw Exception("Failed to derive private key: ${e.message}", e)
        }
    }

    /**
     * 生成隨機 salt
     */
    private fun generateSalt(size: Int = SALT_LENGTH): ByteArray {
        return CryptoUtils.randomBytes(size)
    }

    /**
     * 獲取鏈的標準推導路徑
     */
    private fun getDerivationPath(chainType: ChainType): String {
        return when (chainType) {
            ChainType.BITCOIN -> "m/84'/0'/0'/0/0" // Native SegWit (Bech32)
            ChainType.LITECOIN -> "m/84'/2'/0'/0/0" // Native SegWit
            ChainType.DOGECOIN -> "m/44'/3'/0'/0/0" // Legacy
            ChainType.BITCOIN_CASH -> "m/44'/145'/0'/0/0" // Legacy
            ChainType.ETHEREUM,
            ChainType.BSC,
            ChainType.POLYGON,
            ChainType.AVALANCHE,
            ChainType.ARBITRUM,
            ChainType.OPTIMISM,
            ChainType.BASE,
            ChainType.CRONOS,
            ChainType.FANTOM -> "m/44'/60'/0'/0/0" // EVM 鏈
            else -> "m/44'/60'/0'/0/0" // 默認 EVM 路徑
        }
    }

    /**
     * 加密並存儲私鑰（使用 SecureByteArray 保證安全清除）
     * @return 加密後的字符串 (WWEN Base64)
     */
    suspend fun encryptAndStorePrivateKey(
        privateKey: String,
        password: String
    ): Result<String> = withContext(Dispatchers.Default) {
        val privateKeyBytes = privateKey.encodeToByteArray()
        val passwordBytes = password.encodeToByteArray()

        try {
            val envelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = privateKeyBytes,
                password = passwordBytes,
                kdfIterations = PBKDF2_ITERATIONS
            )

            Result.success(envelope.serializeToBase64())
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            SecureByteArray.secureZero(privateKeyBytes)
            SecureByteArray.secureZero(passwordBytes)
        }
    }

    /**
     * 加密並存儲助記詞
     * @return 加密後的字符串，格式與私鑰相同 (WWEN Base64)
     */
    suspend fun encryptAndStoreMnemonic(
        mnemonic: String,
        password: String
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            encryptAndStorePrivateKey(mnemonic, password)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 生成密碼哈希
     * @return 哈希字符串，格式: salt:hash (Base64)
     */
    suspend fun generatePasswordHash(password: String): String {
        return withContext(Dispatchers.Default) {
            val salt = generateSalt()
            val hash = CryptoUtils.pbkdf2(
                password = password.encodeToByteArray(),
                salt = salt,
                iterations = PBKDF2_ITERATIONS,
                keyLength = KEY_LENGTH
            )

            "${salt.toBase64()}:${hash.toBase64()}"
        }
    }
}
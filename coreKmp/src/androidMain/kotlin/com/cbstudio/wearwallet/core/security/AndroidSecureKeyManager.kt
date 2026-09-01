package com.cbstudio.wearwallet.core.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cbstudio.wearwallet.core.common.Result
import io.github.iml1s.crypto.Secp256k1Pure
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android 平台的安全私鑰管理器實現 (Fail-Closed & Hardware-Backed)
 * 使用 Android Keystore 系統提供硬體級別的安全保護 (TEE/StrongBox)
 *
 * 安全規範：
 * 1. 移除所有降級路徑 (P0-1)：
 *    - KeyStore 失敗 -> 拋出 AndroidKeyStoreUnavailableException
 *    - EncryptedSharedPreferences 失敗 -> 拋出 EncryptedStorageUnavailableException
 *    - KeyGenerator 失敗 -> 拋出 KeyGenerationException
 * 2. 嚴禁將任何私鑰寫入普通 SharedPreferences
 * 3. 嚴禁使用未持久化的軟體 AES 金鑰回傳 Success
 * 4. 透過建構子 Provider 模式提供單元測試依賴注入
 * 5. P1-3 Canonical AAD 與 keyId 一致性校驗
 */
class AndroidSecureKeyManager internal constructor(
    private val context: Context,
    private val config: SecureStorageConfig = SecureStorageConfig(),
    private val keyStoreProvider: () -> KeyStore = {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (e: Throwable) {
            throw AndroidKeyStoreUnavailableException("AndroidKeyStore is unavailable: ${e.message}", e)
        }
    },
    private val encryptedPrefsProvider: (Context) -> SharedPreferences = { ctx ->
        try {
            createEncryptedSharedPreferences(ctx)
        } catch (e: Throwable) {
            throw EncryptedStorageUnavailableException("EncryptedSharedPreferences is unavailable: ${e.message}", e)
        }
    },
    private val secretKeyProvider: ((alias: String, requireAuth: Boolean) -> SecretKey)? = null
) : SecureKeyManager, KeyVaultReconciliationCapability, KeyVaultDeletionCapability {

    /**
     * 生產環境公開建構子
     */
    constructor(
        context: Context,
        config: SecureStorageConfig = SecureStorageConfig()
    ) : this(
        context = context,
        config = config,
        keyStoreProvider = {
            try {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            } catch (e: Throwable) {
                throw AndroidKeyStoreUnavailableException("AndroidKeyStore is unavailable: ${e.message}", e)
            }
        },
        encryptedPrefsProvider = { ctx ->
            try {
                createEncryptedSharedPreferences(ctx)
            } catch (e: Throwable) {
                throw EncryptedStorageUnavailableException("EncryptedSharedPreferences is unavailable: ${e.message}", e)
            }
        },
        secretKeyProvider = null
    )

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ENCRYPTED_PREFS_NAME = "secure_wallet_keys"
        const val KEY_ALIAS_PREFIX = "wallet_key_"
        const val IV_SUFFIX = "_iv"
        const val TAG_SUFFIX = "_tag"
        const val REQUIRE_AUTH_SUFFIX = "_require_auth"
        const val AUTH_TAG_LENGTH = 128

        // Root 檢測路徑
        private val ROOT_BINARIES = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        private fun createEncryptedSharedPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private val keyStore: KeyStore by lazy {
        try {
            keyStoreProvider()
        } catch (e: Throwable) {
            if (e is AndroidKeyStoreUnavailableException) throw e
            throw AndroidKeyStoreUnavailableException("Failed to initialize KeyStore: ${e.message}", e)
        }
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            encryptedPrefsProvider(context)
        } catch (e: Throwable) {
            if (e is EncryptedStorageUnavailableException) throw e
            throw EncryptedStorageUnavailableException("Failed to initialize EncryptedSharedPreferences: ${e.message}", e)
        }
    }

    private val securityEvents = MutableSharedFlow<SecurityEvent>()
    private val activeSessions = java.util.concurrent.ConcurrentHashMap<String, ProvisioningSession>()
    private val committedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override suspend fun storePrivateKey(
        keyId: String,
        privateKey: ByteArray,
        requireAuth: Boolean,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit> {
        if (keyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyId must not be blank"))
        }
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }
        if (privateKey.isEmpty()) {
            return Result.Failure(IllegalArgumentException("privateKey must not be empty"))
        }
        val privateKeyBytes = privateKey.copyOf()
        return try {
            // 檢測 root
            if (config.enableRootDetection && isDeviceRooted()) {
                emitSecurityEvent(SecurityEvent.RootDetected(System.currentTimeMillis()))
                return Result.Failure(SecurityException("Device is rooted - operation denied"))
            }

            // 授權驗證 (Authenticate first, Fail-Closed & Atomic Validation & Consumption)
            if (requireAuth) {
                if (authContext == null) {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
                    return Result.Failure(
                        AuthenticationRequiredException("Authentication is required to store key '$keyId' but authContext is null")
                    )
                }

                val consumeResult = AuthHandleRegistry.validateAndConsume(
                    handle = authContext.authHandle,
                    expectedKeyId = keyId,
                    expectedOperation = AuthOperation.IMPORT,
                    expectedFingerprint = null,
                    currentTimeMs = System.currentTimeMillis(),
                    expectedWalletId = expectedWalletId
                )
                if (consumeResult is Result.Failure) {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
                    return Result.Failure(consumeResult.exception)
                }
            }

            // 生成或獲取硬體加密密鑰 (若失敗拋出 KeyGenerationException / AndroidKeyStoreUnavailableException)
            val secretKey = getOrCreateSecretKey(keyId, requireAuth)

            // 加密私鑰
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedDataWithTag = cipher.doFinal(privateKeyBytes)

            // 分離密文和認證標籤 (Tag 固定 16 bytes = 128 bits)
            val ciphertext = encryptedDataWithTag.copyOfRange(0, encryptedDataWithTag.size - 16)
            val authTag = encryptedDataWithTag.copyOfRange(encryptedDataWithTag.size - 16, encryptedDataWithTag.size)

            // 存儲加密的私鑰、IV、Tag 以及 requireAuth 旗標 (同步 .commit() 持久化)
            val committed = encryptedPrefs.edit()
                .putString(keyId, ciphertext.toHexString())
                .putString(keyId + IV_SUFFIX, iv.toHexString())
                .putString(keyId + TAG_SUFFIX, authTag.toHexString())
                .putBoolean(keyId + REQUIRE_AUTH_SUFFIX, requireAuth)
                .commit()
            if (!committed) {
                throw KeyStorageException("Failed to synchronously commit key '$keyId' to encrypted storage")
            }

            emitSecurityEvent(SecurityEvent.KeyCreated(keyId, System.currentTimeMillis()))
            Result.Success(Unit)
        } catch (e: KeyManagementException) {
            Result.Failure(e)
        } catch (e: android.security.keystore.UserNotAuthenticatedException) {
            emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
            Result.Failure(AuthenticationRequiredException("User authentication required to store key '$keyId'", e))
        } catch (e: Exception) {
            Result.Failure(KeyStorageException("Failed to store private key: ${e.message}", e))
        } finally {
            SecureByteArray.secureZero(privateKeyBytes)
        }
    }

    /**
     * 內部解密私鑰字節 (調用者須負責在 finally 區塊清零返回的 ByteArray)
     */
    private suspend fun getPrivateKeyBytesInternal(
        keyId: String,
        authContext: AuthenticationContext?,
        expectedOperation: AuthOperation? = null,
        expectedIntent: String? = null,
        expectedWalletId: String
    ): Result<ByteArray> {
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }
        return try {
            // 檢測 root
            if (config.enableRootDetection && isDeviceRooted()) {
                emitSecurityEvent(SecurityEvent.RootDetected(System.currentTimeMillis()))
                return Result.Failure(SecurityException("Device is rooted - operation denied"))
            }

            // 獲取加密的私鑰和 IV
            val ciphertextHex = encryptedPrefs.getString(keyId, null)
                ?: return Result.Failure(KeyNotFoundException(keyId))
            val ivHex = encryptedPrefs.getString(keyId + IV_SUFFIX, null)
                ?: return Result.Failure(KeyNotFoundException(keyId))
            val tagHex = encryptedPrefs.getString(keyId + TAG_SUFFIX, null)

            val ciphertext = ciphertextHex.hexToByteArray()
            val iv = ivHex.hexToByteArray()
            val authTag = tagHex?.hexToByteArray() ?: byteArrayOf()

            // 檢查該 key 是否需要認證 (預設 true)
            val isAuthRequired = encryptedPrefs.getBoolean(keyId + REQUIRE_AUTH_SUFFIX, true)

            // 授權驗證 (Fail-Closed & Atomic Validation & Consumption before Decryption)
            if (isAuthRequired) {
                if (authContext == null) {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
                    return Result.Failure(
                        AuthenticationRequiredException("Authentication is required for key '$keyId' but authContext is null")
                    )
                }

                if (expectedOperation == null) {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
                    return Result.Failure(
                        IllegalArgumentException("expectedOperation must be provided when decrypting require-auth key '$keyId'")
                    )
                }

                val consumeResult = AuthHandleRegistry.validateAndConsume(
                    handle = authContext.authHandle,
                    expectedKeyId = keyId,
                    expectedOperation = expectedOperation,
                    expectedFingerprint = expectedIntent,
                    currentTimeMs = System.currentTimeMillis(),
                    expectedWalletId = expectedWalletId
                )
                if (consumeResult is Result.Failure) {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
                    return Result.Failure(consumeResult.exception)
                }
            }

            // 檢查是否傳入綁定的 CryptoObject (若有提供則使用，否則使用解鎖的 KeyStore SecretKey 初始化 Cipher)
            val boundCipher = (authContext?.authHandle?.cryptoObject ?: (authContext?.cryptoObject as? BiometricPrompt.CryptoObject))?.cipher

            val cipher = boundCipher ?: run {
                val secretKey = keyStore.getKey(KEY_ALIAS_PREFIX + keyId, null) as? SecretKey
                    ?: return Result.Failure(KeyNotFoundException(keyId))
                val c = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(AUTH_TAG_LENGTH, iv)
                c.init(Cipher.DECRYPT_MODE, secretKey, spec)
                c
            }

            // 解密私鑰 (ciphertext + tag)
            val fullEncryptedPayload = if (authTag.isNotEmpty()) ciphertext + authTag else ciphertext
            val decryptedData = cipher.doFinal(fullEncryptedPayload)

            emitSecurityEvent(SecurityEvent.KeyAccessed(keyId, System.currentTimeMillis()))
            Result.Success(decryptedData)
        } catch (e: KeyManagementException) {
            emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
            Result.Failure(e)
        } catch (e: android.security.keystore.UserNotAuthenticatedException) {
            emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
            Result.Failure(AuthenticationRequiredException("User is not authenticated for key '$keyId'", e))
        } catch (e: Exception) {
            emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
            Result.Failure(KeyAuthenticationException(keyId, e))
        }
    }

    override suspend fun deletePrivateKey(
        keyId: String,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit> {
        if (keyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyId must not be blank"))
        }
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }

        return try {
            // 檢測 root
            if (config.enableRootDetection && isDeviceRooted()) {
                emitSecurityEvent(SecurityEvent.RootDetected(System.currentTimeMillis()))
                return Result.Failure(SecurityException("Device is rooted - operation denied"))
            }

            // 檢查 key 是否存在
            val keyExists = hasPrivateKey(keyId) || keyStore.containsAlias(KEY_ALIAS_PREFIX + keyId)
            if (!keyExists) {
                return Result.Failure(KeyNotFoundException(keyId))
            }

            // 檢查該 key 是否需要認證 (預設 true)
            val isAuthRequired = encryptedPrefs.getBoolean(keyId + REQUIRE_AUTH_SUFFIX, true)
            if (isAuthRequired) {
                if (authContext == null) {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
                    return Result.Failure(
                        AuthenticationRequiredException("Authentication is required to delete key '$keyId' but no authContext was provided")
                    )
                }

                val consumeResult = AuthHandleRegistry.validateAndConsume(
                    handle = authContext.authHandle,
                    expectedKeyId = keyId,
                    expectedOperation = AuthOperation.DELETE,
                    expectedFingerprint = null,
                    currentTimeMs = System.currentTimeMillis(),
                    expectedWalletId = expectedWalletId
                )
                if (consumeResult is Result.Failure) {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
                    return Result.Failure(consumeResult.exception)
                }
            }

            // 刪除 Keystore 中的密鑰
            if (keyStore.containsAlias(KEY_ALIAS_PREFIX + keyId)) {
                keyStore.deleteEntry(KEY_ALIAS_PREFIX + keyId)
            }

            // 刪除加密存儲 (同步 .commit() 持久化)
            val committed = encryptedPrefs.edit()
                .remove(keyId)
                .remove(keyId + IV_SUFFIX)
                .remove(keyId + TAG_SUFFIX)
                .remove(keyId + REQUIRE_AUTH_SUFFIX)
                .commit()
            if (!committed) {
                throw KeyStorageException("Failed to synchronously commit key deletion for '$keyId' to encrypted storage")
            }

            emitSecurityEvent(SecurityEvent.KeyDeleted(keyId, System.currentTimeMillis()))
            Result.Success(Unit)
        } catch (e: KeyManagementException) {
            Result.Failure(e)
        } catch (e: Exception) {
            Result.Failure(KeyStorageException("Failed to delete key: ${e.message}", e))
        }
    }

    override suspend fun deletePrivateKeyWithGrant(
        grant: DeletionAuthorizationGrant,
        expectedWalletId: String
    ): Result<Unit> {
        val keyId = grant.keyAlias
        if (keyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyAlias in grant must not be blank"))
        }

        // 檢測 root
        if (config.enableRootDetection && isDeviceRooted()) {
            emitSecurityEvent(SecurityEvent.RootDetected(System.currentTimeMillis()))
            return Result.Failure(SecurityException("Device is rooted - operation denied"))
        }

        // 原子校驗 HMAC 與消費 Grant Nonce (含 expectedWalletId)
        val grantConsumeResult = DeletionGrantRegistry.validateAndConsume(
            grant = grant,
            expectedKeyAlias = keyId,
            expectedWalletId = if (expectedWalletId.isNotBlank()) expectedWalletId else grant.walletId
        )
        if (grantConsumeResult is Result.Failure) {
            emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, System.currentTimeMillis()))
            return Result.Failure(grantConsumeResult.exception)
        }

        return try {
            // 檢查 key 是否存在
            val keyExists = hasPrivateKey(keyId) || keyStore.containsAlias(KEY_ALIAS_PREFIX + keyId)
            if (!keyExists) {
                return Result.Failure(KeyNotFoundException(keyId))
            }

            // 刪除 Keystore 中的密鑰
            if (keyStore.containsAlias(KEY_ALIAS_PREFIX + keyId)) {
                keyStore.deleteEntry(KEY_ALIAS_PREFIX + keyId)
            }

            // 刪除加密存儲 (同步 .commit() 持久化)
            val committed = encryptedPrefs.edit()
                .remove(keyId)
                .remove(keyId + IV_SUFFIX)
                .remove(keyId + TAG_SUFFIX)
                .remove(keyId + REQUIRE_AUTH_SUFFIX)
                .commit()
            if (!committed) {
                throw KeyStorageException("Failed to synchronously commit grant key deletion for '$keyId' to encrypted storage")
            }

            emitSecurityEvent(SecurityEvent.KeyDeleted(keyId, System.currentTimeMillis()))
            Result.Success(Unit)
        } catch (e: KeyManagementException) {
            Result.Failure(e)
        } catch (e: Exception) {
            Result.Failure(KeyStorageException("Failed to delete key with grant: ${e.message}", e))
        }
    }

    override suspend fun startProvisioningSession(): ProvisioningSession {
        val session = ProvisioningSession.create()
        activeSessions[session.sessionId] = session
        return session
    }

    override suspend fun getActiveProvisioningSession(sessionId: String): ProvisioningSession? {
        val session = activeSessions[sessionId] ?: return null
        return if (session.isActive) session else null
    }

    override suspend fun storeStagedPrivateKey(
        sessionId: String,
        stagedKeyAlias: String,
        privateKey: ByteArray,
        requireAuth: Boolean,
        authContext: AuthenticationContext?
    ): Result<Unit> {
        val session = activeSessions[sessionId]
        if (session != null) {
            if (session.isCommitted) {
                return Result.Failure(IllegalStateException("Cannot store key in already committed session: $sessionId"))
            }
            if (session.isRolledBack) {
                return Result.Failure(IllegalStateException("Cannot store key in already rolled back session: $sessionId"))
            }
            if (!session.isActive) {
                return Result.Failure(IllegalStateException("Provisioning session $sessionId has expired"))
            }
        }
        if (requireAuth) {
            if (authContext == null) {
                return Result.Failure(
                    AuthenticationRequiredException("Authentication is required to store staged key for session '$sessionId' but authContext is null")
                )
            }
            val handle = authContext.authHandle
            if (handle == null) {
                return Result.Failure(
                    AuthenticationRequiredException("Authentication is required to store staged key for session '$sessionId' but authHandle is null")
                )
            }
            if (handle.keyId.isBlank() || handle.keyId != stagedKeyAlias) {
                return Result.Failure(
                    AuthenticationRequiredException("Cross-key handle rejected: expected keyId '$stagedKeyAlias' but got '${handle.keyId}'")
                )
            }
            if (handle.sessionId.isBlank() || handle.sessionId != sessionId) {
                return Result.Failure(
                    AuthenticationRequiredException("Session mismatch: expected session '$sessionId' but got '${handle.sessionId}'")
                )
            }
            if (handle.operation != AuthOperation.IMPORT) {
                return Result.Failure(
                    AuthenticationRequiredException("Auth handle operation '${handle.operation}' does not match expected 'IMPORT'")
                )
            }
        }
        if (session != null) {
            activeSessions[session.sessionId] = session
        }
        val targetWalletId = authContext?.authHandle?.walletId?.ifBlank { sessionId } ?: sessionId
        return storePrivateKey(
            keyId = stagedKeyAlias,
            privateKey = privateKey,
            requireAuth = requireAuth,
            authContext = authContext,
            expectedWalletId = targetWalletId
        )
    }

    override suspend fun storeStagedPrivateKey(
        session: ProvisioningSession,
        privateKey: ByteArray,
        requireAuth: Boolean,
        authContext: AuthenticationContext?
    ): Result<Unit> {
        return storeStagedPrivateKey(
            sessionId = session.sessionId,
            stagedKeyAlias = session.stagedKeyAlias,
            privateKey = privateKey,
            requireAuth = requireAuth,
            authContext = authContext
        )
    }

    override suspend fun commitProvisioningSession(session: ProvisioningSession): Result<Unit> {
        return try {
            session.markCommitted()
            activeSessions.remove(session.sessionId)
            committedKeys.add(session.stagedKeyAlias)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun rollbackProvisioningSession(session: ProvisioningSession): Result<Unit> {
        val activeSession = activeSessions[session.sessionId]
            ?: return Result.Failure(IllegalStateException("Unknown or inactive provisioning session: ${session.sessionId}"))
        if (activeSession.stagedKeyAlias != session.stagedKeyAlias) {
            return Result.Failure(IllegalStateException("Provisioning session staged key alias mismatch"))
        }
        if (!activeSession.isActive || !session.isActive) {
            activeSessions.remove(session.sessionId)
            return Result.Failure(IllegalStateException("Provisioning session has expired"))
        }
        if (session.isCommitted || activeSession.isCommitted || committedKeys.contains(session.stagedKeyAlias)) {
            return Result.Failure(IllegalStateException("Cannot rollback an already committed session or key: ${session.sessionId} / ${session.stagedKeyAlias}"))
        }
        if (session.isRolledBack) {
            return Result.Success(Unit)
        }
        return try {
            session.markRolledBack()
            activeSessions.remove(session.sessionId)

            // 刪除 Keystore 中的密鑰 (若存在)
            if (keyStore.containsAlias(KEY_ALIAS_PREFIX + session.stagedKeyAlias)) {
                keyStore.deleteEntry(KEY_ALIAS_PREFIX + session.stagedKeyAlias)
            }

            // 刪除加密存儲 (同步 .commit() 持久化)
            val committed = encryptedPrefs.edit()
                .remove(session.stagedKeyAlias)
                .remove(session.stagedKeyAlias + IV_SUFFIX)
                .remove(session.stagedKeyAlias + TAG_SUFFIX)
                .remove(session.stagedKeyAlias + REQUIRE_AUTH_SUFFIX)
                .commit()
            if (!committed) {
                throw KeyStorageException("Failed to synchronously commit session rollback for '${session.stagedKeyAlias}'")
            }

            emitSecurityEvent(SecurityEvent.KeyDeleted(session.stagedKeyAlias, System.currentTimeMillis()))
            Result.Success(Unit)
        } catch (e: KeyManagementException) {
            Result.Failure(e)
        } catch (e: Exception) {
            Result.Failure(KeyStorageException("Failed to rollback provisioning session: ${e.message}", e))
        }
    }

    override suspend fun rollbackStagedKeyInternal(
        grant: RecoveryGrant
    ): Result<Unit> {
        val stagedKeyAlias = grant.alias
        val sessionId = grant.sessionId

        if (stagedKeyAlias.isBlank()) {
            return Result.Failure(IllegalArgumentException("stagedKeyAlias must not be blank"))
        }
        if (sessionId.isBlank()) {
            return Result.Failure(IllegalArgumentException("sessionId must not be blank"))
        }
        if (committedKeys.contains(stagedKeyAlias)) {
            return Result.Failure(IllegalStateException("Cannot rollback a committed key in memory: $stagedKeyAlias"))
        }

        // 原子驗證並消費 RecoveryGrant
        val grantValidation = RecoveryGrantRegistry.validateAndConsume(grant, stagedKeyAlias)
        if (grantValidation is Result.Failure) {
            return Result.Failure(grantValidation.exception)
        }

        return try {
            activeSessions.remove(sessionId)

            // 刪除 Keystore 中的密鑰 (若存在)
            if (keyStore.containsAlias(KEY_ALIAS_PREFIX + stagedKeyAlias)) {
                keyStore.deleteEntry(KEY_ALIAS_PREFIX + stagedKeyAlias)
            }

            // 刪除加密存儲 (同步 .commit() 持久化)
            val committed = encryptedPrefs.edit()
                .remove(stagedKeyAlias)
                .remove(stagedKeyAlias + IV_SUFFIX)
                .remove(stagedKeyAlias + TAG_SUFFIX)
                .remove(stagedKeyAlias + REQUIRE_AUTH_SUFFIX)
                .commit()
            if (!committed) {
                throw KeyStorageException("Failed to synchronously commit internal rollback for '$stagedKeyAlias'")
            }

            emitSecurityEvent(SecurityEvent.KeyDeleted(stagedKeyAlias, System.currentTimeMillis()))
            Result.Success(Unit)
        } catch (e: KeyManagementException) {
            Result.Failure(e)
        } catch (e: Exception) {
            Result.Failure(KeyStorageException("Failed to rollback staged key internally: ${e.message}", e))
        }
    }

    override suspend fun checkKeyPresence(keyId: String): KeyPresence {
        if (keyId.isBlank()) return KeyPresence.Absent
        return try {
            val hasKeyStore = try {
                keyStore.containsAlias(KEY_ALIAS_PREFIX + keyId)
            } catch (e: Throwable) {
                return KeyPresence.Unavailable(AndroidKeyStoreUnavailableException("Failed to check KeyStore for alias '$keyId': ${e.message}", e))
            }
            val hasCiphertext = encryptedPrefs.contains(keyId)
            val hasIv = encryptedPrefs.contains(keyId + IV_SUFFIX)
            val hasTag = encryptedPrefs.contains(keyId + TAG_SUFFIX)
            val hasAuth = encryptedPrefs.contains(keyId + REQUIRE_AUTH_SUFFIX)

            val presentCount = (if (hasKeyStore) 1 else 0) +
                    (if (hasCiphertext) 1 else 0) +
                    (if (hasIv) 1 else 0) +
                    (if (hasTag) 1 else 0) +
                    (if (hasAuth) 1 else 0)

            when (presentCount) {
                5 -> KeyPresence.Present
                0 -> KeyPresence.Absent
                else -> KeyPresence.Partial(
                    "Key '$keyId' in partial state (hasKeyStoreKey=$hasKeyStore, hasCiphertext=$hasCiphertext, hasIv=$hasIv, hasTag=$hasTag, hasRequireAuth=$hasAuth, keyStore=$hasKeyStore, iv=$hasIv, tag=$hasTag, requireAuth=$hasAuth)"
                )
            }
        } catch (e: Throwable) {
            KeyPresence.Unavailable(e)
        }
    }

    override suspend fun hasPrivateKey(keyId: String): Boolean {
        return checkKeyPresence(keyId) is KeyPresence.Present
    }

    override suspend fun listKeyIds(): List<String> {
        return try {
            encryptedPrefs.all.keys
                .filter { !it.endsWith(IV_SUFFIX) && !it.endsWith(TAG_SUFFIX) && !it.endsWith(REQUIRE_AUTH_SUFFIX) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun signWithKey(
        keyId: String,
        data: ByteArray,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<ByteArray> {
        if (data.isEmpty()) {
            return Result.Failure(IllegalArgumentException("Data to sign cannot be empty"))
        }

        val digest = if (data.size == 32) data else CryptoUtils.sha256(data)
        val expectedIntent = digest.toHexString()

        val privateKeyBytesResult = getPrivateKeyBytesInternal(
            keyId = keyId,
            authContext = authContext,
            expectedOperation = AuthOperation.SIGN,
            expectedIntent = expectedIntent,
            expectedWalletId = expectedWalletId
        )
        if (privateKeyBytesResult is Result.Failure) {
            return Result.Failure(privateKeyBytesResult.exception)
        }

        val privateKeyBytes = (privateKeyBytesResult as Result.Success).data
        authContext?.authHandle?.invalidate()
        var normalizedKeyBytes: ByteArray? = null
        return try {
            // 將 hex 或原始字節規範化為 32 字節私鑰
            normalizedKeyBytes = if (privateKeyBytes.size == 64) {
                // Hex 字串字節 (64 hex chars)
                privateKeyBytes.decodeToString().hexToByteArray()
            } else if (privateKeyBytes.size == 32) {
                privateKeyBytes.copyOf()
            } else {
                // 嘗試解析為 hex 字串
                val keyStr = privateKeyBytes.decodeToString().trim().removePrefix("0x")
                if (keyStr.length == 64) {
                    keyStr.hexToByteArray()
                } else {
                    return Result.Failure(IllegalArgumentException("Invalid private key length: ${privateKeyBytes.size} bytes"))
                }
            }

            if (normalizedKeyBytes.size != 32) {
                return Result.Failure(IllegalArgumentException("Normalized private key must be 32 bytes"))
            }

            // 使用 Secp256k1Pure 進行真實確定性 ECDSA 簽名 (RFC 6979)
            val signature = Secp256k1Pure.signWithRecovery(digest, normalizedKeyBytes)
            val sigBytes = signature.r + signature.s + byteArrayOf(signature.yParity.toByte())

            Result.Success(sigBytes)
        } catch (e: Exception) {
            Result.Failure(KeyManagementException("Cryptographic signing failed: ${e.message}", e))
        } finally {
            SecureByteArray.secureZero(privateKeyBytes)
            normalizedKeyBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    override suspend fun revealMnemonic(
        keyId: String,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<ScopedMnemonic> {
        return Result.Failure(UnsupportedOperationException("revealMnemonic not supported directly on AndroidSecureKeyManager"))
    }

    override suspend fun getSecurityLevel(): SecurityLevel {
        val hasHardware = try {
            keyStore.aliases().toList().any { alias ->
                try {
                    val key = keyStore.getKey(alias, null)
                    key != null
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }

        val hasStrongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val testAlias = "strongbox_probe_test"
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    testAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setIsStrongBoxBacked(true)
                    .build()

                keyGenerator.init(spec)
                keyGenerator.generateKey()
                keyStore.deleteEntry(testAlias)
                true
            } catch (e: StrongBoxUnavailableException) {
                false
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        val biometricManager = BiometricManager.from(context)
        val hasBiometric = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

        val level = when {
            hasStrongBox -> SecurityLevel.Level.STRONGBOX
            hasHardware -> SecurityLevel.Level.HARDWARE
            else -> SecurityLevel.Level.KEYSTORE
        }

        return SecurityLevel(
            level = level,
            hasHardwareBacking = hasHardware,
            hasStrongBox = hasStrongBox,
            hasBiometricSupport = hasBiometric,
            isRooted = isDeviceRooted()
        )
    }

    override suspend fun exportEncryptedKey(
        keyId: String,
        backupPassword: CharArray,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<EncryptedBackup> {
        if (keyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyId cannot be blank"))
        }
        if (backupPassword.isEmpty()) {
            return Result.Failure(IllegalArgumentException("Backup password cannot be empty"))
        }

        val privateKeyBytesResult = getPrivateKeyBytesInternal(
            keyId = keyId,
            authContext = authContext,
            expectedOperation = AuthOperation.EXPORT,
            expectedWalletId = expectedWalletId
        )
        if (privateKeyBytesResult is Result.Failure) {
            return Result.Failure(privateKeyBytesResult.exception)
        }

        val privateKeyBytes = (privateKeyBytesResult as Result.Success).data
        authContext?.authHandle?.invalidate()
        val passwordBytes = backupPassword.encodeToUtf8Bytes()
        return try {
            val canonicalAad = CanonicalAad.forKeyBackup(keyId)
            val envelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = privateKeyBytes,
                password = passwordBytes,
                keyId = keyId,
                aad = canonicalAad
            )

            Result.Success(EncryptedBackup.fromEnvelope(envelope))
        } catch (e: Exception) {
            Result.Failure(KeyManagementException("Failed to export encrypted key: ${e.message}", e))
        } finally {
            SecureByteArray.secureZero(privateKeyBytes)
            SecureByteArray.secureZero(passwordBytes)
        }
    }

    override suspend fun importEncryptedKey(
        keyId: String,
        encryptedBackup: EncryptedBackup,
        backupPassword: CharArray,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit> {
        if (keyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyId cannot be blank"))
        }
        if (encryptedBackup.base64Payload.isBlank()) {
            return Result.Failure(IllegalArgumentException("Encrypted backup data cannot be empty"))
        }
        if (backupPassword.isEmpty()) {
            return Result.Failure(IllegalArgumentException("Backup password cannot be empty"))
        }

        val passwordBytes = backupPassword.encodeToUtf8Bytes()
        var decryptedKeyBytes: ByteArray? = null
        return try {
            val expectedAad = CanonicalAad.forKeyBackup(keyId)
            val payload = encryptedBackup.base64Payload

            val envelope = if (VersionedEncryptedEnvelope.isLegacyFormat(payload)) {
                val pwStr = String(backupPassword)
                VersionedEncryptedEnvelope.migrateLegacy(
                    legacyString = payload,
                    password = pwStr,
                    keyId = keyId,
                    aad = expectedAad
                )
            } else {
                VersionedEncryptedEnvelope.deserializeFromBase64(payload)
            }

            // P1-3: 強制檢查 keyId 匹配，防止跨金鑰覆蓋/Relabeling 攻擊
            if (envelope.keyId.isNotEmpty() && envelope.keyId != keyId) {
                return Result.Failure(IllegalArgumentException("Envelope keyId '${envelope.keyId}' does not match target keyId '$keyId'"))
            }

            // P1-3: expected AAD 由調用端獨立重建，不得盲目信任 envelope.aad
            decryptedKeyBytes = envelope.decrypt(passwordBytes, expectedAad = expectedAad)
            storePrivateKey(
                keyId = keyId,
                privateKey = decryptedKeyBytes,
                requireAuth = true,
                authContext = authContext,
                expectedWalletId = expectedWalletId
            )
        } catch (e: Exception) {
            Result.Failure(KeyManagementException("Failed to import encrypted key: ${e.message}", e))
        } finally {
            SecureByteArray.secureZero(passwordBytes)
            decryptedKeyBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    /**
     * 建立用於生物識別認證的 CryptoObject (解密模式)
     */
    fun createCryptoObjectForDecryption(keyId: String): BiometricPrompt.CryptoObject? {
        return try {
            val ivHex = encryptedPrefs.getString(keyId + IV_SUFFIX, null) ?: return null
            val iv = ivHex.hexToByteArray()
            val secretKey = keyStore.getKey(KEY_ALIAS_PREFIX + keyId, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(AUTH_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 建立用於生物識別認證的 CryptoObject (加密模式)
     */
    fun createCryptoObjectForEncryption(keyId: String): BiometricPrompt.CryptoObject? {
        return try {
            val secretKey = getOrCreateSecretKey(keyId, requireAuth = true)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            null
        }
    }

    override fun observeSecurityEvents(): Flow<SecurityEvent> {
        return securityEvents.asSharedFlow()
    }

    // === 私有輔助方法 ===

    private fun getOrCreateSecretKey(keyId: String, requireAuth: Boolean): SecretKey {
        val alias = KEY_ALIAS_PREFIX + keyId

        // 1. 若 Keystore 中已有該 alias，直接載入
        try {
            if (keyStore.containsAlias(alias)) {
                val key = keyStore.getKey(alias, null) as? SecretKey
                if (key != null) return key
            }
        } catch (e: Throwable) {
            if (e is KeyManagementException) throw e
            throw AndroidKeyStoreUnavailableException("Failed to query alias '$alias' from KeyStore: ${e.message}", e)
        }

        // 2. 若提供自訂 secretKeyProvider (例如單元測試環境注入)
        if (secretKeyProvider != null) {
            return try {
                secretKeyProvider.invoke(alias, requireAuth)
            } catch (e: Throwable) {
                if (e is KeyGenerationException) throw e
                throw KeyGenerationException("Custom secretKeyProvider failed for alias '$alias': ${e.message}", e)
            }
        }

        // 3. 生產環境：嚴格使用 AndroidKeyStore KeyGenerator 生成硬體密鑰
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)

        if (config.useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val strongBoxSpec = buildKeyGenSpec(alias, requireAuth, isStrongBox = true)
                keyGenerator.init(strongBoxSpec)
                val key = keyGenerator.generateKey()
                android.util.Log.i("AndroidSecureKeyManager", "KeyStore key '$alias' generated with StrongBox backing")
                return key
            } catch (e: Throwable) {
                if (isStrongBoxUnavailable(e)) {
                    android.util.Log.w("AndroidSecureKeyManager", "StrongBox unavailable for key '$alias' (${e.message}), falling back to hardware TEE")
                } else {
                    throw KeyGenerationException("Failed to generate StrongBox key for alias '$alias': ${e.message}", e)
                }
            }
        }

        // Hardware TEE fallback / generation (Fail-Closed: no software fallback)
        try {
            val teeSpec = buildKeyGenSpec(alias, requireAuth, isStrongBox = false)
            keyGenerator.init(teeSpec)
            val key = keyGenerator.generateKey()
            android.util.Log.i("AndroidSecureKeyManager", "KeyStore key '$alias' generated with TEE hardware backing")
            return key
        } catch (e: Throwable) {
            // 🚨 徹底移除軟體 AES Fallback：嚴禁生成 unpersisted key
            throw KeyGenerationException("Failed to generate AndroidKeyStore key for alias '$alias': ${e.message}", e)
        }
    }

    private fun isStrongBoxUnavailable(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && current is StrongBoxUnavailableException) {
                return true
            }
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("strongbox") && (msg.contains("unavailable") || msg.contains("not supported") || msg.contains("not available"))) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun buildKeyGenSpec(alias: String, requireAuth: Boolean, isStrongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (requireAuth && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    config.userAuthenticationValidityDuration,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
        } else if (requireAuth) {
            builder.setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(config.userAuthenticationValidityDuration)
        }

        if (isStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        if (config.invalidateOnBiometricEnrollment) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        return builder.build()
    }

    private fun isDeviceRooted(): Boolean {
        for (path in ROOT_BINARIES) {
            if (File(path).exists()) {
                return true
            }
        }

        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        return try {
            Runtime.getRuntime().exec("su")
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun emitSecurityEvent(event: SecurityEvent) {
        securityEvents.emit(event)
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun String.hexToByteArray(): ByteArray {
        val clean = removePrefix("0x")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

/**
 * Android 平台的工廠實現
 */
actual class SecureKeyManagerFactory {
    actual companion object {
        actual fun create(config: SecureStorageConfig): SecureKeyManager {
            throw IllegalStateException(
                "On Android, use createWithContext(context, config) instead."
            )
        }

        fun createWithContext(context: Context, config: SecureStorageConfig = SecureStorageConfig()): SecureKeyManager {
            return AndroidSecureKeyManager(context.applicationContext, config)
        }
    }
}
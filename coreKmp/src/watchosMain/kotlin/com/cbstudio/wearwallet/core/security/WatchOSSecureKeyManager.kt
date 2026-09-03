@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.cinterop.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.LocalAuthentication.LAContext
import platform.Security.*
import platform.darwin.noErr

/**
 * watchOS 平台的真 Keychain 安全私鑰管理器實作 (P0-2)
 *
 * 安全規範：
 * 1. 徹底移除記憶體 Map，私鑰一律持久化於 Apple Keychain
 * 2. 存取策略：requireAuth 時使用 kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly，否則使用 kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
 * 3. 簽名後立即執行 SecureByteArray.secureZero 清零記憶體
 * 4. 禁止 getPrivateKey 回傳 Raw Key String（拋出 UnsupportedOperationException / Fail-Closed）
 * 5. import/export 嚴格使用 VersionedEncryptedEnvelope 與 Canonical AAD
 */
class WatchOSSecureKeyManager(
    private val config: SecureStorageConfig = SecureStorageConfig()
) : SecureKeyManager, KeyVaultReconciliationCapability, KeyVaultDeletionCapability {

    companion object {
        private const val SERVICE_NAME = "com.cbstudio.wearwallet.watchos"
        private const val ACCESS_GROUP = "com.cbstudio.wearwallet.keychain"
    }

    private val securityEvents = MutableSharedFlow<SecurityEvent>()
    private val activeSessions = mutableMapOf<String, ProvisioningSession>()
    private val committedKeys = mutableSetOf<String>()

    override suspend fun storePrivateKey(
        keyId: String,
        privateKey: ByteArray,
        requireAuth: Boolean,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit> {
        if (keyId.isBlank() || privateKey.isEmpty()) {
            return Result.Failure(IllegalArgumentException("KeyId and privateKey cannot be blank or empty"))
        }
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }

        var privateKeyBytes: ByteArray? = null
        return try {
            if (requireAuth) {
                val authValidation = validateAndConsumeAuthHandle(
                    authContext = authContext,
                    keyId = keyId,
                    operation = AuthOperation.IMPORT,
                    expectedWalletId = expectedWalletId
                )
                if (authValidation is Result.Failure) {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, Clock.System.now().toEpochMilliseconds()))
                    return authValidation
                }
            }

            privateKeyBytes = privateKey.copyOf()

            val accessControl = if (requireAuth) {
                SecAccessControlCreateWithFlags(
                    null,
                    kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
                    kSecAccessControlUserPresence,
                    null
                ) ?: return Result.Failure(KeyStorageException("Failed to create SecAccessControl with userPresence for watchOS"))
            } else null

            val laContext = authContext?.authHandle?.laContext ?: (authContext?.cryptoObject as? LAContext)
            val nsData = privateKeyBytes.toNSData()

            // 先刪除舊項目（若存在）
            withKeychainQuery({
                set(kSecClass, kSecClassGenericPassword)
                set(kSecAttrService, SERVICE_NAME)
                set(kSecAttrAccount, keyId)
                set(kSecAttrAccessGroup, ACCESS_GROUP)
                if (laContext != null) {
                    set(kSecUseAuthenticationContext, laContext)
                }
            }) { deleteQuery ->
                SecItemDelete(deleteQuery)
            }

            val status = withKeychainQuery({
                set(kSecClass, kSecClassGenericPassword)
                set(kSecAttrService, SERVICE_NAME)
                set(kSecAttrAccount, keyId)
                set(kSecValueData, nsData)
                set(kSecAttrAccessGroup, ACCESS_GROUP)
                if (accessControl != null) {
                    set(kSecAttrAccessControl, accessControl)
                } else {
                    set(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                }
                if (laContext != null) {
                    set(kSecUseAuthenticationContext, laContext)
                }
            }) { addQuery ->
                SecItemAdd(addQuery, null)
            }

            if (status == noErr.toInt()) {
                emitSecurityEvent(SecurityEvent.KeyCreated(keyId, Clock.System.now().toEpochMilliseconds()))
                Result.Success(Unit)
            } else {
                Result.Failure(KeyStorageException("Failed to store key in watchOS Keychain, OSStatus: $status"))
            }
        } catch (e: Exception) {
            Result.Failure(KeyStorageException("watchOS key storage failed: ${e.message}", e))
        } finally {
            privateKeyBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    override suspend fun deletePrivateKey(
        keyId: String,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit> {
        if (keyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyId cannot be blank"))
        }
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }

        val authValidation = validateAndConsumeAuthHandle(
            authContext = authContext,
            keyId = keyId,
            operation = AuthOperation.DELETE,
            expectedWalletId = expectedWalletId
        )
        if (authValidation is Result.Failure) {
            emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, Clock.System.now().toEpochMilliseconds()))
            return authValidation
        }

        return try {
            val laContext = authContext?.authHandle?.laContext ?: (authContext?.cryptoObject as? LAContext)
            val status = withKeychainQuery({
                set(kSecClass, kSecClassGenericPassword)
                set(kSecAttrService, SERVICE_NAME)
                set(kSecAttrAccount, keyId)
                set(kSecAttrAccessGroup, ACCESS_GROUP)
                if (laContext != null) {
                    set(kSecUseAuthenticationContext, laContext)
                }
            }) { deleteQuery ->
                SecItemDelete(deleteQuery)
            }

            if (status == noErr.toInt() || status == errSecItemNotFound.toInt()) {
                emitSecurityEvent(SecurityEvent.KeyDeleted(keyId, Clock.System.now().toEpochMilliseconds()))
                Result.Success(Unit)
            } else {
                emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, Clock.System.now().toEpochMilliseconds()))
                Result.Failure(KeyStorageException("Failed to delete key from watchOS Keychain, OSStatus: $status"))
            }
        } catch (e: KeyManagementException) {
            Result.Failure(e)
        } catch (e: Exception) {
            Result.Failure(KeyStorageException("watchOS delete key failed: ${e.message}", e))
        }
    }

    override suspend fun deletePrivateKeyWithGrant(
        grant: DeletionAuthorizationGrant,
        expectedWalletId: String
    ): Result<Unit> {
        val keyId = grant.keyAlias
        if (keyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyId in grant cannot be blank"))
        }
        val grantConsumeResult = DeletionGrantRegistry.validateAndConsume(
            grant = grant,
            expectedKeyAlias = keyId,
            expectedWalletId = if (expectedWalletId.isNotBlank()) expectedWalletId else grant.walletId
        )
        if (grantConsumeResult is Result.Failure) {
            emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, Clock.System.now().toEpochMilliseconds()))
            return Result.Failure(grantConsumeResult.exception)
        }

        return try {
            val status = withKeychainQuery({
                set(kSecClass, kSecClassGenericPassword)
                set(kSecAttrService, SERVICE_NAME)
                set(kSecAttrAccount, keyId)
                set(kSecAttrAccessGroup, ACCESS_GROUP)
            }) { deleteQuery ->
                SecItemDelete(deleteQuery)
            }

            if (status == noErr.toInt() || status == errSecItemNotFound.toInt()) {
                emitSecurityEvent(SecurityEvent.KeyDeleted(keyId, Clock.System.now().toEpochMilliseconds()))
                Result.Success(Unit)
            } else {
                emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, Clock.System.now().toEpochMilliseconds()))
                Result.Failure(KeyStorageException("Failed to delete key from watchOS Keychain, OSStatus: $status"))
            }
        } catch (e: KeyManagementException) {
            Result.Failure(e)
        } catch (e: Exception) {
            Result.Failure(KeyStorageException("watchOS delete key failed: ${e.message}", e))
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

            val status = withKeychainQuery({
                set(kSecClass, kSecClassGenericPassword)
                set(kSecAttrService, SERVICE_NAME)
                set(kSecAttrAccount, session.stagedKeyAlias)
                set(kSecAttrAccessGroup, ACCESS_GROUP)
            }) { query ->
                SecItemDelete(query)
            }
            emitSecurityEvent(SecurityEvent.KeyDeleted(session.stagedKeyAlias, Clock.System.now().toEpochMilliseconds()))
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

        val grantValidation = RecoveryGrantRegistry.validateAndConsume(grant, stagedKeyAlias)
        if (grantValidation is Result.Failure) {
            return Result.Failure(grantValidation.exception)
        }

        return try {
            activeSessions.remove(sessionId)

            val status = withKeychainQuery({
                set(kSecClass, kSecClassGenericPassword)
                set(kSecAttrService, SERVICE_NAME)
                set(kSecAttrAccount, stagedKeyAlias)
                set(kSecAttrAccessGroup, ACCESS_GROUP)
            }) { query ->
                SecItemDelete(query)
            }
            emitSecurityEvent(SecurityEvent.KeyDeleted(stagedKeyAlias, Clock.System.now().toEpochMilliseconds()))
            Result.Success(Unit)
        } catch (e: KeyManagementException) {
            Result.Failure(e)
        } catch (e: Exception) {
            Result.Failure(KeyStorageException("Failed to rollback staged key internally: ${e.message}", e))
        }
    }

    override suspend fun checkKeyPresence(keyId: String): KeyPresence {
        if (keyId.isBlank()) return KeyPresence.Absent
        val status = withKeychainQuery({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, SERVICE_NAME)
            set(kSecAttrAccount, keyId)
            set(kSecAttrAccessGroup, ACCESS_GROUP)
            set(kSecReturnData, kCFBooleanFalse)
            set(kSecMatchLimit, kSecMatchLimitOne)
        }) { query ->
            SecItemCopyMatching(query, null)
        }
        return when (status) {
            noErr.toInt() -> KeyPresence.Present
            errSecItemNotFound.toInt() -> KeyPresence.Absent
            else -> KeyPresence.Unavailable(KeyStorageException("watchOS Keychain error checking presence for key '$keyId': status=$status"))
        }
    }

    override suspend fun hasPrivateKey(keyId: String): Boolean {
        return checkKeyPresence(keyId) is KeyPresence.Present
    }

    override suspend fun listKeyIds(): List<String> {
        memScoped {
            val resultPtr = alloc<COpaquePointerVar>()
            val status = withKeychainQuery({
                set(kSecClass, kSecClassGenericPassword)
                set(kSecAttrService, SERVICE_NAME)
                set(kSecAttrAccessGroup, ACCESS_GROUP)
                set(kSecReturnAttributes, kCFBooleanTrue)
                set(kSecMatchLimit, kSecMatchLimitAll)
            }) { query ->
                SecItemCopyMatching(query, resultPtr.ptr)
            }
            if (status == noErr.toInt() && resultPtr.value != null) {
                val items = CFBridgingRelease(resultPtr.value) as? NSArray
                val keyIds = mutableListOf<String>()
                if (items != null) {
                    val bridgedAttrAccount = CFBridgingRelease(CFRetain(kSecAttrAccount)) as? platform.darwin.NSObject
                    for (i in 0 until items.count.toInt()) {
                        val item = items.objectAtIndex(i.toULong())
                        val dict = item as? NSDictionary
                        val keyId = if (bridgedAttrAccount != null) {
                            dict?.objectForKey(bridgedAttrAccount) as? String
                        } else null
                        if (keyId != null) {
                            keyIds.add(keyId)
                        }
                    }
                }
                return keyIds
            }
            return emptyList()
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

        if (!hasPrivateKey(keyId)) {
            return Result.Failure(KeyNotFoundException(keyId))
        }

        val digest = if (data.size == 32) data else CryptoUtils.sha256(data)
        val expectedIntent = digest.toHexString()

        val authValidation = validateAndConsumeAuthHandle(
            authContext = authContext,
            keyId = keyId,
            operation = AuthOperation.SIGN,
            expectedIntent = expectedIntent,
            expectedWalletId = expectedWalletId
        )
        if (authValidation is Result.Failure) {
            emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, Clock.System.now().toEpochMilliseconds()))
            return Result.Failure(authValidation.exception)
        }

        var privateKeyBytes: ByteArray? = null
        var normalizedKeyBytes: ByteArray? = null
        return try {
            privateKeyBytes = loadKeyBytesFromKeychain(keyId, authContext)
                ?: run {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, Clock.System.now().toEpochMilliseconds()))
                    return Result.Failure(
                        AuthenticationRequiredException("Authentication failed or required for key '$keyId' on watchOS")
                    )
                }

            emitSecurityEvent(SecurityEvent.KeyAccessed(keyId, Clock.System.now().toEpochMilliseconds()))

            normalizedKeyBytes = if (privateKeyBytes.size == 32) {
                privateKeyBytes.copyOf()
            } else {
                val str = privateKeyBytes.decodeToString().trim().removePrefix("0x")
                if (str.length == 64) {
                    ByteArray(32) { i ->
                        str.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                    }
                } else {
                    return Result.Failure(IllegalArgumentException("Invalid private key length: ${privateKeyBytes.size} bytes"))
                }
            }

            val signature = io.github.iml1s.crypto.Secp256k1Pure.signWithRecovery(digest, normalizedKeyBytes)
            val sigBytes = signature.r + signature.s + byteArrayOf(signature.yParity.toByte())
            Result.Success(sigBytes)
        } catch (e: Exception) {
            Result.Failure(KeyManagementException("watchOS signing failed: ${e.message}", e))
        } finally {
            privateKeyBytes?.let { SecureByteArray.secureZero(it) }
            normalizedKeyBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    override suspend fun revealMnemonic(
        keyId: String,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<ScopedMnemonic> = Result.Failure(UnsupportedOperationException("revealMnemonic not supported directly on WatchOSSecureKeyManager"))

    override suspend fun getSecurityLevel(): SecurityLevel {
        return SecurityLevel(
            level = SecurityLevel.Level.KEYSTORE,
            hasHardwareBacking = false,
            hasStrongBox = false,
            hasBiometricSupport = false, // Apple Watch uses wrist detection/passcode
            isRooted = false
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
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }

        if (!hasPrivateKey(keyId)) {
            return Result.Failure(KeyNotFoundException(keyId))
        }

        val authValidation = validateAndConsumeAuthHandle(
            authContext = authContext,
            keyId = keyId,
            operation = AuthOperation.EXPORT,
            expectedWalletId = expectedWalletId
        )
        if (authValidation is Result.Failure) {
            emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, Clock.System.now().toEpochMilliseconds()))
            return Result.Failure(authValidation.exception)
        }

        var keyBytes: ByteArray? = null
        val passwordBytes = backupPassword.encodeToUtf8Bytes()
        return try {
            keyBytes = loadKeyBytesFromKeychain(keyId, authContext)
                ?: run {
                    emitSecurityEvent(SecurityEvent.AuthenticationFailed(keyId, Clock.System.now().toEpochMilliseconds()))
                    return Result.Failure(
                        AuthenticationRequiredException("Authentication failed or required for exporting key '$keyId' on watchOS")
                    )
                }

            // Canonical AAD context binding (P1-3)
            val canonicalAad = CanonicalAad.forKeyBackup(keyId)

            val envelope = VersionedEncryptedEnvelope.encrypt(
                plaintext = keyBytes,
                password = passwordBytes,
                keyId = keyId,
                aad = canonicalAad
            )
            Result.Success(EncryptedBackup.fromEnvelope(envelope))
        } catch (e: Exception) {
            Result.Failure(KeyManagementException("Failed to export encrypted key: ${e.message}", e))
        } finally {
            keyBytes?.let { SecureByteArray.secureZero(it) }
            SecureByteArray.secureZero(passwordBytes)
        }
    }

    private fun validateAndConsumeAuthHandle(
        authContext: AuthenticationContext?,
        keyId: String,
        operation: AuthOperation,
        expectedIntent: String? = null,
        expectedWalletId: String
    ): Result<Unit> {
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }
        if (authContext == null) {
            return Result.Failure(
                AuthenticationRequiredException("Authentication is required for key '$keyId' but authContext is null")
            )
        }
        return AuthHandleRegistry.validateAndConsume(
            handle = authContext.authHandle,
            expectedKeyId = keyId,
            expectedOperation = operation,
            expectedFingerprint = expectedIntent,
            currentTimeMs = Clock.System.now().toEpochMilliseconds(),
            expectedWalletId = expectedWalletId
        )
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
        if (encryptedBackup.base64Payload.isBlank() || backupPassword.isEmpty()) {
            return Result.Failure(IllegalArgumentException("Backup and password cannot be empty"))
        }
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }

        val passwordBytes = backupPassword.encodeToUtf8Bytes()
        var decryptedBytes: ByteArray? = null
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

            // P1-3: 強制檢查 keyId 匹配，防止跨金鑰覆蓋攻擊
            if (envelope.keyId.isNotEmpty() && envelope.keyId != keyId) {
                return Result.Failure(IllegalArgumentException("Envelope keyId '${envelope.keyId}' does not match target keyId '$keyId'"))
            }

            // P1-3: 外部預期 Canonical AAD 重建驗證
            decryptedBytes = envelope.decrypt(passwordBytes, expectedAad = expectedAad)
            storePrivateKey(
                keyId = keyId,
                privateKey = decryptedBytes,
                requireAuth = true,
                authContext = authContext,
                expectedWalletId = expectedWalletId
            )
        } catch (e: Exception) {
            Result.Failure(KeyManagementException("Failed to import key: ${e.message}", e))
        } finally {
            SecureByteArray.secureZero(passwordBytes)
            decryptedBytes?.let { SecureByteArray.secureZero(it) }
        }
    }

    override fun observeSecurityEvents(): Flow<SecurityEvent> {
        return securityEvents.asSharedFlow()
    }

    private suspend fun emitSecurityEvent(event: SecurityEvent) {
        securityEvents.emit(event)
    }

    private fun loadKeyBytesFromKeychain(
        keyId: String,
        authContext: AuthenticationContext? = null
    ): ByteArray? {
        val laContext = authContext?.authHandle?.laContext ?: (authContext?.cryptoObject as? LAContext)
        return memScoped {
            val resultPtr = alloc<COpaquePointerVar>()
            val status = withKeychainQuery({
                set(kSecClass, kSecClassGenericPassword)
                set(kSecAttrService, SERVICE_NAME)
                set(kSecAttrAccount, keyId)
                set(kSecAttrAccessGroup, ACCESS_GROUP)
                set(kSecReturnData, kCFBooleanTrue)
                set(kSecMatchLimit, kSecMatchLimitOne)
                if (laContext != null) {
                    set(kSecUseAuthenticationContext, laContext)
                }
            }) { query ->
                SecItemCopyMatching(query, resultPtr.ptr)
            }

            if (status == noErr.toInt() && resultPtr.value != null) {
                val data = CFBridgingRelease(resultPtr.value) as? NSData
                if (data != null) {
                    return data.toByteArray()
                }
            }
            return null
        }
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

    private class KeychainQueryBuilder {
        private val dict = NSMutableDictionary()

        fun set(key: CFStringRef?, value: Any?) {
            if (key == null || value == null) return
            val bridgedKey = CFBridgingRelease(CFRetain(key)) ?: return
            val bridgedValue = when (value) {
                is String -> value as NSString
                is Boolean -> if (value) CFBridgingRelease(CFRetain(kCFBooleanTrue)) else CFBridgingRelease(CFRetain(kCFBooleanFalse))
                is CPointer<*> -> CFBridgingRelease(CFRetain(value)) ?: return
                else -> value
            } ?: return
            dict.setObject(bridgedValue, forKeyedSubscript = bridgedKey as platform.Foundation.NSCopyingProtocol)
        }

        fun build(): CFDictionaryRef? {
            return CFBridgingRetain(dict)?.reinterpret()
        }
    }

    private inline fun <R> withKeychainQuery(
        block: KeychainQueryBuilder.() -> Unit,
        action: (CFDictionaryRef?) -> R
    ): R {
        val builder = KeychainQueryBuilder().apply(block)
        val cfDict = builder.build()
        return try {
            action(cfDict)
        } finally {
            if (cfDict != null) {
                CFBridgingRelease(cfDict)
            }
        }
    }
}

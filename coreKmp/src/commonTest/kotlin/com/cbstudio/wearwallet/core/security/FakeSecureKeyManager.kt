package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import io.github.iml1s.crypto.Secp256k1Pure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.withLock

/**
 * 測試專用的 FakeSecureKeyManager
 * 提供確定性 SECP256k1 簽名與私鑰安全隔離。
 */
class FakeSecureKeyManager : SecureKeyManager, KeyVaultReconciliationCapability, KeyVaultDeletionCapability {

    private data class KeyEntry(
        val privateKeyHex: String,
        val requireAuth: Boolean = true
    )

    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val keyMap = mutableMapOf<String, KeyEntry>()
    private val activeSessions = mutableMapOf<String, ProvisioningSession>()
    private val committedKeys = mutableSetOf<String>()
    private val keyPresenceOverrides = mutableMapOf<String, KeyPresence>()
    data class SignCall(val keyId: String, val data: ByteArray, val authContext: AuthenticationContext?)
    val signCalls = mutableListOf<SignCall>()
    private var _signCount = 0
    val signCount: Int
        get() = _signCount
    private var _deleteCount = 0
    val deleteCount: Int
        get() = _deleteCount

    fun resetDeleteCount() {
        _deleteCount = 0
    }

    fun setKeyPresenceOverride(keyId: String, presence: KeyPresence) {
        keyPresenceOverrides[keyId] = presence
    }

    fun clearKeyPresenceOverrides() {
        keyPresenceOverrides.clear()
    }

    fun setKey(keyId: String, privateKeyHex: String, requireAuth: Boolean = false) {
        keyMap[keyId] = KeyEntry(privateKeyHex, requireAuth)
    }

    fun resetSignCount() {
        _signCount = 0
        signCalls.clear()
    }

    override suspend fun storePrivateKey(
        keyId: String,
        privateKey: ByteArray,
        requireAuth: Boolean,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit> = mutex.withLock {
        if (keyId.isBlank()) {
            return@withLock Result.Failure(IllegalArgumentException("keyId must not be blank"))
        }
        if (expectedWalletId.isBlank()) {
            return@withLock Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }
        if (privateKey.isEmpty()) {
            return@withLock Result.Failure(IllegalArgumentException("privateKey must not be empty"))
        }
        if (requireAuth) {
            if (authContext == null) {
                return@withLock Result.Failure(
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
                return@withLock Result.Failure(consumeResult.exception)
            }
        }
        val pkHex = if (privateKey.size == 32) {
            privateKey.toHexString()
        } else {
            privateKey.decodeToString().removePrefix("0x")
        }
        keyMap[keyId] = KeyEntry(pkHex, requireAuth)
        Result.Success(Unit)
    }

    override suspend fun deletePrivateKey(
        keyId: String,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit> = mutex.withLock {
        if (keyId.isBlank()) {
            return@withLock Result.Failure(IllegalArgumentException("keyId must not be blank"))
        }
        if (expectedWalletId.isBlank()) {
            return@withLock Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }
        val entry = keyMap[keyId]
            ?: return@withLock Result.Failure(IllegalArgumentException("No key found for keyId: $keyId"))

        if (entry.requireAuth) {
            if (authContext == null) {
                return@withLock Result.Failure(
                    AuthenticationRequiredException("Authentication is required for key '$keyId' but authContext is null")
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
                return@withLock Result.Failure(consumeResult.exception)
            }
        }

        if (keyMap.containsKey(keyId)) {
            keyMap.remove(keyId)
            _deleteCount++
        }
        Result.Success(Unit)
    }

    override suspend fun deletePrivateKeyWithGrant(
        grant: DeletionAuthorizationGrant,
        expectedWalletId: String
    ): Result<Unit> = mutex.withLock {
        val keyId = grant.keyAlias
        if (keyId.isBlank()) {
            return@withLock Result.Failure(IllegalArgumentException("keyAlias in grant must not be blank"))
        }
        val grantConsumeResult = DeletionGrantRegistry.validateAndConsume(
            grant = grant,
            expectedKeyAlias = keyId,
            expectedWalletId = if (expectedWalletId.isNotBlank()) expectedWalletId else grant.walletId
        )
        if (grantConsumeResult is Result.Failure) {
            return@withLock Result.Failure(grantConsumeResult.exception)
        }
        if (keyMap.containsKey(keyId)) {
            keyMap.remove(keyId)
            _deleteCount++
        }
        Result.Success(Unit)
    }

    /** Test-only TTL override so cold-CI suites are not flaky under 60s production default. */
    var provisioningSessionTtlMs: Long = 600_000L

    override suspend fun startProvisioningSession(): ProvisioningSession = mutex.withLock {
        val session = ProvisioningSession.create(maxValidityDurationMs = provisioningSessionTtlMs)
        activeSessions[session.sessionId] = session
        session
    }

    override suspend fun getActiveProvisioningSession(sessionId: String): ProvisioningSession? = mutex.withLock {
        val session = activeSessions[sessionId] ?: return@withLock null
        if (session.isActive) session else null
    }

    override suspend fun storeStagedPrivateKey(
        sessionId: String,
        stagedKeyAlias: String,
        privateKey: ByteArray,
        requireAuth: Boolean,
        authContext: AuthenticationContext?
    ): Result<Unit> = mutex.withLock {
        val session = activeSessions[sessionId]
        if (session != null) {
            if (session.isCommitted) {
                return@withLock Result.Failure(IllegalStateException("Cannot store key in already committed session: $sessionId"))
            }
            if (session.isRolledBack) {
                return@withLock Result.Failure(IllegalStateException("Cannot store key in already rolled back session: $sessionId"))
            }
            if (!session.isActive) {
                return@withLock Result.Failure(IllegalStateException("Provisioning session $sessionId has expired"))
            }
        }
        if (requireAuth) {
            if (authContext == null) {
                return@withLock Result.Failure(
                    AuthenticationRequiredException("Authentication is required to store staged key for session '$sessionId' but authContext is null")
                )
            }
            val handle = authContext.authHandle
            if (handle == null) {
                return@withLock Result.Failure(
                    AuthenticationRequiredException("Authentication is required to store staged key for session '$sessionId' but authHandle is null")
                )
            }
            if (handle.keyId.isBlank() || handle.keyId != stagedKeyAlias) {
                return@withLock Result.Failure(
                    AuthenticationRequiredException("Cross-key handle rejected: expected keyId '$stagedKeyAlias' but got '${handle.keyId}'")
                )
            }
            if (handle.sessionId.isBlank() || handle.sessionId != sessionId) {
                return@withLock Result.Failure(
                    AuthenticationRequiredException("Session mismatch: expected session '$sessionId' but got '${handle.sessionId}'")
                )
            }
            if (handle.operation != AuthOperation.IMPORT) {
                return@withLock Result.Failure(
                    AuthenticationRequiredException("Auth handle operation '${handle.operation}' does not match expected 'IMPORT'")
                )
            }
            val targetWalletId = handle.walletId.ifBlank { sessionId }
            val consumeResult = AuthHandleRegistry.validateAndConsume(
                handle = handle,
                expectedKeyId = stagedKeyAlias,
                expectedOperation = AuthOperation.IMPORT,
                expectedFingerprint = null,
                currentTimeMs = System.currentTimeMillis(),
                expectedWalletId = targetWalletId
            )
            if (consumeResult is Result.Failure) {
                return@withLock Result.Failure(consumeResult.exception)
            }
        }
        val pkHex = if (privateKey.size == 32) {
            privateKey.toHexString()
        } else {
            privateKey.decodeToString().removePrefix("0x")
        }
        if (session != null) {
            activeSessions[session.sessionId] = session
        }
        keyMap[stagedKeyAlias] = KeyEntry(pkHex, requireAuth)
        Result.Success(Unit)
    }

    override suspend fun storeStagedPrivateKey(
        session: ProvisioningSession,
        privateKey: ByteArray,
        requireAuth: Boolean,
        authContext: AuthenticationContext?
    ): Result<Unit> {
        if (!session.isActive) {
            return Result.Failure(IllegalStateException("Provisioning session ${session.sessionId} has expired"))
        }
        if (session.isCommitted) {
            return Result.Failure(IllegalStateException("Cannot store key in already committed session: ${session.sessionId}"))
        }
        if (session.isRolledBack) {
            return Result.Failure(IllegalStateException("Cannot store key in already rolled back session: ${session.sessionId}"))
        }
        return storeStagedPrivateKey(
            sessionId = session.sessionId,
            stagedKeyAlias = session.stagedKeyAlias,
            privateKey = privateKey,
            requireAuth = requireAuth,
            authContext = authContext
        )
    }

    override suspend fun commitProvisioningSession(session: ProvisioningSession): Result<Unit> = mutex.withLock {
        try {
            session.markCommitted()
            activeSessions.remove(session.sessionId)
            committedKeys.add(session.stagedKeyAlias)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun rollbackProvisioningSession(session: ProvisioningSession): Result<Unit> = mutex.withLock {
        val activeSession = activeSessions[session.sessionId]
            ?: return@withLock Result.Failure(IllegalStateException("Unknown or inactive provisioning session: ${session.sessionId}"))
        if (activeSession.stagedKeyAlias != session.stagedKeyAlias) {
            return@withLock Result.Failure(IllegalStateException("Provisioning session staged key alias mismatch"))
        }
        if (!activeSession.isActive || !session.isActive) {
            activeSessions.remove(session.sessionId)
            return@withLock Result.Failure(IllegalStateException("Provisioning session has expired"))
        }
        if (session.isCommitted || activeSession.isCommitted || committedKeys.contains(session.stagedKeyAlias)) {
            return@withLock Result.Failure(IllegalStateException("Cannot rollback an already committed session or key: ${session.sessionId} / ${session.stagedKeyAlias}"))
        }
        if (session.isRolledBack) {
            return@withLock Result.Success(Unit)
        }
        session.markRolledBack()
        activeSessions.remove(session.sessionId)
        keyMap.remove(session.stagedKeyAlias)
        Result.Success(Unit)
    }

    override suspend fun rollbackStagedKeyInternal(
        grant: RecoveryGrant
    ): Result<Unit> = mutex.withLock {
        val stagedKeyAlias = grant.alias
        val sessionId = grant.sessionId
        if (stagedKeyAlias.isBlank()) {
            return@withLock Result.Failure(IllegalArgumentException("stagedKeyAlias must not be blank"))
        }
        if (sessionId.isBlank()) {
            return@withLock Result.Failure(IllegalArgumentException("sessionId must not be blank"))
        }
        if (committedKeys.contains(stagedKeyAlias)) {
            return@withLock Result.Failure(IllegalStateException("Cannot rollback a committed key: $stagedKeyAlias"))
        }

        val grantValidation = RecoveryGrantRegistry.validateAndConsume(grant, stagedKeyAlias)
        if (grantValidation is Result.Failure) {
            return@withLock Result.Failure(grantValidation.exception)
        }

        activeSessions.remove(sessionId)
        if (keyMap.containsKey(stagedKeyAlias)) {
            keyMap.remove(stagedKeyAlias)
            _deleteCount++
        }
        Result.Success(Unit)
    }

    override suspend fun checkKeyPresence(keyId: String): KeyPresence = mutex.withLock {
        if (keyId.isBlank()) return@withLock KeyPresence.Absent
        keyPresenceOverrides[keyId]?.let { return@withLock it }
        if (keyMap.containsKey(keyId)) KeyPresence.Present else KeyPresence.Absent
    }

    override suspend fun hasPrivateKey(keyId: String): Boolean = checkKeyPresence(keyId) is KeyPresence.Present

    override suspend fun listKeyIds(): List<String> = mutex.withLock {
        keyMap.keys.toList()
    }

    override suspend fun signWithKey(
        keyId: String,
        data: ByteArray,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<ByteArray> {
        if (keyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyId must not be blank"))
        }
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }
        if (data.isEmpty()) {
            return Result.Failure(IllegalArgumentException("Data to sign cannot be empty"))
        }

        val entry = mutex.withLock {
            signCalls.add(SignCall(keyId, data, authContext))
            keyMap[keyId]
        } ?: return Result.Failure(IllegalArgumentException("No key found for keyId: $keyId"))

        if (entry.requireAuth) {
            if (authContext == null) {
                return Result.Failure(
                    AuthenticationRequiredException("Authentication is required for key '$keyId' but authContext is null")
                )
            }
            val expectedDigest = (if (data.size == 32) data else CryptoUtils.sha256(data)).toHexString()
            val handle = authContext.authHandle
            val fingerprint = if (handle?.intentFingerprint.equals(data.toHexString(), ignoreCase = true)) {
                data.toHexString()
            } else {
                expectedDigest
            }
            val consumeResult = AuthHandleRegistry.validateAndConsume(
                handle = handle,
                expectedKeyId = keyId,
                expectedOperation = AuthOperation.SIGN,
                expectedFingerprint = fingerprint,
                currentTimeMs = System.currentTimeMillis(),
                expectedWalletId = expectedWalletId
            )
            if (consumeResult is Result.Failure) {
                return Result.Failure(consumeResult.exception)
            }
        }

        val clean = entry.privateKeyHex.removePrefix("0x")
        if (clean.length != 64) {
            return Result.Failure(IllegalArgumentException("Invalid private key length: ${clean.length}"))
        }

        return try {
            val pkBytes = ByteArray(32) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val sig = Secp256k1Pure.signWithRecovery(data, pkBytes)
            val sigBytes = sig.r + sig.s + byteArrayOf(sig.yParity.toByte())
            mutex.withLock {
                _signCount++
            }
            Result.Success(sigBytes)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun revealMnemonic(
        keyId: String,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<ScopedMnemonic> = Result.Failure(UnsupportedOperationException())

    override suspend fun getSecurityLevel(): SecurityLevel {
        return SecurityLevel(
            level = SecurityLevel.Level.BASIC,
            hasHardwareBacking = false,
            hasStrongBox = false,
            hasBiometricSupport = false,
            isRooted = false
        )
    }

    override suspend fun exportEncryptedKey(
        keyId: String,
        backupPassword: CharArray,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<EncryptedBackup> = Result.Failure(UnsupportedOperationException())

    override suspend fun importEncryptedKey(
        keyId: String,
        encryptedBackup: EncryptedBackup,
        backupPassword: CharArray,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit> = Result.Failure(UnsupportedOperationException())

    override fun observeSecurityEvents(): Flow<SecurityEvent> = emptyFlow()
}

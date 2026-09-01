package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.monero.synchronized
import kotlinx.datetime.Clock

internal data class AuthSessionMetadata(
    val sessionId: String,
    val expiresAtMs: Long,
    val keyId: String,
    val operation: AuthOperation,
    val intentFingerprint: String,
    val walletId: String,
    val issuedAtMs: Long,
    val authenticatorType: String
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(keyId.isNotBlank()) { "keyId must not be blank" }
        require(walletId.isNotBlank()) { "walletId must not be blank" }
        require(authenticatorType.isNotBlank()) { "authenticatorType must not be blank" }
        require(issuedAtMs > 0L) { "issuedAtMs must be positive (got $issuedAtMs)" }
        require(expiresAtMs > issuedAtMs) {
            "expiresAtMs ($expiresAtMs) must be strictly greater than issuedAtMs ($issuedAtMs)"
        }
    }

    val fingerprint: String get() = intentFingerprint
}

/**
 * 進程範圍單次使用認證 Session 註冊表 (Process-Scoped Single-Use Auth Session Registry)
 * 負責追蹤已簽發的有效授權 Session，確保每個 Session 僅能被消費一次（Single Consumption）。
 * 杜絕離線重放攻擊、偽造 Session 以及跨操作重複使用。
 */
internal object AuthHandleRegistry {
    private val lock = Any()
    private val activeSessions = mutableMapOf<String, AuthSessionMetadata>() // sessionId -> Metadata
    private val consumedSessions = mutableMapOf<String, AuthSessionMetadata>() // sessionId -> Metadata

    /**
     * 註冊新簽發的 Session（僅限 Authenticator 簽發時調用）
     */
    internal fun register(
        sessionId: String,
        expiresAtMs: Long,
        keyId: String,
        operation: AuthOperation,
        intentFingerprint: String,
        walletId: String,
        issuedAtMs: Long,
        authenticatorType: String
    ) {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(keyId.isNotBlank()) { "keyId must not be blank" }
        require(walletId.isNotBlank()) { "walletId must not be blank" }
        require(authenticatorType.isNotBlank()) { "authenticatorType must not be blank" }
        require(issuedAtMs > 0L) { "issuedAtMs must be positive (got $issuedAtMs)" }
        require(expiresAtMs > issuedAtMs) {
            "expiresAtMs ($expiresAtMs) must be strictly greater than issuedAtMs ($issuedAtMs)"
        }
        synchronized(lock) {
            if (consumedSessions.containsKey(sessionId)) {
                throw IllegalStateException("Cannot re-register already consumed auth session: $sessionId")
            }
            if (activeSessions.containsKey(sessionId)) {
                throw IllegalStateException("Cannot re-register already active auth session: $sessionId")
            }
            activeSessions[sessionId] = AuthSessionMetadata(
                sessionId = sessionId,
                expiresAtMs = expiresAtMs,
                keyId = keyId,
                operation = operation,
                intentFingerprint = intentFingerprint,
                walletId = walletId,
                issuedAtMs = issuedAtMs,
                authenticatorType = authenticatorType
            )
        }
    }

    /**
     * 檢查 Session 是否處於註冊且未被消費狀態
     */
    internal fun isRegistered(
        sessionId: String,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    ): Boolean {
        if (sessionId.isBlank()) return false
        return synchronized(lock) {
            val meta = activeSessions[sessionId] ?: return@synchronized false
            if (meta.expiresAtMs > 0 && currentTimeMs >= meta.expiresAtMs) {
                activeSessions.remove(sessionId)
                return@synchronized false
            }
            !consumedSessions.containsKey(sessionId)
        }
    }

    /**
     * 檢查 Session 是否已被消費
     */
    internal fun isConsumed(sessionId: String): Boolean {
        if (sessionId.isBlank()) return false
        return synchronized(lock) {
            consumedSessions.containsKey(sessionId)
        }
    }

    /**
     * 獲取已消費 Session 的 Metadata
     */
    internal fun getConsumedSessionMetadata(sessionId: String): AuthSessionMetadata? {
        if (sessionId.isBlank()) return null
        return synchronized(lock) {
            consumedSessions[sessionId]
        }
    }

    /**
     * 獲取活躍 Session 的 Metadata
     */
    internal fun getActiveSessionMetadata(sessionId: String): AuthSessionMetadata? {
        if (sessionId.isBlank()) return null
        return synchronized(lock) {
            activeSessions[sessionId]
        }
    }

    /**
     * 消費並註銷 Session（單次使用）
     */
    internal fun consume(sessionId: String): Boolean {
        if (sessionId.isBlank()) return false
        return synchronized(lock) {
            val meta = activeSessions.remove(sessionId) ?: return@synchronized false
            consumedSessions[sessionId] = meta
            true
        }
    }

    /**
     * 在單一同步鎖中原子性校驗並消費 AuthHandle (Atomic Validation & Single-Use Consumption)
     * 消除 Time-of-Check to Time-of-Use (TOCTOU) 併發多重解密/偽造重放漏洞。
     *
     * 原子校驗項：
     * 1. handle 非空且未被手動作廢 (_isInvalidated == false)
     * 2. sessionId 非空、已於 activeSessions 註冊且未被消費 (consumedSessions.contains == false)
     * 3. handle 未過期 (expiresAtMs <= 0 || now <= expiresAtMs) 且非未來簽發 (issuedAtMs <= 0 || now >= issuedAtMs)
     * 4. keyId 精確吻合 expectedKeyId (拒絕空字串、跨金鑰或萬用字元)
     * 5. operation 精確吻合 expectedOperation
     * 6. intentFingerprint 若指定則必須忽略大小寫精確比對
     * 7. ProofTokenVerifier.verify 驗證進程級 HMAC-SHA256 防偽簽名
     *
     * 若全數通過，則在同一鎖內立即將 sessionId 自 activeSessions 移除並加入 consumedSessions，
     * 並回傳 Result.Success(Unit)；若有任何一項不符合，則回傳 Result.Failure(AuthenticationRequiredException(...))。
     */
    internal fun validateAndConsume(
        handle: PlatformAuthHandle?,
        expectedKeyId: String,
        expectedOperation: AuthOperation,
        expectedFingerprint: String? = null,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds(),
        expectedWalletId: String
    ): Result<Unit> {
        if (expectedKeyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedKeyId must not be blank"))
        }
        if (expectedWalletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedWalletId must not be blank"))
        }
        if (handle == null) {
            return Result.Failure(
                AuthenticationRequiredException("Authentication is required for key '$expectedKeyId' but authHandle is null")
            )
        }

        return synchronized(lock) {
            val sessionId = handle.sessionId
            if (sessionId.isBlank()) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle sessionId is blank for key '$expectedKeyId'")
                )
            }

            if (handle.isInvalidated || consumedSessions.containsKey(sessionId)) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle session '$sessionId' for key '$expectedKeyId' is invalidated or already consumed")
                )
            }

            val meta = activeSessions[sessionId]
                ?: return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle session '$sessionId' is not registered in active sessions")
                )

            if (meta.expiresAtMs > 0 && currentTimeMs >= meta.expiresAtMs) {
                activeSessions.remove(sessionId)
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle session '$sessionId' for key '$expectedKeyId' has expired")
                )
            }

            if (handle.isExpired(currentTimeMs)) {
                activeSessions.remove(sessionId)
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle for key '$expectedKeyId' has expired (currentTime: $currentTimeMs, expiresAt: ${handle.expiresAtMs})")
                )
            }

            if (handle.issuedAtMs > 0 && currentTimeMs < handle.issuedAtMs) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle for key '$expectedKeyId' is not yet valid (issued in future: ${handle.issuedAtMs})")
                )
            }

            if (handle.keyId.isBlank() || handle.keyId != expectedKeyId) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Cross-key handle rejected (Cross-key auth handle rejected): expected keyId '$expectedKeyId' but got '${handle.keyId}'")
                )
            }

            if (handle.operation != expectedOperation) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle operation '${handle.operation}' does not match expected '$expectedOperation' (expected $expectedOperation)")
                )
            }

            if (meta.keyId != expectedKeyId) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Cross-key session mismatch: expected '$expectedKeyId' but session bound to '${meta.keyId}'")
                )
            }

            if (meta.operation != expectedOperation) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Operation mismatch: expected $expectedOperation but session is for ${meta.operation}")
                )
            }

            if (expectedFingerprint != null && !handle.intentFingerprint.equals(expectedFingerprint, ignoreCase = true)) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Intent fingerprint mismatch: expected '$expectedFingerprint' but got '${handle.intentFingerprint}'")
                )
            }

            // 嚴格校驗 walletId 綁定：Handle 與 Session 註冊之 walletId 必須與傳入的 expectedWalletId 完全相符（禁止 keyAlias fallback）
            if (handle.walletId != expectedWalletId) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Cross-wallet handle rejected: expected walletId '$expectedWalletId' but handle was issued for '${handle.walletId}'")
                )
            }
            if (meta.walletId != expectedWalletId) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Cross-wallet session mismatch: expected '$expectedWalletId' but session bound to '${meta.walletId}'")
                )
            }

            val isProofValid = ProofTokenVerifier.verify(
                proofToken = handle.proofToken,
                keyId = handle.keyId,
                operation = handle.operation,
                intentFingerprint = handle.intentFingerprint,
                sessionId = handle.sessionId,
                nonce = handle.nonce,
                issuedAtMs = handle.issuedAtMs,
                expiresAtMs = handle.expiresAtMs,
                walletId = handle.walletId
            )

            if (!isProofValid) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Proof token verification failed for key '$expectedKeyId'")
                )
            }

            // 原子性消費
            activeSessions.remove(sessionId)
            consumedSessions[sessionId] = meta
            Result.Success(Unit)
        }
    }

    /**
     * 在單一同步鎖中原子性校驗 DELETE AuthHandle、消費該 Handle 並簽發受保護的 DeletionAuthorizationGrant
     */
    internal fun validateConsumeAndIssueGrant(
        handle: PlatformAuthHandle?,
        walletId: String,
        expectedKeyId: String,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    ): Result<DeletionAuthorizationGrant> {
        if (expectedKeyId.isBlank()) {
            return Result.Failure(IllegalArgumentException("expectedKeyId must not be blank"))
        }
        if (walletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("walletId must not be blank"))
        }
        if (handle == null) {
            return Result.Failure(
                AuthenticationRequiredException("Authentication is required for key '$expectedKeyId' but authHandle is null")
            )
        }

        return synchronized(lock) {
            val sessionId = handle.sessionId
            if (sessionId.isBlank()) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle sessionId is blank for key '$expectedKeyId'")
                )
            }

            if (handle.isInvalidated || consumedSessions.containsKey(sessionId)) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle session '$sessionId' for key '$expectedKeyId' is invalidated or already consumed")
                )
            }

            val meta = activeSessions[sessionId]
                ?: return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle session '$sessionId' is not registered in active sessions")
                )

            if (meta.expiresAtMs > 0 && currentTimeMs >= meta.expiresAtMs) {
                activeSessions.remove(sessionId)
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle session '$sessionId' for key '$expectedKeyId' has expired")
                )
            }

            if (handle.isExpired(currentTimeMs)) {
                activeSessions.remove(sessionId)
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle for key '$expectedKeyId' has expired (currentTime: $currentTimeMs, expiresAt: ${handle.expiresAtMs})")
                )
            }

            if (handle.issuedAtMs > 0 && currentTimeMs < handle.issuedAtMs) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle for key '$expectedKeyId' is not yet valid (issued in future: ${handle.issuedAtMs})")
                )
            }

            if (handle.keyId.isBlank() || handle.keyId != expectedKeyId) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Cross-key handle rejected (Cross-key auth handle rejected): expected keyId '$expectedKeyId' but got '${handle.keyId}'")
                )
            }

            if (handle.operation != AuthOperation.DELETE) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth handle operation '${handle.operation}' is not DELETE (expected ${AuthOperation.DELETE})")
                )
            }

            if (meta.keyId != expectedKeyId) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Cross-key session mismatch: expected '$expectedKeyId' but session bound to '${meta.keyId}'")
                )
            }

            if (meta.operation != AuthOperation.DELETE) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Auth session registered operation '${meta.operation}' is not DELETE")
                )
            }

            // 嚴格校驗 walletId 綁定：Handle 與 Session 註冊之 walletId 必須與傳入的 walletId 完全相符
            if (handle.walletId != walletId) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Cross-wallet handle rejected: expected walletId '$walletId' but handle was issued for '${handle.walletId}'")
                )
            }
            if (meta.walletId != walletId) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Cross-wallet session mismatch: expected '$walletId' but session bound to '${meta.walletId}'")
                )
            }

            val isProofValid = ProofTokenVerifier.verify(
                proofToken = handle.proofToken,
                keyId = handle.keyId,
                operation = handle.operation,
                intentFingerprint = handle.intentFingerprint,
                sessionId = handle.sessionId,
                nonce = handle.nonce,
                issuedAtMs = handle.issuedAtMs,
                expiresAtMs = handle.expiresAtMs,
                walletId = handle.walletId
            )

            if (!isProofValid) {
                return@synchronized Result.Failure(
                    AuthenticationRequiredException("Proof token verification failed for key '$expectedKeyId'")
                )
            }

            // 原子性消費 Handle
            activeSessions.remove(sessionId)
            consumedSessions[sessionId] = meta

            // 簽發 Grant
            val grantNonce = CryptoUtils.randomBytes(16).toHexString()
            val grantHmac = DeletionGrantVerifier.sign(
                walletId = walletId,
                keyAlias = expectedKeyId,
                operation = AuthOperation.DELETE,
                originalSessionId = sessionId,
                nonce = grantNonce,
                issuedAtMs = currentTimeMs,
                expiresAtMs = handle.expiresAtMs
            )

            val grant = DeletionAuthorizationGrant(
                walletId = walletId,
                keyAlias = expectedKeyId,
                operation = AuthOperation.DELETE,
                originalAuthSessionId = sessionId,
                issuedAtMs = currentTimeMs,
                expiresAtMs = handle.expiresAtMs,
                nonce = grantNonce,
                proofToken = grantHmac
            )

            // 註冊 Grant 至 DeletionGrantRegistry
            DeletionGrantRegistry.register(grant)

            Result.Success(grant)
        }
    }

    /**
     * 測試或重置使用
     */
    internal fun clearForTesting() {
        synchronized(lock) {
            activeSessions.clear()
            consumedSessions.clear()
        }
    }
}

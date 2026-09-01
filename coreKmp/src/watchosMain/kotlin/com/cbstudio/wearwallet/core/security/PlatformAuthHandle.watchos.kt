package com.cbstudio.wearwallet.core.security

import kotlinx.datetime.Clock
import platform.LocalAuthentication.LAContext

/**
 * watchOS 平台特定認證句柄
 *
 * 安全規範：
 * 1. 內建 proofToken 與 AuthHandleRegistry，校驗不可偽造性與單次使用性。
 * 2. isValid 嚴格比對：禁止 keyId / intentFingerprint 空白繞過漏洞。
 * 3. 支援單次使用 invalidate() 作廢，防止重放攻擊。
 * 4. 建構子為 internal，嚴格由 WatchOSPlatformAuthenticator 或內部測試認證器簽發。
 */
actual class PlatformAuthHandle internal actual constructor(
    actual val keyId: String,
    actual val operation: AuthOperation,
    actual val intentFingerprint: String,
    actual val sessionId: String,
    actual val nonce: String,
    actual val issuedAtMs: Long,
    actual val expiresAtMs: Long,
    actual val proofToken: String,
    actual val walletId: String
) {
    init {
        require(walletId.isNotBlank()) { "walletId must not be blank" }
    }

    var laContext: LAContext? = null
        internal set

    private var _isInvalidated: Boolean = false

    actual val isInvalidated: Boolean
        get() = _isInvalidated || AuthHandleRegistry.isConsumed(sessionId)

    actual fun invalidate() {
        _isInvalidated = true
        AuthHandleRegistry.consume(sessionId)
    }

    actual fun isExpired(currentTimeMs: Long): Boolean {
        return expiresAtMs > 0 && currentTimeMs >= expiresAtMs
    }

    actual fun isValid(
        expectedKeyId: String?,
        expectedIntentFingerprint: String?,
        expectedOperation: AuthOperation?,
        currentTimeMs: Long,
        expectedWalletId: String?
    ): Boolean {
        if (_isInvalidated) return false
        if (AuthHandleRegistry.isConsumed(sessionId)) return false
        if (isExpired(currentTimeMs)) return false
        if (issuedAtMs > 0 && currentTimeMs < issuedAtMs) return false
        if (keyId.isBlank()) return false
        if (expectedKeyId != null && keyId != expectedKeyId) return false
        if (expectedIntentFingerprint != null && !intentFingerprint.equals(expectedIntentFingerprint, ignoreCase = true)) return false
        if (expectedOperation != null && operation != expectedOperation) return false
        if (expectedWalletId != null && walletId != expectedWalletId) return false
        if (!AuthHandleRegistry.isRegistered(sessionId)) return false
        return ProofTokenVerifier.verify(
            proofToken = proofToken,
            keyId = keyId,
            operation = operation,
            intentFingerprint = intentFingerprint,
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = issuedAtMs,
            expiresAtMs = expiresAtMs,
            walletId = walletId
        )
    }

    internal companion object {
        internal fun createInternal(
            keyId: String,
            operation: AuthOperation,
            intentFingerprint: String = "",
            sessionId: String = CryptoUtils.randomBytes(16).toHexString(),
            validityDurationMs: Long = 10_000L,
            expiresAtMs: Long? = null,
            walletId: String,
            laContext: LAContext? = null,
            authenticatorType: String = "WATCHOS_LOCAL_AUTHENTICATION"
        ): PlatformAuthHandle {
            require(walletId.isNotBlank()) { "walletId must not be blank" }
            val now = Clock.System.now().toEpochMilliseconds()
            val expiresAt = expiresAtMs ?: if (validityDurationMs > 0) now + validityDurationMs else 0L
            val nonce = CryptoUtils.randomBytes(16).toHexString()
            val token = if (keyId.isNotBlank() && sessionId.isNotBlank() && nonce.isNotBlank()) {
                ProofTokenVerifier.sign(
                    keyId = keyId,
                    operation = operation,
                    intentFingerprint = intentFingerprint,
                    sessionId = sessionId,
                    nonce = nonce,
                    issuedAtMs = now,
                    expiresAtMs = expiresAt,
                    walletId = walletId,
                    authenticatorType = authenticatorType
                )
            } else {
                "invalid_token"
            }
            return PlatformAuthHandle(
                keyId = keyId,
                operation = operation,
                intentFingerprint = intentFingerprint,
                sessionId = sessionId,
                nonce = nonce,
                issuedAtMs = now,
                expiresAtMs = expiresAt,
                proofToken = token,
                walletId = walletId
            ).apply {
                this.laContext = laContext
            }
        }
    }
}
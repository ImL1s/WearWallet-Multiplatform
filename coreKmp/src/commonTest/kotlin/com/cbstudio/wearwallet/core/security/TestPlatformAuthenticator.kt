package com.cbstudio.wearwallet.core.security

import kotlinx.datetime.Clock

/**
 * 測試專用認證器 (Test Platform Authenticator)
 * 僅供單元測試與整合測試模擬硬體/生物識別認證成功時簽發真實有效的 PlatformAuthHandle。
 * 遵循完整之 ProofToken 簽發與 Session 註冊機制，杜絕生產代碼直接調用未授權構造。
 */
object TestPlatformAuthenticator {
    fun issueHandle(
        keyId: String,
        operation: AuthOperation = AuthOperation.SIGN,
        intentFingerprint: String = "",
        validityDurationMs: Long = 60_000L,
        sessionId: String = CryptoUtils.randomBytes(16).toHexString(),
        expiresAtMs: Long? = null,
        walletId: String = if (keyId.isNotBlank()) keyId else if (sessionId.isNotBlank()) sessionId else "test_wallet_default",
        issuedAtMs: Long? = null
    ): PlatformAuthHandle {
        require(keyId.isNotBlank()) { "keyId must not be blank" }
        require(walletId.isNotBlank()) { "walletId must not be blank" }
        val now = issuedAtMs ?: if (expiresAtMs != null && validityDurationMs > 0 && expiresAtMs - validityDurationMs <= Clock.System.now().toEpochMilliseconds()) {
            expiresAtMs - validityDurationMs
        } else {
            Clock.System.now().toEpochMilliseconds()
        }
        val expiresAt = expiresAtMs ?: if (validityDurationMs > 0) now + validityDurationMs else 0L
        val nonce = CryptoUtils.randomBytes(16).toHexString()
        val token = ProofTokenVerifier.sign(
            keyId = keyId,
            operation = operation,
            intentFingerprint = intentFingerprint,
            sessionId = sessionId,
            nonce = nonce,
            issuedAtMs = now,
            expiresAtMs = expiresAt,
            walletId = walletId,
            authenticatorType = "TEST_AUTHENTICATOR"
        )
        if (sessionId.isNotBlank() && keyId.isNotBlank() && walletId.isNotBlank() && now > 0L && expiresAt > now && !AuthHandleRegistry.isRegistered(sessionId)) {
            AuthHandleRegistry.register(
                sessionId = sessionId,
                expiresAtMs = expiresAt,
                keyId = keyId,
                operation = operation,
                intentFingerprint = intentFingerprint,
                walletId = walletId,
                issuedAtMs = now,
                authenticatorType = "TEST_AUTHENTICATOR"
            )
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
        )
    }
}

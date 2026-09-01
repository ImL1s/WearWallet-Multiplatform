package com.cbstudio.wearwallet.core.security

import androidx.biometric.BiometricPrompt

/**
 * Android 專屬之 TestPlatformAuthenticator 擴充方法，支援附加 CryptoObject。
 */
fun TestPlatformAuthenticator.issueHandle(
    keyId: String,
    operation: AuthOperation = AuthOperation.SIGN,
    intentFingerprint: String = "",
    validityDurationMs: Long = 10_000L,
    sessionId: String = CryptoUtils.randomBytes(16).toHexString(),
    expiresAtMs: Long? = null,
    cryptoObject: BiometricPrompt.CryptoObject?,
    walletId: String = if (keyId.isNotBlank()) keyId else "test_wallet_default",
    issuedAtMs: Long? = null
): PlatformAuthHandle {
    return issueHandle(
        keyId = keyId,
        operation = operation,
        intentFingerprint = intentFingerprint,
        validityDurationMs = validityDurationMs,
        sessionId = sessionId,
        expiresAtMs = expiresAtMs,
        walletId = walletId,
        issuedAtMs = issuedAtMs
    ).apply {
        this.cryptoObject = cryptoObject
    }
}

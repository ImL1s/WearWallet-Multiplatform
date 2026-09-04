package com.cbstudio.wearwallet.core.security

import androidx.biometric.BiometricPrompt

/**
 * Android 專屬認證器服務 (Android Platform Authenticator Service)
 * 統一簽發不可偽造之 PlatformAuthHandle，嚴格綁定硬體生物識別/設備憑證證明。
 */
object AndroidPlatformAuthenticator {

    /**
     * 於生物識別或設備認證成功後簽發 PlatformAuthHandle（強制要求非空 AuthenticationResult 與 walletId）
     */
    fun issueHandle(
        keyId: String,
        operation: AuthOperation,
        authenticationResult: BiometricPrompt.AuthenticationResult,
        walletId: String,
        intentFingerprint: String = "",
        validityDurationMs: Long = 10_000L,
        sessionId: String = CryptoUtils.randomBytes(16).toHexString()
    ): PlatformAuthHandle {
        require(keyId.isNotBlank()) { "keyId must not be blank" }
        require(walletId.isNotBlank()) { "walletId must not be blank" }
        return PlatformAuthHandle.createInternal(
            keyId = keyId,
            operation = operation,
            intentFingerprint = intentFingerprint,
            sessionId = sessionId,
            validityDurationMs = validityDurationMs,
            walletId = walletId,
            cryptoObject = authenticationResult.cryptoObject,
            authenticatorType = "ANDROID_BIOMETRIC"
        )
    }
}


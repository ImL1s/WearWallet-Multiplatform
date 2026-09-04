package com.cbstudio.wearwallet.core.security

import androidx.biometric.BiometricPrompt
import kotlinx.datetime.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Test fixture for Wear OS UI layer unit tests.
 * Issues genuine PlatformAuthHandle with real ProofToken HMAC and AuthHandleRegistry registration
 * via reflection, allowing wear unit tests to execute complete cryptographic validation
 * while maintaining strict production visibility boundaries.
 */
object TestPlatformAuthenticator {
    private val sessionCounter = AtomicLong(0)

    fun nextSessionId(): String = "test_session_${UUID.randomUUID()}_${sessionCounter.incrementAndGet()}_${System.nanoTime()}"
    fun nextNonce(): String = "nonce_${UUID.randomUUID()}_${sessionCounter.incrementAndGet()}_${System.nanoTime()}"

    fun issueHandle(
        keyId: String = "test_key",
        operation: AuthOperation = AuthOperation.SIGN,
        intentFingerprint: String = "",
        validityDurationMs: Long = 60_000L,
        sessionId: String = nextSessionId(),
        expiresAtMs: Long? = null,
        cryptoObject: Any? = null,
        walletId: String = if (keyId.isNotBlank()) keyId else "test_wallet_default"
    ): PlatformAuthHandle {
        val now = if (expiresAtMs != null && validityDurationMs > 0 && expiresAtMs - validityDurationMs <= Clock.System.now().toEpochMilliseconds()) {
            expiresAtMs - validityDurationMs
        } else if (expiresAtMs != null && expiresAtMs <= Clock.System.now().toEpochMilliseconds()) {
            expiresAtMs - 10_000L
        } else {
            Clock.System.now().toEpochMilliseconds()
        }
        val calculatedExpiresAt = expiresAtMs ?: if (validityDurationMs > 0) now + validityDurationMs else 0L
        val nonce = nextNonce()

        // 1. Invoke ProofTokenVerifier.sign via reflection to generate real HMAC and register session
        val verifierClass = Class.forName("com.cbstudio.wearwallet.core.security.ProofTokenVerifier")
        val instanceField = verifierClass.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        val verifierInstance = instanceField.get(null)

        val signMethod = verifierClass.declaredMethods.first { it.name.startsWith("sign") && (it.parameterCount == 8 || it.parameterCount == 9) }
        signMethod.isAccessible = true
        val token = if (signMethod.parameterCount == 9) {
            signMethod.invoke(
                verifierInstance,
                keyId,
                operation,
                intentFingerprint,
                sessionId,
                nonce,
                now,
                calculatedExpiresAt,
                walletId,
                "TEST_WEAR_UI"
            ) as String
        } else {
            signMethod.invoke(
                verifierInstance,
                keyId,
                operation,
                intentFingerprint,
                sessionId,
                nonce,
                now,
                calculatedExpiresAt,
                walletId
            ) as String
        }

        // 2. Construct genuine PlatformAuthHandle via reflection
        val constructor = PlatformAuthHandle::class.java.getDeclaredConstructor(
            String::class.java,
            AuthOperation::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            String::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        val handle = constructor.newInstance(
            keyId,
            operation,
            intentFingerprint,
            sessionId,
            nonce,
            now,
            calculatedExpiresAt,
            token,
            walletId
        )

        // 3. Attach optional CryptoObject
        if (cryptoObject is BiometricPrompt.CryptoObject) {
            val cryptoField = PlatformAuthHandle::class.java.getDeclaredField("cryptoObject")
            cryptoField.isAccessible = true
            cryptoField.set(handle, cryptoObject)
        }

        return handle
    }
}

fun TestPlatformAuthenticator.issueHandle(
    keyId: String,
    operation: AuthOperation = AuthOperation.SIGN,
    intentFingerprint: String = "",
    validityDurationMs: Long = 10_000L,
    sessionId: String = TestPlatformAuthenticator.nextSessionId(),
    expiresAtMs: Long? = null,
    cryptoObject: BiometricPrompt.CryptoObject?,
    walletId: String = keyId
): PlatformAuthHandle {
    return issueHandle(
        keyId = keyId,
        operation = operation,
        intentFingerprint = intentFingerprint,
        validityDurationMs = validityDurationMs,
        sessionId = sessionId,
        expiresAtMs = expiresAtMs,
        cryptoObject = cryptoObject as Any?,
        walletId = walletId
    )
}
